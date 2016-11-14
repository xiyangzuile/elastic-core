/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Collections;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import nxt.crypto.Crypto;
import nxt.util.Convert;

public interface Attachment extends Appendix {
	TransactionType getTransactionType();

	abstract class AbstractAttachment extends Appendix.AbstractAppendix implements Attachment {

		private AbstractAttachment(ByteBuffer buffer, byte transactionVersion) {
			super(buffer, transactionVersion);
		}

		private AbstractAttachment(JSONObject attachmentData) {
			super(attachmentData);
		}

		private AbstractAttachment(int version) {
			super(version);
		}

		private AbstractAttachment() {
		}

		@Override
		final String getAppendixName() {
			return getTransactionType().getName();
		}

		@Override
		final void validate(Transaction transaction) throws NxtException.ValidationException {
			getTransactionType().validateAttachment(transaction);
		}

		@Override
		final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
			getTransactionType().apply((TransactionImpl) transaction, senderAccount, recipientAccount);
		}

		@Override
		public final Fee getBaselineFee(Transaction transaction) {
			return getTransactionType().getBaselineFee(transaction);
		}

		@Override
		public final Fee getNextFee(Transaction transaction) {
			return getTransactionType().getNextFee(transaction);
		}

		@Override
		public final int getBaselineFeeHeight() {
			return getTransactionType().getBaselineFeeHeight();
		}

		@Override
		public final int getNextFeeHeight() {
			return getTransactionType().getNextFeeHeight();
		}

		final int getFinishValidationHeight(Transaction transaction) {
			return Nxt.getBlockchain().getHeight();
		}

	}

	abstract class EmptyAttachment extends AbstractAttachment {

		private EmptyAttachment() {
			super(0);
		}

		@Override
		final int getMySize() {
			return 0;
		}

		@Override
		final void putMyBytes(ByteBuffer buffer) {
		}

		@Override
		final void putMyJSON(JSONObject json) {
		}

		@Override
		final boolean verifyVersion(byte transactionVersion) {
			// FIXME (Schauen wieso das vorher nur alte blocks gecheckt hat. ist
			// das attaCHMENT obsolet?)
			return getVersion() == 0;
		}

	}

	EmptyAttachment ORDINARY_PAYMENT = new EmptyAttachment() {

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.Payment.ORDINARY;
		}

	};

	// the message payload is in the Appendix
	EmptyAttachment ARBITRARY_MESSAGE = new EmptyAttachment() {

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.Messaging.ARBITRARY_MESSAGE;
		}

	};

	final class MessagingHubAnnouncement extends AbstractAttachment {

		private final long minFeePerByteNQT;
		private final String[] uris;

		MessagingHubAnnouncement(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.minFeePerByteNQT = buffer.getLong();
			int numberOfUris = buffer.get();
			if (numberOfUris > Constants.MAX_HUB_ANNOUNCEMENT_URIS) {
				throw new NxtException.NotValidException("Invalid number of URIs: " + numberOfUris);
			}
			this.uris = new String[numberOfUris];
			for (int i = 0; i < uris.length; i++) {
				uris[i] = Convert.readString(buffer, buffer.getShort(), Constants.MAX_HUB_ANNOUNCEMENT_URI_LENGTH);
			}
		}

		MessagingHubAnnouncement(JSONObject attachmentData) throws NxtException.NotValidException {
			super(attachmentData);
			this.minFeePerByteNQT = (Long) attachmentData.get("minFeePerByte");
			try {
				JSONArray urisData = (JSONArray) attachmentData.get("uris");
				this.uris = new String[urisData.size()];
				for (int i = 0; i < uris.length; i++) {
					uris[i] = (String) urisData.get(i);
				}
			} catch (RuntimeException e) {
				throw new NxtException.NotValidException("Error parsing hub terminal announcement parameters", e);
			}
		}

		public MessagingHubAnnouncement(long minFeePerByteNQT, String[] uris) {
			this.minFeePerByteNQT = minFeePerByteNQT;
			this.uris = uris;
		}

		@Override
		int getMySize() {
			int size = 8 + 1;
			for (String uri : uris) {
				size += 2 + Convert.toBytes(uri).length;
			}
			return size;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			buffer.putLong(minFeePerByteNQT);
			buffer.put((byte) uris.length);
			for (String uri : uris) {
				byte[] uriBytes = Convert.toBytes(uri);
				buffer.putShort((short) uriBytes.length);
				buffer.put(uriBytes);
			}
		}

		@Override
		void putMyJSON(JSONObject attachment) {
			attachment.put("minFeePerByteNQT", minFeePerByteNQT);
			JSONArray uris = new JSONArray();
			Collections.addAll(uris, this.uris);
			attachment.put("uris", uris);
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.Messaging.HUB_ANNOUNCEMENT;
		}

		public long getMinFeePerByteNQT() {
			return minFeePerByteNQT;
		}

		public String[] getUris() {
			return uris;
		}

	}

	public final static class WorkIdentifierCancellationRequest extends AbstractAttachment {

		public long getWorkId() {
			return workId;
		}

		private final long workId;

		WorkIdentifierCancellationRequest(ByteBuffer buffer, byte transactionVersion)
				throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workId = buffer.getLong();
		}

		WorkIdentifierCancellationRequest(JSONObject attachmentData) {
			super(attachmentData);
			this.workId = Convert.parseUnsignedLong((String) attachmentData.get("id"));
		}

		public WorkIdentifierCancellationRequest(long workId) {
			this.workId = workId;
		}

		@Override
		int getMySize() {
			return 8;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			buffer.putLong(this.workId);
		}

		@Override
		void putMyJSON(JSONObject attachment) {
			attachment.put("id", Convert.toUnsignedLong(this.workId));
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.CANCEL_TASK_REQUEST;
		}
	}
	
	public final static class RedeemAttachment extends AbstractAttachment {

		private short address_length;
		private short secp_length;
		private String address;
		private String secp_signatures;

		RedeemAttachment(ByteBuffer buffer, byte transactionVersion)
				throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.address_length = buffer.getShort();
			this.address = Convert.readString(buffer, address_length, 4096);
			this.secp_length = buffer.getShort();
			this.secp_signatures = Convert.readString(buffer, secp_length, 10400);
		}

		RedeemAttachment(JSONObject attachmentData) {
			super(attachmentData);
			this.address = (String) attachmentData.get("address");
			this.address_length = (short) this.address.length();
			this.secp_signatures = (String) attachmentData.get("secp_signatures");
			this.secp_length = (short) this.secp_signatures.length();
		}

		public RedeemAttachment(String address, String secp_signatures) {
			this.address = address;
			this.address_length = (short) address.length();
			this.secp_signatures = secp_signatures;
			this.secp_length = (short) this.secp_signatures.length();
		}

		@Override
		int getMySize() {
			return 2 + 2 + 8 + Convert.toBytes(this.address).length + Convert.toBytes(this.secp_signatures).length;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			buffer.putShort((short) Convert.toBytes(this.address).length);
			byte[] byteAddr = Convert.toBytes(this.address);
			byte[] byteSecp = Convert.toBytes(this.secp_signatures);
			buffer.put(byteAddr);
			buffer.putShort((short) Convert.toBytes(this.secp_signatures).length);
			buffer.put(byteSecp);
		}

		public String getAddress() {
			return address;
		}

		public String getSecp_signatures() {
			return secp_signatures;
		}

		

		@Override
		void putMyJSON(JSONObject attachment) {
			attachment.put("address", this.address);
			attachment.put("secp_signatures", this.secp_signatures);
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.Payment.REDEEM;
		}
	}
	

	public final static class PiggybackedProofOfWork extends AbstractAttachment implements Hashable {

		public long getWorkId() {
			return workId;
		}

		public int toInt(byte[] bytes, int offset) {
			int ret = 0;
			for (int i = 0; i < 4 && i + offset < bytes.length; i++) {
				ret <<= 8;
				ret |= (int) bytes[i + offset] & 0xFF;
			}
			return ret;
		}

		private final long workId;
		private final byte[] multiplicator;
		final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
		public static MessageDigest dig = Crypto.md5();
		public static String bytesToHex(byte[] bytes) {
			char[] hexChars = new char[bytes.length * 2];
			for (int j = 0; j < bytes.length; j++) {
				int v = bytes[j] & 0xFF;
				hexChars[j * 2] = hexArray[v >>> 4];
				hexChars[j * 2 + 1] = hexArray[v & 0x0F];
			}
			return new String(hexChars);
		}

		public int[] personalizedIntStream(byte[] publicKey, long blockId){
			int[] stream = new int[12];
	    	
	    	
	    	
	    	dig.reset();
	    	dig.update(multiplicator);
	    	dig.update(publicKey);
	    	
	    	byte[] b1 = new byte[16];
	    	for (int i = 0; i < 8; ++i) {
	    	  b1[i] = (byte) (workId >> (8 - i - 1 << 3));
	    	}
	    	
	    	for (int i = 0; i < 8; ++i) {
	    	  b1[i+8] = (byte) (blockId >> (8 - i - 1 << 3));
	    	}
	    	
	    	dig.update(b1);
	    	
	    	byte[] digest = dig.digest();
	    	int ln = digest.length;
	    	if(ln==0){
	    		digest=new byte[4];
	    		digest[0]=0x01;
	    		digest[1]=0x01;
	    		digest[2]=0x01;
	    		digest[3]=0x01;
	    		ln=4;
	    	}
	    	for(int i=0;i<12;++i){
	    		int got = toInt(digest,i*4 % ln) ;
	    		if(i>4){
	    			got = got ^ stream[i-3];
	    		}
	    		stream[i] = got;
	    		
	    	}
	    	return stream;  
	    	
	    }

		PiggybackedProofOfWork(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workId = buffer.getLong();

			this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
			for (int i = 0; i < Constants.WORK_MULTIPLICATOR_BYTES; i++) {
				multiplicator[i] = buffer.get();
			}
		}

		PiggybackedProofOfWork(JSONObject attachmentData) {
			super(attachmentData);
			this.workId = Convert.parseUnsignedLong((String) attachmentData.get("id"));

			String inputRaw = (String) attachmentData.get("multiplicator");

			this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
			// null it first (just to be safe)
			for (int i = 0; i < Constants.WORK_MULTIPLICATOR_BYTES; ++i) {
				this.multiplicator[i] = 0;
			}
			if (inputRaw != null) {
				BigInteger multiplicator_bigint = new BigInteger(inputRaw, 16);
				// restore fixed sized multiplicator array
				byte[] multiplicator_byte_representation = multiplicator_bigint.toByteArray();
				int back_position = Constants.WORK_MULTIPLICATOR_BYTES - 1;
				for (int i = Math.min(multiplicator_byte_representation.length, 32); i > 0; --i) {
					multiplicator[back_position] = multiplicator_byte_representation[i - 1];
					back_position--;
				}
			}
		}

		public PiggybackedProofOfWork(long workId, byte[] multiplicator) {
			this.workId = workId;
			if (multiplicator.length == Constants.WORK_MULTIPLICATOR_BYTES)
				this.multiplicator = multiplicator;
			else {
				this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
				for (int i = 0; i < 32; ++i) {
					this.multiplicator[i] = 0;
				}
			}
		}

		@Override
		int getMySize() {
			return 8 + Constants.WORK_MULTIPLICATOR_BYTES;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			buffer.putLong(this.workId);
			for (int i = 0; i < Constants.WORK_MULTIPLICATOR_BYTES; i++) {
				buffer.put(this.multiplicator[i]);
			}
		}

		@Override
		void putMyJSON(JSONObject attachment) {
			attachment.put("id", Convert.toUnsignedLong(this.workId));
			BigInteger multiplicator_bigint = new BigInteger(this.multiplicator);
			String hex_string = multiplicator_bigint.toString(16);
			attachment.put("multiplicator", hex_string);
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.PROOF_OF_WORK;
		}

		@Override
		public byte[] getHash() {
			MessageDigest dig = Crypto.sha256();

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
			try {
				dos.writeLong(this.workId);
				dos.write(this.multiplicator);
				dos.writeBoolean(false); // distinguish between pow and bounty
				dos.close();
			} catch (IOException e) {

			}
			byte[] longBytes = baos.toByteArray();
			if (longBytes == null)
				longBytes = new byte[0];
			dig.update(longBytes);
			byte[] digest = dig.digest();
			return digest;
		}

		public byte[] getMultiplicator() {
			return multiplicator;
		}
	}

	public final static class PiggybackedProofOfBounty extends AbstractAttachment implements Hashable {
		public byte[] getMultiplicator() {
			return multiplicator;
		}

		public long getWorkId() {
			return workId;
		}

		public  static int toInt(byte[] bytes, int offset) {
			int ret = 0;
			for (int i = 0; i < 4 && i + offset < bytes.length; i++) {
				ret <<= 8;
				ret |= (int) bytes[i + offset] & 0xFF;
			}
			return ret;
		}

		private final long workId;
		private final byte[] multiplicator;
		public MessageDigest dig = Crypto.md5();
		public int[] personalizedIntStream(byte[] publicKey, long blockId){
	    	int[] stream = new int[12];
	    	
	    	
	    	
	    	dig.reset();
	    	dig.update(this.multiplicator);
	    	dig.update(publicKey);
	    	
	    	byte[] b1 = new byte[16];
	    	for (int i = 0; i < 8; ++i) {
	    	  b1[i] = (byte) (workId >> (8 - i - 1 << 3));
	    	}
	    	
	    	for (int i = 0; i < 8; ++i) {
	    	  b1[i+8] = (byte) (blockId >> (8 - i - 1 << 3));
	    	}
	    	
	    	dig.update(b1);
	    	
	    	byte[] digest = dig.digest();
	    	int ln = digest.length;
	    	if(ln==0){
	    		digest=new byte[4];
	    		digest[0]=0x01;
	    		digest[1]=0x01;
	    		digest[2]=0x01;
	    		digest[3]=0x01;
	    		ln=4;
	    	}
	    	for(int i=0;i<12;++i){
	    		int got = toInt(digest,i*4 % ln) ;
	    		if(i>4){
	    			got = got ^ stream[i-3];
	    		}
	    		stream[i] = got;
	    	}
	    	return stream;  
	    	
	    }

		PiggybackedProofOfBounty(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workId = buffer.getLong();

			this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
			for (int i = 0; i < Constants.WORK_MULTIPLICATOR_BYTES; i++) {
				multiplicator[i] = buffer.get();
			}
		}

		PiggybackedProofOfBounty(JSONObject attachmentData) {
			super(attachmentData);
			this.workId = Convert.parseUnsignedLong((String) attachmentData.get("id"));

			String inputRaw = (String) attachmentData.get("multiplicator");

			this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
			// null it first (just to be safe)
			for (int i = 0; i < Constants.WORK_MULTIPLICATOR_BYTES; ++i) {
				this.multiplicator[i] = 0;
			}
			if (inputRaw != null) {
				BigInteger multiplicator_bigint = new BigInteger(inputRaw, 16);
				// restore fixed sized multiplicator array
				byte[] multiplicator_byte_representation = multiplicator_bigint.toByteArray();
				int back_position = Constants.WORK_MULTIPLICATOR_BYTES - 1;
				for (int i = Math.min(multiplicator_byte_representation.length, 32); i > 0; --i) {
					multiplicator[back_position] = multiplicator_byte_representation[i - 1];
					back_position--;
				}
			}
		}

		public PiggybackedProofOfBounty(long workId, byte[] multiplicator) {
			this.workId = workId;
			if (multiplicator.length == Constants.WORK_MULTIPLICATOR_BYTES)
				this.multiplicator = multiplicator;
			else {
				this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
				for (int i = 0; i < 32; ++i) {
					this.multiplicator[i] = 0;
				}
			}
		}

		@Override
		int getMySize() {
			return 8 + Constants.WORK_MULTIPLICATOR_BYTES;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			buffer.putLong(this.workId);
			for (int i = 0; i < Constants.WORK_MULTIPLICATOR_BYTES; i++) {
				buffer.put(this.multiplicator[i]);
			}
		}

		@Override
		void putMyJSON(JSONObject attachment) {
			attachment.put("id", Convert.toUnsignedLong(this.workId));
			BigInteger multiplicator_bigint = new BigInteger(this.multiplicator);
			String hex_string = multiplicator_bigint.toString(16);

			attachment.put("multiplicator", hex_string);
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.BOUNTY;
		}

		@Override
		public byte[] getHash() {
			MessageDigest dig = Crypto.sha256();

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
			try {
				dos.writeLong(this.workId);
				dos.write(this.multiplicator);
				dos.writeBoolean(true); // distinguish between pow and bounty
				dos.close();
			} catch (IOException e) {

			}
			byte[] longBytes = baos.toByteArray();
			if (longBytes == null)
				longBytes = new byte[0];
			dig.update(longBytes);
			byte[] digest = dig.digest();
			return digest;
		}
	}

	public final static class PiggybackedProofOfBountyAnnouncement extends AbstractAttachment {

		public long getWorkId() {
			return workId;
		}

		private final long workId;
		private final byte[] hashAnnounced;

		PiggybackedProofOfBountyAnnouncement(ByteBuffer buffer, byte transactionVersion)
				throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workId = buffer.getLong();
			short hashSize = buffer.getShort();
			
			if (hashSize > 0 && hashSize <= Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES) {
				this.hashAnnounced = new byte[hashSize];
				buffer.get(hashAnnounced, 0, hashSize);
			} else {
				this.hashAnnounced = null;
			}

		}

		PiggybackedProofOfBountyAnnouncement(JSONObject attachmentData) {
			super(attachmentData);
			this.workId = Convert.parseUnsignedLong((String) attachmentData.get("id"));
			String inputRaw = (String) attachmentData.get("hash_announced");

			if (inputRaw != null) {
				BigInteger multiplicator_bigint = new BigInteger(inputRaw, 16);
				// restore fixed sized multiplicator array
				byte[] multiplicator_byte_representation = multiplicator_bigint.toByteArray();
				
				if (multiplicator_byte_representation.length > 0
						&& multiplicator_byte_representation.length <= Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES) {
					this.hashAnnounced = new byte[multiplicator_byte_representation.length];
					for (int i = 0; i < this.hashAnnounced.length; ++i) {
						hashAnnounced[i] = multiplicator_byte_representation[i];
					}
				} else {
					this.hashAnnounced = null;
				}
			} else {
				this.hashAnnounced = null;
			}
		}
		

		public PiggybackedProofOfBountyAnnouncement(long workId, byte[] hash_assigned) {
			this.workId = workId;
			this.hashAnnounced = hash_assigned;
		}

		@Override
		int getMySize() {
			if(this.hashAnnounced != null)
				return 8 + 2 + this.hashAnnounced.length;
			else
				return 8 + 2;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			buffer.putLong(this.workId);
			if(this.hashAnnounced != null)
				{
				buffer.putShort((new Integer(this.hashAnnounced.length)).shortValue());
				buffer.put(this.hashAnnounced);
				}
			else
				buffer.putShort((short)0);
		}

		@Override
		void putMyJSON(JSONObject attachment) {
			attachment.put("id", Convert.toUnsignedLong(this.workId));
			BigInteger hash = new BigInteger(this.hashAnnounced);
			String hex_string = hash.toString(16);

			attachment.put("hash_announced", hex_string);
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.BOUNTY_ANNOUNCEMENT;
		}

		public byte[] getHashAnnounced() {
			return hashAnnounced;
		}
	}

	public final static class WorkCreation extends AbstractAttachment {

		private final String workTitle;
		private final int deadline;
		private final long xelPerPow;
		private final int bountyLimit;
		private final long xelPerBounty;

		WorkCreation(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workTitle = Convert.readString(buffer, buffer.getShort(), Constants.MAX_TITLE_LENGTH);
			this.deadline = buffer.getInt();
			this.bountyLimit = buffer.getInt();
			this.xelPerPow = buffer.getLong();
			this.xelPerBounty = buffer.getLong();
		}

		WorkCreation(JSONObject attachmentData) {
			super(attachmentData);

			this.workTitle = ((String) attachmentData.get("title")).trim();
			this.deadline = ((Long) attachmentData.get("deadline")).intValue();
			this.bountyLimit = ((Long) attachmentData.get("bountyLimit")).intValue();
			this.xelPerPow = ((Long) attachmentData.get("xel_per_pow")).longValue();
			this.xelPerBounty = ((Long) attachmentData.get("xel_per_bounty")).longValue();

		}

		public WorkCreation(String workTitle, byte workLanguage, byte[] programmCode, int deadline, int bountyLimit,
				long xel_per_pow, long xel_per_bounty) {
			this.workTitle = workTitle;
			this.deadline = deadline;
			this.bountyLimit = bountyLimit;
			this.xelPerPow = xel_per_pow;
			this.xelPerBounty = xel_per_bounty;
		}

		@Override
		int getMySize() {
			int size = 2 + Convert.toBytes(workTitle).length + 4 + 4 + 8 + 8;
			return size;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			byte[] name = Convert.toBytes(this.workTitle);
			buffer.putShort((short) name.length);
			buffer.put(name);
			buffer.putInt(this.deadline);
			buffer.putInt(this.bountyLimit);
			buffer.putLong(this.xelPerPow);
			buffer.putLong(this.xelPerBounty);
		}

		@Override
		void putMyJSON(JSONObject attachment) {
			attachment.put("title", this.workTitle);
			attachment.put("deadline", this.deadline);
			attachment.put("bountyLimit", this.bountyLimit);
			attachment.put("xel_per_pow", this.xelPerPow);
			attachment.put("xel_per_bounty", this.xelPerBounty);
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.NEW_TASK;
		}

		public String getWorkTitle() {
			return workTitle;
		}

		public int getBountyLimit() {
			return bountyLimit;
		}

		public long getXelPerBounty() {
			return xelPerBounty;
		}

		public long getXelPerPow() {
			return xelPerPow;
		}

		public int getDeadline() {
			return deadline;
		}

	}

	final class MessagingAccountInfo extends AbstractAttachment {

		private final String name;
		private final String description;

		MessagingAccountInfo(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_NAME_LENGTH);
			this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH);
		}

		MessagingAccountInfo(JSONObject attachmentData) {
			super(attachmentData);
			this.name = Convert.nullToEmpty((String) attachmentData.get("name"));
			this.description = Convert.nullToEmpty((String) attachmentData.get("description"));
		}

		public MessagingAccountInfo(String name, String description) {
			this.name = name;
			this.description = description;
		}

		@Override
		int getMySize() {
			return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			byte[] name = Convert.toBytes(this.name);
			byte[] description = Convert.toBytes(this.description);
			buffer.put((byte) name.length);
			buffer.put(name);
			buffer.putShort((short) description.length);
			buffer.put(description);
		}

		@Override
		void putMyJSON(JSONObject attachment) {
			attachment.put("name", name);
			attachment.put("description", description);
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.Messaging.ACCOUNT_INFO;
		}

		public String getName() {
			return name;
		}

		public String getDescription() {
			return description;
		}

	}

	final class AccountControlEffectiveBalanceLeasing extends AbstractAttachment {

		private final int period;

		AccountControlEffectiveBalanceLeasing(ByteBuffer buffer, byte transactionVersion) {
			super(buffer, transactionVersion);
			this.period = Short.toUnsignedInt(buffer.getShort());
		}

		AccountControlEffectiveBalanceLeasing(JSONObject attachmentData) {
			super(attachmentData);
			this.period = ((Long) attachmentData.get("period")).intValue();
		}

		public AccountControlEffectiveBalanceLeasing(int period) {
			this.period = period;
		}

		@Override
		int getMySize() {
			return 2;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			buffer.putShort((short) period);
		}

		@Override
		void putMyJSON(JSONObject attachment) {
			attachment.put("period", period);
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
		}

		public int getPeriod() {
			return period;
		}
	}
}

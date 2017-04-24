/******************************************************************************
 * Copyright © 2013-2016 The XEL Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
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
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import nxt.NxtException.NotValidException;
import nxt.crypto.Crypto;
import nxt.util.Convert;

public interface Attachment extends Appendix {
	abstract class AbstractAttachment extends Appendix.AbstractAppendix implements Attachment {

		private AbstractAttachment() {
		}

		private AbstractAttachment(final ByteBuffer buffer, final byte transactionVersion) {
			super(buffer, transactionVersion);
		}

		private AbstractAttachment(final int version) {
			super(version);
		}

		private AbstractAttachment(final JSONObject attachmentData) {
			super(attachmentData);
		}

		@Override
		final void apply(final Transaction transaction, final Account senderAccount, final Account recipientAccount)
				throws NotValidException {

			this.getTransactionType().apply((TransactionImpl) transaction, senderAccount, recipientAccount);
		}

		@Override
		final String getAppendixName() {
			return this.getTransactionType().getName();
		}

		@Override
		public final Fee getBaselineFee(final Transaction transaction) {
			return this.getTransactionType().getBaselineFee(transaction);
		}

		@Override
		public final int getBaselineFeeHeight() {
			return this.getTransactionType().getBaselineFeeHeight();
		}

		final int getFinishValidationHeight(final Transaction transaction) {
			return Nxt.getBlockchain().getHeight();
		}

		@Override
		public final Fee getNextFee(final Transaction transaction) {
			return this.getTransactionType().getNextFee(transaction);
		}

		@Override
		public final int getNextFeeHeight() {
			return this.getTransactionType().getNextFeeHeight();
		}

		@Override
		final void validate(final Transaction transaction) throws NxtException.ValidationException {
			this.getTransactionType().validateAttachment(transaction);
		}

	}

	final class AccountControlEffectiveBalanceLeasing extends AbstractAttachment {

		private final int period;

		AccountControlEffectiveBalanceLeasing(final ByteBuffer buffer, final byte transactionVersion) {
			super(buffer, transactionVersion);
			this.period = Short.toUnsignedInt(buffer.getShort());
		}

		public AccountControlEffectiveBalanceLeasing(final int period) {
			this.period = period;
		}

		AccountControlEffectiveBalanceLeasing(final JSONObject attachmentData) {
			super(attachmentData);
			this.period = ((Long) attachmentData.get("period")).intValue();
		}

		@Override
		int getMySize() {
			return 2;
		}

		public int getPeriod() {
			return this.period;
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			buffer.putShort((short) this.period);
		}

		@Override
		void putMyJSON(final JSONObject attachment) {
			attachment.put("period", this.period);
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
		final void putMyBytes(final ByteBuffer buffer) {
		}

		@Override
		final void putMyJSON(final JSONObject json) {
		}

		@Override
		final boolean verifyVersion(final byte transactionVersion) {
			// FIXME (Schauen wieso das vorher nur alte blocks gecheckt hat. ist
			// das attaCHMENT obsolet?)
			return this.getVersion() == 0;
		}

	}

	final class MessagingAccountInfo extends AbstractAttachment {

		private final String name;
		private final String description;

		MessagingAccountInfo(final ByteBuffer buffer, final byte transactionVersion)
				throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_NAME_LENGTH);
			this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH);
		}

		MessagingAccountInfo(final JSONObject attachmentData) {
			super(attachmentData);
			this.name = Convert.nullToEmpty((String) attachmentData.get("name"));
			this.description = Convert.nullToEmpty((String) attachmentData.get("description"));
		}

		public MessagingAccountInfo(final String name, final String description) {
			this.name = name;
			this.description = description;
		}

		public String getDescription() {
			return this.description;
		}

		@Override
		int getMySize() {
			return 1 + Convert.toBytes(this.name).length + 2 + Convert.toBytes(this.description).length;
		}

		public String getName() {
			return this.name;
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.Messaging.ACCOUNT_INFO;
		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			final byte[] name = Convert.toBytes(this.name);
			final byte[] description = Convert.toBytes(this.description);
			buffer.put((byte) name.length);
			buffer.put(name);
			buffer.putShort((short) description.length);
			buffer.put(description);
		}

		@Override
		void putMyJSON(final JSONObject attachment) {
			attachment.put("name", this.name);
			attachment.put("description", this.description);
		}

	}

	final class MessagingSupernodeAnnouncement extends AbstractAttachment {

		private final String[] uris;
		private long guardNodeBlockId = 0;

		public long getGuardNodeBlockId() {
			return guardNodeBlockId;
		}

		MessagingSupernodeAnnouncement(final ByteBuffer buffer, final byte transactionVersion)
				throws NxtException.NotValidException {
			super(buffer, transactionVersion);

			final int numberOfUris = buffer.get();

			this.uris = new String[numberOfUris];
			for (int i = 0; i < this.uris.length; i++)
				this.uris[i] = Convert.readString(buffer, buffer.getShort(), Constants.MAX_SUPERNODE_ANNOUNCEMENT_URI_LENGTH);
			this.guardNodeBlockId = buffer.getLong();

			if (guardNodeBlockId == 0 && (numberOfUris > Constants.MAX_SUPERNODE_ANNOUNCEMENT_URIS || numberOfUris <= 0))
				throw new NotValidException("Invalid number of URIs: " + numberOfUris);
			if (guardNodeBlockId != 0 && (numberOfUris != 0))
				throw new NotValidException("Guardnode block must not have any IDs");
		}

		MessagingSupernodeAnnouncement(final JSONObject attachmentData) throws NxtException.NotValidException {
			super(attachmentData);
			try {
				final JSONArray urisData = (JSONArray) attachmentData.get("uris");
				this.uris = new String[urisData.size()];
				for (int i = 0; i < this.uris.length; i++) this.uris[i] = (String) urisData.get(i);
				this.guardNodeBlockId = (Long)attachmentData.get("guardNodeBlockId");
			} catch (final RuntimeException e) {
				throw new NxtException.NotValidException("Error parsing SN announcement parameters", e);
			}
		}

		public MessagingSupernodeAnnouncement(final String[] uris, final long guardNodeBlockId) {
			this.uris = uris;
			this.guardNodeBlockId = guardNodeBlockId;
		}

		@Override
		int getMySize() {
			int size = 1;
			size += Arrays.stream(this.uris).mapToInt(uri -> 2 + Convert.toBytes(uri).length).sum();
			size += 8; // guardNodeBlockId
			return size;
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.Messaging.SUPERNODE_ANNOUNCEMENT;
		}

		public String[] getUris() {
			return this.uris;
		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			buffer.put((byte) this.uris.length);
			for (final String uri : this.uris) {
				final byte[] uriBytes = Convert.toBytes(uri);
				buffer.putShort((short) uriBytes.length);
				buffer.put(uriBytes);
			}
			buffer.putLong(this.guardNodeBlockId);
		}

		@Override
		void putMyJSON(final JSONObject attachment) {
			final JSONArray uris = new JSONArray();
			Collections.addAll(uris, this.uris);
			attachment.put("uris", uris);
			attachment.put("guardNodeBlockId", guardNodeBlockId);
		}

	}

	public final static class PiggybackedProofOfBounty extends AbstractAttachment implements Hashable {
		public static int toInt(final byte[] bytes, final int offset) {
			int ret = 0;
			for (int i = 0; (i < 4) && ((i + offset) < bytes.length); i++) {
				ret <<= 8;
				ret |= bytes[i + offset] & 0xFF;
			}
			return ret;
		}

		private final long workId;

		private final byte[] multiplicator;

		private final int[] storage;

		public final MessageDigest dig = Crypto.md5();

		PiggybackedProofOfBounty(final ByteBuffer buffer, final byte transactionVersion)
				throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workId = buffer.getLong();

			this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
			buffer.get(this.multiplicator);

			byte[] tempStorage = new byte[Constants.BOUNTY_STORAGE_INTS*4];
			buffer.get(tempStorage);
			this.storage = Convert.byte2int(tempStorage);

		}

		PiggybackedProofOfBounty(final JSONObject attachmentData) {
			super(attachmentData);
			this.workId = Convert.parseUnsignedLong((String) attachmentData.get("id"));

			final String inputRaw = (String) attachmentData.get("multiplicator");
			if (inputRaw != null) {
				final byte[] multiplicator_byte_representation = Convert.parseHexString(inputRaw);
				this.multiplicator = Convert.toFixedBytesCutter(multiplicator_byte_representation, Constants.WORK_MULTIPLICATOR_BYTES);
			}else this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];

			final String storageRaw = (String) attachmentData.get("storage");
			if (storageRaw != null) {
				byte[] storage_byte_representation = Convert.parseHexString(inputRaw);
				storage_byte_representation = Convert.toFixedBytesCutter(storage_byte_representation, Constants.BOUNTY_STORAGE_INTS*4);
				this.storage = Convert.byte2int(storage_byte_representation);
			}else this.storage = new int[Constants.BOUNTY_STORAGE_INTS];
		}

		public PiggybackedProofOfBounty(final long workId, final byte[] multiplicator, final int[] storage) {
			this.workId = workId;
			this.multiplicator = Convert.toFixedBytesCutter(multiplicator, Constants.WORK_MULTIPLICATOR_BYTES);
			this.storage = storage;
		}

		@Override
		public byte[] getHash() {
			final MessageDigest dig = Crypto.sha256();

			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			final DataOutputStream dos = new DataOutputStream(baos);
			try {
				dos.writeLong(this.workId);
				dos.write(this.multiplicator);
				dos.write(Convert.int2byte(this.storage));
				dos.writeBoolean(true); // distinguish between pow and bounty
				dos.close();
			} catch (final IOException ignored) {

			}
			byte[] longBytes = baos.toByteArray();
			if (longBytes == null) longBytes = new byte[0];
			dig.update(longBytes);
			return dig.digest();
		}

		public byte[] getMultiplicator() {
			return this.multiplicator;
		}

		@Override
		int getMySize() {
			return 8 + Constants.WORK_MULTIPLICATOR_BYTES + Constants.BOUNTY_STORAGE_INTS*4;
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.BOUNTY;
		}

		@Override
		public long getWorkId() {
			return this.workId;
		}

		@Override
		public int[] personalizedIntStream(final byte[] publicKey, final long blockId) {
			final int[] stream = new int[12];

			this.dig.reset();
			this.dig.update(this.multiplicator);
			this.dig.update(publicKey);

			final byte[] b1 = new byte[16];
			for (int i = 0; i < 8; ++i) b1[i] = (byte) (this.workId >> ((8 - i - 1) << 3));

			for (int i = 0; i < 8; ++i) b1[i + 8] = (byte) (blockId >> ((8 - i - 1) << 3));

			this.dig.update(b1);

			byte[] digest = this.dig.digest();
			int ln = digest.length;
			if (ln == 0) {
				digest = new byte[4];
				digest[0] = 0x01;
				digest[1] = 0x01;
				digest[2] = 0x01;
				digest[3] = 0x01;
				ln = 4;
			}
			for (int i = 0; i < 12; ++i) {
				int got = PiggybackedProofOfBounty.toInt(digest, (i * 4) % ln);
				if (i > 4) got = got ^ stream[i - 3];
				stream[i] = got;
			}
			return stream;

		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			buffer.putLong(this.workId);
			buffer.put(this.multiplicator);
			buffer.put(Convert.int2byte(this.storage));
		}

		@Override
		void putMyJSON(final JSONObject attachment) {
			attachment.put("id", Convert.toUnsignedLong(this.workId));
			attachment.put("multiplicator", Convert.toHexString(this.multiplicator));
			attachment.put("storage", Convert.toHexString(Convert.int2byte(this.storage)));
		}

        public int[] getStorage() {
            return storage;
        }
    }

	public final static class PiggybackedProofOfBountyAnnouncement extends AbstractAttachment {

		private final long workId;

		private final byte[] hashAnnounced;

		PiggybackedProofOfBountyAnnouncement(final ByteBuffer buffer, final byte transactionVersion)
				throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workId = buffer.getLong();
		    this.hashAnnounced = new byte[Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES];
			buffer.get(this.hashAnnounced);
		}

		PiggybackedProofOfBountyAnnouncement(final JSONObject attachmentData) {
			super(attachmentData);
			this.workId = Convert.parseUnsignedLong((String) attachmentData.get("id"));
			final String inputRaw = (String) attachmentData.get("hash_announced");
			if (inputRaw != null) {
				final byte[] multiplicator_byte_representation = Convert.parseHexString(inputRaw);
				this.hashAnnounced = Convert.toFixedBytesCutter(multiplicator_byte_representation, Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES);
			}else this.hashAnnounced = new byte[Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES];
		}

		public PiggybackedProofOfBountyAnnouncement(final long workId, final byte[] hash_assigned) {
			this.workId = workId;

			// Here, make sure that only 32 byte are treated here. This will "deal" with a crippled xel_miner
			this.hashAnnounced = Convert.toFixedBytesCutter(hash_assigned, Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES);
		}

		public byte[] getHashAnnounced() {
			return this.hashAnnounced;
		}

		@Override
		int getMySize() {
			return 8 + Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES;
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.BOUNTY_ANNOUNCEMENT;
		}

		public long getWorkId() {
			return this.workId;
		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			buffer.putLong(this.workId);
			buffer.put(this.hashAnnounced);
		}

		@Override
		void putMyJSON(final JSONObject attachment) {
			attachment.put("id", Convert.toUnsignedLong(this.workId));
			attachment.put("hash_announced", Convert.toHexString(this.hashAnnounced));
		}
	}

	public final static class PiggybackedProofOfWork extends AbstractAttachment implements Hashable {

		public static final MessageDigest dig = Crypto.md5();


		private final long workId;
		private final byte[] multiplicator;

		PiggybackedProofOfWork(final ByteBuffer buffer, final byte transactionVersion)
				throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workId = buffer.getLong();
			this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
			buffer.get(multiplicator);
		}

		PiggybackedProofOfWork(final JSONObject attachmentData) {
			super(attachmentData);
			this.workId = Convert.parseUnsignedLong((String) attachmentData.get("id"));

			final String inputRaw = (String) attachmentData.get("multiplicator");

			if (inputRaw != null) {
				final byte[] multiplicator_byte_representation = Convert.parseHexString(inputRaw);
				this.multiplicator = Convert.toFixedBytesCutter(multiplicator_byte_representation, Constants.WORK_MULTIPLICATOR_BYTES);
			}else this.multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
		}

		public PiggybackedProofOfWork(final long workId, final byte[] multiplicator) {
			this.workId = workId;
			this.multiplicator = Convert.toFixedBytesCutter(multiplicator, Constants.WORK_MULTIPLICATOR_BYTES);
		}

		@Override
		public byte[] getHash() {
			final MessageDigest dig = Crypto.sha256();

			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			final DataOutputStream dos = new DataOutputStream(baos);
			try {
				dos.writeLong(this.workId);
				dos.write(this.multiplicator);
				dos.writeBoolean(false); // distinguish between pow and bounty
				dos.close();
			} catch (final IOException ignored) {

			}
			byte[] longBytes = baos.toByteArray();
			if (longBytes == null) longBytes = new byte[0];
			dig.update(longBytes);
			return dig.digest();
		}

		public byte[] getMultiplicator() {
			return this.multiplicator;
		}

		@Override
		int getMySize() {
			return 8 + Constants.WORK_MULTIPLICATOR_BYTES;
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.PROOF_OF_WORK;
		}

		@Override
		public long getWorkId() {
			return this.workId;
		}

		@Override
		public int[] personalizedIntStream(final byte[] publicKey, final long blockId) {
			final int[] stream = new int[12];

			PiggybackedProofOfWork.dig.reset();
			PiggybackedProofOfWork.dig.update(this.multiplicator);
			PiggybackedProofOfWork.dig.update(publicKey);

			final byte[] b1 = new byte[16];
			for (int i = 0; i < 8; ++i) b1[i] = (byte) (this.workId >> ((8 - i - 1) << 3));

			for (int i = 0; i < 8; ++i) b1[i + 8] = (byte) (blockId >> ((8 - i - 1) << 3));

			PiggybackedProofOfWork.dig.update(b1);

			byte[] digest = PiggybackedProofOfWork.dig.digest();
			int ln = digest.length;
			if (ln == 0) {
				digest = new byte[4];
				digest[0] = 0x01;
				digest[1] = 0x01;
				digest[2] = 0x01;
				digest[3] = 0x01;
				ln = 4;
			}
			for (int i = 0; i < 12; ++i) {
				int got = this.toInt(digest, (i * 4) % ln);
				if (i > 4) got = got ^ stream[i - 3];
				stream[i] = got;

			}
			return stream;

		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			buffer.putLong(this.workId);
			buffer.put(this.multiplicator);
		}

		@Override
		void putMyJSON(final JSONObject attachment) {
			attachment.put("id", Convert.toUnsignedLong(this.workId));
			attachment.put("multiplicator", Convert.toHexString(this.multiplicator));
		}

		public int toInt(final byte[] bytes, final int offset) {
			int ret = 0;
			for (int i = 0; (i < 4) && ((i + offset) < bytes.length); i++) {
				ret <<= 8;
				ret |= bytes[i + offset] & 0xFF;
			}
			return ret;
		}
	}

	public final static class RedeemAttachment extends AbstractAttachment {

		private final short address_length;
		private final short secp_length;
		private final String address;
		private final String secp_signatures;

		RedeemAttachment(final ByteBuffer buffer, final byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.address_length = buffer.getShort();
			this.address = Convert.readString(buffer, this.address_length, 4096);
			this.secp_length = buffer.getShort();
			this.secp_signatures = Convert.readString(buffer, this.secp_length, 10400);
		}

		RedeemAttachment(final JSONObject attachmentData) {
			super(attachmentData);
			this.address = (String) attachmentData.get("address");
			this.address_length = (short) this.address.length();
			this.secp_signatures = (String) attachmentData.get("secp_signatures");
			this.secp_length = (short) this.secp_signatures.length();
		}

		public RedeemAttachment(final String address, final String secp_signatures) {
			this.address = address;
			this.address_length = (short) address.length();
			this.secp_signatures = secp_signatures;
			this.secp_length = (short) this.secp_signatures.length();
		}

		public int getRequiredTimestamp(){
			int timestamp = 0;
			for(int i=0;i<Redeem.listOfAddresses.length;++i)
				if (Redeem.listOfAddresses[i].equalsIgnoreCase(this.address)) timestamp = Redeem.times[i];

			return timestamp;
		}

		public String getAddress() {
			return this.address;
		}

		@Override
		int getMySize() {
			return 2 + 2 + Convert.toBytes(this.address).length + Convert.toBytes(this.secp_signatures).length;
		}

		public String getSecp_signatures() {
			return this.secp_signatures;
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.Payment.REDEEM;
		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			buffer.putShort((short) Convert.toBytes(this.address).length);
			final byte[] byteAddr = Convert.toBytes(this.address);
			final byte[] byteSecp = Convert.toBytes(this.secp_signatures);
			buffer.put(byteAddr);
			buffer.putShort((short) Convert.toBytes(this.secp_signatures).length);
			buffer.put(byteSecp);
		}

		@Override
		void putMyJSON(final JSONObject attachment) {
			attachment.put("address", this.address);
			attachment.put("secp_signatures", this.secp_signatures);
		}
	}

	public final static class WorkCreation extends AbstractAttachment {

		private final String workTitle;
		private final int deadline;
		private final long xelPerPow;
		private final int bountyLimit;
		private final long xelPerBounty;

		WorkCreation(final ByteBuffer buffer, final byte transactionVersion) throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workTitle = Convert.readString(buffer, buffer.getShort(), Constants.MAX_TITLE_LENGTH);
			this.deadline = buffer.getInt();
			this.bountyLimit = buffer.getInt();
			this.xelPerPow = buffer.getLong();
			this.xelPerBounty = buffer.getLong();
		}

		WorkCreation(final JSONObject attachmentData) {
			super(attachmentData);

			this.workTitle = ((String) attachmentData.get("title")).trim();
			this.deadline = ((Long) attachmentData.get("deadline")).intValue();
			this.bountyLimit = ((Long) attachmentData.get("bountyLimit")).intValue();

			// Sometimes it comes with quotes, sometimes not! Why?
			if(attachmentData.get("xel_per_pow") instanceof String) {
				this.xelPerPow = Long.parseLong((String) attachmentData.get("xel_per_pow"));
				this.xelPerBounty = Long.parseLong((String) attachmentData.get("xel_per_bounty"));
			}else{
				this.xelPerPow = (Long) attachmentData.get("xel_per_pow");
				this.xelPerBounty = (Long) attachmentData.get("xel_per_bounty");
			}

		}

		public WorkCreation(final String workTitle, final byte workLanguage,
				final int deadline, final int bountyLimit, final long xel_per_pow, final long xel_per_bounty) {
			this.workTitle = workTitle;
			this.deadline = deadline;
			this.bountyLimit = bountyLimit;
			this.xelPerPow = xel_per_pow;
			this.xelPerBounty = xel_per_bounty;
		}

		public int getBountyLimit() {
			return this.bountyLimit;
		}

		public int getDeadline() {
			return this.deadline;
		}

		@Override
		int getMySize() {
			return 2 + Convert.toBytes(this.workTitle).length + 4 + 4 + 8 + 8;
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.NEW_TASK;
		}

		public String getWorkTitle() {
			return this.workTitle;
		}

		public long getXelPerBounty() {
			return this.xelPerBounty;
		}

		public long getXelPerPow() {
			return this.xelPerPow;
		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			final byte[] name = Convert.toBytes(this.workTitle);
			buffer.putShort((short) name.length);
			buffer.put(name);
			buffer.putInt(this.deadline);
			buffer.putInt(this.bountyLimit);
			buffer.putLong(this.xelPerPow);
			buffer.putLong(this.xelPerBounty);
		}

		@Override
		void putMyJSON(final JSONObject attachment) {
			attachment.put("title", this.workTitle);
			attachment.put("deadline", this.deadline);
			attachment.put("bountyLimit", this.bountyLimit);
			attachment.put("xel_per_pow", this.xelPerPow);
			attachment.put("xel_per_bounty", this.xelPerBounty);
		}

	}

	public final static class WorkIdentifierCancellationRequest extends AbstractAttachment {

		private final long workId;

		WorkIdentifierCancellationRequest(final ByteBuffer buffer, final byte transactionVersion)
				throws NxtException.NotValidException {
			super(buffer, transactionVersion);
			this.workId = buffer.getLong();
		}

		WorkIdentifierCancellationRequest(final JSONObject attachmentData) {
			super(attachmentData);
			this.workId = Convert.parseUnsignedLong((String) attachmentData.get("id"));
		}

		public WorkIdentifierCancellationRequest(final long workId) {
			this.workId = workId;
		}

		@Override
		int getMySize() {
			return 8;
		}

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.WorkControl.CANCEL_TASK_REQUEST;
		}

		public long getWorkId() {
			return this.workId;
		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			buffer.putLong(this.workId);
		}

		@Override
		void putMyJSON(final JSONObject attachment) {
			attachment.put("id", Convert.toUnsignedLong(this.workId));
		}
	}

	EmptyAttachment ORDINARY_PAYMENT = new EmptyAttachment() {

		@Override
		public TransactionType getTransactionType() {
			return TransactionType.Payment.ORDINARY;
		}

	};

	TransactionType getTransactionType();
}

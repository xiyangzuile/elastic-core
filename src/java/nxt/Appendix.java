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

import static nxt.Appendix.hasAppendix;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

import org.json.simple.JSONObject;

import nxt.crypto.Crypto;
import nxt.crypto.EncryptedData;
import nxt.util.Convert;

public interface Appendix {

	int getSize();

	int getFullSize();

	void putBytes(ByteBuffer buffer);

	JSONObject getJSONObject();

	byte getVersion();

	int getBaselineFeeHeight();

	Fee getBaselineFee(Transaction transaction);

	int getNextFeeHeight();

	Fee getNextFee(Transaction transaction);

	interface Prunable {
		byte[] getHash();

		boolean hasPrunableData();

		void restorePrunableData(Transaction transaction, int blockTimestamp, int height);

		default boolean shouldLoadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
			return Nxt.getEpochTime()
					- transaction.getTimestamp() < (includeExpiredPrunable && Constants.INCLUDE_EXPIRED_PRUNABLE
							? Constants.MAX_PRUNABLE_LIFETIME : Constants.MIN_PRUNABLE_LIFETIME);
		}
	}

	interface Encryptable {
		void encrypt(String secretPhrase);
	}

	abstract class AbstractAppendix implements Appendix {

		private final byte version;

		AbstractAppendix(JSONObject attachmentData) {
			Long l = (Long) attachmentData.get("version." + getAppendixName());
			version = (byte) (l == null ? 0 : l);
		}

		AbstractAppendix(ByteBuffer buffer, byte transactionVersion) {
			if (transactionVersion == 0) {
				version = 0;
			} else {
				version = buffer.get();
			}
		}

		AbstractAppendix(int version) {
			this.version = (byte) version;
		}

		AbstractAppendix() {
			this.version = 1;
		}

		abstract String getAppendixName();

		@Override
		public final int getSize() {
			return getMySize() + (version > 0 ? 1 : 0);
		}

		@Override
		public final int getFullSize() {
			return getMyFullSize() + (version > 0 ? 1 : 0);
		}

		abstract int getMySize();

		int getMyFullSize() {
			return getMySize();
		}

		@Override
		public final void putBytes(ByteBuffer buffer) {
			if (version > 0) {
				buffer.put(version);
			}
			putMyBytes(buffer);
		}

		abstract void putMyBytes(ByteBuffer buffer);

		@Override
		public final JSONObject getJSONObject() {
			JSONObject json = new JSONObject();
			json.put("version." + getAppendixName(), version);
			putMyJSON(json);
			return json;
		}

		abstract void putMyJSON(JSONObject json);

		@Override
		public final byte getVersion() {
			return version;
		}

		boolean verifyVersion(byte transactionVersion) {
			return version == 1;
		}

		@Override
		public int getBaselineFeeHeight() {
			return 0;
		}

		@Override
		public Fee getBaselineFee(Transaction transaction) {
			return Fee.NONE;
		}

		@Override
		public int getNextFeeHeight() {
			return Integer.MAX_VALUE;
		}

		@Override
		public Fee getNextFee(Transaction transaction) {
			return getBaselineFee(transaction);
		}

		abstract void validate(Transaction transaction) throws NxtException.ValidationException;

		abstract void apply(Transaction transaction, Account senderAccount, Account recipientAccount);

		final void loadPrunable(Transaction transaction) {
			loadPrunable(transaction, false);
		}

		void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
		}

	}

	static boolean hasAppendix(String appendixName, JSONObject attachmentData) {
		return attachmentData.get("version." + appendixName) != null;
	}

	class PrunableSourceCode extends Appendix.AbstractAppendix implements Prunable {

		private static final String appendixName = "PrunableSourceCode";

		private static final Fee PRUNABLE_SOURCE_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT / 10) {
			@Override
			public int getSize(TransactionImpl transaction, Appendix appendix) {
				return appendix.getFullSize();
			}
		};

		static PrunableSourceCode parse(JSONObject attachmentData) {
			if (!hasAppendix(appendixName, attachmentData)) {
				return null;
			}
			return new PrunableSourceCode(attachmentData);
		}

		private final byte[] hash;
		private final byte[] source;
		private final short language;

		private volatile nxt.PrunableSourceCode prunableSourceCode;

		PrunableSourceCode(ByteBuffer buffer, byte transactionVersion) {
			super(buffer, transactionVersion);
			this.hash = new byte[32];
			buffer.get(this.hash);
			this.source = null;
			this.language = 0;
		}

		private PrunableSourceCode(JSONObject attachmentData) {
			super(attachmentData);
			String hashString = Convert.emptyToNull((String) attachmentData.get("messageHash"));
			String messageString = Convert.emptyToNull((String) attachmentData.get("source"));
			String languageString = Convert.emptyToNull((String) attachmentData.get("language"));

			if (hashString != null && messageString == null) {
				this.hash = Convert.parseHexString(hashString);
				this.source = null;
				this.language = 0;
			} else {
				this.hash = null;
				this.source = Convert.toBytes(messageString, true);
				this.language = Short.parseShort(languageString);
			}
		}

		public PrunableSourceCode(String source, short language) {
			this(Convert.toBytes(source, true), language);
		}

		public PrunableSourceCode(byte[] source, short language) {
			this.source = source;
			this.hash = null;
			this.language = language;
		}

		@Override
		String getAppendixName() {
			return appendixName;
		}

		@Override
		public Fee getBaselineFee(Transaction transaction) {
			return PRUNABLE_SOURCE_FEE;
		}

		@Override
		int getMySize() {
			return 32;
		}

		@Override
		int getMyFullSize() {
			return getSource() == null ? 0
					: getSource().length + 2 /* short for language id */;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			buffer.put(getHash());
		}

		@Override
		void putMyJSON(JSONObject json) {
			if (prunableSourceCode != null) {
				json.put("source", Convert.toString(prunableSourceCode.getSource(), true));
				json.put("language", Short.toString(prunableSourceCode.getLanguage()));
			} else if (source != null) {
				json.put("source", Convert.toString(source, true));
				json.put("language", Short.toString(language));
			}
			json.put("messageHash", Convert.toHexString(getHash()));
		}

		@Override
		void validate(Transaction transaction) throws NxtException.ValidationException {
			if (transaction.getType() != TransactionType.WorkControl.NEW_TASK) {
				throw new NxtException.NotValidException(
						"Source code can only be attached to work-creation transactions!");
			}

			if (source != null) {
				byte[] src = getSource();

				if (src != null && src.length > Constants.MAX_WORK_CODE_LENGTH) {
					throw new NxtException.NotValidException("Invalid source code length: " + src.length);
				}
				if (src == null && Nxt.getEpochTime() - transaction.getTimestamp() < Constants.MIN_PRUNABLE_LIFETIME) {
					throw new NxtException.NotCurrentlyValidException("Source code has been pruned prematurely");
				}
				

				
				if (language == 0x01) {
					try {
						Executioner.checkSyntax(src);
					} catch (Exception e) {
						e.printStackTrace();
						throw new NxtException.NotValidException(e.getMessage());
					}
				} else {
					throw new NxtException.NotValidException("Source code language is not supported");
				}
				// OTHER LANGUAGES MUST BE APPENDED HERE IF ADDED LATER ON!!
			}
		}

		@Override
		void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
			if (Nxt.getEpochTime() - transaction.getTimestamp() < Constants.MAX_PRUNABLE_LIFETIME) {
				nxt.PrunableSourceCode.add((TransactionImpl) transaction, this);
			}
		}

		public byte[] getSource() {
			if (prunableSourceCode != null) {
				return prunableSourceCode.getSource();
			}
			return source;
		}

		public short getLanguage() {
			if (prunableSourceCode != null) {
				return prunableSourceCode.getLanguage();
			}
			return language;
		}

		@Override
		public byte[] getHash() {
			if (hash != null) {
				return hash;
			}
			MessageDigest digest = Crypto.sha256();
			digest.update(source);
			digest.update(Short.toString(language).getBytes());
			return digest.digest();
		}
		
		public byte[] recalcHash() {
			MessageDigest digest = Crypto.sha256();
			digest.update(source);
			digest.update(Short.toString(language).getBytes());
			return digest.digest();
		}

		@Override
		final void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
			if (!hasPrunableData() && shouldLoadPrunable(transaction, includeExpiredPrunable)) {
				nxt.PrunableSourceCode prunableSourceCode = nxt.PrunableSourceCode
						.getPrunableSourceCode(transaction.getId());
				if (prunableSourceCode != null && prunableSourceCode.getSource() != null) {
					this.prunableSourceCode = prunableSourceCode;
				}
			}
		}

		@Override
		public final boolean hasPrunableData() {
			return (prunableSourceCode != null || source != null);
		}

		@Override
		public void restorePrunableData(Transaction transaction, int blockTimestamp, int height) {
			nxt.PrunableSourceCode.add((TransactionImpl) transaction, this, blockTimestamp, height);
		}
	}

	final class PublicKeyAnnouncement extends AbstractAppendix {

		private static final String appendixName = "PublicKeyAnnouncement";

		static PublicKeyAnnouncement parse(JSONObject attachmentData) {
			if (!hasAppendix(appendixName, attachmentData)) {
				return null;
			}
			return new PublicKeyAnnouncement(attachmentData);
		}

		private final byte[] publicKey;

		PublicKeyAnnouncement(ByteBuffer buffer, byte transactionVersion) {
			super(buffer, transactionVersion);
			this.publicKey = new byte[32];
			buffer.get(this.publicKey);
		}

		PublicKeyAnnouncement(JSONObject attachmentData) {
			super(attachmentData);
			this.publicKey = Convert.parseHexString((String) attachmentData.get("recipientPublicKey"));
		}

		public PublicKeyAnnouncement(byte[] publicKey) {
			this.publicKey = publicKey;
		}

		@Override
		String getAppendixName() {
			return appendixName;
		}

		@Override
		int getMySize() {
			return 32;
		}

		@Override
		void putMyBytes(ByteBuffer buffer) {
			buffer.put(publicKey);
		}

		@Override
		void putMyJSON(JSONObject json) {
			json.put("recipientPublicKey", Convert.toHexString(publicKey));
		}

		@Override
		void validate(Transaction transaction) throws NxtException.ValidationException {
			if (transaction.getRecipientId() == 0) {
				throw new NxtException.NotValidException(
						"PublicKeyAnnouncement cannot be attached to transactions with no recipient");
			}
			if (!Crypto.isCanonicalPublicKey(publicKey)) {
				throw new NxtException.NotValidException(
						"Invalid recipient public key: " + Convert.toHexString(publicKey));
			}
			long recipientId = transaction.getRecipientId();
			if (Account.getId(this.publicKey) != recipientId) {
				throw new NxtException.NotValidException("Announced public key does not match recipient accountId");
			}
			byte[] recipientPublicKey = Account.getPublicKey(recipientId);
			if (recipientPublicKey != null && !Arrays.equals(publicKey, recipientPublicKey)) {
				throw new NxtException.NotCurrentlyValidException(
						"A different public key for this account has already been announced");
			}
		}

		@Override
		void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
			if (Account.setOrVerify(recipientAccount.getId(), publicKey)) {
				recipientAccount.apply(this.publicKey);
			}
		}

		public byte[] getPublicKey() {
			return publicKey;
		}

	}
}

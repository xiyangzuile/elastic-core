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

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

import org.json.simple.JSONObject;

import nxt.NxtException.NotValidException;
import nxt.crypto.Crypto;
import nxt.util.Convert;

public interface Appendix {

	abstract class AbstractAppendix implements Appendix {

		private final byte version;

		AbstractAppendix() {
			this.version = 1;
		}

		AbstractAppendix(final ByteBuffer buffer, final byte transactionVersion) {
			if (transactionVersion == 0) {
				this.version = 0;
			} else {
				this.version = buffer.get();
			}
		}

		AbstractAppendix(final int version) {
			this.version = (byte) version;
		}

		AbstractAppendix(final JSONObject attachmentData) {
			final Long l = (Long) attachmentData.get("version." + this.getAppendixName());
			this.version = (byte) (l == null ? 0 : l);
		}

		abstract void apply(Transaction transaction, Account senderAccount, Account recipientAccount)
				throws NotValidException;

		abstract String getAppendixName();

		@Override
		public Fee getBaselineFee(final Transaction transaction) {
			return Fee.NONE;
		}

		@Override
		public int getBaselineFeeHeight() {
			return 0;
		}

		@Override
		public final int getFullSize() {
			return this.getMyFullSize() + (this.version > 0 ? 1 : 0);
		}

		@Override
		public final JSONObject getJSONObject() {
			final JSONObject json = new JSONObject();
			json.put("version." + this.getAppendixName(), this.version);
			this.putMyJSON(json);
			return json;
		}

		int getMyFullSize() {
			return this.getMySize();
		}

		abstract int getMySize();

		@Override
		public Fee getNextFee(final Transaction transaction) {
			return this.getBaselineFee(transaction);
		}

		@Override
		public int getNextFeeHeight() {
			return Integer.MAX_VALUE;
		}

		@Override
		public final int getSize() {
			return this.getMySize() + (this.version > 0 ? 1 : 0);
		}

		@Override
		public final byte getVersion() {
			return this.version;
		}

		final void loadPrunable(final Transaction transaction) {
			this.loadPrunable(transaction, false);
		}

		void loadPrunable(final Transaction transaction, final boolean includeExpiredPrunable) {
		}

		@Override
		public final void putBytes(final ByteBuffer buffer) {
			if (this.version > 0) {
				buffer.put(this.version);
			}
			this.putMyBytes(buffer);
		}

		abstract void putMyBytes(ByteBuffer buffer);

		abstract void putMyJSON(JSONObject json);

		abstract void validate(Transaction transaction) throws NxtException.ValidationException;

		boolean verifyVersion(final byte transactionVersion) {
			return this.version == 1;
		}

	}

	interface Encryptable {
		void encrypt(String secretPhrase);
	}

	interface Prunable {
		byte[] getHash();

		boolean hasPrunableData();

		void restorePrunableData(Transaction transaction, int blockTimestamp, int height);

		default boolean shouldLoadPrunable(final Transaction transaction, final boolean includeExpiredPrunable) {
			return (Nxt.getEpochTime()
					- transaction.getTimestamp()) < (includeExpiredPrunable && Constants.INCLUDE_EXPIRED_PRUNABLE
							? Constants.MAX_PRUNABLE_LIFETIME : Constants.MIN_PRUNABLE_LIFETIME);
		}
	}

	class PrunableSourceCode extends Appendix.AbstractAppendix implements Prunable {

		private static final String appendixName = "PrunableSourceCode";

		private static final Fee PRUNABLE_SOURCE_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT / 10) {
			@Override
			public int getSize(final TransactionImpl transaction, final Appendix appendix) {
				return appendix.getFullSize();
			}
		};

		static PrunableSourceCode parse(final JSONObject attachmentData) {
			if (!Appendix.hasAppendix(PrunableSourceCode.appendixName, attachmentData)) {
				return null;
			}
			return new PrunableSourceCode(attachmentData);
		}

		private byte[] hash;
		private byte[] source;
		private final short language;

		private volatile nxt.PrunableSourceCode prunableSourceCode;

		private PrunableSourceCode(final byte[] source, final short language) {
			this.source = source;
			this.hash = null;
			this.language = language;
		}

		PrunableSourceCode(final ByteBuffer buffer, final byte transactionVersion) {
			super(buffer, transactionVersion);
			this.hash = new byte[32];
			buffer.get(this.hash);
			this.source = null;
			this.language = 0;
		}

		public void simulatePruning(){
			this.hash = getHash();
			this.source = null;
		}

		private PrunableSourceCode(final JSONObject attachmentData) {
			super(attachmentData);
			final String hashString = Convert.emptyToNull((String) attachmentData.get("messageHash"));
			final String messageString = Convert.emptyToNull((String) attachmentData.get("source"));
			final String languageString = Convert.emptyToNull((String) attachmentData.get("language"));

			if ((hashString != null) && (messageString == null)) {
				this.hash = Convert.parseHexString(hashString);
				this.source = null;
				this.language = 0;
			} else {
				this.hash = null;
				this.source = Convert.compress(Convert.toBytes(messageString, true));
				this.language = Short.parseShort(languageString);
			}
		}

		public PrunableSourceCode(final String source, final short language) {
			this(Convert.compress(Convert.toBytes(source, true)), language);
		}

		@Override
		void apply(final Transaction transaction, final Account senderAccount, final Account recipientAccount) {
			if ((Nxt.getEpochTime() - transaction.getTimestamp()) < Constants.MAX_PRUNABLE_LIFETIME) {
				nxt.PrunableSourceCode.add((TransactionImpl) transaction, this);
			}
		}

		@Override
		String getAppendixName() {
			return PrunableSourceCode.appendixName;
		}

		@Override
		public Fee getBaselineFee(final Transaction transaction) {
			return PrunableSourceCode.PRUNABLE_SOURCE_FEE;
		}

		@Override
		public byte[] getHash() {
			if (this.hash != null) {
				return this.hash;
			}
			final MessageDigest digest = Crypto.sha256();
			digest.update(this.source);
			digest.update(Short.toString(this.language).getBytes());
			return digest.digest();
		}

		public short getLanguage() {
			if (this.prunableSourceCode != null) {
				return this.prunableSourceCode.getLanguage();
			}
			return this.language;
		}

		@Override
		int getMyFullSize() {
			return this.getSource() == null ? 0
					: this.getSource().length + 2 /* short for language id */;
		}

		@Override
		int getMySize() {
			return 32;
		}

		public byte[] getSource() {
			if (this.prunableSourceCode != null) {
				return this.prunableSourceCode.getSource();
			}
			return this.source;
		}

		@Override
		public final boolean hasPrunableData() {
			return ((this.prunableSourceCode != null) || (this.source != null));
		}

		@Override
		final void loadPrunable(final Transaction transaction, final boolean includeExpiredPrunable) {
			if (!this.hasPrunableData() && this.shouldLoadPrunable(transaction, includeExpiredPrunable)) {
				final nxt.PrunableSourceCode prunableSourceCode = nxt.PrunableSourceCode
						.getPrunableSourceCode(transaction.getId());
				if ((prunableSourceCode != null) && (prunableSourceCode.getSource() != null)) {
					this.prunableSourceCode = prunableSourceCode;
				}
			}
		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			buffer.put(this.getHash());
		}

		@Override
		void putMyJSON(final JSONObject json) {
			if (this.prunableSourceCode != null) {
				json.put("source", Convert.toString(Convert.uncompress(this.prunableSourceCode.getSource()), true));
				json.put("language", Short.toString(this.prunableSourceCode.getLanguage()));
			} else if (this.source != null) {
				json.put("source", Convert.toString(Convert.uncompress(this.source), true));
				json.put("language", Short.toString(this.language));
			}
			json.put("messageHash", Convert.toHexString(this.getHash()));
		}

		public byte[] recalcHash() {
			final MessageDigest digest = Crypto.sha256();
			digest.update(this.source);
			digest.update(Short.toString(this.language).getBytes());
			return digest.digest();
		}

		@Override
		public void restorePrunableData(final Transaction transaction, final int blockTimestamp, final int height) {
			nxt.PrunableSourceCode.add((TransactionImpl) transaction, this, blockTimestamp, height);
		}

		@Override
		void validate(final Transaction transaction) throws NxtException.ValidationException {
			if (transaction.getType() != TransactionType.WorkControl.NEW_TASK) {
				throw new NxtException.NotValidException(
						"Source code can only be attached to work-creation transactions!");
			}

			if (this.source != null) {
				byte[] src = this.getSource();
				byte[] dec = null;

				// try to decompress
				if(src!=null) {
					try {
						dec = Convert.uncompress(src);
					} catch (Exception e) {
						throw new NotValidException(e.toString());
					}
				}
				if ((src == null)
						&& ((Nxt.getEpochTime() - transaction.getTimestamp()) < Constants.MIN_PRUNABLE_LIFETIME)) {
					throw new NxtException.NotCurrentlyValidException("Source code has been pruned prematurely");
				}
				if ((dec != null) && (dec.length > Constants.MAX_WORK_CODE_LENGTH)) {
					throw new NxtException.NotValidException("Invalid source code length: " + src.length);
				}

			}

			// Other source code checks are performed by super nodes
		}
	}

	final class PublicKeyAnnouncement extends AbstractAppendix {

		private static final String appendixName = "PublicKeyAnnouncement";

		static PublicKeyAnnouncement parse(final JSONObject attachmentData) {
			if (!Appendix.hasAppendix(PublicKeyAnnouncement.appendixName, attachmentData)) {
				return null;
			}
			return new PublicKeyAnnouncement(attachmentData);
		}

		private final byte[] publicKey;

		public PublicKeyAnnouncement(final byte[] publicKey) {
			this.publicKey = publicKey;
		}

		PublicKeyAnnouncement(final ByteBuffer buffer, final byte transactionVersion) {
			super(buffer, transactionVersion);
			this.publicKey = new byte[32];
			buffer.get(this.publicKey);
		}

		PublicKeyAnnouncement(final JSONObject attachmentData) {
			super(attachmentData);
			this.publicKey = Convert.parseHexString((String) attachmentData.get("recipientPublicKey"));
		}

		@Override
		void apply(final Transaction transaction, final Account senderAccount, final Account recipientAccount)
				throws NotValidException {
			if (recipientAccount == null) {
				throw new NxtException.NotValidException("PublicKeyAnnouncement must have a correct receipient");
			}
			if (Account.setOrVerify(recipientAccount.getId(), this.publicKey)) {
				recipientAccount.apply(this.publicKey);
			}
		}

		@Override
		String getAppendixName() {
			return PublicKeyAnnouncement.appendixName;
		}

		@Override
		int getMySize() {
			return 32;
		}

		public byte[] getPublicKey() {
			return this.publicKey;
		}

		@Override
		void putMyBytes(final ByteBuffer buffer) {
			buffer.put(this.publicKey);
		}

		@Override
		void putMyJSON(final JSONObject json) {
			json.put("recipientPublicKey", Convert.toHexString(this.publicKey));
		}

		@Override
		void validate(final Transaction transaction) throws NxtException.ValidationException {
			if (transaction.getRecipientId() == 0 || transaction.getType().canHaveRecipient() == false || transaction.getType() != TransactionType.Payment.REDEEM) {
				throw new NxtException.NotValidException(
						"PublicKeyAnnouncement cannot be attached to transactions with no recipient or to redeem transactions");
			}
			if (!Crypto.isCanonicalPublicKey(this.publicKey)) {
				throw new NxtException.NotValidException(
						"Invalid recipient public key: " + Convert.toHexString(this.publicKey));
			}
			final long recipientId = transaction.getRecipientId();
			if (Account.getId(this.publicKey) != recipientId) {
				throw new NxtException.NotValidException("Announced public key does not match recipient accountId");
			}
			final byte[] recipientPublicKey = Account.getPublicKey(recipientId);
			if ((recipientPublicKey != null) && !Arrays.equals(this.publicKey, recipientPublicKey)) {
				throw new NxtException.NotCurrentlyValidException(
						"A different public key for this account has already been announced");
			}
		}

	}

	static boolean hasAppendix(final String appendixName, final JSONObject attachmentData) {
		return attachmentData.get("version." + appendixName) != null;
	}

	Fee getBaselineFee(Transaction transaction);

	int getBaselineFeeHeight();

	int getFullSize();

	JSONObject getJSONObject();

	Fee getNextFee(Transaction transaction);

	int getNextFeeHeight();

	int getSize();

	byte getVersion();

	void putBytes(ByteBuffer buffer);
}

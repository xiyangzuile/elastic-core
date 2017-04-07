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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.*;

import org.json.simple.JSONObject;

import nxt.NxtException.NotValidException;
import nxt.crypto.Crypto;
import nxt.db.DbKey;
import nxt.util.Convert;
import nxt.util.Filter;
import nxt.util.Logger;

public final class TransactionImpl implements Transaction {


	static final class BuilderImpl implements Builder {

		private final short deadline;
		private final byte[] senderPublicKey;
		private byte[] superNodePublicKey;
		private final long amountNQT;
		private final long feeNQT;
		private final TransactionType type;
		private final byte version;
		private Attachment.AbstractAttachment attachment;

		private long recipientId;
		private byte[] referencedTransactionFullHash;
		private byte[] signature;
		private byte[] supernode_signature;

		private Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
		private Appendix.PrunableSourceCode prunableSourceCode;
		private long blockId;
		private int height = Integer.MAX_VALUE;
		private long id;
		private long senderId;
		private int timestamp = Integer.MAX_VALUE;
		private int blockTimestamp = -1;
		private byte[] fullHash;
		private boolean ecBlockSet = false;
		private int ecBlockHeight;
		private long ecBlockId;
		private short index = -1;

		BuilderImpl(final byte version, final byte[] senderPublicKey, final long amountNQT, final long feeNQT,
				final short deadline, final Attachment.AbstractAttachment attachment) {
			this.version = version;
			this.deadline = deadline;
			this.senderPublicKey = senderPublicKey;
			this.amountNQT = amountNQT;
			this.feeNQT = feeNQT;
			this.attachment = attachment;
			this.type = attachment.getTransactionType();
		}

		@Override
		public BuilderImpl appendix(final Appendix.PrunableSourceCode prunableSourceCode) {
			this.prunableSourceCode = prunableSourceCode;
			return this;
		}

		@Override
		public BuilderImpl appendix(final Appendix.PublicKeyAnnouncement publicKeyAnnouncement) {
			this.publicKeyAnnouncement = publicKeyAnnouncement;
			return this;
		}

		BuilderImpl appendix(final Attachment.AbstractAttachment attachment) {
			this.attachment = attachment;
			return this;
		}

		BuilderImpl blockId(final long blockId) {
			this.blockId = blockId;
			return this;
		}

		BuilderImpl blockTimestamp(final int blockTimestamp) {
			this.blockTimestamp = blockTimestamp;
			return this;
		}

		@Override
		public TransactionImpl build() throws NxtException.NotValidException {
			return this.build(null);
		}

		@Override
		public TransactionImpl build(final String secretPhrase) throws NxtException.NotValidException {
			if (this.timestamp == Integer.MAX_VALUE) {
				this.timestamp = Nxt.getEpochTime();
			}
			if (!this.ecBlockSet) {
				final Block ecBlock = BlockchainImpl.getInstance().getECBlock(this.timestamp);
				this.ecBlockHeight = ecBlock.getHeight();
				this.ecBlockId = ecBlock.getId();
			}
			return new TransactionImpl(this, secretPhrase);
		}

		@Override
		public TransactionImpl buildUnixTimeStamped(final String secretPhrase, final int unixTimestamp) throws NxtException.NotValidException {
			if (this.timestamp == Integer.MAX_VALUE) {
				this.timestamp = Convert.toEpochTime(unixTimestamp * 1000L);
			}
			if (!this.ecBlockSet) {
				final Block ecBlock = BlockchainImpl.getInstance().getECBlock(this.timestamp);
				this.ecBlockHeight = ecBlock.getHeight();
				this.ecBlockId = ecBlock.getId();
			}
			return new TransactionImpl(this, secretPhrase);
		}

		@Override
		public BuilderImpl ecBlockHeight(final int height) {
			this.ecBlockHeight = height;
			this.ecBlockSet = true;
			return this;
		}

		@Override
		public BuilderImpl ecBlockId(final long blockId) {
			this.ecBlockId = blockId;
			this.ecBlockSet = true;
			return this;
		}

		BuilderImpl fullHash(final byte[] fullHash) {
			this.fullHash = fullHash;
			return this;
		}

		BuilderImpl height(final int height) {
			this.height = height;
			return this;
		}

		BuilderImpl id(final long id) {
			this.id = id;
			return this;
		}

		BuilderImpl index(final short index) {
			this.index = index;
			return this;
		}

		@Override
		public BuilderImpl recipientId(final long recipientId) {
			this.recipientId = recipientId;
			return this;
		}

		BuilderImpl referencedTransactionFullHash(final byte[] referencedTransactionFullHash) {
			this.referencedTransactionFullHash = referencedTransactionFullHash;
			return this;
		}

		@Override
		public BuilderImpl referencedTransactionFullHash(final String referencedTransactionFullHash) {
			this.referencedTransactionFullHash = Convert.parseHexString(referencedTransactionFullHash);
			return this;
		}

		BuilderImpl senderId(final long senderId) {
			this.senderId = senderId;
			return this;
		}

		BuilderImpl signature(final byte[] signature) {
			this.signature = signature;
			return this;
		}

		BuilderImpl supernode_signature(final byte[] pubkey, final byte[] signature) {
			this.supernode_signature = signature;
			this.superNodePublicKey = pubkey;
			return this;
		}

		@Override
		public BuilderImpl timestamp(final int timestamp) {
			this.timestamp = timestamp;
			return this;
		}

	}

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(final byte[] bytes) {
		final char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			final int v = bytes[j] & 0xFF;
			hexChars[j * 2] = TransactionImpl.hexArray[v >>> 4];
			hexChars[(j * 2) + 1] = TransactionImpl.hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	static TransactionImpl.BuilderImpl newTransactionBuilder(final byte[] bytes) throws NxtException.NotValidException {
		try {
			final ByteBuffer buffer = ByteBuffer.wrap(bytes);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			final byte type = buffer.get();
			byte subtype = buffer.get();
			final byte version = (byte) ((subtype & 0xF0) >> 4);
			subtype = (byte) (subtype & 0x0F);
			final int timestamp = buffer.getInt();
			final short deadline = buffer.getShort();
			final byte[] senderPublicKey = new byte[32];
			buffer.get(senderPublicKey);
			byte[] superNodePublicKey = new byte[32];
			buffer.get(superNodePublicKey);
			superNodePublicKey = Convert.emptyToNull(superNodePublicKey);

			final long recipientId = buffer.getLong();
			final long amountNQT = buffer.getLong();
			final long feeNQT = buffer.getLong();
			byte[] referencedTransactionFullHash = new byte[32];
			buffer.get(referencedTransactionFullHash);
			referencedTransactionFullHash = Convert.emptyToNull(referencedTransactionFullHash);
			byte[] signature = new byte[64];
			byte[] supernode_signature = new byte[64];
			buffer.get(signature);
			signature = Convert.emptyToNull(signature);
			buffer.get(supernode_signature);
			supernode_signature = Convert.emptyToNull(supernode_signature);
			int flags = 0;
			int ecBlockHeight = 0;
			long ecBlockId = 0;
			flags = buffer.getInt();
			ecBlockHeight = buffer.getInt();
			ecBlockId = buffer.getLong();

			final TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
			if (transactionType == null) {
				throw new NxtException.NotValidException("Unknown transaction type");
			}

			final TransactionImpl.BuilderImpl builder = new BuilderImpl(version, senderPublicKey, amountNQT, feeNQT,
					deadline, transactionType.parseAttachment(buffer, version)).timestamp(timestamp)
							.referencedTransactionFullHash(referencedTransactionFullHash).signature(signature).supernode_signature(superNodePublicKey, supernode_signature)
							.ecBlockHeight(ecBlockHeight).ecBlockId(ecBlockId);
			if (transactionType.canHaveRecipient()) {
				builder.recipientId(recipientId);
			}
			int position = 1;
			if ((flags & position) != 0) {
				builder.appendix(new Appendix.PublicKeyAnnouncement(buffer, version));
			}
			position <<= 1;
			if ((flags & position) != 0) {
				builder.appendix(new Appendix.PrunableSourceCode(buffer, version));
			}
			if (buffer.hasRemaining()) {
				throw new NxtException.NotValidException("Transaction bytes too long, " + buffer.remaining()
						+ " extra bytes, TX type = " + type + ":" + subtype);
			}
			return builder;
		} catch (NxtException.NotValidException | RuntimeException e) {
			Logger.logDebugMessage("Failed to parse transaction bytes: " + Convert.toHexString(bytes));
			throw e;
		}
	}

	static TransactionImpl.BuilderImpl newTransactionBuilder(final byte[] bytes, final JSONObject prunableAttachments)
			throws NxtException.NotValidException {
		final BuilderImpl builder = TransactionImpl.newTransactionBuilder(bytes);
		if (prunableAttachments != null) {

			final Appendix.PrunableSourceCode prunableSourceCode = Appendix.PrunableSourceCode
					.parse(prunableAttachments);
			if (prunableSourceCode != null) {
				builder.appendix(prunableSourceCode);
			}
		}
		return builder;
	}

	static TransactionImpl.BuilderImpl newTransactionBuilder(final JSONObject transactionData)
			throws NxtException.NotValidException {
		try {
			final byte type = ((Long) transactionData.get("type")).byteValue();
			final byte subtype = ((Long) transactionData.get("subtype")).byteValue();
			final int timestamp = ((Long) transactionData.get("timestamp")).intValue();
			final short deadline = ((Long) transactionData.get("deadline")).shortValue();
			final byte[] senderPublicKey = Convert.parseHexString((String) transactionData.get("senderPublicKey"));
			final byte[] superNodePublicKey = Convert.parseHexString((String) transactionData.get("superNodePublicKey"));
			final long amountNQT = Convert.parseLong(transactionData.get("amountNQT"));
			final long feeNQT = Convert.parseLong(transactionData.get("feeNQT"));
			final String referencedTransactionFullHash = (String) transactionData.get("referencedTransactionFullHash");
			final byte[] signature = Convert.parseHexString((String) transactionData.get("signature"));
			final byte[] supernode_signature = Convert.parseHexString((String) transactionData.get("supernode_signature"));
			final Long versionValue = (Long) transactionData.get("version");
			final byte version = versionValue == null ? 0 : versionValue.byteValue();
			final JSONObject attachmentData = (JSONObject) transactionData.get("attachment");
			int ecBlockHeight = 0;
			long ecBlockId = 0;
			ecBlockHeight = ((Long) transactionData.get("ecBlockHeight")).intValue();
			ecBlockId = Convert.parseUnsignedLong((String) transactionData.get("ecBlockId"));

			final TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
			if (transactionType == null) {
				throw new NxtException.NotValidException("Invalid transaction type: " + type + ", " + subtype);
			}
			final TransactionImpl.BuilderImpl builder = new BuilderImpl(version, senderPublicKey, amountNQT, feeNQT,
					deadline, transactionType.parseAttachment(attachmentData)).timestamp(timestamp)
							.referencedTransactionFullHash(referencedTransactionFullHash).signature(signature).supernode_signature(superNodePublicKey, supernode_signature)
							.ecBlockHeight(ecBlockHeight).ecBlockId(ecBlockId);
			if (transactionType.canHaveRecipient()) {
				final long recipientId = Convert.parseUnsignedLong((String) transactionData.get("recipient"));
				builder.recipientId(recipientId);
			}
			if (attachmentData != null) {
				builder.appendix((Appendix.PublicKeyAnnouncement.parse(attachmentData)));
				builder.appendix(Appendix.PrunableSourceCode.parse(attachmentData));
			}
			return builder;
		} catch (NxtException.NotValidException | RuntimeException e) {
			Logger.logDebugMessage("Failed to parse transaction: " + transactionData.toJSONString());
			throw e;
		}
	}

	public static TransactionImpl parseTransaction(final JSONObject transactionData) throws NxtException.NotValidException {
		final TransactionImpl transaction = TransactionImpl.newTransactionBuilder(transactionData).build();

		if ((transaction.getSignature() != null) && !transaction.checkSignature()) {
			throw new NxtException.NotValidException(
					"Invalid transaction signature for transaction " + transaction.getJSONObject().toJSONString());
		}

		return transaction;
	}

	private final short deadline;
	private volatile byte[] senderPublicKey;
	private volatile byte[] superNodePublicKey;
	private final long recipientId;
	private final long amountNQT;
	private final long feeNQT;
	private final byte[] referencedTransactionFullHash;
	private final TransactionType type;
	private final int ecBlockHeight;
	private final long ecBlockId;

	private final byte version;
	private final int timestamp;
	private long sncleanid = 0;

	private volatile byte[] signature;
	private volatile byte[] supernode_signature;
	private final Attachment.AbstractAttachment attachment;
	private final Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
	private final Appendix.PrunableSourceCode prunableSourceCode;
	private final List<Appendix.AbstractAppendix> appendages;
	private final int appendagesSize;
	private volatile int height = Integer.MAX_VALUE;
	private volatile long blockId;
	private volatile BlockImpl block;
	private volatile int blockTimestamp = -1;
	private volatile short index = -1;

	private volatile long id = 0;

	private volatile String stringId;

	private volatile long senderId;

	private volatile byte[] fullHash;

	private volatile DbKey dbKey;

	private volatile byte[] bytes = null;

	private String extraInfo = "";

	private volatile boolean hasValidSignature = false;

	private volatile boolean hasValidSupernodeSignature = false;

	private TransactionImpl(final BuilderImpl builder, final String secretPhrase)
			throws NxtException.NotValidException {

		this.timestamp = builder.timestamp;
		this.deadline = builder.deadline;
		this.senderPublicKey = builder.senderPublicKey;
		this.superNodePublicKey = Convert.emptyToNull(builder.superNodePublicKey);
		this.recipientId = builder.recipientId;
		this.amountNQT = builder.amountNQT;
		this.referencedTransactionFullHash = builder.referencedTransactionFullHash;
		this.type = builder.type;
		this.version = builder.version;
		this.blockId = builder.blockId;
		this.height = builder.height;
		this.index = builder.index;
		this.id = builder.id;
		this.senderId = builder.senderId;
		this.blockTimestamp = builder.blockTimestamp;
		this.fullHash = builder.fullHash;
		this.ecBlockHeight = builder.ecBlockHeight;
		this.ecBlockId = builder.ecBlockId;

		final List<Appendix.AbstractAppendix> list = new ArrayList<>();
		if ((this.attachment = builder.attachment) != null) {
			list.add(this.attachment);
		}

		if ((this.publicKeyAnnouncement = builder.publicKeyAnnouncement) != null) {
			list.add(this.publicKeyAnnouncement);
		}

		if ((this.prunableSourceCode = builder.prunableSourceCode) != null) {
			list.add(this.prunableSourceCode);
		}

		this.appendages = Collections.unmodifiableList(list);
		int appendagesSize = 0;
		for (final Appendix appendage : this.appendages) {
			if ((secretPhrase != null) && (appendage instanceof Appendix.Encryptable)) {
				((Appendix.Encryptable) appendage).encrypt(secretPhrase);
			}
			appendagesSize += appendage.getSize();
		}
		this.appendagesSize = appendagesSize;
		if ((builder.feeNQT <= 0) || (Constants.correctInvalidFees && (builder.signature == null))) {
			if (this.type.zeroFeeTransaction()) {
				this.feeNQT = 0;
			} else {
				final int effectiveHeight = (this.height < Integer.MAX_VALUE ? this.height
						: Nxt.getBlockchain().getHeight());
				final long minFee = this.getMinimumFeeNQT(effectiveHeight);
				this.feeNQT = Math.max(minFee, builder.feeNQT);
			}
		} else {
			this.feeNQT = builder.feeNQT;
		}

		// save supernode sig
		this.supernode_signature = Convert.emptyToNull(builder.supernode_signature);

		if ((builder.signature != null) && (secretPhrase != null)) {
			throw new NxtException.NotValidException("Transaction is already signed");
		} else if (builder.signature != null) {
			this.signature = builder.signature;
		} else if (secretPhrase != null) {
			if (!(this.getAttachment() instanceof Attachment.RedeemAttachment) && (this.getSenderPublicKey() != null)
					&& !Arrays.equals(this.senderPublicKey, Crypto.getPublicKey(secretPhrase))) {
				throw new NxtException.NotValidException("Secret phrase doesn't match transaction sender public key");
			}
			if ((this.getAttachment() instanceof Attachment.RedeemAttachment) && (this.getSenderPublicKey() != null)
					&& !Arrays.equals(this.senderPublicKey, Convert.parseHexString(Genesis.REDEEM_ID_PUBKEY))) {
				throw new NxtException.NotValidException("Secret phrase doesn't match REDEEM_ID public key");
			}
			if ((this.getAttachment() instanceof Attachment.RedeemAttachment) && (this.getSenderPublicKey() == null)) {
				throw new NxtException.NotValidException("This transaction must be bound to REDEEM_ID public key");
			}
			byte[] toSignBytes = this.bytes();
			this.signature = Crypto.sign(toSignBytes, secretPhrase);
			this.bytes = null;
			this.id = 0;
			this.sncleanid = 0;
			Logger.logSignMessage("Signing HEX:\t" + Convert.toHexString(toSignBytes) +
					"\nJson Trans.:\t" + this.getJSONObject() + "\nTrans. IDNR:\t"+Convert.toUnsignedLong(this.getId()));
		} else {
			this.signature = null;
			this.bytes = null;
			this.id = 0;
			this.sncleanid = 0;
		}
	}

	public void signSuperNode(String secretPhrase) {
		this.supernode_signature = Crypto.sign(this.zeroPartSignature(this.getBytes()), secretPhrase);
		this.superNodePublicKey = Crypto.getPublicKey(secretPhrase);
		this.bytes = null;
		this.id = 0;
		this.sncleanid = 0;
	}

	void sign(String secretPhrase){
		this.signature = Crypto.sign(this.bytes(), secretPhrase);
		this.bytes = null;
		this.id = 0;
		this.sncleanid = 0;
	} // Only for genesis block creation

	void apply() throws NotValidException {
		final Account senderAccount = Account.getAccount(this.getSenderId());
		senderAccount.apply(this.getSenderPublicKey());
		Account recipientAccount = null;
		if (this.recipientId != 0) {
			recipientAccount = Account.getAccount(this.recipientId);
			if (recipientAccount == null) {
				recipientAccount = Account.addOrGetAccount(this.recipientId);
			}
		}
		if (this.referencedTransactionFullHash != null) {
			senderAccount.addToUnconfirmedBalanceNQT(this.getType().getLedgerEvent(), this.getId(), 0,
					Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
		}
		for (final Appendix.AbstractAppendix appendage : this.appendages) {
			appendage.loadPrunable(this);
			appendage.apply(this, senderAccount, recipientAccount);
		}
	}

	// returns false iff double spending
	boolean applyUnconfirmed() {
		final Account senderAccount = Account.getAccount(this.getSenderId());
		return (senderAccount != null) && this.type.applyUnconfirmed(this, senderAccount);
	}

	boolean attachmentIsDuplicate(final Map<TransactionType, Map<String, Integer>> duplicates,
			final boolean atAcceptanceHeight) {
		if (!atAcceptanceHeight) {
			return false;
		}
		if (atAcceptanceHeight) {
			// all are checked at acceptance height for block duplicates
			if (this.type.isBlockDuplicate(this, duplicates)) {
				return true;
			}
		}
		// non-phased at acceptance height, and phased at execution height
		return this.type.isDuplicate(this, duplicates);
	}

	@Override
	public byte[] getSupernodeSig() {
		if(this.supernode_signature == null || this.supernode_signature == new byte[32]){
			return null;
		}
		return Convert.emptyToNull(this.supernode_signature);
	}

	private byte[] bytes() {
		if (this.bytes == null) {
			try {
				final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				buffer.put(this.type.getType());
				buffer.put((byte) ((this.version << 4) | this.type.getSubtype()));
				buffer.putInt(this.timestamp);
				buffer.putShort(this.deadline);
				buffer.put(this.getSenderPublicKey());
				buffer.put(Convert.emptyToNull(this.superNodePublicKey)!= null ? this.superNodePublicKey : new byte[32]);
				buffer.putLong(this.type.canHaveRecipient() ? this.recipientId : Genesis.CREATOR_ID);
					buffer.putLong(this.amountNQT);
					buffer.putLong(this.feeNQT);
					if (this.referencedTransactionFullHash != null) {
						buffer.put(this.referencedTransactionFullHash);
					} else {
						buffer.put(new byte[32]);
					}

				buffer.put(this.signature != null ? this.signature : new byte[64]);
				buffer.put(Convert.emptyToNull(this.supernode_signature) != null ? this.supernode_signature : new byte[64]);
				buffer.putInt(this.getFlags());
				buffer.putInt(this.ecBlockHeight);
				buffer.putLong(this.ecBlockId);

				for (final Appendix appendage : this.appendages) {
					appendage.putBytes(buffer);
				}
				this.bytes = buffer.array();
			} catch (final RuntimeException e) {
				if (this.signature != null) {
					Logger.logDebugMessage(
							"Failed to get transaction bytes for transaction: " + this.getJSONObject().toJSONString());
				}
				throw e;
			}
		}
		return this.bytes;
	}

	public byte[] getSuperNodePublicKey() {
		return Convert.emptyToNull(this.superNodePublicKey);
	}

	private boolean checkSignature() {
		if (!this.hasValidSignature) {
			if (this.getAttachment() instanceof Attachment.RedeemAttachment) {
				this.hasValidSignature = true; // TODO FIXME! CHECK IF SIGNATURE
												// BELONGS TO RECEIVER!!!!!
			} else {
				byte[] toVerifyBytes = this.getBytes();


				this.hasValidSignature = (this.signature != null) && Crypto.verify(this.signature,
						this.zeroSignature(toVerifyBytes), this.getSenderPublicKey());
				Logger.logSignMessage("Verifying HEX:\t" + Convert.toHexString(toVerifyBytes) +
						"\nZero'ed HEX:\t" + Convert.toHexString(this.zeroSignature(toVerifyBytes)) +
						"\nJson Trans.:\t" + this.getJSONObject() + "\nVERIFY RESULT:\t"+this.hasValidSignature + "\nTrans. IDNR:\t"+Convert.toUnsignedLong(this.getId()));
			}
		}
		return this.hasValidSignature;
	}

	private boolean checkSuperNodeSignature() {
		if (!this.hasValidSupernodeSignature) {
			byte[] zerobytes = this.zeroPartSignature(this.getBytes());
			this.hasValidSupernodeSignature = (Convert.emptyToNull(this.supernode_signature) != null && Convert.emptyToNull(this.superNodePublicKey) != null) && Crypto.verify(this.supernode_signature, zerobytes, this.getSuperNodePublicKey());
		}
		return this.hasValidSupernodeSignature;
	}

	@Override
	public boolean equals(final Object o) {
		return (o instanceof TransactionImpl) && (this.getId() == ((Transaction) o).getId());
	}

	byte[] fullHash() {
		if (this.fullHash == null) {
			this.getId();
		}
		return this.fullHash;
	}

	@Override
	public long getAmountNQT() {
		return this.amountNQT;
	}

	@Override
	public List<Appendix.AbstractAppendix> getAppendages() {
		return this.getAppendages(false);
	}

	@Override
	public List<Appendix.AbstractAppendix> getAppendages(final boolean includeExpiredPrunable) {
		for (final Appendix.AbstractAppendix appendage : this.appendages) {
			appendage.loadPrunable(this, includeExpiredPrunable);
		}
		return this.appendages;
	}

	@Override
	public List<Appendix> getAppendages(final Filter<Appendix> filter, final boolean includeExpiredPrunable) {
		final List<Appendix> result = new ArrayList<>();
		this.appendages.forEach(appendix -> {
			if (filter.ok(appendix)) {
				appendix.loadPrunable(this, includeExpiredPrunable);
				result.add(appendix);
			}
		});
		return result;
	}

	@Override
	public Attachment.AbstractAttachment getAttachment() {
		this.attachment.loadPrunable(this);
		return this.attachment;
	}

	long[] getBackFees() {
		return this.type.getBackFees(this);
	}

	@Override
	public BlockImpl getBlock() {
		if ((this.block == null) && (this.blockId != 0)) {
			this.block = BlockchainImpl.getInstance().getBlock(this.blockId);
		}
		return this.block;
	}

	@Override
	public long getBlockId() {
		return this.blockId;
	}

	@Override
	public int getBlockTimestamp() {
		return this.blockTimestamp;
	}

	@Override
	public byte[] getBytes() {
		return Arrays.copyOf(this.bytes(), this.bytes.length);
	}

	DbKey getDbKey() {
		if (this.dbKey == null) {
			this.dbKey = TransactionProcessorImpl.getInstance().unconfirmedTransactionDbKeyFactory.newKey(this.getId());
		}
		return this.dbKey;
	}

	@Override
	public short getDeadline() {
		return this.deadline;
	}

	@Override
	public int getECBlockHeight() {
		return this.ecBlockHeight;
	}

	@Override
	public long getECBlockId() {
		return this.ecBlockId;
	}

	@Override
	public int getExpiration() {
		return this.timestamp + (this.deadline * 60);
	}

	@Override
	public String getExtraInfo() {
		return this.extraInfo;
	}

	@Override
	public long getFeeNQT() {
		return this.feeNQT;
	}

	private int getFlags() {
		int flags = 0;
		int position = 1;
		if (this.publicKeyAnnouncement != null) {
			flags |= position;
		}
		position <<= 1;
		if (this.prunableSourceCode != null) {
			flags |= position;
		}
		return flags;
	}

	@Override
	public String getFullHash() {
		return Convert.toHexString(this.fullHash());
	}

	@Override
	public int getFullSize() {
		int fullSize = this.getSize() - this.appendagesSize;
		for (final Appendix.AbstractAppendix appendage : this.getAppendages()) {
			fullSize += appendage.getFullSize();
		}
		return fullSize;
	}

	@Override
	public int getHeight() {
		return this.height;
	}

	@Override
	public long getId() {
		if (this.id == 0) {
			if (this.signature == null) {
				{
					IllegalStateException ex = new IllegalStateException("Transaction is not signed yet: " + Convert.toHexString(this.bytes()));
					ex.printStackTrace();
					throw ex;
				}
			}
				final byte[] data = this.zeroSignature(this.getBytes());
				final byte[] signatureHash = Crypto.sha256().digest(this.signature);
				byte[] supernodeHash = null;
				if(Convert.emptyToNull(this.supernode_signature) != null)
					supernodeHash = Crypto.sha256().digest(this.supernode_signature);
				final MessageDigest digest = Crypto.sha256();
				digest.update(data);
				if(supernodeHash!=null)
					digest.update(supernodeHash);
				this.fullHash = digest.digest(signatureHash);

			final BigInteger bigInteger = new BigInteger(1,
					new byte[] { this.fullHash[7], this.fullHash[6], this.fullHash[5], this.fullHash[4],
							this.fullHash[3], this.fullHash[2], this.fullHash[1], this.fullHash[0] });
			this.id = bigInteger.longValue();
			this.stringId = bigInteger.toString();
		}
		return this.id;
	}

	@Override
	public long getSNCleanedId() {
		if (this.sncleanid == 0) {
			if (this.signature == null) {
				{
					IllegalStateException ex = new IllegalStateException("Transaction is not signed yet: " + Convert.toHexString(this.bytes()));
					ex.printStackTrace();
					throw ex;
				}
			}
				final byte[] data = this.zeroSignature(this.getBytes());
				final byte[] signatureHash = Crypto.sha256().digest(this.signature);

				final MessageDigest digest = Crypto.sha256();
				digest.update(data);

				this.fullHash = digest.digest(signatureHash);

			final BigInteger bigInteger = new BigInteger(1,
					new byte[] { this.fullHash[7], this.fullHash[6], this.fullHash[5], this.fullHash[4],
							this.fullHash[3], this.fullHash[2], this.fullHash[1], this.fullHash[0] });
			this.sncleanid = bigInteger.longValue();
		}
		return this.sncleanid;
	}

	@Override
	public short getIndex() {
		if (this.index == -1) {
			throw new IllegalStateException("Transaction index has not been set");
		}
		return this.index;
	}

	@Override
	public JSONObject getJSONObject() {
		final JSONObject json = new JSONObject();
		json.put("type", this.type.getType());
		json.put("subtype", this.type.getSubtype());
		json.put("timestamp", this.timestamp);
		json.put("deadline", this.deadline);
		json.put("senderPublicKey", Convert.toHexString(this.getSenderPublicKey()));

		if(this.getSuperNodePublicKey()!=null)
			json.put("superNodePublicKey", Convert.toHexString(this.getSuperNodePublicKey()));

		if (this.type.canHaveRecipient()) {
			json.put("recipient", Long.toUnsignedString(this.recipientId));
		}
		json.put("amountNQT", this.amountNQT);
		json.put("feeNQT", this.feeNQT);
		if (this.referencedTransactionFullHash != null) {
			json.put("referencedTransactionFullHash", Convert.toHexString(this.referencedTransactionFullHash));
		}
		json.put("ecBlockHeight", this.ecBlockHeight);
		json.put("ecBlockId", Long.toUnsignedString(this.ecBlockId));
		json.put("signature", Convert.toHexString(this.signature));
		if(this.getSupernodeSig() != null)
			json.put("supernode_signature", Convert.toHexString(Convert.emptyToNull(this.supernode_signature)));


		final JSONObject attachmentJSON = new JSONObject();
		for (final Appendix.AbstractAppendix appendage : this.appendages) {
			appendage.loadPrunable(this);
			attachmentJSON.putAll(appendage.getJSONObject());
		}
		if (!attachmentJSON.isEmpty()) {
			json.put("attachment", attachmentJSON);
		}
		json.put("version", this.version);
		return json;
	}

	private long getMinimumFeeNQT(final int blockchainHeight) {
		long totalFee = 0;
		for (final Appendix.AbstractAppendix appendage : this.appendages) {
			appendage.loadPrunable(this);
			if (blockchainHeight < appendage.getBaselineFeeHeight()) {
				return 0; // No need to validate fees before baseline block
			}
			final Fee fee = blockchainHeight >= appendage.getNextFeeHeight() ? appendage.getNextFee(this)
					: appendage.getBaselineFee(this);
			totalFee = Math.addExact(totalFee, fee.getFee(this, appendage));
		}
		if (this.referencedTransactionFullHash != null) {
			totalFee = Math.addExact(totalFee, Constants.ONE_NXT);
		}
		return totalFee;
	}

	@Override
	public JSONObject getPrunableAttachmentJSON() {
		JSONObject prunableJSON = null;
		for (final Appendix.AbstractAppendix appendage : this.appendages) {
			if (appendage instanceof Appendix.Prunable) {
				appendage.loadPrunable(this);
				if (prunableJSON == null) {
					prunableJSON = appendage.getJSONObject();
				} else {
					prunableJSON.putAll(appendage.getJSONObject());
				}
			}
		}
		return prunableJSON;
	}

	@Override
	public Appendix.PrunableSourceCode getPrunableSourceCode() {
		if (this.prunableSourceCode != null) {
			this.prunableSourceCode.loadPrunable(this);
		}
		return this.prunableSourceCode;
	}

	Appendix.PublicKeyAnnouncement getPublicKeyAnnouncement() {
		return this.publicKeyAnnouncement;
	}

	@Override
	public long getRecipientId() {
		return this.recipientId;
	}

	@Override
	public String getReferencedTransactionFullHash() {
		return Convert.toHexString(this.referencedTransactionFullHash);
	}

	@Override
	public long getSenderId() {
		if (this.senderId == 0) {
			this.senderId = Account.getId(this.getSenderPublicKey());
		}
		return this.senderId;
	}

	@Override
	public byte[] getSenderPublicKey() {
		if ((this.getAttachment() instanceof Attachment.RedeemAttachment)) {
			return Convert.parseHexString(Genesis.REDEEM_ID_PUBKEY);
		}

		if (this.senderPublicKey == null) {
			this.senderPublicKey = Account.getPublicKey(this.senderId);
		}
		return this.senderPublicKey;
	}

	@Override
	public byte[] getSignature() {
		return this.signature;
	}

	private int getSize() {
		return this.signatureOffset() + 64 + 64 + 4 + 4 + 8 + this.appendagesSize; // 2*64 for both sig and supernode_sig
	}

	@Override
	public String getStringId() {
		if (this.stringId == null) {
			this.getId();
			if (this.stringId == null) {
				this.stringId = Long.toUnsignedString(this.id);
			}
		}
		return this.stringId;
	}

	@Override
	public int getTimestamp() {
		return this.timestamp;
	}

	@Override
	public TransactionType getType() {
		return this.type;
	}

	@Override
	public byte[] getUnsignedBytes() {
		return this.zeroSignature(this.getBytes());
	}

	@Override
	public byte getVersion() {
		return this.version;
	}

	@Override
	public int hashCode() {
		return (int) (this.getId() ^ (this.getId() >>> 32));
	}

	boolean hasPrunableSourceCode() {
		return this.prunableSourceCode != null;
	}

	boolean isUnconfirmedDuplicate(final Map<TransactionType, Map<String, Integer>> duplicates) {
		return this.type.isUnconfirmedDuplicate(this, duplicates);
	}

	byte[] referencedTransactionFullHash() {
		return this.referencedTransactionFullHash;
	}

	void setBlock(final BlockImpl block) {
		this.block = block;
		this.blockId = block.getId();
		this.height = block.getHeight();
		this.blockTimestamp = block.getTimestamp();
	}

	@Override
	public void setExtraInfo(final String extraInfo) {
		this.extraInfo = extraInfo;
	}

	void setHeight(final int height) {
		this.height = height;
	}

	void setIndex(final int index) {
		this.index = (short) index;
	}

	private int signatureOffset() {
		return 1 + 1 + 4 + 2 + 32 + 32 + 8 +  8 + 8 + 32 ; // two public keys, sender and supernode
	}

	private int snPubkeyOffset() {
		return 1 + 1 + 4 + 2 + 32;
	}

	void undoUnconfirmed() {
		final Account senderAccount = Account.getAccount(this.getSenderId());
		this.type.undoUnconfirmed(this, senderAccount);
	}

	void unsetBlock() {
		this.block = null;
		this.blockId = 0;
		this.blockTimestamp = -1;
		this.index = -1;
		// must keep the height set, as transactions already having been
		// included in a popped-off block before
		// get priority when sorted for inclusion in a new block
	}

	@Override
	public long getSupernodeId(){
		if(superNodePublicKey == null) return 0;
		long accountId = Account.getId(this.superNodePublicKey);
		return accountId;
	}

	@Override
	public void validateWithoutSn() throws NxtException.ValidationException {
		if (this.type == null) {
			throw new NxtException.NotValidException("Invalid transaction type");
		}
		if (this.timestamp == 0 ? ((this.deadline != 0) || (this.feeNQT != 0))
				: ((this.deadline < 1) || ((this.type.zeroFeeTransaction() == false) && (this.feeNQT <= 0))
				|| ((this.type.zeroFeeTransaction() == true) && (this.feeNQT != 0)))
				|| (this.feeNQT > Constants.MAX_BALANCE_NQT) || (this.amountNQT < 0)
				|| (this.amountNQT > Constants.MAX_BALANCE_NQT) || (this.type == null)) {
			throw new NxtException.NotValidException(
					"Invalid transaction parameters:\n type: " + this.type + ", timestamp: " + this.timestamp
							+ ", deadline: " + this.deadline + ", fee: " + this.feeNQT + ", amount: " + this.amountNQT);
		}

		// Just a safe guard, should never be fulfilled actually
		final long maxMangle = Math.max(this.amountNQT, this.feeNQT);
		if ((this.amountNQT + this.feeNQT) < maxMangle) {
			throw new NxtException.NotValidException("Keep out, script kiddie.");
		}

		if ((this.referencedTransactionFullHash != null) && (this.referencedTransactionFullHash.length != 32)) {
			throw new NxtException.NotValidException("Invalid referenced transaction full hash "
					+ Convert.toHexString(this.referencedTransactionFullHash));
		}

		if ((this.attachment == null) || (this.type != this.attachment.getTransactionType())) {
			throw new NxtException.NotValidException(
					"Invalid attachment " + this.attachment + " for transaction of type " + this.type);
		}

		// Redeemer-Account is not allowed to do any transaction whatsoever
		if ((this.getSenderId() == Genesis.REDEEM_ID) && (this.type != TransactionType.Payment.REDEEM)) {
			throw new NxtException.NotValidException("Redeem Account is not allowed to do anything.");
		}

		if ((this.getSenderId() != Genesis.REDEEM_ID) && (this.type == TransactionType.Payment.REDEEM)) {
			throw new NxtException.NotValidException("Redeem Account is the only one allowed to send redeem transactions.");
		}

		if (this.getRecipientId() == Genesis.REDEEM_ID) {
			throw new NxtException.NotValidException("Redeem Account is not allowed to do anything.");
		}

		// just another safe guard, better be safe than sorry
		if (!Objects.equals(this.type, TransactionType.Payment.REDEEM) && this.getAttachment() != null
				&& (this.getAttachment() instanceof Attachment.RedeemAttachment)) {
			throw new NxtException.NotValidException("Keep out, script kiddie.");
		}

		// Check redeem timestamp validity
		if (Objects.equals(this.type, TransactionType.Payment.REDEEM)){
			Attachment.RedeemAttachment att = (Attachment.RedeemAttachment) this.getAttachment();
			if(Convert.toEpochTime(att.getRequiredTimestamp() * 1000L) != this.getTimestamp()){
				throw new NxtException.NotValidException("Redeem timestamp not valid, you gave " + this.getTimestamp() + ", must be " + Convert.toEpochTime(att.getRequiredTimestamp()) + "!");
			}
		}

		if (!this.type.canHaveRecipient()) {
			if (this.recipientId != 0) {
				throw new NxtException.NotValidException(
						"Transactions of this type must have recipient == 0, amount == 0");
			}
		}

		if (this.type.mustHaveRecipient() && (this.version > 0)) {
			if (this.recipientId == 0) {
				throw new NxtException.NotValidException("Transactions of this type must have a valid recipient");
			}
		}


		// This type does not require any supernode sig
		if(Convert.emptyToNull(this.supernode_signature) != null || Convert.emptyToNull(this.superNodePublicKey) != null){
			throw new NxtException.NotValidException("The transaction " + this.getId() + " must not be signed by any supernode");
		}


		for (final Appendix.AbstractAppendix appendage : this.appendages) {
			appendage.loadPrunable(this);
			if (!appendage.verifyVersion(this.version)) {
				throw new NxtException.NotValidException("Invalid attachment version " + appendage.getVersion()
						+ " for transaction version " + this.version);
			}

			appendage.validate(this);
		}

		if (this.getFullSize() > Constants.MAX_PAYLOAD_LENGTH) {
			throw new NxtException.NotValidException(
					"Transaction size " + this.getFullSize() + " exceeds maximum payload size");
		}

		final int blockchainHeight = Nxt.getBlockchain().getHeight();
		final long minimumFeeNQT = this.getMinimumFeeNQT(blockchainHeight);
		if ((this.type.zeroFeeTransaction() == false) && (this.feeNQT < minimumFeeNQT)) {
			throw new NxtException.NotCurrentlyValidException(
					String.format("Transaction fee %f NXT less than minimum fee %f NXT at height %d",
							((double) this.feeNQT) / Constants.ONE_NXT, ((double) minimumFeeNQT) / Constants.ONE_NXT,
							blockchainHeight));
		}
		if ((this.type.zeroFeeTransaction() == true) && (this.feeNQT != 0)) {
			throw new NxtException.NotValidException(String.format("Transaction fee must be zero for zeroFeeTx!"));
		}
		if (this.ecBlockId != 0) {
			if (blockchainHeight < this.ecBlockHeight) {
				throw new NxtException.NotCurrentlyValidException(
						"ecBlockHeight " + this.ecBlockHeight + " exceeds blockchain height " + blockchainHeight);
			}
			if (BlockDb.findBlockIdAtHeight(this.ecBlockHeight) != this.ecBlockId) {
				throw new NxtException.NotCurrentlyValidException(
						"ecBlockHeight " + this.ecBlockHeight + " does not match ecBlockId "
								+ Long.toUnsignedString(this.ecBlockId) + ", transaction was generated on a fork");
			}
		}
	}

	@Override
	public void validate() throws NxtException.ValidationException {
		if (this.type == null) {
			throw new NxtException.NotValidException("Invalid transaction type");
		}
		if (this.timestamp == 0 ? ((this.deadline != 0) || (this.feeNQT != 0))
				: ((this.deadline < 1) || ((this.type.zeroFeeTransaction() == false) && (this.feeNQT <= 0))
						|| ((this.type.zeroFeeTransaction() == true) && (this.feeNQT != 0)))
						|| (this.feeNQT > Constants.MAX_BALANCE_NQT) || (this.amountNQT < 0)
						|| (this.amountNQT > Constants.MAX_BALANCE_NQT) || (this.type == null)) {
			throw new NxtException.NotValidException(
					"Invalid transaction parameters:\n type: " + this.type + ", timestamp: " + this.timestamp
							+ ", deadline: " + this.deadline + ", fee: " + this.feeNQT + ", amount: " + this.amountNQT);
		}

		// Just a safe guard, should never be fulfilled actually
		final long maxMangle = Math.max(this.amountNQT, this.feeNQT);
		if ((this.amountNQT + this.feeNQT) < maxMangle) {
			throw new NxtException.NotValidException("Keep out, script kiddie.");
		}

		if ((this.referencedTransactionFullHash != null) && (this.referencedTransactionFullHash.length != 32)) {
			throw new NxtException.NotValidException("Invalid referenced transaction full hash "
					+ Convert.toHexString(this.referencedTransactionFullHash));
		}

		if ((this.attachment == null) || (this.type != this.attachment.getTransactionType())) {
			throw new NxtException.NotValidException(
					"Invalid attachment " + this.attachment + " for transaction of type " + this.type);
		}

		// Redeemer-Account is not allowed to do any transaction whatsoever
		if ((this.getSenderId() == Genesis.REDEEM_ID) && (this.type != TransactionType.Payment.REDEEM)) {
			throw new NxtException.NotValidException("Redeem Account is not allowed to do anything.");
		}
		if ((this.getSenderId() != Genesis.REDEEM_ID) && (this.type == TransactionType.Payment.REDEEM)) {
			throw new NxtException.NotValidException("Redeem Account is the only one allowed to send redeem transactions.");
		}

		if (this.getRecipientId() == Genesis.REDEEM_ID) {
			throw new NxtException.NotValidException("Redeem Account is not allowed to do anything.");
		}

		// just another safe guard, better be safe than sorry
		if (!Objects.equals(this.type, TransactionType.Payment.REDEEM) && this.getAttachment() != null
				&& (this.getAttachment() instanceof Attachment.RedeemAttachment)) {
			throw new NxtException.NotValidException("Keep out, script kiddie.");
		}

		// Check redeem timestamp validity
		if (Objects.equals(this.type, TransactionType.Payment.REDEEM)){
			Attachment.RedeemAttachment att = (Attachment.RedeemAttachment) this.getAttachment();
			if(Convert.toEpochTime(att.getRequiredTimestamp() * 1000L) != this.getTimestamp()){
				throw new NxtException.NotValidException("Redeem timestamp not valid, you gave " + this.getTimestamp() + ", must be " + Convert.toEpochTime(att.getRequiredTimestamp()) + "!");
			}
		}

		if (!this.type.canHaveRecipient()) {
			if (this.recipientId != 0) {
				throw new NxtException.NotValidException(
						"Transactions of this type must have recipient == 0, amount == 0");
			}
		}

		if (this.type.mustHaveRecipient() && (this.version > 0)) {
			if (this.recipientId == 0) {
				throw new NxtException.NotValidException("Transactions of this type must have a valid recipient");
			}
		}

		if(this.type.mustHaveSupernodeSignature()){
			if(Convert.emptyToNull(this.supernode_signature) == null || Convert.emptyToNull(this.superNodePublicKey) == null){
				throw new NxtException.NotValidException("The transaction must be signed by a supernode");
			}else{
				// check signature in verifySignature() routine ... if it is there. Just make sure now that the public key actually belongs to a supernode
				long accountId = Account.getId(this.superNodePublicKey);
				Account snode = Account.getAccount(accountId);
				if(snode == null){
					throw new NxtException.NotValidException("The super node is unknown");
				}
				if(snode.isSuperNode() == false){
					throw new NxtException.NotValidException("The super node is not a super node");
				}
			}
		}else{
			// This type does not require any supernode sig
			if(Convert.emptyToNull(this.supernode_signature) != null || Convert.emptyToNull(this.superNodePublicKey) != null){
				throw new NxtException.NotValidException("The transaction " + this.getId() + " must not be signed by any supernode");
			}
		}

		for (final Appendix.AbstractAppendix appendage : this.appendages) {
			appendage.loadPrunable(this);
			if (!appendage.verifyVersion(this.version)) {
				throw new NxtException.NotValidException("Invalid attachment version " + appendage.getVersion()
						+ " for transaction version " + this.version);
			}

			appendage.validate(this);
		}

		if (this.getFullSize() > Constants.MAX_PAYLOAD_LENGTH) {
			throw new NxtException.NotValidException(
					"Transaction size " + this.getFullSize() + " exceeds maximum payload size");
		}

		final int blockchainHeight = Nxt.getBlockchain().getHeight();
		final long minimumFeeNQT = this.getMinimumFeeNQT(blockchainHeight);
		if ((this.type.zeroFeeTransaction() == false) && (this.feeNQT < minimumFeeNQT)) {
			throw new NxtException.NotCurrentlyValidException(
					String.format("Transaction fee %f NXT less than minimum fee %f NXT at height %d",
							((double) this.feeNQT) / Constants.ONE_NXT, ((double) minimumFeeNQT) / Constants.ONE_NXT,
							blockchainHeight));
		}
		if ((this.type.zeroFeeTransaction() == true) && (this.feeNQT != 0)) {
			throw new NxtException.NotValidException(String.format("Transaction fee must be zero for zeroFeeTx!"));
		}
		if (this.ecBlockId != 0) {
			if (blockchainHeight < this.ecBlockHeight) {
				throw new NxtException.NotCurrentlyValidException(
						"ecBlockHeight " + this.ecBlockHeight + " exceeds blockchain height " + blockchainHeight);
			}
			if (BlockDb.findBlockIdAtHeight(this.ecBlockHeight) != this.ecBlockId) {
				throw new NxtException.NotCurrentlyValidException(
						"ecBlockHeight " + this.ecBlockHeight + " does not match ecBlockId "
								+ Long.toUnsignedString(this.ecBlockId) + ", transaction was generated on a fork");
			}
		}
	}

	@Override
	public boolean verifySignature() {

		boolean result = false;

		if (this.getAttachment() instanceof Attachment.RedeemAttachment) {
			result = this.checkSignature();// TODO: check redeem
		} else {
			result = this.checkSignature() && Account.setOrVerify(this.getSenderId(), this.getSenderPublicKey());
		}

		if(result && Convert.emptyToNull(this.supernode_signature) != null){
			result = this.checkSuperNodeSignature();
		}

		return result;
	}

	private byte[] zeroSignature(final byte[] data) {
		final int start = this.signatureOffset();
		for (int i = start; i < (start + 64 * 2); i++) { // zeroing two signatures (*2), namely the user and the supernode signature
			data[i] = 0;
		}

		// Also, important, do not forget to zero the SN public key
		final int start_snpubkey = this.snPubkeyOffset();
		for (int i = start_snpubkey; i < (start_snpubkey + 32); i++) { // zeroing two signatures (*2), namely the user and the supernode signature
			data[i] = 0;
		}

		return data;
	}
	private byte[] zeroPartSignature(final byte[] data) {
		final int start = this.signatureOffset();
		for (int i = start + 64; i < (start + 64 * 2); i++) { // zeroing supernode sig only
			data[i] = 0;
		}

		// Also, important, do not forget to zero the SN public key
		final int start_snpubkey = this.snPubkeyOffset();
		for (int i = start_snpubkey; i < (start_snpubkey + 32); i++) {
			data[i] = 0;
		}

		return data;
	}

}

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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import nxt.AccountLedger.LedgerEvent;
import nxt.TransactionType.Payment;
import nxt.crypto.Crypto;
import nxt.db.DbIterator;
import nxt.util.Convert;
import nxt.util.Logger;

public final class BlockImpl implements Block {

	private static LRUCache powDifficultyLRUCache = new LRUCache(50);
	private static DoubleLongLRUCache powPerBlockAndWorkLRUCache = new DoubleLongLRUCache(100);
	private static final long[] badBlocks = new long[] {};
	static {
		Arrays.sort(BlockImpl.badBlocks);
	}

	public static BigInteger calculateNextMinPowTarget(final long lastBlockId) {

		final BigInteger cached = BlockImpl.powDifficultyLRUCache.get(lastBlockId);
		if (cached != null) {
			return cached;
		}

		BigInteger converted_new_pow = BigInteger.valueOf(0);

		try (DbIterator<Work> it = Work.getLastTenClosed()) {

			if (it.hasNext() == false) {
				converted_new_pow = Constants.least_possible_target;
			} else {
				while (it.hasNext()) {
					final Work w = it.next();
					final BigInteger candidate = w.getWork_min_pow_target_bigint();
					if (candidate.compareTo(converted_new_pow) == 1) {
						converted_new_pow = candidate;
					}
				}
			}

			BlockImpl.powDifficultyLRUCache.set(lastBlockId, converted_new_pow);
			return converted_new_pow;
		} catch (final Exception e) {
			return Constants.least_possible_target; /* FIXME TODO, check this */
		}
	}

	static BlockImpl parseBlock(final JSONObject blockData) throws NxtException.NotValidException {
		try {
			final int version = ((Long) blockData.get("version")).intValue();
			final int timestamp = ((Long) blockData.get("timestamp")).intValue();
			final long previousBlock = Convert.parseUnsignedLong((String) blockData.get("previousBlock"));
			final long totalAmountNQT = Convert.parseLong(blockData.get("totalAmountNQT"));
			final long totalFeeNQT = Convert.parseLong(blockData.get("totalFeeNQT"));
			final int payloadLength = ((Long) blockData.get("payloadLength")).intValue();
			final byte[] payloadHash = Convert.parseHexString((String) blockData.get("payloadHash"));
			final byte[] generatorPublicKey = Convert.parseHexString((String) blockData.get("generatorPublicKey"));
			final byte[] generationSignature = Convert.parseHexString((String) blockData.get("generationSignature"));
			final byte[] blockSignature = Convert.parseHexString((String) blockData.get("blockSignature"));
			final byte[] previousBlockHash = Convert.parseHexString((String) blockData.get("previousBlockHash"));
			final List<TransactionImpl> blockTransactions = new ArrayList<>();
			for (final Object transactionData : (JSONArray) blockData.get("transactions")) {
				blockTransactions.add(TransactionImpl.parseTransaction((JSONObject) transactionData));
			}
			final BlockImpl block = new BlockImpl(version, timestamp, previousBlock, totalAmountNQT, totalFeeNQT,
					payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature,
					previousBlockHash, blockTransactions, null);
			if (!block.checkSignature()) {
				throw new NxtException.NotValidException("Invalid block signature");
			}
			return block;
		} catch (NxtException.NotValidException | RuntimeException e) {
			Logger.logDebugMessage("Failed to parse block: " + blockData.toJSONString());
			throw e;
		}
	}

	private final int version;
	private final int timestamp;
	private final long previousBlockId;
	private volatile byte[] generatorPublicKey;

	private final byte[] previousBlockHash;
	private final long totalAmountNQT;
	private final long totalFeeNQT;

	private final int payloadLength;
	private final byte[] generationSignature;
	private final byte[] payloadHash;
	private volatile List<TransactionImpl> blockTransactions;
	private byte[] blockSignature;
	private BigInteger cumulativeDifficulty = BigInteger.ZERO;
	private long baseTarget = Constants.INITIAL_BASE_TARGET;
	private volatile long nextBlockId;
	private int height = -1;
	private volatile long id;

	private volatile String stringId = null;

	private volatile long generatorId;

	private volatile byte[] bytes = null;

	private BigInteger local_min_pow_target = null;

	private volatile boolean hasValidSignature = false;

	BlockImpl(final int version, final int timestamp, final long previousBlockId, final long totalAmountNQT,
			final long totalFeeNQT, final int payloadLength, final byte[] payloadHash, final byte[] generatorPublicKey,
			final byte[] generationSignature, final byte[] blockSignature, final byte[] previousBlockHash,
			final List<TransactionImpl> transactions, final BigInteger min_pow_target) {
		this.version = version;
		this.timestamp = timestamp;
		this.previousBlockId = previousBlockId;
		this.totalAmountNQT = totalAmountNQT;
		this.totalFeeNQT = totalFeeNQT;
		this.payloadLength = payloadLength;
		this.payloadHash = payloadHash;
		this.generatorPublicKey = generatorPublicKey;
		this.generationSignature = generationSignature;
		this.blockSignature = blockSignature;
		this.previousBlockHash = previousBlockHash;
		this.local_min_pow_target = min_pow_target;
		if (transactions != null) {
			this.blockTransactions = Collections.unmodifiableList(transactions);
		}
	}

	BlockImpl(final int version, final int timestamp, final long previousBlockId, final long totalAmountNQT,
			final long totalFeeNQT, final int payloadLength, final byte[] payloadHash, final byte[] generatorPublicKey,
			final byte[] generationSignature, final byte[] previousBlockHash, final List<TransactionImpl> transactions,
			final String secretPhrase, final BigInteger min_pow_target) {
		this(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
				generatorPublicKey, generationSignature, null, previousBlockHash, transactions, min_pow_target);

		if (secretPhrase != null) {
			this.blockSignature = Crypto.sign(this.bytes(), secretPhrase);
		}
		this.bytes = null;
	}

	BlockImpl(final int version, final int timestamp, final long previousBlockId, final long totalAmountNQT,
			final long totalFeeNQT, final int payloadLength, final byte[] payloadHash, final long generatorId,
			final byte[] generationSignature, final byte[] blockSignature, final byte[] previousBlockHash,
			final BigInteger cumulativeDifficulty, final long baseTarget, final long nextBlockId, final int height,
			final long id, final List<TransactionImpl> blockTransactions, final BigInteger min_pow_target) {
		this(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash, null,
				generationSignature, blockSignature, previousBlockHash, null, min_pow_target);
		this.cumulativeDifficulty = cumulativeDifficulty;
		this.baseTarget = baseTarget;
		this.nextBlockId = nextBlockId;
		this.height = height;
		this.id = id;
		this.generatorId = generatorId;
		this.blockTransactions = blockTransactions;

	}

	void apply() {
		final Account generatorAccount = Account.addOrGetAccount(this.getGeneratorId());
		generatorAccount.apply(this.getGeneratorPublicKey());
		long totalBackFees = 0;
		final long[] backFees = new long[3];
		for (final TransactionImpl transaction : this.getTransactions()) {
			final long[] fees = transaction.getBackFees();
			for (int i = 0; i < fees.length; i++) {
				backFees[i] += fees[i];
			}
		}
		for (int i = 0; i < backFees.length; i++) {
			if (backFees[i] == 0) {
				break;
			}
			totalBackFees += backFees[i];
			final Account previousGeneratorAccount = Account
					.getAccount(BlockDb.findBlockAtHeight(this.height - i - 1).getGeneratorId());
			Logger.logDebugMessage("Back fees %f NXT to forger at height %d",
					((double) backFees[i]) / Constants.ONE_NXT, this.height - i - 1);
			previousGeneratorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, this.getId(),
					backFees[i]);
			previousGeneratorAccount.addToForgedBalanceNQT(backFees[i]);
		}

		if (totalBackFees != 0) {
			Logger.logDebugMessage("Fee reduced by %f NXT at height %d", ((double) totalBackFees) / Constants.ONE_NXT,
					this.height);
		}
		generatorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, this.getId(),
				this.totalFeeNQT - totalBackFees);
		generatorAccount.addToForgedBalanceNQT(this.totalFeeNQT - totalBackFees);
	}

	byte[] bytes() {

		if (this.bytes == null) {
			final ByteBuffer buffer = ByteBuffer
					.allocate(4 + 4 + 8 + 4 + 8 + 8 + 4 + 32 + 32 + 32 + 32 + (this.blockSignature != null ? 64 : 0));
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer.putInt(this.version);
			buffer.putInt(this.timestamp);
			buffer.putLong(this.previousBlockId);
			buffer.putInt(this.getTransactions().size());
			buffer.putLong(this.totalAmountNQT);
			buffer.putLong(this.totalFeeNQT);
			buffer.putInt(this.payloadLength);
			buffer.put(this.payloadHash);
			buffer.put(this.getGeneratorPublicKey());
			buffer.put(this.generationSignature);
			buffer.put(this.previousBlockHash);
			if (this.blockSignature != null) {
				buffer.put(this.blockSignature);
			}
			this.bytes = buffer.array();
		}
		return this.bytes;
	}

	private void calculateBaseTarget(final BlockImpl previousBlock) {
		final long prevBaseTarget = previousBlock.baseTarget;

		if (((previousBlock.getHeight() % 2) == 0)
				&& (previousBlock.getHeight() > 2 /* fix for early forkers */)) {
			final BlockImpl block = BlockDb.findBlockAtHeight(previousBlock.getHeight() - 2);
			final int blocktimeAverage = (this.timestamp - block.timestamp) / 3;
			if (blocktimeAverage > 60) {
				this.baseTarget = (prevBaseTarget * Math.min(blocktimeAverage, Constants.MAX_BLOCKTIME_LIMIT)) / 60;
			} else {
				this.baseTarget = prevBaseTarget - ((prevBaseTarget * Constants.BASE_TARGET_GAMMA
						* (60 - Math.max(blocktimeAverage, Constants.MIN_BLOCKTIME_LIMIT))) / 6000);
			}
			if ((this.baseTarget < 0) || (this.baseTarget > Constants.MAX_BASE_TARGET_2)) {
				this.baseTarget = Constants.MAX_BASE_TARGET_2;
			}
			if (this.baseTarget < Constants.MIN_BASE_TARGET) {
				this.baseTarget = Constants.MIN_BASE_TARGET;
			}
		} else {
			this.baseTarget = prevBaseTarget;
		}
		this.cumulativeDifficulty = previousBlock.cumulativeDifficulty
				.add(Convert.two64.divide(BigInteger.valueOf(this.baseTarget)));
	}

	private boolean checkSignature() {

		boolean is_special_case = false;
		for (final Transaction t : this.blockTransactions) {
			if (t.getType() == Payment.REDEEM) {
				is_special_case = true; // TODO: check if correct
				break;
			}
		}

		if (is_special_case) {
			this.hasValidSignature = true;
		}

		if (!this.hasValidSignature) {
			final byte[] data = Arrays.copyOf(this.bytes(), this.bytes.length - 64);
			this.hasValidSignature = (this.blockSignature != null)
					&& Crypto.verify(this.blockSignature, data, this.getGeneratorPublicKey(), true);
		}
		return this.hasValidSignature;
	}

	public byte[] sign(String secretPhrase) throws Exception {
		if (this.blockSignature != null) {
			throw new Exception("Don't sign what is already signed!");
		}
		final byte[] data = this.bytes();
		return Crypto.sign(data, secretPhrase);
	}

	@Override
	public int countNumberPOW() {
		int cntr = 0;
		for (final TransactionImpl t : this.getTransactions()) {
			if (t.getAttachment().getTransactionType() == TransactionType.WorkControl.PROOF_OF_WORK) {
				cntr++;
			}
		}
		return cntr;
	}

	@Override
	public long countNumberPOWPerWorkId(final long work_id) {
		long cached = BlockImpl.powPerBlockAndWorkLRUCache.get(this.getId(), work_id);
		if (cached != -1) {
			return cached;
		}
		cached = 0;
		for (final TransactionImpl t : this.getTransactions()) {
			if (t.getAttachment().getTransactionType() == TransactionType.WorkControl.PROOF_OF_WORK) {
				final Attachment.PiggybackedProofOfWork patt = (Attachment.PiggybackedProofOfWork) t.getAttachment();
				if (patt.getWorkId() == work_id) {
					cached++;
				}
			}
		}
		BlockImpl.powPerBlockAndWorkLRUCache.set(this.getId(), work_id, cached);
		return cached;
	}

	@Override
	public boolean equals(final Object o) {
		return (o instanceof BlockImpl) && (this.getId() == ((BlockImpl) o).getId());
	}

	@Override
	public long getBaseTarget() {
		return this.baseTarget;
	}

	@Override
	public byte[] getBlockSignature() {
		return this.blockSignature;
	}

	@Override
	public byte[] getBytes() {
		return Arrays.copyOf(this.bytes(), this.bytes.length);
	}

	@Override
	public BigInteger getCumulativeDifficulty() {
		return this.cumulativeDifficulty;
	}

	@Override
	public byte[] getGenerationSignature() {
		return this.generationSignature;
	}

	@Override
	public long getGeneratorId() {
		if (this.generatorId == 0) {
			this.generatorId = Account.getId(this.getGeneratorPublicKey());
		}
		return this.generatorId;
	}

	@Override
	public byte[] getGeneratorPublicKey() {
		if (this.generatorPublicKey == null) {
			this.generatorPublicKey = Account.getPublicKey(this.generatorId);
		}
		return this.generatorPublicKey;
	}

	@Override
	public int getHeight() {
		if (this.height == -1) {
			throw new IllegalStateException("Block height not yet set");
		}
		return this.height;
	}

	@Override
	public long getId() {
		if (this.id == 0) {
			boolean is_special_case = false;
			for (final Transaction t : this.blockTransactions) {
				if (t.getType() == Payment.REDEEM) {
					is_special_case = true;
					break;
				}
			}

			if ((is_special_case == false) && (this.blockSignature == null)) {
				throw new IllegalStateException("Block is not signed yet");
			}
			final byte[] hash = Crypto.sha256().digest(this.bytes());
			final BigInteger bigInteger = new BigInteger(1,
					new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
			this.id = bigInteger.longValue();
			this.stringId = bigInteger.toString();
		}
		return this.id;
	}

	@Override
	public JSONObject getJSONObject() {
		final JSONObject json = new JSONObject();
		json.put("version", this.version);
		json.put("timestamp", this.timestamp);
		json.put("previousBlock", Long.toUnsignedString(this.previousBlockId));
		json.put("totalAmountNQT", this.totalAmountNQT);
		json.put("totalFeeNQT", this.totalFeeNQT);
		json.put("payloadLength", this.payloadLength);
		json.put("payloadHash", Convert.toHexString(this.payloadHash));
		json.put("generatorPublicKey", Convert.toHexString(this.getGeneratorPublicKey()));
		json.put("generationSignature", Convert.toHexString(this.generationSignature));
		json.put("previousBlockHash", Convert.toHexString(this.previousBlockHash));
		json.put("blockSignature", Convert.toHexString(this.blockSignature));
		final JSONArray transactionsData = new JSONArray();
		this.getTransactions().forEach(transaction -> transactionsData.add(transaction.getJSONObject()));
		json.put("transactions", transactionsData);
		return json;
	}

	@Override
	public BigInteger getMinPowTarget() {
		if (this.local_min_pow_target != null) {
			return this.local_min_pow_target;
		} else {
			this.local_min_pow_target = BlockImpl.calculateNextMinPowTarget(this.previousBlockId);
			return this.local_min_pow_target;
		}
	}

	@Override
	public long getNextBlockId() {
		return this.nextBlockId;
	}

	@Override
	public byte[] getPayloadHash() {
		return this.payloadHash;
	}

	@Override
	public int getPayloadLength() {
		return this.payloadLength;
	}

	@Override
	public BlockImpl getPreviousBlock() {
		return BlockchainImpl.getInstance().getBlock(this.getPreviousBlockId());
	}

	@Override
	public byte[] getPreviousBlockHash() {
		return this.previousBlockHash;
	}

	@Override
	public long getPreviousBlockId() {
		return this.previousBlockId;
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
	public long getTotalAmountNQT() {
		return this.totalAmountNQT;
	}

	@Override
	public long getTotalFeeNQT() {
		return this.totalFeeNQT;
	}

	@Override
	public List<TransactionImpl> getTransactions() {
		if (this.blockTransactions == null) {
			final List<TransactionImpl> transactions = Collections
					.unmodifiableList(TransactionDb.findBlockTransactions(this.getId()));
			for (final TransactionImpl transaction : transactions) {
				transaction.setBlock(this);
			}
			this.blockTransactions = transactions;
		}
		return this.blockTransactions;
	}

	@Override
	public int getVersion() {
		return this.version;
	}

	@Override
	public int hashCode() {
		return (int) (this.getId() ^ (this.getId() >>> 32));
	}

	void loadTransactions() {
		for (final TransactionImpl transaction : this.getTransactions()) {
			transaction.bytes();
			transaction.getAppendages();
		}
	}

	void setNextBlockId(final long nextBlockId) {
		this.nextBlockId = nextBlockId;
	}

	void setPrevious(final BlockImpl block) {
		if (block != null) {
			if (block.getId() != this.getPreviousBlockId()) {
				// shouldn't happen as previous id is already verified, but just
				// in case
				throw new IllegalStateException("Previous block id doesn't match");
			}
			this.height = block.getHeight() + 1;
			this.calculateBaseTarget(block);
		} else {
			this.height = 0;
		}
		short index = 0;
		for (final TransactionImpl transaction : this.getTransactions()) {
			transaction.setBlock(this);
			transaction.setIndex(index++);
		}
	}

	boolean verifyBlockSignature() {
		// Fail is "REMEED_ACCOUNT" created block, he should be not allowed to
		// do anything
		if (this.getGeneratorId() == Genesis.REDEEM_ID) {
			return false;
		}

		return this.checkSignature() && Account.setOrVerify(this.getGeneratorId(), this.getGeneratorPublicKey());
	}

	boolean verifyGenerationSignature() throws BlockchainProcessor.BlockOutOfOrderException {

		try {

			final BlockImpl previousBlock = BlockchainImpl.getInstance().getBlock(this.getPreviousBlockId());
			if (previousBlock == null) {
				throw new BlockchainProcessor.BlockOutOfOrderException(
						"Can't verify signature because previous block is missing", this);
			}

			// Now comes a dirty hack, if the block contains a redeem
			// transaction, and the blockheight is low enough (1440) then anyone
			// can mine the block!
			// This helps bootstrapping the blockchain, as at the beginning
			// nobody has coins and so nobody could forge
			// PLEASE DISCUSS THIS IN THE COMMUNITY
			for (final Transaction t : this.blockTransactions) {
				if (t.getType() == Payment.REDEEM) {
					return true;
				}
			}

			final Account account = Account.getAccount(this.getGeneratorId());
			final long effectiveBalance = account == null ? 0 : account.getEffectiveBalanceNXT();
			if (effectiveBalance <= 0) {
				return false;
			}

			final MessageDigest digest = Crypto.sha256();
			byte[] generationSignatureHash;

			digest.update(previousBlock.generationSignature);
			generationSignatureHash = digest.digest(this.getGeneratorPublicKey());
			if (!Arrays.equals(this.generationSignature, generationSignatureHash)) {
				return false;
			}

			final BigInteger hit = new BigInteger(1,
					new byte[] { generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5],
							generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2],
							generationSignatureHash[1], generationSignatureHash[0] });

			return Generator.verifyHit(hit, BigInteger.valueOf(effectiveBalance), previousBlock, this.timestamp);

		} catch (final RuntimeException e) {

			Logger.logMessage("Error verifying block generation signature", e);
			return false;

		}

	}

	@Override
	public int getTimestampPrevious() {
		Block b = this.getPreviousBlock();
		if (b==null)
			return this.timestamp;
		else
			return b.getTimestamp();
	}

	public boolean ensureNoRealOrSNCleanDuplicates() {
		boolean ret = true;
		Set<Long> s = new HashSet<>();
		for (final TransactionImpl t : this.getTransactions()) {
			if(s.contains(t.getId())){
				ret = false;
				break;
			}
			if(t.getType().mustHaveSupernodeSignature() && s.contains(t.getSNCleanedId())){
				ret = false;
				break;
			}
			s.add(t.getId());
			if(t.getType().mustHaveSupernodeSignature())
				s.add(t.getSNCleanedId());
		}
		return ret;

	}
}

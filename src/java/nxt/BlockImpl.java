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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import nxt.AccountLedger.LedgerEvent;
import nxt.TransactionType.Payment;
import nxt.crypto.Crypto;
import nxt.db.DbIterator;
import nxt.util.Convert;
import nxt.util.Logger;

public final class BlockImpl implements Block {

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
	private static LRUCache powDifficultyLRUCache = new LRUCache(50);
	private static DoubleLongLRUCache powPerBlockAndWorkLRUCache = new DoubleLongLRUCache(100);

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

	BlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT,
			int payloadLength, byte[] payloadHash, byte[] generatorPublicKey, byte[] generationSignature,
			byte[] previousBlockHash, List<TransactionImpl> transactions, String secretPhrase,
			BigInteger min_pow_target) {
		this(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
				generatorPublicKey, generationSignature, null, previousBlockHash, transactions, min_pow_target);
		if (secretPhrase != null)
			blockSignature = Crypto.sign(bytes(), secretPhrase);
		bytes = null;

	}

	BlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT,
			int payloadLength, byte[] payloadHash, byte[] generatorPublicKey, byte[] generationSignature,
			byte[] blockSignature, byte[] previousBlockHash, List<TransactionImpl> transactions,
			BigInteger min_pow_target) {
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

	BlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT,
			int payloadLength, byte[] payloadHash, long generatorId, byte[] generationSignature, byte[] blockSignature,
			byte[] previousBlockHash, BigInteger cumulativeDifficulty, long baseTarget, long nextBlockId, int height,
			long id, List<TransactionImpl> blockTransactions, BigInteger min_pow_target) {
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

	@Override
	public int getVersion() {
		return version;
	}

	@Override
	public int getTimestamp() {
		return timestamp;
	}

	@Override
	public long getPreviousBlockId() {
		return previousBlockId;
	}

	@Override
	public byte[] getGeneratorPublicKey() {
		if (generatorPublicKey == null) {
			generatorPublicKey = Account.getPublicKey(generatorId);
		}
		return generatorPublicKey;
	}

	@Override
	public byte[] getPreviousBlockHash() {
		return previousBlockHash;
	}

	@Override
	public long getTotalAmountNQT() {
		return totalAmountNQT;
	}

	@Override
	public long getTotalFeeNQT() {
		return totalFeeNQT;
	}

	@Override
	public int getPayloadLength() {
		return payloadLength;
	}

	@Override
	public byte[] getPayloadHash() {
		return payloadHash;
	}

	@Override
	public byte[] getGenerationSignature() {
		return generationSignature;
	}

	@Override
	public byte[] getBlockSignature() {
		return blockSignature;
	}

	@Override
	public List<TransactionImpl> getTransactions() {
		if (this.blockTransactions == null) {
			List<TransactionImpl> transactions = Collections
					.unmodifiableList(TransactionDb.findBlockTransactions(getId()));
			for (TransactionImpl transaction : transactions) {
				transaction.setBlock(this);
			}
			this.blockTransactions = transactions;
		}
		return this.blockTransactions;
	}

	@Override
	public long getBaseTarget() {
		return baseTarget;
	}

	@Override
	public BigInteger getCumulativeDifficulty() {
		return cumulativeDifficulty;
	}

	@Override
	public long getNextBlockId() {
		return nextBlockId;
	}

	void setNextBlockId(long nextBlockId) {
		this.nextBlockId = nextBlockId;
	}

	@Override
	public int getHeight() {
		if (height == -1) {
			throw new IllegalStateException("Block height not yet set");
		}
		return height;
	}

	@Override
	public long getId() {
		if (id == 0) {
			boolean is_special_case = false;
			if (this.previousBlockId == 0 || this.getPreviousBlock().getHeight() <= 5000) {
				for (Transaction t : this.blockTransactions) {
					if (t.getType() == Payment.REDEEM) {
						is_special_case = true;
						break;
					}
				}
			}
			if (is_special_case == false && blockSignature == null) {
				throw new IllegalStateException("Block is not signed yet");
			}
			byte[] hash = Crypto.sha256().digest(bytes());
			BigInteger bigInteger = new BigInteger(1,
					new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
			id = bigInteger.longValue();
			stringId = bigInteger.toString();
		}
		return id;
	}

	@Override
	public String getStringId() {
		if (stringId == null) {
			getId();
			if (stringId == null) {
				stringId = Long.toUnsignedString(id);
			}
		}
		return stringId;
	}

	@Override
	public long getGeneratorId() {
		if (generatorId == 0) {
			generatorId = Account.getId(getGeneratorPublicKey());
		}
		return generatorId;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof BlockImpl && this.getId() == ((BlockImpl) o).getId();
	}

	@Override
	public int hashCode() {
		return (int) (getId() ^ (getId() >>> 32));
	}

	@Override
	public JSONObject getJSONObject() {
		JSONObject json = new JSONObject();
		json.put("version", version);
		json.put("timestamp", timestamp);
		json.put("previousBlock", Long.toUnsignedString(previousBlockId));
		json.put("totalAmountNQT", totalAmountNQT);
		json.put("totalFeeNQT", totalFeeNQT);
		json.put("payloadLength", payloadLength);
		json.put("payloadHash", Convert.toHexString(payloadHash));
		json.put("generatorPublicKey", Convert.toHexString(getGeneratorPublicKey()));
		json.put("generationSignature", Convert.toHexString(generationSignature));
		json.put("previousBlockHash", Convert.toHexString(previousBlockHash));
		json.put("blockSignature", Convert.toHexString(blockSignature));
		JSONArray transactionsData = new JSONArray();
		getTransactions().forEach(transaction -> transactionsData.add(transaction.getJSONObject()));
		json.put("transactions", transactionsData);
		return json;
	}

	static BlockImpl parseBlock(JSONObject blockData) throws NxtException.NotValidException {
		try {
			int version = ((Long) blockData.get("version")).intValue();
			int timestamp = ((Long) blockData.get("timestamp")).intValue();
			long previousBlock = Convert.parseUnsignedLong((String) blockData.get("previousBlock"));
			long totalAmountNQT = Convert.parseLong(blockData.get("totalAmountNQT"));
			long totalFeeNQT = Convert.parseLong(blockData.get("totalFeeNQT"));
			int payloadLength = ((Long) blockData.get("payloadLength")).intValue();
			byte[] payloadHash = Convert.parseHexString((String) blockData.get("payloadHash"));
			byte[] generatorPublicKey = Convert.parseHexString((String) blockData.get("generatorPublicKey"));
			byte[] generationSignature = Convert.parseHexString((String) blockData.get("generationSignature"));
			byte[] blockSignature = Convert.parseHexString((String) blockData.get("blockSignature"));
			byte[] previousBlockHash = Convert.parseHexString((String) blockData.get("previousBlockHash"));
			List<TransactionImpl> blockTransactions = new ArrayList<>();
			for (Object transactionData : (JSONArray) blockData.get("transactions")) {
				blockTransactions.add(TransactionImpl.parseTransaction((JSONObject) transactionData));
			}
			BlockImpl block = new BlockImpl(version, timestamp, previousBlock, totalAmountNQT, totalFeeNQT,
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

	@Override
	public byte[] getBytes() {
		return Arrays.copyOf(bytes(), bytes.length);
	}

	byte[] bytes() {

		if (bytes == null) {
			ByteBuffer buffer = ByteBuffer
					.allocate(4 + 4 + 8 + 4 + 8 + 8 + 4 + 32 + 32 + 32 + 32 + (blockSignature != null ? 64 : 0));
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer.putInt(version);
			buffer.putInt(timestamp);
			buffer.putLong(previousBlockId);
			buffer.putInt(getTransactions().size());
			buffer.putLong(totalAmountNQT);
			buffer.putLong(totalFeeNQT);
			buffer.putInt(payloadLength);
			buffer.put(payloadHash);
			buffer.put(getGeneratorPublicKey());
			buffer.put(generationSignature);
			buffer.put(previousBlockHash);
			if (blockSignature != null) {
				buffer.put(blockSignature);
			}
			bytes = buffer.array();
		}
		return bytes;
	}

	boolean verifyBlockSignature() {
		// Fail is "REMEED_ACCOUNT" created block, he should be not allowed to
		// do anything
		if (getGeneratorId() == Genesis.REDEEM_ID) {
			return false;
		}

		return checkSignature() && Account.setOrVerify(getGeneratorId(), getGeneratorPublicKey());
	}

	private volatile boolean hasValidSignature = false;

	private boolean checkSignature() {

		boolean is_special_case = false;
		if (this.previousBlockId == 0 || this.getPreviousBlock().getHeight() <= 5000) {
			for (Transaction t : this.blockTransactions) {
				if (t.getType() == Payment.REDEEM) {
					is_special_case = true;
					break;
				}
			}
		}

		if (is_special_case) {
			hasValidSignature = true;
		}

		if (!hasValidSignature) {
			byte[] data = Arrays.copyOf(bytes(), bytes.length - 64);
			hasValidSignature = blockSignature != null
					&& Crypto.verify(blockSignature, data, getGeneratorPublicKey(), true);
		}
		return hasValidSignature;
	}

	boolean verifyGenerationSignature() throws BlockchainProcessor.BlockOutOfOrderException {

		try {

			BlockImpl previousBlock = BlockchainImpl.getInstance().getBlock(getPreviousBlockId());
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
			if (this.previousBlockId == 0 || this.getPreviousBlock().getHeight() <= 5000) {
				for (Transaction t : this.blockTransactions) {
					if (t.getType() == Payment.REDEEM) {
						return true;
					}
				}
			}

			Account account = Account.getAccount(getGeneratorId());
			long effectiveBalance = account == null ? 0 : account.getEffectiveBalanceNXT();
			if (effectiveBalance <= 0) {
				return false;
			}

			MessageDigest digest = Crypto.sha256();
			byte[] generationSignatureHash;

			digest.update(previousBlock.generationSignature);
			generationSignatureHash = digest.digest(getGeneratorPublicKey());
			if (!Arrays.equals(generationSignature, generationSignatureHash)) {
				return false;
			}

			BigInteger hit = new BigInteger(1,
					new byte[] { generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5],
							generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2],
							generationSignatureHash[1], generationSignatureHash[0] });

			return Generator.verifyHit(hit, BigInteger.valueOf(effectiveBalance), previousBlock, timestamp);

		} catch (RuntimeException e) {

			Logger.logMessage("Error verifying block generation signature", e);
			return false;

		}

	}

	private static final long[] badBlocks = new long[] {};
	static {
		Arrays.sort(badBlocks);
	}

	void apply() {
		Account generatorAccount = Account.addOrGetAccount(getGeneratorId());
		generatorAccount.apply(getGeneratorPublicKey());
		long totalBackFees = 0;
		long[] backFees = new long[3];
		for (TransactionImpl transaction : getTransactions()) {
			long[] fees = transaction.getBackFees();
			for (int i = 0; i < fees.length; i++) {
				backFees[i] += fees[i];
			}
		}
		for (int i = 0; i < backFees.length; i++) {
			if (backFees[i] == 0) {
				break;
			}
			totalBackFees += backFees[i];
			Account previousGeneratorAccount = Account
					.getAccount(BlockDb.findBlockAtHeight(this.height - i - 1).getGeneratorId());
			Logger.logDebugMessage("Back fees %f NXT to forger at height %d",
					((double) backFees[i]) / Constants.ONE_NXT, this.height - i - 1);
			previousGeneratorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getId(),
					backFees[i]);
			previousGeneratorAccount.addToForgedBalanceNQT(backFees[i]);
		}

		if (totalBackFees != 0) {
			Logger.logDebugMessage("Fee reduced by %f NXT at height %d", ((double) totalBackFees) / Constants.ONE_NXT,
					this.height);
		}
		generatorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getId(),
				totalFeeNQT - totalBackFees);
		generatorAccount.addToForgedBalanceNQT(totalFeeNQT - totalBackFees);
	}

	void setPrevious(BlockImpl block) {
		if (block != null) {
			if (block.getId() != getPreviousBlockId()) {
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
		for (TransactionImpl transaction : getTransactions()) {
			transaction.setBlock(this);
			transaction.setIndex(index++);
		}
	}

	void loadTransactions() {
		for (TransactionImpl transaction : getTransactions()) {
			transaction.bytes();
			transaction.getAppendages();
		}
	}

	private void calculateBaseTarget(BlockImpl previousBlock) {
		long prevBaseTarget = previousBlock.baseTarget;

		if (previousBlock.getHeight() % 2 == 0
				&& previousBlock.getHeight() > 2 /* fix for early forkers */) {
			BlockImpl block = BlockDb.findBlockAtHeight(previousBlock.getHeight() - 2);
			int blocktimeAverage = (this.timestamp - block.timestamp) / 3;
			if (blocktimeAverage > 60) {
				baseTarget = (prevBaseTarget * Math.min(blocktimeAverage, Constants.MAX_BLOCKTIME_LIMIT)) / 60;
			} else {
				baseTarget = prevBaseTarget - prevBaseTarget * Constants.BASE_TARGET_GAMMA
						* (60 - Math.max(blocktimeAverage, Constants.MIN_BLOCKTIME_LIMIT)) / 6000;
			}
			if (baseTarget < 0 || baseTarget > Constants.MAX_BASE_TARGET_2) {
				baseTarget = Constants.MAX_BASE_TARGET_2;
			}
			if (baseTarget < Constants.MIN_BASE_TARGET) {
				baseTarget = Constants.MIN_BASE_TARGET;
			}
		} else {
			baseTarget = prevBaseTarget;
		}
		cumulativeDifficulty = previousBlock.cumulativeDifficulty
				.add(Convert.two64.divide(BigInteger.valueOf(baseTarget)));
	}

	public BlockImpl getPreviousBlock() {
		return BlockchainImpl.getInstance().getBlock(this.getPreviousBlockId());
	}

	public static BigInteger calculateNextMinPowTarget(long lastBlockId) {

		BigInteger cached = powDifficultyLRUCache.get(lastBlockId);
		if (cached != null)
			return cached;

		BigInteger converted_new_pow = BigInteger.valueOf(0);

		DbIterator<Work> it = Work.getLastTenClosed();
		long counter = 0;

		if (it.hasNext() == false) {
			converted_new_pow = Constants.least_possible_target;
		} else {
			while (it.hasNext()) {
				Work w = it.next();
				BigInteger candidate = w.getWork_min_pow_target_bigint();
				if (candidate.compareTo(converted_new_pow) == 1) {
					converted_new_pow = candidate;
				}
			}
		}

		powDifficultyLRUCache.set(lastBlockId, converted_new_pow);
		return converted_new_pow;
	}

	public int countNumberPOW() {
		int cntr = 0;
		for (TransactionImpl t : getTransactions()) {
			if (t.getAttachment().getTransactionType() == TransactionType.WorkControl.PROOF_OF_WORK) {
				cntr++;
			}
		}
		return cntr;
	}

	public long countNumberPOWPerWorkId(long work_id) {
		long cached = powPerBlockAndWorkLRUCache.get(this.getId(), work_id);
		if (cached != -1)
			return cached;
		cached = 0;
		for (TransactionImpl t : getTransactions()) {
			if (t.getAttachment().getTransactionType() == TransactionType.WorkControl.PROOF_OF_WORK) {
				Attachment.PiggybackedProofOfWork patt = (Attachment.PiggybackedProofOfWork) t.getAttachment();
				if (patt.getWorkId() == work_id)
					cached++;
			}
		}
		powPerBlockAndWorkLRUCache.set(this.getId(), work_id, cached);
		return cached;
	}

	@Override
	public BigInteger getMinPowTarget() {
		if (local_min_pow_target != null)
			return this.local_min_pow_target;
		else {
			this.local_min_pow_target = calculateNextMinPowTarget(this.previousBlockId);
			return this.local_min_pow_target;
		}
	}

}

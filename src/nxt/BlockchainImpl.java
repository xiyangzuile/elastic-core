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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import nxt.Transaction;

import nxt.db.DbIterator;
import nxt.db.DbUtils;
import nxt.util.Convert;
import nxt.util.Filter;
import nxt.util.ReadWriteUpdateLock;

final class BlockchainImpl implements Blockchain {

	private static final BlockchainImpl instance = new BlockchainImpl();

	static BlockchainImpl getInstance() {
		return BlockchainImpl.instance;
	}

	private final ReadWriteUpdateLock lock = new ReadWriteUpdateLock();

	private final AtomicReference<BlockImpl> lastBlock = new AtomicReference<>();

	private BlockchainImpl() {
	}

	@Override
	public DbIterator<BlockImpl> getAllBlocks() {
		Connection con = null;
		try {
			con = Db.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block ORDER BY db_id ASC");
			return this.getBlocks(con, pstmt);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public DbIterator<TransactionImpl> getAllTransactions() {
		Connection con = null;
		try {
			con = Db.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement("SELECT * FROM transaction ORDER BY db_id ASC");
			return this.getTransactions(con, pstmt);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public BlockImpl getBlock(final long blockId) {
		final BlockImpl block = this.lastBlock.get();
		if ((block != null) && (block.getId() == blockId)) {
			return block;
		}
		return BlockDb.findBlock(blockId);
	}

	@Override
	public BlockImpl getBlockAtHeight(final int height) {
		final BlockImpl block = this.lastBlock.get();
		if (height > block.getHeight()) {
			throw new IllegalArgumentException(
					"Invalid height " + height + ", current blockchain is at " + block.getHeight());
		}
		if (height == block.getHeight()) {
			return block;
		}
		return BlockDb.findBlockAtHeight(height);
	}

	@Override
	public int getBlockCount(final long accountId) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM block WHERE generator_id = ?")) {
			pstmt.setLong(1, accountId);
			try (ResultSet rs = pstmt.executeQuery()) {
				rs.next();
				return rs.getInt(1);
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public Integer getBlockHeight(final long lastBlockId) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT height FROM block WHERE ID = ?")) {
			pstmt.setLong(1, lastBlockId);
			try (DbIterator<Integer> it = new DbIterator<>(con, pstmt, (con1, rs) -> {

				int height = rs.getInt("height");
				return height;
			})) {
				if (it.hasNext()) {
					return it.next();
				} else {
					return 0;
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public long getBlockIdAtHeight(final int height) {
		final Block block = this.lastBlock.get();
		if (height > block.getHeight()) {
			throw new IllegalArgumentException(
					"Invalid height " + height + ", current blockchain is at " + block.getHeight());
		}
		if (height == block.getHeight()) {
			return block.getId();
		}
		return BlockDb.findBlockIdAtHeight(height);
	}

	@Override
	public List<Long> getBlockIdsAfter(final long blockId, final int limit) {
		// Check the block cache
		final List<Long> result = new ArrayList<>(BlockDb.BLOCK_CACHE_SIZE);
		synchronized (BlockDb.blockCache) {
			final BlockImpl block = BlockDb.blockCache.get(blockId);
			if (block != null) {
				final Collection<BlockImpl> cacheMap = BlockDb.heightMap.tailMap(block.getHeight() + 1).values();
				for (final BlockImpl cacheBlock : cacheMap) {
					if (result.size() >= limit) {
						break;
					}
					result.add(cacheBlock.getId());
				}
				return result;
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"SELECT id FROM block " + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), "
								+ Long.MAX_VALUE + ") " + "ORDER BY db_id ASC LIMIT ?")) {
			pstmt.setLong(1, blockId);
			pstmt.setInt(2, limit);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					result.add(rs.getLong("id"));
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return result;
	}

	@Override
	public DbIterator<BlockImpl> getBlocks(final Connection con, final PreparedStatement pstmt) {
		return new DbIterator<>(con, pstmt, BlockDb::loadBlock);
	}

	@Override
	public List<BlockImpl> getBlocks(final int from, final int to) {
		final List<BlockImpl> result = new ArrayList<>();
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"SELECT * FROM block WHERE height <= ? AND height >= ? ORDER BY height DESC");) {
			final int blockchainHeight = this.getHeight();
			pstmt.setInt(1, blockchainHeight - from);
			pstmt.setInt(2, blockchainHeight - to);
			final DbIterator<BlockImpl> idb = this.getBlocks(con, pstmt);
			if (idb != null) {
				while (idb.hasNext()) {
					result.add(idb.next());
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return result;
	}

	@Override
	public List<BlockImpl> getBlocks(final long accountId, final int timestamp) {
		return this.getBlocks(accountId, timestamp, 0, -1);
	}

	@Override
	public List<BlockImpl> getBlocks(final long accountId, final int timestamp, final int from, final int to) {
		final List<BlockImpl> result = new ArrayList<>();
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"SELECT * FROM block WHERE generator_id = ? " + (timestamp > 0 ? " AND timestamp >= ? " : " ")
								+ "ORDER BY height DESC" + DbUtils.limitsClause(from, to));) {
			int i = 0;
			pstmt.setLong(++i, accountId);
			if (timestamp > 0) {
				pstmt.setInt(++i, timestamp);
			}
			DbUtils.setLimits(++i, pstmt, from, to);
			final DbIterator<BlockImpl> idb = this.getBlocks(con, pstmt);
			if (idb != null) {
				while (idb.hasNext()) {
					result.add(idb.next());
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

		return result;
	}

	@Override
	public List<BlockImpl> getBlocksAfter(final long blockId, final int limit) {
		if (limit <= 0) {
			return Collections.emptyList();
		}
		// Check the block cache
		final List<BlockImpl> result = new ArrayList<>(BlockDb.BLOCK_CACHE_SIZE);
		synchronized (BlockDb.blockCache) {
			final BlockImpl block = BlockDb.blockCache.get(blockId);
			if (block != null) {
				final Collection<BlockImpl> cacheMap = BlockDb.heightMap.tailMap(block.getHeight() + 1).values();
				for (final BlockImpl cacheBlock : cacheMap) {
					if (result.size() >= limit) {
						break;
					}
					result.add(cacheBlock);
				}
				return result;
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"SELECT * FROM block " + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), "
								+ Long.MAX_VALUE + ") " + "ORDER BY db_id ASC LIMIT ?")) {
			pstmt.setLong(1, blockId);
			pstmt.setInt(2, limit);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					result.add(BlockDb.loadBlock(con, rs, true));
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return result;
	}

	@Override
	public List<BlockImpl> getBlocksAfter(final long blockId, final List<Long> blockList) {
		if (blockList.isEmpty()) {
			return Collections.emptyList();
		}
		// Check the block cache
		final List<BlockImpl> result = new ArrayList<>(BlockDb.BLOCK_CACHE_SIZE);
		synchronized (BlockDb.blockCache) {
			final BlockImpl block = BlockDb.blockCache.get(blockId);
			if (block != null) {
				final Collection<BlockImpl> cacheMap = BlockDb.heightMap.tailMap(block.getHeight() + 1).values();
				int index = 0;
				for (final BlockImpl cacheBlock : cacheMap) {
					if ((result.size() >= blockList.size()) || (cacheBlock.getId() != blockList.get(index++))) {
						break;
					}
					result.add(cacheBlock);
				}
				return result;
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"SELECT * FROM block " + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), "
								+ Long.MAX_VALUE + ") " + "ORDER BY db_id ASC LIMIT ?")) {
			pstmt.setLong(1, blockId);
			pstmt.setInt(2, blockList.size());
			try (ResultSet rs = pstmt.executeQuery()) {
				int index = 0;
				while (rs.next()) {
					final BlockImpl block = BlockDb.loadBlock(con, rs, true);
					if (block.getId() != blockList.get(index++)) {
						break;
					}
					result.add(block);
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return result;
	}

	@Override
	public BlockImpl getECBlock(final int timestamp) {
		final Block block = this.getLastBlock(timestamp);
		if (block == null) {
			return this.getBlockAtHeight(0);
		}
		return BlockDb.findBlockAtHeight(Math.max(block.getHeight() - 720, 0));
	}

	@Override
	public List<TransactionImpl> getExpectedTransactions(final Filter<Transaction> filter) {
		final Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
		final BlockchainProcessorImpl blockchainProcessor = BlockchainProcessorImpl.getInstance();
		final List<TransactionImpl> result = new ArrayList<>();
		this.readLock();
		try {

			blockchainProcessor.selectUnconfirmedTransactions(duplicates, this.getLastBlock(), -1)
					.forEach(unconfirmedTransaction -> {
						final TransactionImpl transaction = unconfirmedTransaction.getTransaction();
						if (filter.ok(transaction)) {
							result.add(transaction);
						}
					});
		} finally {
			this.readUnlock();
		}
		return result;
	}

	@Override
	public int getHeight() {
		final BlockImpl last = this.lastBlock.get();
		return last == null ? 0 : last.getHeight();
	}

	@Override
	public BlockImpl getLastBlock() {
		return this.lastBlock.get();
	}

	@Override
	public BlockImpl getLastBlock(final int timestamp) {
		final BlockImpl block = this.lastBlock.get();
		if (timestamp >= block.getTimestamp()) {
			return block;
		}
		return BlockDb.findLastBlock(timestamp);
	}

	@Override
	public long getLastBlockId() {
		final BlockImpl last = this.lastBlock.get();
		return last == null ? 0 : last.getId();
	}

	@Override
	public int getLastBlockTimestamp() {
		final BlockImpl last = this.lastBlock.get();
		return last == null ? 0 : last.getTimestamp();
	}

	@Override
	public List<Transaction> getReferencingTransactions(final long transactionId, final int from,
			final int to) {
		List<Transaction> ret = new ArrayList<>();
		try (
			Connection con = Db.db.getConnection();
			final PreparedStatement pstmt = con
					.prepareStatement("SELECT transaction.* FROM transaction, referenced_transaction "
							+ "WHERE referenced_transaction.referenced_transaction_id = ? "
							+ "AND referenced_transaction.transaction_id = transaction.id "
							+ "ORDER BY transaction.block_timestamp DESC, transaction.transaction_index DESC "
							+ DbUtils.limitsClause(from, to))){
			int i = 0;
			pstmt.setLong(++i, transactionId);
			DbUtils.setLimits(++i, pstmt, from, to);
			try(DbIterator<TransactionImpl> dbit = this.getTransactions(con, pstmt)){
				while(dbit.hasNext()){
					ret.add(dbit.next());
				}
			}
			
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return ret;
	}

	@Override
	public TransactionImpl getTransaction(final long transactionId) {
		return TransactionDb.findTransaction(transactionId);
	}

	@Override
	public TransactionImpl getTransactionByFullHash(final String fullHash) {
		return TransactionDb.findTransactionByFullHash(Convert.parseHexString(fullHash));
	}

	@Override
	public int getTransactionCount() {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM transaction");
				ResultSet rs = pstmt.executeQuery()) {
			rs.next();
			return rs.getInt(1);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public DbIterator<TransactionImpl> getTransactions(final Connection con, final PreparedStatement pstmt) {
		return new DbIterator<>(con, pstmt, TransactionDb::loadTransaction);
	}

	@Override
	public List<Transaction> getTransactions(final long accountId, final byte type, final byte subtype,
			final int blockTimestamp, final boolean includeExpiredPrunable) {
		return this.getTransactions(accountId, 0, type, subtype, blockTimestamp, false, false, false, 0, -1,
				includeExpiredPrunable, false);
	}

	@Override
	public List<Transaction> getTransactions(final long accountId, final int numberOfConfirmations, final byte type,
			final byte subtype, final int blockTimestamp, final boolean withMessage, final boolean phasedOnly,
			final boolean nonPhasedOnly, final int from, final int to, final boolean includeExpiredPrunable,
			final boolean executedOnly) {

		List<Transaction> ret = new ArrayList<>();

		if (phasedOnly && nonPhasedOnly) {
			throw new IllegalArgumentException("At least one of phasedOnly or nonPhasedOnly must be false");
		}
		final int height = numberOfConfirmations > 0 ? this.getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
		if (height < 0) {
			throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
					+ " exceeds current blockchain height " + this.getHeight());
		}
		try {
			final StringBuilder buf = new StringBuilder();
			buf.append("SELECT transaction.* FROM transaction ");
			if (executedOnly && !nonPhasedOnly) {
				buf.append(" LEFT JOIN phasing_poll_result ON transaction.id = phasing_poll_result.id ");
			}
			buf.append("WHERE recipient_id = ? AND sender_id <> ? ");
			if (blockTimestamp > 0) {
				buf.append("AND block_timestamp >= ? ");
			}
			if (type >= 0) {
				buf.append("AND type = ? ");
				if (subtype >= 0) {
					buf.append("AND subtype = ? ");
				}
			}
			if (height < Integer.MAX_VALUE) {
				buf.append("AND transaction.height <= ? ");
			}
			if (withMessage) {
				buf.append("AND (has_message = TRUE OR has_encrypted_message = TRUE ");
				buf.append(
						"OR ((has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE) AND timestamp > ?)) ");
			}
			if (phasedOnly) {
				buf.append("AND phased = TRUE ");
			} else if (nonPhasedOnly) {
				buf.append("AND phased = FALSE ");
			}
			if (executedOnly && !nonPhasedOnly) {
				buf.append("AND (phased = FALSE OR approved = TRUE) ");
			}
			buf.append("UNION ALL SELECT transaction.* FROM transaction ");
			if (executedOnly && !nonPhasedOnly) {
				buf.append(" LEFT JOIN phasing_poll_result ON transaction.id = phasing_poll_result.id ");
			}
			buf.append("WHERE sender_id = ? ");
			if (blockTimestamp > 0) {
				buf.append("AND block_timestamp >= ? ");
			}
			if (type >= 0) {
				buf.append("AND type = ? ");
				if (subtype >= 0) {
					buf.append("AND subtype = ? ");
				}
			}
			if (height < Integer.MAX_VALUE) {
				buf.append("AND transaction.height <= ? ");
			}
			if (withMessage) {
				buf.append(
						"AND (has_message = TRUE OR has_encrypted_message = TRUE OR has_encrypttoself_message = TRUE ");
				buf.append(
						"OR ((has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE) AND timestamp > ?)) ");
			}
			if (phasedOnly) {
				buf.append("AND phased = TRUE ");
			} else if (nonPhasedOnly) {
				buf.append("AND phased = FALSE ");
			}
			if (executedOnly && !nonPhasedOnly) {
				buf.append("AND (phased = FALSE OR approved = TRUE) ");
			}

			buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
			buf.append(DbUtils.limitsClause(from, to));

			try (Connection con = Db.db.getConnection();
					PreparedStatement pstmt = con.prepareStatement(buf.toString());) {
				int i = 0;
				pstmt.setLong(++i, accountId);
				pstmt.setLong(++i, accountId);
				if (blockTimestamp > 0) {
					pstmt.setInt(++i, blockTimestamp);
				}
				if (type >= 0) {
					pstmt.setByte(++i, type);
					if (subtype >= 0) {
						pstmt.setByte(++i, subtype);
					}
				}
				if (height < Integer.MAX_VALUE) {
					pstmt.setInt(++i, height);
				}
				final int prunableExpiration = Math.max(0,
						Constants.INCLUDE_EXPIRED_PRUNABLE && includeExpiredPrunable
								? Nxt.getEpochTime() - Constants.MAX_PRUNABLE_LIFETIME
								: Nxt.getEpochTime() - Constants.MIN_PRUNABLE_LIFETIME);
				if (withMessage) {
					pstmt.setInt(++i, prunableExpiration);
				}
				pstmt.setLong(++i, accountId);
				if (blockTimestamp > 0) {
					pstmt.setInt(++i, blockTimestamp);
				}
				if (type >= 0) {
					pstmt.setByte(++i, type);
					if (subtype >= 0) {
						pstmt.setByte(++i, subtype);
					}
				}
				if (height < Integer.MAX_VALUE) {
					pstmt.setInt(++i, height);
				}
				if (withMessage) {
					pstmt.setInt(++i, prunableExpiration);
				}
				DbUtils.setLimits(++i, pstmt, from, to);
				try(DbIterator<TransactionImpl> it = this.getTransactions(con, pstmt);){
					while(it.hasNext())
						ret.add(it.next());
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

		return ret;
	}

	@Override
	public boolean hasBlock(final long blockId) {
		return (this.lastBlock.get().getId() == blockId) || BlockDb.hasBlock(blockId);
	}

	@Override
	public boolean hasTransaction(final long transactionId) {
		return TransactionDb.hasTransaction(transactionId);
	}

	@Override
	public boolean hasTransactionByFullHash(final String fullHash) {
		return TransactionDb.hasTransactionByFullHash(Convert.parseHexString(fullHash));
	}

	@Override
	public void readLock() {
		this.lock.readLock().lock();
	}

	@Override
	public void readUnlock() {
		this.lock.readLock().unlock();
	}

	void setLastBlock(final BlockImpl block) {
		this.lastBlock.set(block);
	}

	@Override
	public void updateLock() {
		this.lock.updateLock().lock();
	}

	@Override
	public void updateUnlock() {
		this.lock.updateLock().unlock();
	}

	void writeLock() {
		this.lock.writeLock().lock();
	}

	void writeUnlock() {
		this.lock.writeLock().unlock();
	}

}

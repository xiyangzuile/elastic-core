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

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import nxt.db.DbUtils;
import nxt.util.Logger;

final class BlockDb {

	/** Block cache */
	static final int BLOCK_CACHE_SIZE = 10;
	static final Map<Long, BlockImpl> blockCache = new HashMap<>();
	static final SortedMap<Integer, BlockImpl> heightMap = new TreeMap<>();
	static final Map<Long, TransactionImpl> transactionCache = new HashMap<>();
	static final Map<Long, TransactionImpl> sncleantransactionCache = new HashMap<>();
	static final Blockchain blockchain = Nxt.getBlockchain();
	static {
		Nxt.getBlockchainProcessor().addListener((block) -> {
			synchronized (BlockDb.blockCache) {
				final int height = block.getHeight();
				final Iterator<BlockImpl> it = BlockDb.blockCache.values().iterator();
				while (it.hasNext()) {
					final Block cacheBlock = it.next();
					final int cacheHeight = cacheBlock.getHeight();
					if ((cacheHeight <= (height - BlockDb.BLOCK_CACHE_SIZE)) || (cacheHeight >= height)) {
						cacheBlock.getTransactions().forEach((tx) -> BlockDb.transactionCache.remove(tx.getId()));
						cacheBlock.getTransactions().forEach((tx) -> BlockDb.sncleantransactionCache.remove(tx.getSNCleanedId()));
						BlockDb.heightMap.remove(cacheHeight);
						it.remove();
					}
				}
				block.getTransactions().forEach((tx) -> BlockDb.transactionCache.put(tx.getId(), (TransactionImpl) tx));
				block.getTransactions().forEach((tx) -> BlockDb.sncleantransactionCache.put(tx.getSNCleanedId(), (TransactionImpl) tx));
				BlockDb.heightMap.put(height, (BlockImpl) block);
				BlockDb.blockCache.put(block.getId(), (BlockImpl) block);
			}
		}, BlockchainProcessor.Event.BLOCK_PUSHED);
	}

	static private void clearBlockCache() {
		synchronized (BlockDb.blockCache) {
			BlockDb.blockCache.clear();
			BlockDb.heightMap.clear();
			BlockDb.transactionCache.clear();
		}
	}

	static void deleteAll() {
		if (!Db.db.isInTransaction()) {
			try {
				Db.db.beginTransaction();
				BlockDb.deleteAll();
				Db.db.commitTransaction();
			} catch (final Exception e) {
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
			return;
		}
		Logger.logMessage("Deleting blockchain...");
		try (Connection con = Db.db.getConnection(); Statement stmt = con.createStatement()) {
			try {
				stmt.executeUpdate("SET REFERENTIAL_INTEGRITY FALSE");
				stmt.executeUpdate("TRUNCATE TABLE transaction");
				stmt.executeUpdate("TRUNCATE TABLE block");
				BlockchainProcessorImpl.getInstance().getDerivedTables().forEach(table -> {
					if (table.isPersistent()) try {
                        stmt.executeUpdate("TRUNCATE TABLE " + table.toString());
                    } catch (final SQLException ignore) {
                    }
				});
				stmt.executeUpdate("SET REFERENTIAL_INTEGRITY TRUE");
				Db.db.commitTransaction();
			} catch (final SQLException e) {
				Db.db.rollbackTransaction();
				throw e;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		} finally {
			BlockDb.clearBlockCache();
		}
	}

	// relying on cascade triggers in the database to delete the transactions
	// and public keys for all deleted blocks
	static BlockImpl deleteBlocksFrom(final long blockId) {
		if (!Db.db.isInTransaction()) {
			BlockImpl lastBlock;
			try {
				Db.db.beginTransaction();
				lastBlock = BlockDb.deleteBlocksFrom(blockId);
				Db.db.commitTransaction();
			} catch (final Exception e) {
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
			return lastBlock;
		}
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmtSelect = con.prepareStatement("SELECT db_id FROM block WHERE timestamp >= "
						+ "IFNULL ((SELECT timestamp FROM block WHERE id = ?), " + Integer.MAX_VALUE
						+ ") ORDER BY timestamp DESC");
				PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM block WHERE db_id = ?")) {
			try {
				pstmtSelect.setLong(1, blockId);
				try (ResultSet rs = pstmtSelect.executeQuery()) {
					Db.db.commitTransaction();
					while (rs.next()) {
						pstmtDelete.setLong(1, rs.getLong("db_id"));
						pstmtDelete.executeUpdate();
						Db.db.commitTransaction();
					}
				}
				final BlockImpl lastBlock = BlockDb.findLastBlock();
				lastBlock.setNextBlockId(0);
				try (PreparedStatement pstmt = con
						.prepareStatement("UPDATE block SET next_block_id = NULL WHERE id = ?")) {
					pstmt.setLong(1, lastBlock.getId());
					pstmt.executeUpdate();
				}
				Db.db.commitTransaction();
				return lastBlock;
			} catch (final SQLException e) {
				Db.db.rollbackTransaction();
				throw e;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		} finally {
			BlockDb.clearBlockCache();
		}
	}

	static void deleteBlocksFromHeight(final int height) {
		long blockId;
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block WHERE height = ?")) {
            //noinspection SuspiciousNameCombination
            pstmt.setInt(1, height);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (!rs.next()) return;
				blockId = rs.getLong("id");
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		Logger.logDebugMessage("Deleting blocks starting from height %s", height);
		BlockDb.deleteBlocksFrom(blockId);
	}

	static BlockImpl findBlock(final long blockId) {
		// Check the block cache
		synchronized (BlockDb.blockCache) {
			final BlockImpl block = BlockDb.blockCache.get(blockId);
			if (block != null) return block;
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE id = ?")) {
			pstmt.setLong(1, blockId);
			try (ResultSet rs = pstmt.executeQuery()) {
				BlockImpl block = null;
				if (rs.next()) block = BlockDb.loadBlock(con, rs);
				return block;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static BlockImpl findBlockAtHeight(final int height) {
		// Check the cache

		BlockImpl block;

		synchronized (BlockDb.blockCache) {
			block = BlockDb.heightMap.get(height);
			if (block != null) return block;
		}

		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE height = ?")) {
            //noinspection SuspiciousNameCombination
            pstmt.setInt(1, height);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) block = BlockDb.loadBlock(con, rs);
                else block = null;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

		if (block != null) return block;
        else throw new RuntimeException("Block at height " + height + " not found in database!");
	}

	static long findBlockIdAtHeight(final int height) {
		// Check the cache
		synchronized (BlockDb.blockCache) {
			final BlockImpl block = BlockDb.heightMap.get(height);
			if (block != null) return block.getId();
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block WHERE height = ?")) {
            //noinspection SuspiciousNameCombination
            pstmt.setInt(1, height);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (!rs.next()) throw new RuntimeException("Block at height " + height + " not found in database!");
				return rs.getLong("id");
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static BlockImpl findLastBlock() {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block ORDER BY timestamp DESC LIMIT 1")) {
			BlockImpl block = null;
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) block = BlockDb.loadBlock(con, rs);
			}
			return block;
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static BlockImpl findLastBlock(final int timestamp) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT * FROM block WHERE timestamp <= ? ORDER BY timestamp DESC LIMIT 1")) {
			pstmt.setInt(1, timestamp);
			BlockImpl block = null;
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) block = BlockDb.loadBlock(con, rs);
			}
			return block;
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static Set<Long> getBlockGenerators(final int startHeight) {
		final Set<Long> generators = new HashSet<>();
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"SELECT generator_id, COUNT(generator_id) AS count FROM block WHERE height >= ? GROUP BY generator_id")) {
            //noinspection SuspiciousNameCombination
            pstmt.setInt(1, startHeight);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) if (rs.getInt("count") > 1) generators.add(rs.getLong("generator_id"));
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return generators;
	}

	static boolean hasBlock(final long blockId) {
		return BlockDb.hasBlock(blockId, Integer.MAX_VALUE);
	}

	static boolean hasBlock(final long blockId, final int height) {

		// Check the block cache
		synchronized (BlockDb.blockCache) {
			final BlockImpl block = BlockDb.blockCache.get(blockId);
			if (block != null) return block.getHeight() <= height;
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT height FROM block WHERE id = ?")) {
			pstmt.setLong(1, blockId);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next() && (rs.getInt("height") <= height);
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static BlockImpl loadBlock(final Connection con, final ResultSet rs) {
		return BlockDb.loadBlock(con, rs, false);
	}

	static BlockImpl loadBlock(final Connection con, final ResultSet rs, final boolean loadTransactions) {
		try {
			final int version = rs.getInt("version");
			final int timestamp = rs.getInt("timestamp");
			final long previousBlockId = rs.getLong("previous_block_id");
			final long totalAmountNQT = rs.getLong("total_amount");
			final long totalFeeNQT = rs.getLong("total_fee");
			final long softforkVotes = rs.getLong("softforkVotes");
			final int payloadLength = rs.getInt("payload_length");
			final long generatorId = rs.getLong("generator_id");
			final byte[] previousBlockHash = rs.getBytes("previous_block_hash");
			final BigInteger cumulativeDifficulty = new BigInteger(rs.getBytes("cumulative_difficulty"));
			final long baseTarget = rs.getLong("base_target");
			final long nextBlockId = rs.getLong("next_block_id");
			final int height = rs.getInt("height");
			final byte[] generationSignature = rs.getBytes("generation_signature");
			final byte[] blockSignature = rs.getBytes("block_signature");
			final byte[] payloadHash = rs.getBytes("payload_hash");
			final BigInteger min_pow_target = new BigInteger(rs.getBytes("min_pow_target"));
			final long id = rs.getLong("id");
			return new BlockImpl(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, softforkVotes, payloadLength,
					payloadHash, generatorId, generationSignature, blockSignature, previousBlockHash,
					cumulativeDifficulty, baseTarget, nextBlockId, height, id,
					loadTransactions ? TransactionDb.findBlockTransactions(con, id) : null, min_pow_target);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static void saveBlock(final Connection con, final BlockImpl block) {
		try {
			try (PreparedStatement pstmt = con
					.prepareStatement("INSERT INTO block (id, version, timestamp, previous_block_id, "
							+ "total_amount, total_fee, softforkVotes, payload_length, previous_block_hash, cumulative_difficulty, "
							+ "base_target, height, generation_signature, block_signature, payload_hash, generator_id, min_pow_target) "
							+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
				int i = 0;
				pstmt.setLong(++i, block.getId());
				pstmt.setInt(++i, block.getVersion());
				pstmt.setInt(++i, block.getTimestamp());
				DbUtils.setLongZeroToNull(pstmt, ++i, block.getPreviousBlockId());
				pstmt.setLong(++i, block.getTotalAmountNQT());
				pstmt.setLong(++i, block.getTotalFeeNQT());
				pstmt.setLong(++i, block.getSoftforkVotes());
				pstmt.setInt(++i, block.getPayloadLength());
				pstmt.setBytes(++i, block.getPreviousBlockHash());
				pstmt.setBytes(++i, block.getCumulativeDifficulty().toByteArray());
				pstmt.setLong(++i, block.getBaseTarget());
				pstmt.setInt(++i, block.getHeight());
				pstmt.setBytes(++i, block.getGenerationSignature());
				pstmt.setBytes(++i, block.getBlockSignature());
				pstmt.setBytes(++i, block.getPayloadHash());
				pstmt.setLong(++i, block.getGeneratorId());
				pstmt.setBytes(++i, block.getMinPowTarget().toByteArray());
				pstmt.executeUpdate();
				TransactionDb.saveTransactions(con, block.getTransactions());
			}
			if (block.getPreviousBlockId() != 0) {
				try (PreparedStatement pstmt = con
						.prepareStatement("UPDATE block SET next_block_id = ? WHERE id = ?")) {
					pstmt.setLong(1, block.getId());
					pstmt.setLong(2, block.getPreviousBlockId());
					pstmt.executeUpdate();
				}
				BlockImpl previousBlock;
				synchronized (BlockDb.blockCache) {
					previousBlock = BlockDb.blockCache.get(block.getPreviousBlockId());
				}
				if (previousBlock != null) previousBlock.setNextBlockId(block.getId());
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

}

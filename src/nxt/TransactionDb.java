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
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nxt.db.DbUtils;
import nxt.util.Convert;

final class TransactionDb {

	static class PrunableTransaction {
		private final long id;
		private final TransactionType transactionType;
		private final boolean prunableSourceCode;

		public PrunableTransaction(final long id, final TransactionType transactionType,
				final boolean prunableAttachment, final boolean prunablePlainMessage,
				final boolean prunableEncryptedMessage, final boolean prunableSourceCode) {
			this.id = id;
			this.transactionType = transactionType;
			this.prunableSourceCode = prunableSourceCode;
		}

		public long getId() {
			return this.id;
		}

		public TransactionType getTransactionType() {
			return this.transactionType;
		}

		public boolean hasPrunableSourceCode() {
			return this.prunableSourceCode;
		}
	}

	static List<TransactionImpl> findBlockTransactions(final Connection con, final long blockId) {
		try (PreparedStatement pstmt = con
				.prepareStatement("SELECT * FROM transaction WHERE block_id = ? ORDER BY transaction_index")) {
			pstmt.setLong(1, blockId);
			pstmt.setFetchSize(50);
			try (ResultSet rs = pstmt.executeQuery()) {
				final List<TransactionImpl> list = new ArrayList<>();
				while (rs.next()) {
					list.add(TransactionDb.loadTransaction(con, rs));
				}
				return list;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		} catch (final NxtException.ValidationException e) {
			throw new RuntimeException("Transaction already in database for block_id = "
					+ Long.toUnsignedString(blockId) + " does not pass validation!", e);
		}
	}

	static List<TransactionImpl> findBlockTransactions(final long blockId) {
		// Check the block cache
		synchronized (BlockDb.blockCache) {
			final BlockImpl block = BlockDb.blockCache.get(blockId);
			if (block != null) {
				return block.getTransactions();
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection()) {
			return TransactionDb.findBlockTransactions(con, blockId);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static List<PrunableTransaction> findPrunableTransactions(final Connection con, final int minTimestamp,
			final int maxTimestamp) {
		final List<PrunableTransaction> result = new ArrayList<>();
		try (PreparedStatement pstmt = con.prepareStatement("SELECT id, type, subtype, "
				+ "has_prunable_attachment AS prunable_attachment, "
				+ "has_prunable_message AS prunable_plain_message, "
				+ "has_prunable_source_code AS prunable_source_code, "
				+ "has_prunable_encrypted_message AS prunable_encrypted_message "
				+ "has_prunable_source_code AS prunable_source_code "
				+ "FROM transaction WHERE (timestamp BETWEEN ? AND ?) AND "
				+ "(has_prunable_attachment = TRUE OR has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE)")) {
			pstmt.setInt(1, minTimestamp);
			pstmt.setInt(2, maxTimestamp);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					final long id = rs.getLong("id");
					final byte type = rs.getByte("type");
					final byte subtype = rs.getByte("subtype");
					final TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
					result.add(new PrunableTransaction(id, transactionType, rs.getBoolean("prunable_attachment"),
							rs.getBoolean("prunable_plain_message"), rs.getBoolean("prunable_encrypted_message"),
							rs.getBoolean("prunable_source_code")));
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return result;
	}

	static TransactionImpl findTransaction(final long transactionId) {
		return TransactionDb.findTransaction(transactionId, Integer.MAX_VALUE);
	}

	static TransactionImpl findTransaction(final long transactionId, final int height) {
		// Check the block cache
		synchronized (BlockDb.blockCache) {
			final TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
			if (transaction != null) {
				return transaction.getHeight() <= height ? transaction : null;
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM transaction WHERE id = ?")) {
			pstmt.setLong(1, transactionId);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next() && (rs.getInt("height") <= height)) {
					return TransactionDb.loadTransaction(con, rs);
				}
				return null;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		} catch (final NxtException.ValidationException e) {
			throw new RuntimeException(
					"Transaction already in database, id = " + transactionId + ", does not pass validation!", e);
		}
	}

	static TransactionImpl findTransactionByFullHash(final byte[] fullHash) {
		return TransactionDb.findTransactionByFullHash(fullHash, Integer.MAX_VALUE);
	}

	static TransactionImpl findTransactionByFullHash(final byte[] fullHash, final int height) {
		final long transactionId = Convert.fullHashToId(fullHash);
		// Check the cache
		synchronized (BlockDb.blockCache) {
			final TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
			if (transaction != null) {
				return ((transaction.getHeight() <= height) && Arrays.equals(transaction.fullHash(), fullHash)
						? transaction : null);
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM transaction WHERE id = ?")) {
			pstmt.setLong(1, transactionId);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next() && Arrays.equals(rs.getBytes("full_hash"), fullHash) && (rs.getInt("height") <= height)) {
					return TransactionDb.loadTransaction(con, rs);
				}
				return null;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		} catch (final NxtException.ValidationException e) {
			throw new RuntimeException("Transaction already in database, full_hash = " + Convert.toHexString(fullHash)
					+ ", does not pass validation!", e);
		}
	}

	static byte[] getFullHash(final long transactionId) {
		// Check the block cache
		synchronized (BlockDb.blockCache) {
			final TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
			if (transaction != null) {
				return transaction.fullHash();
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT full_hash FROM transaction WHERE id = ?")) {
			pstmt.setLong(1, transactionId);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next() ? rs.getBytes("full_hash") : null;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static boolean hasTransaction(final long transactionId) {
		return TransactionDb.hasTransaction(transactionId, Integer.MAX_VALUE);
	}

	static boolean hasTransaction(final long transactionId, final int height) {
		// Check the block cache
		synchronized (BlockDb.blockCache) {
			final TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
			if (transaction != null) {
				return (transaction.getHeight() <= height);
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT height FROM transaction WHERE id = ?")) {
			pstmt.setLong(1, transactionId);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next() && (rs.getInt("height") <= height);
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static boolean hasSNCleanTransaction(final long transactionId) {
		return TransactionDb.hasSNCleanTransaction(transactionId, Integer.MAX_VALUE);
	}

	static boolean hasSNCleanTransaction(final long SNCleantransactionId, final int height) {
		// Check the block cache
		synchronized (BlockDb.blockCache) {
			final TransactionImpl transaction = BlockDb.sncleantransactionCache.get(SNCleantransactionId);
			if (transaction != null) {
				return (transaction.getHeight() <= height);
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
			 PreparedStatement pstmt = con.prepareStatement("SELECT height FROM transaction WHERE sncleanid = ?")) {
			pstmt.setLong(1, SNCleantransactionId);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next() && (rs.getInt("height") <= height);
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static boolean hasTransactionByFullHash(final byte[] fullHash) {
		return Arrays.equals(fullHash, TransactionDb.getFullHash(Convert.fullHashToId(fullHash)));
	}

	static boolean hasTransactionByFullHash(final byte[] fullHash, final int height) {
		final long transactionId = Convert.fullHashToId(fullHash);
		// Check the block cache
		synchronized (BlockDb.blockCache) {
			final TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
			if (transaction != null) {
				return ((transaction.getHeight() <= height) && Arrays.equals(transaction.fullHash(), fullHash));
			}
		}
		// Search the database
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT full_hash, height FROM transaction WHERE id = ?")) {
			pstmt.setLong(1, transactionId);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next() && Arrays.equals(rs.getBytes("full_hash"), fullHash)
						&& (rs.getInt("height") <= height);
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static TransactionImpl loadTransaction(final Connection con, final ResultSet rs)
			throws NxtException.NotValidException {
		try {

			final byte type = rs.getByte("type");
			final byte subtype = rs.getByte("subtype");
			final int timestamp = rs.getInt("timestamp");
			final short deadline = rs.getShort("deadline");
			final long amountNQT = rs.getLong("amount");
			final long feeNQT = rs.getLong("fee");
			final byte[] referencedTransactionFullHash = rs.getBytes("referenced_transaction_full_hash");
			final int ecBlockHeight = rs.getInt("ec_block_height");
			final long ecBlockId = rs.getLong("ec_block_id");
			final byte[] signature = rs.getBytes("signature");
			final byte[] supernode_signature = rs.getBytes("supernode_signature");
			final byte[] superNodePublicKey = rs.getBytes("superNodePublicKey");
			final long blockId = rs.getLong("block_id");
			final int height = rs.getInt("height");
			final long id = rs.getLong("id");
			final long senderId = rs.getLong("sender_id");
			final byte[] attachmentBytes = rs.getBytes("attachment_bytes");
			final int blockTimestamp = rs.getInt("block_timestamp");
			final byte[] fullHash = rs.getBytes("full_hash");
			final byte version = rs.getByte("version");
			final short transactionIndex = rs.getShort("transaction_index");

			ByteBuffer buffer = null;
			if (attachmentBytes != null) {
				buffer = ByteBuffer.wrap(attachmentBytes);
				buffer.order(ByteOrder.LITTLE_ENDIAN);
			}

			final TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
			
			if(transactionType == null) throw new NxtException.NotValidException("Unknown TX Type");
			final TransactionImpl.BuilderImpl builder = new TransactionImpl.BuilderImpl(version, null, amountNQT,
					feeNQT, deadline, transactionType.parseAttachment(buffer, version)).timestamp(timestamp)
							.referencedTransactionFullHash(referencedTransactionFullHash).signature(signature)
							.blockId(blockId).height(height).id(id).senderId(senderId).blockTimestamp(blockTimestamp)
							.fullHash(fullHash).ecBlockHeight(ecBlockHeight).ecBlockId(ecBlockId).supernode_signature(superNodePublicKey, supernode_signature)
							.index(transactionIndex);
			if (transactionType.canHaveRecipient()) {
				final long recipientId = rs.getLong("recipient_id");
				if (!rs.wasNull()) {
					builder.recipientId(recipientId);
				}
			}

			if (rs.getBoolean("has_prunable_source_code")) {
				builder.appendix(new Appendix.PrunableSourceCode(buffer, version));
			}
			if (rs.getBoolean("has_public_key_announcement") || rs.getBoolean("has_prunable_encrypted_message")) { // TODO: Remove the second check, its only there for BUG 1086
				builder.appendix(new Appendix.PublicKeyAnnouncement(buffer, version));
			}

			return builder.build();

		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static void saveTransactions(final Connection con, final List<TransactionImpl> transactions) {
		try {
			short index = 0;
			for (final TransactionImpl transaction : transactions) {
				try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO transaction (id, sncleanid, deadline, "
						+ "recipient_id, amount, fee, referenced_transaction_full_hash, height, "
						+ "block_id, signature, superNodePublicKey, supernode_signature, timestamp, type, subtype, sender_id, attachment_bytes, "
						+ "block_timestamp, full_hash, version, has_message, has_encrypted_message, has_public_key_announcement, "
						+ "has_encrypttoself_message, has_prunable_message, has_prunable_source_code, has_prunable_encrypted_message, "
						+ "has_prunable_attachment, ec_block_height, ec_block_id, transaction_index) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
					int i = 0;
					pstmt.setLong(++i, transaction.getId());
					pstmt.setLong(++i, transaction.getSNCleanedId());
					pstmt.setShort(++i, transaction.getDeadline());
					DbUtils.setLongZeroToNull(pstmt, ++i, transaction.getRecipientId());
					pstmt.setLong(++i, transaction.getAmountNQT());
					pstmt.setLong(++i, transaction.getFeeNQT());
					DbUtils.setBytes(pstmt, ++i, transaction.referencedTransactionFullHash());
					pstmt.setInt(++i, transaction.getHeight());
					pstmt.setLong(++i, transaction.getBlockId());
					pstmt.setBytes(++i, transaction.getSignature());
					pstmt.setBytes(++i, transaction.getSuperNodePublicKey());
					pstmt.setBytes(++i, transaction.getSupernodeSig());
					pstmt.setInt(++i, transaction.getTimestamp());
					pstmt.setByte(++i, transaction.getType().getType());
					pstmt.setByte(++i, transaction.getType().getSubtype());
					pstmt.setLong(++i, transaction.getSenderId());
					int bytesLength = 0;
					for (final Appendix appendage : transaction.getAppendages()) {
						bytesLength += appendage.getSize();
					}
					if (bytesLength == 0) {
						pstmt.setNull(++i, Types.VARBINARY);
					} else {
						final ByteBuffer buffer = ByteBuffer.allocate(bytesLength);
						buffer.order(ByteOrder.LITTLE_ENDIAN);
						for (final Appendix appendage : transaction.getAppendages()) {
							appendage.putBytes(buffer);
						}
						pstmt.setBytes(++i, buffer.array());
					}
					pstmt.setInt(++i, transaction.getBlockTimestamp());
					pstmt.setBytes(++i, transaction.fullHash());
					pstmt.setByte(++i, transaction.getVersion());
					pstmt.setBoolean(++i, false);
					pstmt.setBoolean(++i, false);
					pstmt.setBoolean(++i, transaction.getPublicKeyAnnouncement() != null);
					pstmt.setBoolean(++i, false);
					pstmt.setBoolean(++i, false);
					pstmt.setBoolean(++i, transaction.hasPrunableSourceCode());
					pstmt.setBoolean(++i, false);
					pstmt.setBoolean(++i, transaction.getAttachment() instanceof Appendix.Prunable);
					pstmt.setInt(++i, transaction.getECBlockHeight());
					DbUtils.setLongZeroToNull(pstmt, ++i, transaction.getECBlockId());
					pstmt.setShort(++i, index++);
					pstmt.executeUpdate();
				}
				if (transaction.referencedTransactionFullHash() != null) {
					try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO referenced_transaction "
							+ "(transaction_id, referenced_transaction_id) VALUES (?, ?)")) {
						pstmt.setLong(1, transaction.getId());
						pstmt.setLong(2, Convert.fullHashToId(transaction.referencedTransactionFullHash()));
						pstmt.executeUpdate();
					}
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

}

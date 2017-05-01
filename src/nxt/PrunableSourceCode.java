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

import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.PrunableDbTable;

public final class PrunableSourceCode {

	private static final DbKey.LongKeyFactory<PrunableSourceCode> prunableSourceCodeKeyFactory = new DbKey.LongKeyFactory<PrunableSourceCode>(
			"id") {

		@Override
		public DbKey newKey(final PrunableSourceCode prunableSourceCode) {
			return prunableSourceCode.dbKey;
		}

	};

	private static final PrunableDbTable<PrunableSourceCode> prunableSourceCodeTable = new PrunableDbTable<PrunableSourceCode>(
			"prunable_source_code", PrunableSourceCode.prunableSourceCodeKeyFactory) {

		@Override
		protected String defaultSort() {
			return " ORDER BY block_timestamp DESC, db_id DESC ";
		}

		@Override
		protected PrunableSourceCode load(final Connection con, final ResultSet rs, final DbKey dbKey)
				throws SQLException {
			return new PrunableSourceCode(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final PrunableSourceCode prunableSourceCode) throws SQLException {
			prunableSourceCode.save(con);
		}

	};

	static void add(final TransactionImpl transaction, final Appendix.PrunableSourceCode appendix) {
		PrunableSourceCode.add(transaction, appendix, Nxt.getBlockchain().getLastBlockTimestamp(),
				Nxt.getBlockchain().getHeight());
	}

	static void add(final TransactionImpl transaction, final Appendix.PrunableSourceCode appendix,
			final int blockTimestamp, final int height) {
		if (appendix.getSource() != null) {
			PrunableSourceCode prunableSourceCode = PrunableSourceCode.prunableSourceCodeTable
					.get(transaction.getDbKey());
			if (prunableSourceCode == null)
                prunableSourceCode = new PrunableSourceCode(transaction, blockTimestamp, height);
            else if (prunableSourceCode.height != height)
                throw new RuntimeException("Attempt to modify prunable source code from height "
                        + prunableSourceCode.height + " at height " + height);
			if (prunableSourceCode.getSource() == null) {
				prunableSourceCode.setPlain(appendix);
				PrunableSourceCode.prunableSourceCodeTable.insert(prunableSourceCode);
			}
		}
	}

	public static DbIterator<PrunableSourceCode> getAll(final int from, final int to) {
		return PrunableSourceCode.prunableSourceCodeTable.getAll(from, to);
	}

	public static int getCount() {
		return PrunableSourceCode.prunableSourceCodeTable.getCount();
	}

	public static PrunableSourceCode getPrunableSourceCode(final long transactionId) {
		return PrunableSourceCode.prunableSourceCodeTable
				.get(PrunableSourceCode.prunableSourceCodeKeyFactory.newKey(transactionId));
	}

	public static PrunableSourceCode getPrunableSourceCodeByWorkId(final long work_id) {

		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT * FROM prunable_source_code WHERE work_id = ?")) {
			int i = 0;
			pstmt.setLong(++i, work_id);
			final DbIterator<PrunableSourceCode> it = PrunableSourceCode.prunableSourceCodeTable.getManyBy(con, pstmt,
					false);
			PrunableSourceCode s = null;
			if (it.hasNext()) s = it.next();
			it.close();
			return s;
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

	}

	static void init() {
	}

	static boolean isPruned(final long transactionId, final boolean hasPrunableSourceCode) {
		if (!hasPrunableSourceCode) return false;
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT source FROM prunable_source_code WHERE id = ?")) {
			pstmt.setLong(1, transactionId);
			try (ResultSet rs = pstmt.executeQuery()) {
				return !rs.next() || rs.getBytes("source") == null;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static boolean isPrunedByWorkId(final long work_id) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT source FROM prunable_source_code WHERE work_id = ?")) {
			pstmt.setLong(1, work_id);
			try (ResultSet rs = pstmt.executeQuery()) {
				return !rs.next() || (rs.getBytes("source") == null);
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	private final long id;
	private final DbKey dbKey;
	private final long work_id;
	private byte[] source;

	private short language;

	private final int transactionTimestamp;

	private final int blockTimestamp;

	private final int height;

	private PrunableSourceCode(final ResultSet rs, final DbKey dbKey) throws SQLException {
		this.id = rs.getLong("id");
		this.dbKey = dbKey;
		this.work_id = rs.getLong("work_id");
		this.source = rs.getBytes("source");
		this.blockTimestamp = rs.getInt("block_timestamp");
		this.transactionTimestamp = rs.getInt("transaction_timestamp");
		this.height = rs.getInt("height");
		this.language = rs.getShort("language");
	}

	private PrunableSourceCode(final Transaction transaction, final int blockTimestamp, final int height) {
		this.id = transaction.getId();
		this.dbKey = PrunableSourceCode.prunableSourceCodeKeyFactory.newKey(this.id);
		this.work_id = transaction.getSNCleanedId();
		this.blockTimestamp = blockTimestamp;
		this.height = height;
		this.transactionTimestamp = transaction.getTimestamp();
	}

	public int getBlockTimestamp() {
		return this.blockTimestamp;
	}

	public int getHeight() {
		return this.height;
	}

	public long getId() {
		return this.id;
	}

	public short getLanguage() {
		return this.language;
	}

	public byte[] getSource() {
		return this.source;
	}

	public int getTransactionTimestamp() {
		return this.transactionTimestamp;
	}

	public long getWorkId() {
		return this.work_id;
	}

	private void save(final Connection con) throws SQLException {
		if (this.source == null) throw new IllegalStateException("Prunable source code not fully initialized");
		try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO prunable_source_code (id, work_id, "
				+ "source, block_timestamp, transaction_timestamp, height, language) " + "KEY (id) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
			int i = 0;
			pstmt.setLong(++i, this.id);
			pstmt.setLong(++i, this.work_id);
			DbUtils.setBytes(pstmt, ++i, this.source);
			pstmt.setInt(++i, this.blockTimestamp);
			pstmt.setInt(++i, this.transactionTimestamp);
			//noinspection SuspiciousNameCombination
			pstmt.setInt(++i, this.height);
			pstmt.setShort(++i, this.language);
			pstmt.executeUpdate();
		}
	}

	private void setPlain(final Appendix.PrunableSourceCode appendix) {
		this.source = appendix.getSource();
		this.language = appendix.getLanguage();
	}

}

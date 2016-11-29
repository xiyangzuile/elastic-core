/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import nxt.Nxt;
import nxt.util.Logger;

public abstract class EntityDbTable<T> extends DerivedDbTable {

	private final boolean multiversion;
	protected final DbKey.Factory<T> dbKeyFactory;
	private final String defaultSort;
	protected EntityDbTable(final String table, final DbKey.Factory<T> dbKeyFactory) {
		this(table, dbKeyFactory, false, null);
	}

	EntityDbTable(final String table, final DbKey.Factory<T> dbKeyFactory, final boolean multiversion, final String fullTextSearchColumns) {
		super(table);
		this.dbKeyFactory = dbKeyFactory;
		this.multiversion = multiversion;
		this.defaultSort = " ORDER BY " + (multiversion ? dbKeyFactory.getPKColumns() : " height DESC, db_id DESC ");
	}

	protected EntityDbTable(final String table, final DbKey.Factory<T> dbKeyFactory, final String fullTextSearchColumns) {
		this(table, dbKeyFactory, false, fullTextSearchColumns);
	}

	public void checkAvailable(final int height) {
		if (this.multiversion && (height < Nxt.getBlockchainProcessor().getMinRollbackHeight())) {
			throw new IllegalArgumentException("Historical data as of height " + height +" not available.");
		}
		if (height > Nxt.getBlockchain().getHeight()) {
			throw new IllegalArgumentException("Height " + height + " exceeds blockchain height " + Nxt.getBlockchain().getHeight());
		}
	}

	protected void clearCache() {
		DerivedDbTable.db.clearCache(this.table);
	}

	@Override
	public final void createSearchIndex(final Connection con) throws SQLException {

	}

	protected String defaultSort() {
		return this.defaultSort;
	}

	private T get(final Connection con, final PreparedStatement pstmt, final boolean cache) throws SQLException {
		final boolean doCache = cache && DerivedDbTable.db.isInTransaction();
		try (ResultSet rs = pstmt.executeQuery()) {
			if (!rs.next()) {
				return null;
			}
			T t = null;
			DbKey dbKey = null;
			if (doCache) {
				dbKey = this.dbKeyFactory.newKey(rs);
				t = (T) DerivedDbTable.db.getCache(this.table).get(dbKey);
			}
			if (t == null) {
				t = this.load(con, rs, dbKey);
				if (doCache) {
					DerivedDbTable.db.getCache(this.table).put(dbKey, t);
				}
			}
			if (rs.next()) {
				throw new RuntimeException("Multiple records found");
			}
			return t;
		}
	}

	public final T get(final DbKey dbKey) {
		return this.get(dbKey, true);
	}

	public final T get(final DbKey dbKey, final boolean cache) {
		if (cache && DerivedDbTable.db.isInTransaction()) {
			final T t = (T) DerivedDbTable.db.getCache(this.table).get(dbKey);
			if (t != null) {
				return t;
			}
		}
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + this.table + this.dbKeyFactory.getPKClause()
				+ (this.multiversion ? " AND latest = TRUE LIMIT 1" : ""))) {
			dbKey.setPK(pstmt);
			return this.get(con, pstmt, cache);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final T get(final DbKey dbKey, final int height) {
		if ((height < 0) || (height == Nxt.getBlockchain().getHeight())) {
			return this.get(dbKey);
		}
		this.checkAvailable(height);
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + this.table + this.dbKeyFactory.getPKClause()
				+ " AND height <= ?" + (this.multiversion ? " AND (latest = TRUE OR EXISTS ("
						+ "SELECT 1 FROM " + this.table + this.dbKeyFactory.getPKClause() + " AND height > ?)) ORDER BY height DESC LIMIT 1" : ""))) {
			int i = dbKey.setPK(pstmt);
			pstmt.setInt(i, height);
			if (this.multiversion) {
				i = dbKey.setPK(pstmt, ++i);
				pstmt.setInt(i, height);
			}
			return this.get(con, pstmt, false);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final DbIterator<T> getAll(final int from, final int to) {
		return this.getAll(from, to, this.defaultSort());
	}

	public final DbIterator<T> getAll(final int height, final int from, final int to) {
		return this.getAll(height, from, to, this.defaultSort());
	}

	public final DbIterator<T> getAll(final int height, final int from, final int to, final String sort) {
		if ((height < 0) || (height == Nxt.getBlockchain().getHeight())) {
			return this.getAll(from, to, sort);
		}
		this.checkAvailable(height);
		Connection con = null;
		try {
			con = DerivedDbTable.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + this.table + " AS a WHERE height <= ?"
					+ (this.multiversion ? " AND (latest = TRUE OR (latest = FALSE "
							+ "AND EXISTS (SELECT 1 FROM " + this.table + " AS b WHERE b.height > ? AND " + this.dbKeyFactory.getSelfJoinClause()
							+ ") AND NOT EXISTS (SELECT 1 FROM " + this.table + " AS b WHERE b.height <= ? AND " + this.dbKeyFactory.getSelfJoinClause()
							+ " AND b.height > a.height))) " : " ") + sort
					+ DbUtils.limitsClause(from, to));
			int i = 0;
			pstmt.setInt(++i, height);
			if (this.multiversion) {
				pstmt.setInt(++i, height);
				pstmt.setInt(++i, height);
			}
			i = DbUtils.setLimits(++i, pstmt, from, to);
			return this.getManyBy(con, pstmt, false);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final DbIterator<T> getAll(final int from, final int to, final String sort) {
		Connection con = null;
		try {
			con = DerivedDbTable.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + this.table
					+ (this.multiversion ? " WHERE latest = TRUE " : " ") + sort
					+ DbUtils.limitsClause(from, to));
			DbUtils.setLimits(1, pstmt, from, to);
			return this.getManyBy(con, pstmt, true);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final T getBy(final DbClause dbClause) {
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + this.table
						+ " WHERE " + dbClause.getClause() + (this.multiversion ? " AND latest = TRUE LIMIT 1" : ""))) {
			dbClause.set(pstmt, 1);
			return this.get(con, pstmt, true);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final T getBy(final DbClause dbClause, final int height) {
		if ((height < 0) || (height == Nxt.getBlockchain().getHeight())) {
			return this.getBy(dbClause);
		}
		this.checkAvailable(height);
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + this.table + " AS a WHERE " + dbClause.getClause()
				+ " AND height <= ?" + (this.multiversion ? " AND (latest = TRUE OR EXISTS ("
						+ "SELECT 1 FROM " + this.table + " AS b WHERE " + this.dbKeyFactory.getSelfJoinClause()
						+ " AND b.height > ?)) ORDER BY height DESC LIMIT 1" : ""))) {
			int i = 0;
			i = dbClause.set(pstmt, ++i);
			pstmt.setInt(i, height);
			if (this.multiversion) {
				pstmt.setInt(++i, height);
			}
			return this.get(con, pstmt, false);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final int getCount() {
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + this.table
						+ (this.multiversion ? " WHERE latest = TRUE" : ""))) {
			return this.getCount(pstmt);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final int getCount(final DbClause dbClause) {
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + this.table
						+ " WHERE " + dbClause.getClause() + (this.multiversion ? " AND latest = TRUE" : ""))) {
			dbClause.set(pstmt, 1);
			return this.getCount(pstmt);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final int getCount(final DbClause dbClause, final int height) {
		if ((height < 0) || (height == Nxt.getBlockchain().getHeight())) {
			return this.getCount(dbClause);
		}
		this.checkAvailable(height);
		Connection con = null;
		try {
			con = DerivedDbTable.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + this.table + " AS a WHERE " + dbClause.getClause()
			+ "AND a.height <= ?" + (this.multiversion ? " AND (a.latest = TRUE OR (a.latest = FALSE "
					+ "AND EXISTS (SELECT 1 FROM " + this.table + " AS b WHERE " + this.dbKeyFactory.getSelfJoinClause() + " AND b.height > ?) "
					+ "AND NOT EXISTS (SELECT 1 FROM " + this.table + " AS b WHERE " + this.dbKeyFactory.getSelfJoinClause()
					+ " AND b.height <= ? AND b.height > a.height))) "
					: " "));
			int i = 0;
			i = dbClause.set(pstmt, ++i);
			pstmt.setInt(i, height);
			if (this.multiversion) {
				pstmt.setInt(++i, height);
				pstmt.setInt(++i, height);
			}
			return this.getCount(pstmt);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	private int getCount(final PreparedStatement pstmt) throws SQLException {
		try (ResultSet rs = pstmt.executeQuery()) {
			rs.next();
			return rs.getInt(1);
		}
	}

	public final DbIterator<T> getManyBy(final Connection con, final PreparedStatement pstmt, final boolean cache) {
		final boolean doCache = cache && DerivedDbTable.db.isInTransaction();
		return new DbIterator<>(con, pstmt, (connection, rs) -> {
			T t = null;
			DbKey dbKey = null;
			if (doCache) {
				dbKey = this.dbKeyFactory.newKey(rs);
				t = (T) DerivedDbTable.db.getCache(this.table).get(dbKey);
			}
			if (t == null) {
				t = this.load(connection, rs, dbKey);
				if (doCache) {
					DerivedDbTable.db.getCache(this.table).put(dbKey, t);
				}
			}
			return t;
		});
	}

	public final DbIterator<T> getManyBy(final DbClause dbClause, final int from, final int to) {
		return this.getManyBy(dbClause, from, to, this.defaultSort());
	}

	public final DbIterator<T> getManyBy(final DbClause dbClause, final int height, final int from, final int to) {
		return this.getManyBy(dbClause, height, from, to, this.defaultSort());
	}

	public final DbIterator<T> getManyBy(final DbClause dbClause, final int height, final int from, final int to, final String sort) {
		if ((height < 0) || (height == Nxt.getBlockchain().getHeight())) {
			return this.getManyBy(dbClause, from, to, sort);
		}
		this.checkAvailable(height);
		Connection con = null;
		try {
			con = DerivedDbTable.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + this.table + " AS a WHERE " + dbClause.getClause()
			+ "AND a.height <= ?" + (this.multiversion ? " AND (a.latest = TRUE OR (a.latest = FALSE "
					+ "AND EXISTS (SELECT 1 FROM " + this.table + " AS b WHERE " + this.dbKeyFactory.getSelfJoinClause() + " AND b.height > ?) "
					+ "AND NOT EXISTS (SELECT 1 FROM " + this.table + " AS b WHERE " + this.dbKeyFactory.getSelfJoinClause()
					+ " AND b.height <= ? AND b.height > a.height))) "
					: " ") + sort
			+ DbUtils.limitsClause(from, to));
			int i = 0;
			i = dbClause.set(pstmt, ++i);
			pstmt.setInt(i, height);
			if (this.multiversion) {
				pstmt.setInt(++i, height);
				pstmt.setInt(++i, height);
			}
			i = DbUtils.setLimits(++i, pstmt, from, to);
			return this.getManyBy(con, pstmt, false);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final DbIterator<T> getManyBy(final DbClause dbClause, final int from, final int to, final String sort) {
		Connection con = null;
		try {
			con = DerivedDbTable.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + this.table
					+ " WHERE " + dbClause.getClause() + (this.multiversion ? " AND latest = TRUE " : " ") + sort
					+ DbUtils.limitsClause(from, to));
			int i = 0;
			i = dbClause.set(pstmt, ++i);
			i = DbUtils.setLimits(i, pstmt, from, to);
			return this.getManyBy(con, pstmt, true);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final int getRowCount() {
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + this.table)) {
			return this.getCount(pstmt);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final void insert(final T t) {
		if (!DerivedDbTable.db.isInTransaction()) {
			throw new IllegalStateException("Not in transaction");
		}
		final DbKey dbKey = this.dbKeyFactory.newKey(t);
		if (dbKey == null) {
			throw new RuntimeException("DbKey not set");
		}
		final T cachedT = (T) DerivedDbTable.db.getCache(this.table).get(dbKey);
		if (cachedT == null) {
			DerivedDbTable.db.getCache(this.table).put(dbKey, t);
		} else if (t != cachedT) { // not a bug
			Logger.logDebugMessage("In cache : " + cachedT.toString() + ", inserting " + t.toString());
			throw new IllegalStateException("Different instance found in Db cache, perhaps trying to save an object "
					+ "that was read outside the current transaction");
		}
		try (Connection con = DerivedDbTable.db.getConnection()) {
			if (this.multiversion) {
				try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + this.table
						+ " SET latest = FALSE " + this.dbKeyFactory.getPKClause() + " AND latest = TRUE LIMIT 1")) {
					dbKey.setPK(pstmt);
					pstmt.executeUpdate();
				}
			}
			this.save(con, t);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	protected abstract T load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException;

	public final T newEntity(final DbKey dbKey) {
		final boolean cache = DerivedDbTable.db.isInTransaction();
		if (cache) {
			final T t = (T) DerivedDbTable.db.getCache(this.table).get(dbKey);
			if (t != null) {
				return t;
			}
		}
		final T t = this.dbKeyFactory.newEntity(dbKey);
		if (cache) {
			DerivedDbTable.db.getCache(this.table).put(dbKey, t);
		}
		return t;
	}

	@Override
	public void rollback(final int height) {
		if (this.multiversion) {
			VersionedEntityDbTable.rollback(DerivedDbTable.db, this.table, height, this.dbKeyFactory);
		} else {
			super.rollback(height);
		}
	}

	protected abstract void save(Connection con, T t) throws SQLException;

	public final DbIterator<T> search(final String query, final DbClause dbClause, final int from, final int to) {
		return this.search(query, dbClause, from, to, " ORDER BY ft.score DESC ");
	}

	public final DbIterator<T> search(final String query, final DbClause dbClause, final int from, final int to, final String sort) {
		Connection con = null;
		try {
			con = DerivedDbTable.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement("SELECT " + this.table + ".*, ft.score FROM " + this.table +
					", ftl_search('PUBLIC', '" + this.table + "', ?, 2147483647, 0) ft "
					+ " WHERE " + this.table + ".db_id = ft.keys[0] "
					+ (this.multiversion ? " AND " + this.table + ".latest = TRUE " : " ")
					+ " AND " + dbClause.getClause() + sort
					+ DbUtils.limitsClause(from, to));
			int i = 0;
			pstmt.setString(++i, query);
			i = dbClause.set(pstmt, ++i);
			i = DbUtils.setLimits(i, pstmt, from, to);
			return this.getManyBy(con, pstmt, true);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public void trim(final int height) {
		if (this.multiversion) {
			VersionedEntityDbTable.trim(DerivedDbTable.db, this.table, height, this.dbKeyFactory);
		} else {
			super.trim(height);
		}
	}

}

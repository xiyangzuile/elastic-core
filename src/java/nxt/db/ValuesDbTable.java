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
import java.util.ArrayList;
import java.util.List;

public abstract class ValuesDbTable<T, V> extends DerivedDbTable {

	private final boolean multiversion;
	protected final DbKey.Factory<T> dbKeyFactory;

	protected ValuesDbTable(final String table, final DbKey.Factory<T> dbKeyFactory) {
		this(table, dbKeyFactory, false);
	}

	ValuesDbTable(final String table, final DbKey.Factory<T> dbKeyFactory, final boolean multiversion) {
		super(table);
		this.dbKeyFactory = dbKeyFactory;
		this.multiversion = multiversion;
	}

	protected void clearCache() {
		DerivedDbTable.db.clearCache(this.table);
	}

	private List<V> get(final Connection con, final PreparedStatement pstmt) {
		try {
			final List<V> result = new ArrayList<>();
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					result.add(this.load(con, rs));
				}
			}
			return result;
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final List<V> get(final DbKey dbKey) {
		List<V> values;
		if (DerivedDbTable.db.isInTransaction()) {
			values = (List<V>) DerivedDbTable.db.getCache(this.table).get(dbKey);
			if (values != null) {
				return values;
			}
		}
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT * FROM " + this.table + this.dbKeyFactory.getPKClause()
								+ (this.multiversion ? " AND latest = TRUE" : "") + " ORDER BY db_id")) {
			dbKey.setPK(pstmt);
			values = this.get(con, pstmt);
			if (DerivedDbTable.db.isInTransaction()) {
				DerivedDbTable.db.getCache(this.table).put(dbKey, values);
			}
			return values;
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public final void insert(final T t, final List<V> values) {
		if (!DerivedDbTable.db.isInTransaction()) {
			throw new IllegalStateException("Not in transaction");
		}
		final DbKey dbKey = this.dbKeyFactory.newKey(t);
		if (dbKey == null) {
			throw new RuntimeException("DbKey not set");
		}
		DerivedDbTable.db.getCache(this.table).put(dbKey, values);
		try (Connection con = DerivedDbTable.db.getConnection()) {
			if (this.multiversion) {
				try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + this.table + " SET latest = FALSE "
						+ this.dbKeyFactory.getPKClause() + " AND latest = TRUE")) {
					dbKey.setPK(pstmt);
					pstmt.executeUpdate();
				}
			}
			for (final V v : values) {
				this.save(con, t, v);
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	protected abstract V load(Connection con, ResultSet rs) throws SQLException;

	@Override
	public final void rollback(final int height) {
		if (this.multiversion) {
			VersionedEntityDbTable.rollback(DerivedDbTable.db, this.table, height, this.dbKeyFactory);
		} else {
			super.rollback(height);
		}
	}

	protected abstract void save(Connection con, T t, V v) throws SQLException;

	@Override
	public final void trim(final int height) {
		if (this.multiversion) {
			VersionedEntityDbTable.trim(DerivedDbTable.db, this.table, height, this.dbKeyFactory);
		} else {
			super.trim(height);
		}
	}

}

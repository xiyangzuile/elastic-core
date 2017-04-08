/*
 * Copyright © 2013-2016 The XEL Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the XEL software, including this file, may be copied, modified,
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
import java.util.List;

import nxt.Nxt;

public abstract class VersionedValuesDbTable<T, V> extends ValuesDbTable<T, V> {

	protected VersionedValuesDbTable(final String table, final DbKey.Factory<T> dbKeyFactory) {
		super(table, dbKeyFactory, true);
	}

	public final boolean delete(final T t) {
		if (t == null) {
			return false;
		}
		if (!DerivedDbTable.db.isInTransaction()) {
			throw new IllegalStateException("Not in transaction");
		}
		final DbKey dbKey = this.dbKeyFactory.newKey(t);
		final int height = Nxt.getBlockchain().getHeight();
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmtCount = con.prepareStatement(
						"SELECT 1 FROM " + this.table + this.dbKeyFactory.getPKClause() + " AND height < ? LIMIT 1")) {
			final int i = dbKey.setPK(pstmtCount);
			pstmtCount.setInt(i, height);
			try (ResultSet rs = pstmtCount.executeQuery()) {
				if (rs.next()) {
					try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + this.table + " SET latest = FALSE "
							+ this.dbKeyFactory.getPKClause() + " AND height = ? AND latest = TRUE")) {
						final int j = dbKey.setPK(pstmt);
						pstmt.setInt(j, height);
						if (pstmt.executeUpdate() > 0) {
							return true;
						}
					}
					final List<V> values = this.get(dbKey);
					if (values.isEmpty()) {
						return false;
					}
					for (final V v : values) {
						this.save(con, t, v);
					}
					try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + this.table + " SET latest = FALSE "
							+ this.dbKeyFactory.getPKClause() + " AND latest = TRUE")) {
						dbKey.setPK(pstmt);
						if (pstmt.executeUpdate() == 0) {
							throw new RuntimeException(); // should not happen
						}
					}
					return true;
				} else {
					try (PreparedStatement pstmtDelete = con
							.prepareStatement("DELETE FROM " + this.table + this.dbKeyFactory.getPKClause())) {
						dbKey.setPK(pstmtDelete);
						return pstmtDelete.executeUpdate() > 0;
					}
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		} finally {
			DerivedDbTable.db.getCache(this.table).remove(dbKey);
		}
	}

}

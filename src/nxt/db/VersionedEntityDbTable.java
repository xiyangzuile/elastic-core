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
import java.util.ArrayList;
import java.util.List;

import nxt.Nxt;

public abstract class VersionedEntityDbTable<T> extends EntityDbTable<T> {

	static void rollback(final TransactionalDb db, final String table, final int height,
			final DbKey.Factory<?> dbKeyFactory) {
		if (!db.isInTransaction()) {
			throw new IllegalStateException("Not in transaction");
		}
		try (Connection con = db.getConnection();
				PreparedStatement pstmtSelectToDelete = con.prepareStatement(
						"SELECT DISTINCT " + dbKeyFactory.getPKColumns() + " FROM " + table + " WHERE height > ?");
				PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM " + table + " WHERE height > ?");
				PreparedStatement pstmtSetLatest = con.prepareStatement(
						"UPDATE " + table + " SET latest = TRUE " + dbKeyFactory.getPKClause() + " AND height ="
								+ " (SELECT MAX(height) FROM " + table + dbKeyFactory.getPKClause() + ")")) {
			pstmtSelectToDelete.setInt(1, height);
			final List<DbKey> dbKeys = new ArrayList<>();
			try (ResultSet rs = pstmtSelectToDelete.executeQuery()) {
				while (rs.next()) {
					dbKeys.add(dbKeyFactory.newKey(rs));
				}
			}
			/*
			 * if (dbKeys.size() > 0 && Logger.isDebugEnabled()) {
			 * Logger.logDebugMessage(String.
			 * format("rollback table %s found %d records to update to latest",
			 * table, dbKeys.size())); }
			 */
			pstmtDelete.setInt(1, height);
			pstmtDelete.executeUpdate();
			/*
			 * if (deletedRecordsCount > 0 && Logger.isDebugEnabled()) {
			 * Logger.logDebugMessage(String.
			 * format("rollback table %s deleting %d records", table,
			 * deletedRecordsCount)); }
			 */
			for (final DbKey dbKey : dbKeys) {
				int i = 1;
				i = dbKey.setPK(pstmtSetLatest, i);
				i = dbKey.setPK(pstmtSetLatest, i);
				pstmtSetLatest.executeUpdate();
				// Db.getCache(table).remove(dbKey);
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static void trim(final TransactionalDb db, final String table, final int height,
			final DbKey.Factory<?> dbKeyFactory) {
		if (!db.isInTransaction()) {
			throw new IllegalStateException("Not in transaction");
		}
		try (Connection con = db.getConnection();
				PreparedStatement pstmtSelect = con.prepareStatement("SELECT " + dbKeyFactory.getPKColumns()
						+ ", MAX(height) AS max_height" + " FROM " + table + " WHERE height < ? GROUP BY "
						+ dbKeyFactory.getPKColumns() + " HAVING COUNT(DISTINCT height) > 1");
				PreparedStatement pstmtDelete = con.prepareStatement(
						"DELETE FROM " + table + dbKeyFactory.getPKClause() + " AND height < ? AND height >= 0");
				PreparedStatement pstmtDeleteDeleted = con.prepareStatement(
						"DELETE FROM " + table + " WHERE height < ? AND height >= 0 AND latest = FALSE " + " AND ("
								+ dbKeyFactory.getPKColumns() + ") NOT IN (SELECT (" + dbKeyFactory.getPKColumns()
								+ ") FROM " + table + " WHERE height >= ?)")) {
			pstmtSelect.setInt(1, height);
			try (ResultSet rs = pstmtSelect.executeQuery()) {
				while (rs.next()) {
					final DbKey dbKey = dbKeyFactory.newKey(rs);
					final int maxHeight = rs.getInt("max_height");
					int i = 1;
					i = dbKey.setPK(pstmtDelete, i);
					pstmtDelete.setInt(i, maxHeight);
					pstmtDelete.executeUpdate();
				}
				pstmtDeleteDeleted.setInt(1, height);
				pstmtDeleteDeleted.setInt(2, height);
				pstmtDeleteDeleted.executeUpdate();
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	protected VersionedEntityDbTable(final String table, final DbKey.Factory<T> dbKeyFactory) {
		super(table, dbKeyFactory, true, null);
	}

	protected VersionedEntityDbTable(final String table, final DbKey.Factory<T> dbKeyFactory,
			final String fullTextSearchColumns) {
		super(table, dbKeyFactory, true, fullTextSearchColumns);
	}

	public final boolean delete(final T t) {
		return this.delete(t, false);
	}

	public final boolean delete(final T t, final boolean keepInCache) {
		if (t == null) {
			return false;
		}
		if (!DerivedDbTable.db.isInTransaction()) {
			throw new IllegalStateException("Not in transaction");
		}
		final DbKey dbKey = this.dbKeyFactory.newKey(t);
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmtCount = con.prepareStatement(
						"SELECT 1 FROM " + this.table + this.dbKeyFactory.getPKClause() + " AND height < ? LIMIT 1")) {
			final int i = dbKey.setPK(pstmtCount);
			pstmtCount.setInt(i, Nxt.getBlockchain().getHeight());
			try (ResultSet rs = pstmtCount.executeQuery()) {
				if (rs.next()) {
					try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + this.table + " SET latest = FALSE "
							+ this.dbKeyFactory.getPKClause() + " AND latest = TRUE LIMIT 1")) {
						dbKey.setPK(pstmt);
						pstmt.executeUpdate();
						this.save(con, t);
						pstmt.executeUpdate(); // delete after the save
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
			if (!keepInCache) {
				DerivedDbTable.db.getCache(this.table).remove(dbKey);
			}
		}
	}

}

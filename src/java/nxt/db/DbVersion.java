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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import nxt.util.Logger;

public abstract class DbVersion {

	protected BasicDb db;

	protected void apply(final String sql) {
		Connection con = null;
		Statement stmt = null;
		try {
			con = this.db.getConnection();
			stmt = con.createStatement();
			try {
				if (sql != null) {
					Logger.logDebugMessage("Will apply sql:\n" + sql);
					stmt.executeUpdate(sql);
				}
				stmt.executeUpdate("UPDATE version SET next_update = next_update + 1");
				con.commit();
			} catch (final Exception e) {
				DbUtils.rollback(con);
				throw e;
			}
		} catch (final SQLException e) {
			throw new RuntimeException("Database error executing " + sql, e);
		} finally {
			DbUtils.close(stmt, con);
		}
	}

	void init(final BasicDb db) {
		this.db = db;
		Connection con = null;
		Statement stmt = null;
		try {
			con = db.getConnection();
			stmt = con.createStatement();
			int nextUpdate = 1;
			try {
				final ResultSet rs = stmt.executeQuery("SELECT next_update FROM version");
				if (! rs.next()) {
					throw new RuntimeException("Invalid version table");
				}
				nextUpdate = rs.getInt("next_update");
				if (! rs.isLast()) {
					throw new RuntimeException("Invalid version table");
				}
				rs.close();
				Logger.logMessage("Database update may take a while if needed, current db version " + (nextUpdate - 1) + "...");
			} catch (final SQLException e) {
				Logger.logMessage("Initializing an empty database");
				stmt.executeUpdate("CREATE TABLE version (next_update INT NOT NULL)");
				stmt.executeUpdate("INSERT INTO version VALUES (1)");
				con.commit();
			}
			this.update(nextUpdate);
		} catch (final SQLException e) {
			DbUtils.rollback(con);
			throw new RuntimeException(e.toString(), e);
		} finally {
			DbUtils.close(stmt, con);
		}

	}

	protected abstract void update(int nextUpdate);

}

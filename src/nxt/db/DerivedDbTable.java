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
import java.sql.SQLException;
import java.sql.Statement;

import nxt.Db;
import nxt.Nxt;

public abstract class DerivedDbTable {

	protected static final TransactionalDb db = Db.db;

	protected final String table;

	protected DerivedDbTable(final String table) {
		this.table = table;
		Nxt.getBlockchainProcessor().registerDerivedTable(this);
	}

	public void createSearchIndex(final Connection con) throws SQLException {
		// implemented in EntityDbTable only
	}

	public boolean isPersistent() {
		return false;
	}

	public void rollback(final int height) {
		if (!DerivedDbTable.db.isInTransaction()) {
			throw new IllegalStateException("Not in transaction");
		}
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmtDelete = con
						.prepareStatement("DELETE FROM " + this.table + " WHERE height > ?")) {
			pstmtDelete.setInt(1, height);
			pstmtDelete.executeUpdate();
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public final String toString() {
		return this.table;
	}

	public void trim(final int height) {
		// nothing to trim
	}

	public void truncate() {
		if (!DerivedDbTable.db.isInTransaction()) {
			throw new IllegalStateException("Not in transaction");
		}
		try (Connection con = DerivedDbTable.db.getConnection(); Statement stmt = con.createStatement()) {
			stmt.executeUpdate("TRUNCATE TABLE " + this.table);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

}

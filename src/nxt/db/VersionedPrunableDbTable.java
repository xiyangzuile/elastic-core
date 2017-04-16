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

public abstract class VersionedPrunableDbTable<T> extends PrunableDbTable<T> {

	VersionedPrunableDbTable(final String table, final DbKey.Factory<T> dbKeyFactory) {
		super(table, dbKeyFactory, true, null);
	}

	VersionedPrunableDbTable(final String table, final DbKey.Factory<T> dbKeyFactory,
                             final String fullTextSearchColumns) {
		super(table, dbKeyFactory, true, fullTextSearchColumns);
	}

	public final boolean delete(final T t) {
		throw new UnsupportedOperationException("Versioned prunable tables cannot support delete");
	}

	@Override
	public final void rollback(final int height) {
		if (!DerivedDbTable.db.isInTransaction()) throw new IllegalStateException("Not in transaction");
		try (Connection con = DerivedDbTable.db.getConnection();
				PreparedStatement pstmtSetLatest = con.prepareStatement(
						"UPDATE " + this.table + " AS a SET a.latest = TRUE WHERE a.latest = FALSE AND a.height = "
								+ " (SELECT MAX(height) FROM " + this.table + " AS b WHERE "
								+ this.dbKeyFactory.getSelfJoinClause() + ")")) {
			pstmtSetLatest.executeUpdate();
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

}

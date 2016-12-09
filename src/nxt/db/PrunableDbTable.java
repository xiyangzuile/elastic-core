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
import java.sql.SQLException;

import nxt.Constants;
import nxt.Nxt;
import nxt.util.Logger;

public abstract class PrunableDbTable<T> extends PersistentDbTable<T> {

	protected PrunableDbTable(final String table, final DbKey.Factory<T> dbKeyFactory) {
		super(table, dbKeyFactory);
	}

	PrunableDbTable(final String table, final DbKey.Factory<T> dbKeyFactory, final boolean multiversion,
			final String fullTextSearchColumns) {
		super(table, dbKeyFactory, multiversion, fullTextSearchColumns);
	}

	protected PrunableDbTable(final String table, final DbKey.Factory<T> dbKeyFactory,
			final String fullTextSearchColumns) {
		super(table, dbKeyFactory, fullTextSearchColumns);
	}

	protected void prune() {
		if (Constants.ENABLE_PRUNING) {
			try (Connection con = DerivedDbTable.db.getConnection();
					PreparedStatement pstmt = con
							.prepareStatement("DELETE FROM " + this.table + " WHERE transaction_timestamp < ?")) {
				pstmt.setInt(1, Nxt.getEpochTime() - Constants.MAX_PRUNABLE_LIFETIME);
				final int deleted = pstmt.executeUpdate();
				if (deleted > 0) {
					Logger.logDebugMessage("Deleted " + deleted + " expired prunable data from " + this.table);
				}
			} catch (final SQLException e) {
				throw new RuntimeException(e.toString(), e);
			}
		}
	}

	@Override
	public final void trim(final int height) {
		this.prune();
		super.trim(height);
	}

}

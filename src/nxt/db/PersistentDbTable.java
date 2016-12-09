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

public abstract class PersistentDbTable<T> extends EntityDbTable<T> {

	protected PersistentDbTable(final String table, final DbKey.Factory<T> dbKeyFactory) {
		super(table, dbKeyFactory, false, null);
	}

	PersistentDbTable(final String table, final DbKey.Factory<T> dbKeyFactory, final boolean multiversion,
			final String fullTextSearchColumns) {
		super(table, dbKeyFactory, multiversion, fullTextSearchColumns);
	}

	protected PersistentDbTable(final String table, final DbKey.Factory<T> dbKeyFactory,
			final String fullTextSearchColumns) {
		super(table, dbKeyFactory, false, fullTextSearchColumns);
	}

	@Override
	public final boolean isPersistent() {
		return true;
	}

	@Override
	public void rollback(final int height) {
	}

	@Override
	public final void truncate() {
	}

}

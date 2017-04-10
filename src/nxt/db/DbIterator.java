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
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class DbIterator<T> implements Iterator<T>, Iterable<T>, AutoCloseable {

	public interface ResultSetReader<T> {
		T get(Connection con, ResultSet rs) throws Exception;
	}

	private final Connection con;
	private final PreparedStatement pstmt;
	private final ResultSetReader<T> rsReader;
	private final ResultSet rs;

	private boolean hasNext;
	private boolean iterated;

	public DbIterator(final Connection con, final PreparedStatement pstmt, final ResultSetReader<T> rsReader) {
		this.con = con;
		this.pstmt = pstmt;
		this.rsReader = rsReader;
		try {
			this.rs = pstmt.executeQuery();
			this.hasNext = this.rs.next();
		} catch (final SQLException e) {
			DbUtils.close(pstmt, con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public void close() {
		DbUtils.close(this.rs, this.pstmt, this.con);
	}

	@Override
	public boolean hasNext() {
		if (!this.hasNext) {
			DbUtils.close(this.rs, this.pstmt, this.con);
		}
		return this.hasNext;
	}

	@Override
	public Iterator<T> iterator() {
		if (this.iterated) {
			throw new IllegalStateException("Already iterated");
		}
		this.iterated = true;
		return this;
	}

	@Override
	public T next() {
		if (!this.hasNext) {
			DbUtils.close(this.rs, this.pstmt, this.con);
			throw new NoSuchElementException();
		}
		try {
			final T result = this.rsReader.get(this.con, this.rs);
			this.hasNext = this.rs.next();
			return result;
		} catch (final Exception e) {
			DbUtils.close(this.rs, this.pstmt, this.con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("Removal not supported");
	}
}

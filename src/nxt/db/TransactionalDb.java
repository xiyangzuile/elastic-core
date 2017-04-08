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
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nxt.Nxt;
import nxt.util.Logger;

public class TransactionalDb extends BasicDb {

	private final class DbConnection extends FilteredConnection {

		long txStart = 0;

		private DbConnection(final Connection con) {
			super(con, TransactionalDb.factory);
		}

		@Override
		public void close() throws SQLException {
			if (TransactionalDb.this.localConnection.get() == null) {
				super.close();
			} else if (this != TransactionalDb.this.localConnection.get()) {
				throw new IllegalStateException("Previous connection not committed");
			}
		}

		@Override
		public void commit() throws SQLException {
			if (TransactionalDb.this.localConnection.get() == null) {
				super.commit();
			} else if (this != TransactionalDb.this.localConnection.get()) {
				throw new IllegalStateException("Previous connection not committed");
			} else {
				TransactionalDb.this.commitTransaction();
			}
		}

		private void doCommit() throws SQLException {
			super.commit();
		}

		private void doRollback() throws SQLException {
			super.rollback();
		}

		@Override
		public void rollback() throws SQLException {
			if (TransactionalDb.this.localConnection.get() == null) {
				super.rollback();
			} else if (this != TransactionalDb.this.localConnection.get()) {
				throw new IllegalStateException("Previous connection not committed");
			} else {
				TransactionalDb.this.rollbackTransaction();
			}
		}

		@Override
		public void setAutoCommit(final boolean autoCommit) throws SQLException {
			throw new UnsupportedOperationException("Use Db.beginTransaction() to start a new transaction");
		}
	}

	private static final class DbFactory implements FilteredFactory {

		@Override
		public PreparedStatement createPreparedStatement(final PreparedStatement stmt, final String sql) {
			return new DbPreparedStatement(stmt, sql);
		}

		@Override
		public Statement createStatement(final Statement stmt) {
			return new DbStatement(stmt);
		}
	}

	private static final class DbPreparedStatement extends FilteredPreparedStatement {
		private DbPreparedStatement(final PreparedStatement stmt, final String sql) {
			super(stmt, sql);
		}

		@Override
		public boolean execute() throws SQLException {
			final long start = System.currentTimeMillis();
			final boolean b = super.execute();
			final long elapsed = System.currentTimeMillis() - start;
			if (elapsed > TransactionalDb.stmtThreshold) {
				TransactionalDb.logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
						elapsed / 1000.0, Nxt.getBlockchain().getHeight(), this.getSQL()));
			}
			return b;
		}

		@Override
		public ResultSet executeQuery() throws SQLException {
			final long start = System.currentTimeMillis();
			final ResultSet r = super.executeQuery();
			final long elapsed = System.currentTimeMillis() - start;
			if (elapsed > TransactionalDb.stmtThreshold) {
				TransactionalDb.logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
						elapsed / 1000.0, Nxt.getBlockchain().getHeight(), this.getSQL()));
			}
			return r;
		}

		@Override
		public int executeUpdate() throws SQLException {
			final long start = System.currentTimeMillis();
			final int c = super.executeUpdate();
			final long elapsed = System.currentTimeMillis() - start;
			if (elapsed > TransactionalDb.stmtThreshold) {
				TransactionalDb.logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
						elapsed / 1000.0, Nxt.getBlockchain().getHeight(), this.getSQL()));
			}
			return c;
		}
	}

	private static final class DbStatement extends FilteredStatement {

		private DbStatement(final Statement stmt) {
			super(stmt);
		}

		@Override
		public boolean execute(final String sql) throws SQLException {
			final long start = System.currentTimeMillis();
			final boolean b = super.execute(sql);
			final long elapsed = System.currentTimeMillis() - start;
			if (elapsed > TransactionalDb.stmtThreshold) {
				TransactionalDb.logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
						elapsed / 1000.0, Nxt.getBlockchain().getHeight(), sql));
			}
			return b;
		}

		@Override
		public ResultSet executeQuery(final String sql) throws SQLException {
			final long start = System.currentTimeMillis();
			final ResultSet r = super.executeQuery(sql);
			final long elapsed = System.currentTimeMillis() - start;
			if (elapsed > TransactionalDb.stmtThreshold) {
				TransactionalDb.logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
						elapsed / 1000.0, Nxt.getBlockchain().getHeight(), sql));
			}
			return r;
		}

		@Override
		public int executeUpdate(final String sql) throws SQLException {
			final long start = System.currentTimeMillis();
			final int c = super.executeUpdate(sql);
			final long elapsed = System.currentTimeMillis() - start;
			if (elapsed > TransactionalDb.stmtThreshold) {
				TransactionalDb.logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
						elapsed / 1000.0, Nxt.getBlockchain().getHeight(), sql));
			}
			return c;
		}
	}

	/**
	 * Transaction callback interface
	 */
	public interface TransactionCallback {

		/**
		 * Transaction has been committed
		 */
		void commit();

		/**
		 * Transaction has been rolled back
		 */
		void rollback();
	}

	private static final DbFactory factory = new DbFactory();
	private static final long stmtThreshold;
	private static final long txThreshold;
	private static final long txInterval;
	static {
		long temp;
		stmtThreshold = (temp = Nxt.getIntProperty("nxt.statementLogThreshold")) != 0 ? temp : 1000;
		txThreshold = (temp = Nxt.getIntProperty("nxt.transactionLogThreshold")) != 0 ? temp : 5000;
		txInterval = (temp = Nxt.getIntProperty("nxt.transactionLogInterval")) != 0 ? temp * 60 * 1000 : 15 * 60 * 1000;
	}

	private static void logThreshold(final String msg) {
		final StringBuilder sb = new StringBuilder(512);
		sb.append(msg).append('\n');
		final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		boolean firstLine = true;
		for (int i = 3; i < stackTrace.length; i++) {
			final String line = stackTrace[i].toString();
			if (!line.startsWith("nxt.")) {
				break;
			}
			if (firstLine) {
				firstLine = false;
			} else {
				sb.append('\n');
			}
			sb.append("  ").append(line);
		}
		Logger.logDebugMessage(sb.toString());
	}

	private final ThreadLocal<DbConnection> localConnection = new ThreadLocal<>();

	private final ThreadLocal<Map<String, Map<DbKey, Object>>> transactionCaches = new ThreadLocal<>();

	private final ThreadLocal<Set<TransactionCallback>> transactionCallback = new ThreadLocal<>();

	private volatile long txTimes = 0;

	private volatile long txCount = 0;

	private volatile long statsTime = 0;

	public TransactionalDb(final DbProperties dbProperties) {
		super(dbProperties);
	}

	public Connection beginTransaction() {
		if (this.localConnection.get() != null) {
			throw new IllegalStateException("Transaction already in progress");
		}
		try {
			Connection con = this.getPooledConnection();
			con.setAutoCommit(false);
			con = new DbConnection(con);
			((DbConnection) con).txStart = System.currentTimeMillis();
			this.localConnection.set((DbConnection) con);
			this.transactionCaches.set(new HashMap<>());
			return con;
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public void clearCache() {
		this.transactionCaches.get().values().forEach(Map::clear);
	}

	void clearCache(final String tableName) {
		final Map<DbKey, Object> cacheMap = this.transactionCaches.get().get(tableName);
		if (cacheMap != null) {
			cacheMap.clear();
		}
	}

	public void commitTransaction() {
		final DbConnection con = this.localConnection.get();
		if (con == null) {
			throw new IllegalStateException("Not in transaction");
		}
		try {
			con.doCommit();
			final Set<TransactionCallback> callbacks = this.transactionCallback.get();
			if (callbacks != null) {
				callbacks.forEach(TransactionCallback::commit);
				this.transactionCallback.set(null);
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public void endTransaction() {
		final Connection con = this.localConnection.get();
		if (con == null) {
			throw new IllegalStateException("Not in transaction");
		}
		this.localConnection.set(null);
		this.transactionCaches.set(null);
		final long now = System.currentTimeMillis();
		final long elapsed = now - ((DbConnection) con).txStart;
		if (elapsed >= TransactionalDb.txThreshold) {
			TransactionalDb.logThreshold(String.format("Database transaction required %.3f seconds at height %d",
					elapsed / 1000.0, Nxt.getBlockchain().getHeight()));
		} else {
			long count, times;
			boolean logStats = false;
			synchronized (this) {
				count = ++this.txCount;
				times = this.txTimes += elapsed;
				if ((now - this.statsTime) >= TransactionalDb.txInterval) {
					logStats = true;
					this.txCount = 0;
					this.txTimes = 0;
					this.statsTime = now;
				}
			}
			if (logStats) {
				Logger.logDebugMessage(
						String.format("Average database transaction time is %.3f seconds", times / 1000.0 / count));
			}
		}
		DbUtils.close(con);
	}

	Map<DbKey, Object> getCache(final String tableName) {
		if (!this.isInTransaction()) {
			throw new IllegalStateException("Not in transaction");
		}
		Map<DbKey, Object> cacheMap = this.transactionCaches.get().get(tableName);
		if (cacheMap == null) {
			cacheMap = new HashMap<>();
			this.transactionCaches.get().put(tableName, cacheMap);
		}
		return cacheMap;
	}

	@Override
	public Connection getConnection() throws SQLException {
		final Connection con = this.localConnection.get();
		if (con != null) {
			return con;
		}
		return new DbConnection(super.getConnection());
	}

	public boolean isInTransaction() {
		return this.localConnection.get() != null;
	}

	public void registerCallback(final TransactionCallback callback) {
		Set<TransactionCallback> callbacks = this.transactionCallback.get();
		if (callbacks == null) {
			callbacks = new HashSet<>();
			this.transactionCallback.set(callbacks);
		}
		callbacks.add(callback);
	}

	public void rollbackTransaction() {
		final DbConnection con = this.localConnection.get();
		if (con == null) {
			throw new IllegalStateException("Not in transaction");
		}
		try {
			con.doRollback();
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		} finally {
			this.transactionCaches.get().clear();
			final Set<TransactionCallback> callbacks = this.transactionCallback.get();
			if (callbacks != null) {
				callbacks.forEach(TransactionCallback::rollback);
				this.transactionCallback.set(null);
			}
		}
	}
}

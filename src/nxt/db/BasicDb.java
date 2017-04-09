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
import java.sql.SQLException;
import java.sql.Statement;

import org.h2.jdbcx.JdbcConnectionPool;

import nxt.Nxt;
import nxt.util.Logger;

public class BasicDb {

	public static final class DbProperties {

		private long maxCacheSize;
		private String dbUrl;
		private String dbType;
		private String dbDir;
		private String dbParams;
		private String dbUsername;
		private String dbPassword;
		private int maxConnections;
		private int loginTimeout;
		private int defaultLockTimeout;
		private int maxMemoryRows;

		public DbProperties dbDir(final String dbDir) {
			this.dbDir = dbDir;
			return this;
		}

		public DbProperties dbParams(final String dbParams) {
			this.dbParams = dbParams;
			return this;
		}

		public DbProperties dbPassword(final String dbPassword) {
			this.dbPassword = dbPassword;
			return this;
		}

		public DbProperties dbType(final String dbType) {
			this.dbType = dbType;
			return this;
		}

		public DbProperties dbUrl(final String dbUrl) {
			this.dbUrl = dbUrl;
			return this;
		}

		public DbProperties dbUsername(final String dbUsername) {
			this.dbUsername = dbUsername;
			return this;
		}

		public DbProperties defaultLockTimeout(final int defaultLockTimeout) {
			this.defaultLockTimeout = defaultLockTimeout;
			return this;
		}

		public DbProperties loginTimeout(final int loginTimeout) {
			this.loginTimeout = loginTimeout;
			return this;
		}

		public DbProperties maxCacheSize(final int maxCacheSize) {
			this.maxCacheSize = maxCacheSize;
			return this;
		}

		public DbProperties maxConnections(final int maxConnections) {
			this.maxConnections = maxConnections;
			return this;
		}

		public DbProperties maxMemoryRows(final int maxMemoryRows) {
			this.maxMemoryRows = maxMemoryRows;
			return this;
		}

	}

	private JdbcConnectionPool cp;
	private volatile int maxActiveConnections;
	private final String dbUrl;
	private final String dbUsername;
	private final String dbPassword;
	private final int maxConnections;
	private final int loginTimeout;
	private final int defaultLockTimeout;
	private final int maxMemoryRows;
	private volatile boolean initialized = false;

	public BasicDb(final DbProperties dbProperties) {
		long maxCacheSize = dbProperties.maxCacheSize;
		if (maxCacheSize == 0) {
			maxCacheSize = Math.min(256, Math.max(16, ((Runtime.getRuntime().maxMemory() / (1024 * 1024)) - 128) / 2))
					* 1024;
		}
		String dbUrl = dbProperties.dbUrl;
		if (dbUrl == null) {
			final String dbDir = Nxt.getDbDir(dbProperties.dbDir);
			dbUrl = String.format("jdbc:%s:%s;%s", dbProperties.dbType, dbDir, dbProperties.dbParams);
		}
		if (!dbUrl.contains("MV_STORE=")) {
			dbUrl += ";MV_STORE=FALSE";
		}
		if (!dbUrl.contains("CACHE_SIZE=")) {
			dbUrl += ";CACHE_SIZE=" + maxCacheSize;
		}
		this.dbUrl = dbUrl;
		this.dbUsername = dbProperties.dbUsername;
		this.dbPassword = dbProperties.dbPassword;
		this.maxConnections = dbProperties.maxConnections;
		this.loginTimeout = dbProperties.loginTimeout;
		this.defaultLockTimeout = dbProperties.defaultLockTimeout;
		this.maxMemoryRows = dbProperties.maxMemoryRows;
	}

	public void analyzeTables() {
		try (Connection con = this.cp.getConnection(); Statement stmt = con.createStatement()) {
			stmt.execute("ANALYZE SAMPLE_SIZE 0");
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public Connection getConnection() throws SQLException {
		final Connection con = this.getPooledConnection();
		con.setAutoCommit(true);
		return con;
	}

	protected Connection getPooledConnection() throws SQLException {
		final Connection con = this.cp.getConnection();
		final int activeConnections = this.cp.getActiveConnections();
		if (activeConnections > this.maxActiveConnections) {
			this.maxActiveConnections = activeConnections;
			Logger.logDebugMessage("Database connection pool current size: " + activeConnections);
		}
		return con;
	}

	public String getUrl() {
		return this.dbUrl;
	}

	public void init(final DbVersion dbVersion) {
		Logger.logInfoMessage("Database jdbc url set to %s username %s", this.dbUrl, this.dbUsername);
		this.cp = JdbcConnectionPool.create(this.dbUrl, this.dbUsername, this.dbPassword);
		this.cp.setMaxConnections(this.maxConnections);
		this.cp.setLoginTimeout(this.loginTimeout);
		try (Connection con = this.cp.getConnection(); Statement stmt = con.createStatement()) {
			stmt.executeUpdate("SET DEFAULT_LOCK_TIMEOUT " + this.defaultLockTimeout);
			stmt.executeUpdate("SET MAX_MEMORY_ROWS " + this.maxMemoryRows);
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		dbVersion.init(this);
		this.initialized = true;
	}

	public void shutdown() {
		if (!this.initialized) {
			return;
		}
		try (
			final Connection con = this.cp.getConnection();
			final Statement stmt = con.createStatement();){
				stmt.execute("SHUTDOWN COMPACT");
				Logger.logShutdownMessage("Database shutdown completed");
		} catch (final SQLException e) {
			Logger.logShutdownMessage(e.toString(), e);
		}
	}

}

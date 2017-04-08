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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Wrapper for a SQL Connection
 *
 * The wrapper forwards all methods to the wrapped connection. The supplied
 * factory is used to create wrappers for statements and prepared statements
 * that are created for this connection.
 */
public class FilteredConnection implements Connection {

	private final Connection con;
	private final FilteredFactory factory;

	public FilteredConnection(final Connection con, final FilteredFactory factory) {
		this.con = con;
		this.factory = factory;
	}

	@Override
	public void abort(final Executor executor) throws SQLException {
		this.con.abort(executor);
	}

	@Override
	public void clearWarnings() throws SQLException {
		this.con.clearWarnings();
	}

	@Override
	public void close() throws SQLException {
		this.con.close();
	}

	@Override
	public void commit() throws SQLException {
		this.con.commit();
	}

	@Override
	public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
		return this.con.createArrayOf(typeName, elements);
	}

	@Override
	public Blob createBlob() throws SQLException {
		return this.con.createBlob();
	}

	@Override
	public Clob createClob() throws SQLException {
		return this.con.createClob();
	}

	@Override
	public NClob createNClob() throws SQLException {
		return this.con.createNClob();
	}

	@Override
	public SQLXML createSQLXML() throws SQLException {
		return this.con.createSQLXML();
	}

	@Override
	public Statement createStatement() throws SQLException {
		return this.factory.createStatement(this.con.createStatement());
	}

	@Override
	public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
		return this.factory.createStatement(this.con.createStatement(resultSetType, resultSetConcurrency));
	}

	@Override
	public Statement createStatement(final int resultSetType, final int resultSetConcurrency,
			final int resultSetHoldability) throws SQLException {
		return this.factory
				.createStatement(this.con.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
	}

	@Override
	public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
		return this.con.createStruct(typeName, attributes);
	}

	@Override
	public boolean getAutoCommit() throws SQLException {
		return this.con.getAutoCommit();
	}

	@Override
	public String getCatalog() throws SQLException {
		return this.con.getCatalog();
	}

	@Override
	public Properties getClientInfo() throws SQLException {
		return this.con.getClientInfo();
	}

	@Override
	public String getClientInfo(final String name) throws SQLException {
		return this.con.getClientInfo(name);
	}

	@Override
	public int getHoldability() throws SQLException {
		return this.con.getHoldability();
	}

	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		return this.con.getMetaData();
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		return this.con.getNetworkTimeout();
	}

	@Override
	public String getSchema() throws SQLException {
		return this.con.getSchema();
	}

	@Override
	public int getTransactionIsolation() throws SQLException {
		return this.con.getTransactionIsolation();
	}

	@Override
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return this.con.getTypeMap();
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		return this.con.getWarnings();
	}

	@Override
	public boolean isClosed() throws SQLException {
		return this.con.isClosed();
	}

	@Override
	public boolean isReadOnly() throws SQLException {
		return this.con.isReadOnly();
	}

	@Override
	public boolean isValid(final int timeout) throws SQLException {
		return this.con.isValid(timeout);
	}

	@Override
	public boolean isWrapperFor(final Class<?> iface) throws SQLException {
		return this.con.isWrapperFor(iface);
	}

	@Override
	public String nativeSQL(final String sql) throws SQLException {
		return this.con.nativeSQL(sql);
	}

	@Override
	public CallableStatement prepareCall(final String sql) throws SQLException {
		return this.con.prepareCall(sql);
	}

	@Override
	public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency)
			throws SQLException {
		return this.con.prepareCall(sql, resultSetType, resultSetConcurrency);
	}

	@Override
	public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency,
			final int resultSetHoldability) throws SQLException {
		return this.con.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public PreparedStatement prepareStatement(final String sql) throws SQLException {
		return this.factory.createPreparedStatement(this.con.prepareStatement(sql), sql);
	}

	@Override
	public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
		return this.factory.createPreparedStatement(this.con.prepareStatement(sql, autoGeneratedKeys), sql);
	}

	@Override
	public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency)
			throws SQLException {
		return this.factory.createPreparedStatement(this.con.prepareStatement(sql, resultSetType, resultSetConcurrency),
				sql);
	}

	@Override
	public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency,
			final int resultSetHoldability) throws SQLException {
		return this.factory.createPreparedStatement(
				this.con.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql);
	}

	@Override
	public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
		return this.factory.createPreparedStatement(this.con.prepareStatement(sql, columnIndexes), sql);
	}

	@Override
	public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
		return this.factory.createPreparedStatement(this.con.prepareStatement(sql, columnNames), sql);
	}

	@Override
	public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
		this.con.releaseSavepoint(savepoint);
	}

	@Override
	public void rollback() throws SQLException {
		this.con.rollback();
	}

	@Override
	public void rollback(final Savepoint savepoint) throws SQLException {
		this.con.rollback(savepoint);
	}

	@Override
	public void setAutoCommit(final boolean autoCommit) throws SQLException {
		this.con.setAutoCommit(autoCommit);
	}

	@Override
	public void setCatalog(final String catalog) throws SQLException {
		this.con.setCatalog(catalog);
	}

	@Override
	public void setClientInfo(final Properties properties) throws SQLClientInfoException {
		this.con.setClientInfo(properties);
	}

	@Override
	public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
		this.con.setClientInfo(name, value);
	}

	@Override
	public void setHoldability(final int holdability) throws SQLException {
		this.con.setHoldability(holdability);
	}

	@Override
	public void setNetworkTimeout(final Executor executor, final int milliseconds) throws SQLException {
		this.con.setNetworkTimeout(executor, milliseconds);
	}

	@Override
	public void setReadOnly(final boolean readOnly) throws SQLException {
		this.con.setReadOnly(readOnly);
	}

	@Override
	public Savepoint setSavepoint() throws SQLException {
		return this.con.setSavepoint();
	}

	@Override
	public Savepoint setSavepoint(final String name) throws SQLException {
		return this.con.setSavepoint(name);
	}

	@Override
	public void setSchema(final String schema) throws SQLException {
		this.con.setSchema(schema);
	}

	@Override
	public void setTransactionIsolation(final int level) throws SQLException {
		this.con.setTransactionIsolation(level);
	}

	@Override
	public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
		this.con.setTypeMap(map);
	}

	@Override
	public <T> T unwrap(final Class<T> iface) throws SQLException {
		return this.con.unwrap(iface);
	}
}

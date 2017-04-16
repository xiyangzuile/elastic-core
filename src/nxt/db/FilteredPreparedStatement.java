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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.util.Calendar;

/**
 * Wrapper for a SQL PreparedStatement
 *
 * The wrapper forwards all methods to the wrapped prepared statement
 */
class FilteredPreparedStatement extends FilteredStatement implements PreparedStatement {

	private final PreparedStatement stmt;
	private final String sql;

	public FilteredPreparedStatement(final PreparedStatement stmt, final String sql) {
		super(stmt);
		this.stmt = stmt;
		this.sql = sql;
	}

	@Override
	public void addBatch() throws SQLException {
		this.stmt.addBatch();
	}

	@Override
	public void clearParameters() throws SQLException {
		this.stmt.clearParameters();
	}

	@Override
	public boolean execute() throws SQLException {
		return this.stmt.execute();
	}

	@Override
	public long executeLargeUpdate() throws SQLException {
		return this.stmt.executeLargeUpdate();
	}

	@Override
	public ResultSet executeQuery() throws SQLException {
		return this.stmt.executeQuery();
	}

	@Override
	public int executeUpdate() throws SQLException {
		return this.stmt.executeUpdate();
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return this.stmt.getMetaData();
	}

	@Override
	public ParameterMetaData getParameterMetaData() throws SQLException {
		return this.stmt.getParameterMetaData();
	}

	public String getSQL() {
		return this.sql;
	}

	@Override
	public void setArray(final int parameterIndex, final Array x) throws SQLException {
		this.stmt.setArray(parameterIndex, x);
	}

	@Override
	public void setAsciiStream(final int parameterIndex, final java.io.InputStream x) throws SQLException {
		this.stmt.setAsciiStream(parameterIndex, x);
	}

	@Override
	public void setAsciiStream(final int parameterIndex, final java.io.InputStream x, final int length)
			throws SQLException {
		this.stmt.setAsciiStream(parameterIndex, x, length);
	}

	@Override
	public void setAsciiStream(final int parameterIndex, final java.io.InputStream x, final long length)
			throws SQLException {
		this.stmt.setAsciiStream(parameterIndex, x, length);
	}

	@Override
	public void setBigDecimal(final int parameterIndex, final BigDecimal x) throws SQLException {
		this.stmt.setBigDecimal(parameterIndex, x);
	}

	@Override
	public void setBinaryStream(final int parameterIndex, final java.io.InputStream x) throws SQLException {
		this.stmt.setBinaryStream(parameterIndex, x);
	}

	@Override
	public void setBinaryStream(final int parameterIndex, final java.io.InputStream x, final int length)
			throws SQLException {
		this.stmt.setBinaryStream(parameterIndex, x, length);
	}

	@Override
	public void setBinaryStream(final int parameterIndex, final java.io.InputStream x, final long length)
			throws SQLException {
		this.stmt.setBinaryStream(parameterIndex, x, length);
	}

	@Override
	public void setBlob(final int parameterIndex, final Blob x) throws SQLException {
		this.stmt.setBlob(parameterIndex, x);
	}

	@Override
	public void setBlob(final int parameterIndex, final InputStream inputStream) throws SQLException {
		this.stmt.setBlob(parameterIndex, inputStream);
	}

	@Override
	public void setBlob(final int parameterIndex, final InputStream inputStream, final long length)
			throws SQLException {
		this.stmt.setBlob(parameterIndex, inputStream, length);
	}

	@Override
	public void setBoolean(final int parameterIndex, final boolean x) throws SQLException {
		this.stmt.setBoolean(parameterIndex, x);
	}

	@Override
	public void setByte(final int parameterIndex, final byte x) throws SQLException {
		this.stmt.setByte(parameterIndex, x);
	}

	@Override
	public void setBytes(final int parameterIndex, final byte[] x) throws SQLException {
		this.stmt.setBytes(parameterIndex, x);
	}

	@Override
	public void setCharacterStream(final int parameterIndex, final java.io.Reader reader) throws SQLException {
		this.stmt.setCharacterStream(parameterIndex, reader);
	}

	@Override
	public void setCharacterStream(final int parameterIndex, final java.io.Reader reader, final int length)
			throws SQLException {
		this.stmt.setCharacterStream(parameterIndex, reader, length);
	}

	@Override
	public void setCharacterStream(final int parameterIndex, final java.io.Reader reader, final long length)
			throws SQLException {
		this.stmt.setCharacterStream(parameterIndex, reader, length);
	}

	@Override
	public void setClob(final int parameterIndex, final Clob x) throws SQLException {
		this.stmt.setClob(parameterIndex, x);
	}

	@Override
	public void setClob(final int parameterIndex, final Reader reader) throws SQLException {
		this.stmt.setClob(parameterIndex, reader);
	}

	@Override
	public void setClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
		this.stmt.setClob(parameterIndex, reader, length);
	}

	@Override
	public void setDate(final int parameterIndex, final java.sql.Date x) throws SQLException {
		this.stmt.setDate(parameterIndex, x);
	}

	@Override
	public void setDate(final int parameterIndex, final java.sql.Date x, final Calendar cal) throws SQLException {
		this.stmt.setDate(parameterIndex, x, cal);
	}

	@Override
	public void setDouble(final int parameterIndex, final double x) throws SQLException {
		this.stmt.setDouble(parameterIndex, x);
	}

	@Override
	public void setFloat(final int parameterIndex, final float x) throws SQLException {
		this.stmt.setFloat(parameterIndex, x);
	}

	@Override
	public void setInt(final int parameterIndex, final int x) throws SQLException {
		this.stmt.setInt(parameterIndex, x);
	}

	@Override
	public void setLong(final int parameterIndex, final long x) throws SQLException {
		this.stmt.setLong(parameterIndex, x);
	}

	@Override
	public void setNCharacterStream(final int parameterIndex, final Reader value) throws SQLException {
		this.stmt.setNCharacterStream(parameterIndex, value);
	}

	@Override
	public void setNCharacterStream(final int parameterIndex, final Reader value, final long length)
			throws SQLException {
		this.stmt.setNCharacterStream(parameterIndex, value, length);
	}

	@Override
	public void setNClob(final int parameterIndex, final NClob value) throws SQLException {
		this.stmt.setNClob(parameterIndex, value);
	}

	@Override
	public void setNClob(final int parameterIndex, final Reader reader) throws SQLException {
		this.stmt.setNClob(parameterIndex, reader);
	}

	@Override
	public void setNClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
		this.stmt.setNClob(parameterIndex, reader, length);
	}

	@Override
	public void setNString(final int parameterIndex, final String value) throws SQLException {
		this.stmt.setNString(parameterIndex, value);
	}

	@Override
	public void setNull(final int parameterIndex, final int sqlType) throws SQLException {
		this.stmt.setNull(parameterIndex, sqlType);
	}

	@Override
	public void setNull(final int parameterIndex, final int sqlType, final String typeName) throws SQLException {
		this.stmt.setNull(parameterIndex, sqlType, typeName);
	}

	@Override
	public void setObject(final int parameterIndex, final Object x) throws SQLException {
		this.stmt.setObject(parameterIndex, x);
	}

	@Override
	public void setObject(final int parameterIndex, final Object x, final int targetSqlType) throws SQLException {
		this.stmt.setObject(parameterIndex, x, targetSqlType);
	}

	@Override
	public void setObject(final int parameterIndex, final Object x, final int targetSqlType, final int scaleOrLength)
			throws SQLException {
		this.stmt.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
	}

	@Override
	public void setObject(final int parameterIndex, final Object x, final SQLType targetSqlType) throws SQLException {
		this.stmt.setObject(parameterIndex, x, targetSqlType);
	}

	@Override
	public void setObject(final int parameterIndex, final Object x, final SQLType targetSqlType,
			final int scaleOrLength) throws SQLException {
		this.stmt.setObject(parameterIndex, x, targetSqlType);
	}

	@Override
	public void setRef(final int parameterIndex, final Ref x) throws SQLException {
		this.stmt.setRef(parameterIndex, x);
	}

	@Override
	public void setRowId(final int parameterIndex, final RowId x) throws SQLException {
		this.stmt.setRowId(parameterIndex, x);
	}

	@Override
	public void setShort(final int parameterIndex, final short x) throws SQLException {
		this.stmt.setShort(parameterIndex, x);
	}

	@Override
	public void setSQLXML(final int parameterIndex, final SQLXML xmlObject) throws SQLException {
		this.stmt.setSQLXML(parameterIndex, xmlObject);
	}

	@Override
	public void setString(final int parameterIndex, final String x) throws SQLException {
		this.stmt.setString(parameterIndex, x);
	}

	@Override
	public void setTime(final int parameterIndex, final java.sql.Time x) throws SQLException {
		this.stmt.setTime(parameterIndex, x);
	}

	@Override
	public void setTime(final int parameterIndex, final java.sql.Time x, final Calendar cal) throws SQLException {
		this.stmt.setTime(parameterIndex, x, cal);
	}

	@Override
	public void setTimestamp(final int parameterIndex, final java.sql.Timestamp x) throws SQLException {
		this.stmt.setTimestamp(parameterIndex, x);
	}

	@Override
	public void setTimestamp(final int parameterIndex, final java.sql.Timestamp x, final Calendar cal)
			throws SQLException {
		this.stmt.setTimestamp(parameterIndex, x, cal);
	}

	@Deprecated
	@Override
	public void setUnicodeStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
		this.stmt.setUnicodeStream(parameterIndex, x, length);
	}

	@Override
	public void setURL(final int parameterIndex, final java.net.URL x) throws SQLException {
		this.stmt.setURL(parameterIndex, x);
	}
}

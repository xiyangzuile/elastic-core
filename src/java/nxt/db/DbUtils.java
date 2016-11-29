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

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

import nxt.util.Logger;

public final class DbUtils {

	public static void close(final AutoCloseable... closeables) {
		for (final AutoCloseable closeable : closeables) {
			if (closeable != null) {
				try {
					closeable.close();
				} catch (final Exception ignore) {}
			}
		}
	}

	public static <T> T[] getArray(final ResultSet rs, final String columnName, final Class<? extends T[]> cls) throws SQLException {
		return DbUtils.getArray(rs, columnName, cls, null);
	}

	public static <T> T[] getArray(final ResultSet rs, final String columnName, final Class<? extends T[]> cls, final T[] ifNull) throws SQLException {
		final Array array = rs.getArray(columnName);
		if (array != null) {
			final Object[] objects = (Object[]) array.getArray();
			return Arrays.copyOf(objects, objects.length, cls);
		} else {
			return ifNull;
		}
	}

	public static String limitsClause(final int from, final int to) {
		final int limit = (to >=0) && (to >= from) && (to < Integer.MAX_VALUE) ? (to - from) + 1 : 0;
		if ((limit > 0) && (from > 0)) {
			return " LIMIT ? OFFSET ? ";
		} else if (limit > 0) {
			return " LIMIT ? ";
		} else if (from > 0) {
			return " LIMIT NULL OFFSET ? ";
		} else {
			return "";
		}
	}

	public static void rollback(final Connection con) {
		try {
			if (con != null) {
				con.rollback();
			}
		} catch (final SQLException e) {
			Logger.logErrorMessage(e.toString(), e);
		}

	}

	public static <T> void setArray(final PreparedStatement pstmt, final int index, final T[] array) throws SQLException {
		if (array != null) {
			pstmt.setObject(index, array);
		} else {
			pstmt.setNull(index, Types.ARRAY);
		}
	}

	public static <T> void setArrayEmptyToNull(final PreparedStatement pstmt, final int index, final T[] array) throws SQLException {
		if ((array != null) && (array.length > 0)) {
			pstmt.setObject(index, array);
		} else {
			pstmt.setNull(index, Types.ARRAY);
		}
	}

	public static void setBytes(final PreparedStatement pstmt, final int index, final byte[] bytes) throws SQLException {
		if (bytes != null) {
			pstmt.setBytes(index, bytes);
		} else {
			pstmt.setNull(index, Types.BINARY);
		}
	}

	public static void setIntZeroToNull(final PreparedStatement pstmt, final int index, final int n) throws SQLException {
		if (n != 0) {
			pstmt.setInt(index, n);
		} else {
			pstmt.setNull(index, Types.INTEGER);
		}
	}

	public static int setLimits(int index, final PreparedStatement pstmt, final int from, final int to) throws SQLException {
		final int limit = (to >=0) && (to >= from) && (to < Integer.MAX_VALUE) ? (to - from) + 1 : 0;
		if (limit > 0) {
			pstmt.setInt(index++, limit);
		}
		if (from > 0) {
			pstmt.setInt(index++, from);
		}
		return index;
	}

	public static void setLong(final PreparedStatement pstmt, final int index, final Long l) throws SQLException {
		if (l != null) {
			pstmt.setLong(index, l);
		} else {
			pstmt.setNull(index, Types.BIGINT);
		}
	}

	public static void setLongZeroToNull(final PreparedStatement pstmt, final int index, final long l) throws SQLException {
		if (l != 0) {
			pstmt.setLong(index, l);
		} else {
			pstmt.setNull(index, Types.BIGINT);
		}
	}

	public static void setShortZeroToNull(final PreparedStatement pstmt, final int index, final short s) throws SQLException {
		if (s != 0) {
			pstmt.setShort(index, s);
		} else {
			pstmt.setNull(index, Types.SMALLINT);
		}
	}

	public static void setString(final PreparedStatement pstmt, final int index, final String s) throws SQLException {
		if (s != null) {
			pstmt.setString(index, s);
		} else {
			pstmt.setNull(index, Types.VARCHAR);
		}
	}

	private DbUtils() {} // never

}

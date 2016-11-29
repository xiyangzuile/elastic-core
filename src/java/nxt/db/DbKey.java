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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface DbKey {

	abstract class Factory<T> {

		private final String pkClause;
		private final String pkColumns;
		private final String selfJoinClause;

		protected Factory(final String pkClause, final String pkColumns, final String selfJoinClause) {
			this.pkClause = pkClause;
			this.pkColumns = pkColumns;
			this.selfJoinClause = selfJoinClause;
		}

		public final String getPKClause() {
			return this.pkClause;
		}

		public final String getPKColumns() {
			return this.pkColumns;
		}

		// expects tables to be named a and b
		public final String getSelfJoinClause() {
			return this.selfJoinClause;
		}

		public T newEntity(final DbKey dbKey) {
			throw new UnsupportedOperationException("Not implemented");
		}

		public abstract DbKey newKey(ResultSet rs) throws SQLException;

		public abstract DbKey newKey(T t);

	}

	final class LinkKey implements DbKey {

		private final long idA;
		private final long idB;

		private LinkKey(final long idA, final long idB) {
			this.idA = idA;
			this.idB = idB;
		}

		@Override
		public boolean equals(final Object o) {
			return (o instanceof LinkKey) && (((LinkKey) o).idA == this.idA) && (((LinkKey) o).idB == this.idB);
		}

		public long[] getId() {
			return new long[] { this.idA, this.idB };
		}

		@Override
		public int hashCode() {
			return (int) (this.idA ^ (this.idA >>> 32)) ^ (int) (this.idB ^ (this.idB >>> 32));
		}

		@Override
		public int setPK(final PreparedStatement pstmt) throws SQLException {
			return this.setPK(pstmt, 1);
		}

		@Override
		public int setPK(final PreparedStatement pstmt, final int index) throws SQLException {
			pstmt.setLong(index, this.idA);
			pstmt.setLong(index + 1, this.idB);
			return index + 2;
		}

	}

	abstract class LinkKeyFactory<T> extends Factory<T> {

		private final String idColumnA;
		private final String idColumnB;

		public LinkKeyFactory(final String idColumnA, final String idColumnB) {
			super(" WHERE " + idColumnA + " = ? AND " + idColumnB + " = ? ", idColumnA + ", " + idColumnB,
					" a." + idColumnA + " = b." + idColumnA + " AND a." + idColumnB + " = b." + idColumnB + " ");
			this.idColumnA = idColumnA;
			this.idColumnB = idColumnB;
		}

		public DbKey newKey(final long idA, final long idB) {
			return new LinkKey(idA, idB);
		}

		@Override
		public DbKey newKey(final ResultSet rs) throws SQLException {
			return new LinkKey(rs.getLong(this.idColumnA), rs.getLong(this.idColumnB));
		}

	}

	final class LongKey implements DbKey {

		private final long id;

		private LongKey(final long id) {
			this.id = id;
		}

		@Override
		public boolean equals(final Object o) {
			return (o instanceof LongKey) && (((LongKey) o).id == this.id);
		}

		public long getId() {
			return this.id;
		}

		@Override
		public int hashCode() {
			return (int) (this.id ^ (this.id >>> 32));
		}

		@Override
		public int setPK(final PreparedStatement pstmt) throws SQLException {
			return this.setPK(pstmt, 1);
		}

		@Override
		public int setPK(final PreparedStatement pstmt, final int index) throws SQLException {
			pstmt.setLong(index, this.id);
			return index + 1;
		}

	}

	abstract class LongKeyFactory<T> extends Factory<T> {

		private final String idColumn;

		public LongKeyFactory(final String idColumn) {
			super(" WHERE " + idColumn + " = ? ", idColumn, " a." + idColumn + " = b." + idColumn + " ");
			this.idColumn = idColumn;
		}

		public DbKey newKey(final long id) {
			return new LongKey(id);
		}

		@Override
		public DbKey newKey(final ResultSet rs) throws SQLException {
			return new LongKey(rs.getLong(this.idColumn));
		}

	}

	final class StringKey implements DbKey {

		private final String id;

		private StringKey(final String id) {
			this.id = id;
		}

		@Override
		public boolean equals(final Object o) {
			return (o instanceof StringKey)
					&& (this.id != null ? this.id.equals(((StringKey) o).id) : ((StringKey) o).id == null);
		}

		public String getId() {
			return this.id;
		}

		@Override
		public int hashCode() {
			return this.id != null ? this.id.hashCode() : 0;
		}

		@Override
		public int setPK(final PreparedStatement pstmt) throws SQLException {
			return this.setPK(pstmt, 1);
		}

		@Override
		public int setPK(final PreparedStatement pstmt, final int index) throws SQLException {
			pstmt.setString(index, this.id);
			return index + 1;
		}

	}

	abstract class StringKeyFactory<T> extends Factory<T> {

		private final String idColumn;

		public StringKeyFactory(final String idColumn) {
			super(" WHERE " + idColumn + " = ? ", idColumn, " a." + idColumn + " = b." + idColumn + " ");
			this.idColumn = idColumn;
		}

		@Override
		public DbKey newKey(final ResultSet rs) throws SQLException {
			return new StringKey(rs.getString(this.idColumn));
		}

		public DbKey newKey(final String id) {
			return new StringKey(id);
		}

	}

	int setPK(PreparedStatement pstmt) throws SQLException;

	int setPK(PreparedStatement pstmt, int index) throws SQLException;

}

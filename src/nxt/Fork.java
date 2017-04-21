/******************************************************************************
 * Copyright Â© 2013-2016 The XEL Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.VersionedEntityDbTable;
import nxt.util.Logger;

public final class Fork {

    private static final DbKey.LongKeyFactory<Fork> forkDbKeyFactory = new DbKey.LongKeyFactory<Fork>("id") {

        @Override
        public DbKey newKey(final Fork shuffling) {
            return shuffling.dbKey;
        }

    };

    private static final VersionedEntityDbTable<Fork> forkTable = new VersionedEntityDbTable<Fork>("fork",
            Fork.forkDbKeyFactory) {

        @Override
        protected Fork load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
            return new Fork(rs, dbKey);
        }

        @Override
        protected void save(final Connection con, final Fork shuffling) throws SQLException {
            shuffling.save(con);
        }

    };

    public static DbIterator<Fork> getAll(final int from, final int to) {
        return Fork.forkTable.getAll(from, to, " ORDER BY height DESC ");
    }


    public  static DbIterator<Fork> getLockedInForks(final int from, final int to) {
        return Fork.forkTable.getManyBy(
                new DbClause.IntClause("sliding_count", Constants.BLOCKS_TO_LOCKIN_SOFT_FORK).and(new DbClause.BooleanClause("latest", true)), from, to,
                " ORDER BY height DESC ");
    }
    public static int getCount() {
        return Fork.forkTable.getCount();
    }

    public Fork(long id, int sliding_count) {
        this.id = id;
        this.dbKey = Fork.forkDbKeyFactory.newKey(this.id);
        this.sliding_count = sliding_count;
    }

    public static Fork getFork(final int id) {
        return Fork.forkTable.get(Fork.forkDbKeyFactory.newKey(id));
    }

    static void init() {
        final int blockchainHeight = Nxt.getBlockchain().getHeight();

        if (getCount() == 0 && blockchainHeight == 0) {
            Logger.logInfoMessage("Recreating Fork Tracker DB");
            try {
                Db.db.beginTransaction();

                // either 0 or 64
                // make sure 64 features are in DB
                for (long i = 0; i < 64; ++i) {
                    final Fork shuffling = new Fork(i, 0);
                    Fork.forkTable.insert(shuffling);
                }

                Db.db.commitTransaction();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            } finally {
                Db.db.endTransaction();
            }
        }
    }

    private final long id;
    private final DbKey dbKey;
    public int sliding_count;

    private Fork(final ResultSet rs, final DbKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = dbKey;
        this.sliding_count = rs.getInt("sliding_count");
    }

    public DbKey getDbKey() {
        return this.dbKey;
    }

    public long getId() {
        return this.id;
    }

    private void save(final Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement(
                "MERGE INTO fork (id, sliding_count, height, latest) "
                        + "KEY (id, height) "
                        + "VALUES (?, ?, ?, TRUE)")) {
            int i = 0;
            final int blockchainHeight = Nxt.getBlockchain().getHeight();
            pstmt.setLong(++i, this.id);
            pstmt.setInt(++i, this.sliding_count);
            pstmt.setInt(++i, blockchainHeight);
            pstmt.executeUpdate();
        }
    }

    public void store() {
        Fork.forkTable.insert(this);
    }
}
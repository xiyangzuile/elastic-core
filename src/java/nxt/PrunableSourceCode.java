/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

import nxt.crypto.Crypto;
import nxt.crypto.EncryptedData;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.PrunableDbTable;
import nxt.util.Convert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PrunableSourceCode {

    private static final DbKey.LongKeyFactory<PrunableSourceCode> prunableSourceCodeKeyFactory = new DbKey.LongKeyFactory<PrunableSourceCode>("id") {

        @Override
        public DbKey newKey(PrunableSourceCode prunableSourceCode) {
            return prunableSourceCode.dbKey;
        }

    };

    private static final PrunableDbTable<PrunableSourceCode> prunableSourceCodeTable = new PrunableDbTable<PrunableSourceCode>("prunable_source_code", prunableSourceCodeKeyFactory) {

        @Override
        protected PrunableSourceCode load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new PrunableSourceCode(rs, dbKey);
        }

        @Override
        protected void save(Connection con, PrunableSourceCode prunableSourceCode) throws SQLException {
        	prunableSourceCode.save(con);
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY block_timestamp DESC, db_id DESC ";
        }

    };

    public static int getCount() {
        return prunableSourceCodeTable.getCount();
    }

    public static DbIterator<PrunableSourceCode> getAll(int from, int to) {
        return prunableSourceCodeTable.getAll(from, to);
    }

    public static PrunableSourceCode getPrunableSourceCode(long transactionId) {
        return prunableSourceCodeTable.get(prunableSourceCodeKeyFactory.newKey(transactionId));
    }

  
    public static PrunableSourceCode getPrunableSourceCodeByWorkId(long work_id) {
        Connection con = null;
        try {
            con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM prunable_source_code WHERE work_id = ?");
            int i = 0;
            pstmt.setLong(++i, work_id);
            DbIterator<PrunableSourceCode> it =  prunableSourceCodeTable.getManyBy(con, pstmt, false);
            PrunableSourceCode s = null;
            if(it.hasNext())
            	s = it.next();
            it.close();
            return s;
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void init() {}

    private final long id;
    private final DbKey dbKey;
    private final long work_id;
    private byte[] source;
    private short language;
    private final int transactionTimestamp;
    private final int blockTimestamp;
    private final int height;

    private PrunableSourceCode(Transaction transaction, int blockTimestamp, int height) {
        this.id = transaction.getId();
        this.dbKey = prunableSourceCodeKeyFactory.newKey(this.id);
        this.work_id = transaction.getId();
        this.blockTimestamp = blockTimestamp;
        this.height = height;
        this.transactionTimestamp = transaction.getTimestamp();
    }

    private void setPlain(Appendix.PrunableSourceCode appendix) {
        this.source = appendix.getSource();
        this.language = appendix.getLanguage();
    }

    private PrunableSourceCode(ResultSet rs, DbKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = dbKey;
        this.work_id = rs.getLong("work_id");
        this.source = rs.getBytes("source");
        this.blockTimestamp = rs.getInt("block_timestamp");
        this.transactionTimestamp = rs.getInt("transaction_timestamp");
        this.height = rs.getInt("height");
        this.language = rs.getShort("language");
    }

    private void save(Connection con) throws SQLException {
        if (source == null) {
            throw new IllegalStateException("Prunable source code not fully initialized");
        }
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO prunable_source_code (id, work_id, "
                + "source, block_timestamp, transaction_timestamp, height, language) "
                + "KEY (id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.work_id);
            DbUtils.setBytes(pstmt, ++i, this.source);
            pstmt.setInt(++i, this.blockTimestamp);
            pstmt.setInt(++i, this.transactionTimestamp);
            pstmt.setInt(++i, this.height);
            pstmt.setShort(++i, this.language);
            pstmt.executeUpdate();
        }
    }

    public byte[] getSource() {
        return source;
    }

    public long getId() {
        return id;
    }
    
    public short getLanguage() {
        return language;
    }

    public long getWorkId() {
        return work_id;
    }

    public int getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public int getBlockTimestamp() {
        return blockTimestamp;
    }

    public int getHeight() {
        return height;
    }

    static void add(TransactionImpl transaction, Appendix.PrunableSourceCode appendix) {
        add(transaction, appendix, Nxt.getBlockchain().getLastBlockTimestamp(), Nxt.getBlockchain().getHeight());
    }

    static void add(TransactionImpl transaction, Appendix.PrunableSourceCode appendix, int blockTimestamp, int height) {
        if (appendix.getSource() != null) {
            PrunableSourceCode prunableSourceCode = prunableSourceCodeTable.get(transaction.getDbKey());
            if (prunableSourceCode == null) {
            	prunableSourceCode = new PrunableSourceCode(transaction, blockTimestamp, height);
            } else if (prunableSourceCode.height != height) {
                throw new RuntimeException("Attempt to modify prunable source code from height " + prunableSourceCode.height + " at height " + height);
            }
            if (prunableSourceCode.getSource() == null) {
            	prunableSourceCode.setPlain(appendix);
                prunableSourceCodeTable.insert(prunableSourceCode);
            }
        }
    }

   

    static boolean isPruned(long transactionId, boolean hasPrunableSourceCode) {
        if (!hasPrunableSourceCode) {
            return false;
        }
        try (Connection con = Db.db.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT source FROM prunable_source_code WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return !rs.next() || (hasPrunableSourceCode && rs.getBytes("source") == null);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }
    
    static boolean isPrunedByWorkId(long work_id) {
        try (Connection con = Db.db.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT source FROM prunable_source_code WHERE work_id = ?")) {
            pstmt.setLong(1, work_id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return !rs.next() || (rs.getBytes("source") == null);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

}

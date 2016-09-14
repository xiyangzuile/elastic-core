/******************************************************************************
 * Copyright Â© 2013-2016 The Nxt Core Developers.                             *
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

/**
 * Represents a single shuffling participant
 */
package nxt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import nxt.Work.Event;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.VersionedEntityDbTable;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;

public final class PowAndBounty {


    public enum Event {
        POW_SUBMITTED, BOUNTY_SUBMITTED
    }

    

    private static final Listeners<PowAndBounty, Event> listeners = new Listeners<>();

    private static final DbKey.LongKeyFactory<PowAndBounty> powAndBountyDbKeyFactory = new DbKey.LongKeyFactory<PowAndBounty>("work_id") {

        @Override
        public DbKey newKey(PowAndBounty participant) {
            return participant.dbKey;
        }

    };

    private static final VersionedEntityDbTable<PowAndBounty> powAndBountyTable = new VersionedEntityDbTable<PowAndBounty>("pow_and_bounty", powAndBountyDbKeyFactory) {

        @Override
        protected PowAndBounty load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new PowAndBounty(rs, dbKey);
        }

        @Override
        protected void save(Connection con, PowAndBounty participant) throws SQLException {
            participant.save(con);
        }

    };

    public static boolean addListener(Listener<PowAndBounty> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<PowAndBounty> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    public static DbIterator<PowAndBounty> getPows(long wid) {
        return powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", true)), 0, -1, "");
    }
    public static DbIterator<PowAndBounty> getBounties(long wid) {
        return powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", false)), 0, -1, "");
    }
    public static DbIterator<PowAndBounty> getPows(long wid, long aid) {
        return powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", true)).and(
                        new DbClause.LongClause("account_id", aid)), 0, -1, "");
    }
    public static DbIterator<PowAndBounty> getBounties(long wid, long aid) {
        return powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", false)).and(
                        new DbClause.LongClause("account_id", aid)), 0, -1, "");
    }

    static void addPow(Transaction transaction, Attachment.PiggybackedProofOfWork attachment) {
    	PowAndBounty shuffling = new PowAndBounty(transaction, attachment);
    	powAndBountyTable.insert(shuffling);
        listeners.notify(shuffling, Event.POW_SUBMITTED);
    }
    static void addBounty(Transaction transaction, Attachment.PiggybackedProofOfBounty attachment) {
    	PowAndBounty shuffling = new PowAndBounty(transaction, attachment);
    	powAndBountyTable.insert(shuffling);
        listeners.notify(shuffling, Event.BOUNTY_SUBMITTED);
    }
    static int getPowCount(long wid) {
        return powAndBountyTable.getCount(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", true)));
    }
    static int getBountyCount(long wid) {
        return powAndBountyTable.getCount(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", false)));
    }
    static void init() {}
    private final long id;
    private final boolean is_pow;
    private final long work_id;
    private final long accountId;
    private final DbKey dbKey;
    private final int[] input;
    private final byte[] hash;
    
    public void applyPowPayment(){
    	Work w = Work.getWorkByWorkId(this.work_id);
    	
    	// Now create ledger event for "bounty submission"
        AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_POW;
        Account participantAccount = Account.getAccount(this.accountId);
        participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, w.getXel_per_pow());
        
        // Reduce work remaining xel
        w.reduce_one_pow_submission();
    }
    
    public void applyBounty(){
    	Work w = Work.getWorkByWorkId(this.work_id);
    
        // Reduce bounty fund entirely
        w.kill_bounty_fund();
    }
    
    private PowAndBounty(Transaction transaction, Attachment.PiggybackedProofOfWork attachment) {
    	this.id = transaction.getId();
        this.work_id = attachment.getWorkId();
        this.accountId = transaction.getSenderId();
        this.dbKey = powAndBountyDbKeyFactory.newKey(work_id);
        this.input = attachment.getInput();
        this.is_pow = true;
        this.hash = new byte[0]; // FIXME TODO
    }
    private PowAndBounty(Transaction transaction, Attachment.PiggybackedProofOfBounty attachment) {
    	this.id = transaction.getId();
        this.work_id = attachment.getWorkId();
        this.accountId = transaction.getSenderId();
        this.dbKey = powAndBountyDbKeyFactory.newKey(work_id);
        this.input = attachment.getInput();
        this.is_pow = false;
        this.hash = new byte[0]; // FIXME TODO
    }
    private PowAndBounty(ResultSet rs, DbKey dbKey) throws SQLException {
    	this.id = rs.getLong("id");
        this.work_id = rs.getLong("work_id");
        this.accountId = rs.getLong("account_id");
        this.is_pow = rs.getBoolean("is_pow");
        this.dbKey = dbKey;
        
        byte[] bt = rs.getBytes("input");
        IntBuffer ib = ByteBuffer.wrap(bt).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
        this.input = new int[ib.capacity()];
        
        this.hash = rs.getBytes("hash");
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO pow_and_bounty (id, work_id, hash, account_id, input, is_pow,"
                + " height) " + "KEY (id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.work_id);
            DbUtils.setBytes(pstmt, ++i, this.hash);
            pstmt.setLong(++i, this.accountId);
            
            ByteBuffer byteBuffer = ByteBuffer.allocate(input.length * 4);        
            IntBuffer intBuffer = byteBuffer.order(ByteOrder.BIG_ENDIAN).asIntBuffer();
            intBuffer.put(input);
            byte[] array = byteBuffer.array();
            pstmt.setBytes(++i, array);
            pstmt.setBoolean(++i, this.is_pow);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getAccountId() {
        return accountId;
    }

}
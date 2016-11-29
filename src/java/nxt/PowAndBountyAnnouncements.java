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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import org.json.simple.JSONObject;

import nxt.db.DbClause;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.VersionedEntityDbTable;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;

public final class PowAndBountyAnnouncements {


    public enum Event {
        BOUNTY_ANNOUNCEMENT_SUBMITTED
    }

    

    private static final Listeners<PowAndBountyAnnouncements, Event> listeners = new Listeners<>();

    private static final DbKey.LongKeyFactory<PowAndBountyAnnouncements> powAndBountyAnnouncementDbKeyFactory = new DbKey.LongKeyFactory<PowAndBountyAnnouncements>("id") {

        @Override
        public DbKey newKey(PowAndBountyAnnouncements participant) {
            return participant.dbKey;
        }

    };

    private static final VersionedEntityDbTable<PowAndBountyAnnouncements> powAndBountyAnnouncementTable = new VersionedEntityDbTable<PowAndBountyAnnouncements>("pow_and_bounty_announcements", powAndBountyAnnouncementDbKeyFactory) {

        @Override
        protected PowAndBountyAnnouncements load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new PowAndBountyAnnouncements(rs, dbKey);
        }

        @Override
        protected void save(Connection con, PowAndBountyAnnouncements participant) throws SQLException {
            participant.save(con);
        }

    };

    public static PowAndBountyAnnouncements getPowOrBountyById(long id) {
        return powAndBountyAnnouncementTable.get(powAndBountyAnnouncementDbKeyFactory.newKey(id));
    }

    public static boolean addListener(Listener<PowAndBountyAnnouncements> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<PowAndBountyAnnouncements> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }
   
   
 
    static PowAndBountyAnnouncements addBountyAnnouncement(Transaction transaction, Attachment.PiggybackedProofOfBountyAnnouncement attachment) {
    	PowAndBountyAnnouncements shuffling = new PowAndBountyAnnouncements(transaction, attachment);
    	powAndBountyAnnouncementTable.insert(shuffling);
        listeners.notify(shuffling, Event.BOUNTY_ANNOUNCEMENT_SUBMITTED);
        return shuffling;
    }
    

    public static boolean hasHash(long work_id, byte[] hash) {
        return powAndBountyAnnouncementTable.getCount(new DbClause.BytesClause("hash", hash).and(new DbClause.LongClause("work_id", work_id)))>0;
    }
    
    public static boolean hasValidHash(long work_id, byte[] hash) {
        return powAndBountyAnnouncementTable.getCount(new DbClause.BytesClause("hash", hash).and(new DbClause.LongClause("work_id", work_id)).and(new DbClause.BooleanClause("too_late", false)))>0;
    }
    
  
    
    static void init() {}
    private final long id;
    private boolean too_late;
    private final long work_id;
    private final long accountId;
    private final DbKey dbKey;
    private final byte[] hash;
    
    public void applyBountyAnnouncement(Block bl) throws IOException{
    	Work w = Work.getWorkByWorkId(this.work_id);
    	if(w == null)
    		throw new IOException("Unknown work id!");
    	if(w.isClosed() == false && w.isClose_pending() == false){
	    	// Now create ledger event for "bounty submission"
	        AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_BOUNTY_ANNOUNCEMENT;
	        Account participantAccount = Account.getAccount(this.accountId);
	        participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, -1*Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION);
	        w.register_bounty_announcement(bl);
    	}else{
    		this.too_late = true;
    		this.powAndBountyAnnouncementTable.insert(this);
    	}	
        
    }
  
    private PowAndBountyAnnouncements(Transaction transaction, Attachment.PiggybackedProofOfBountyAnnouncement attachment) {
    	this.id = transaction.getId();
        this.work_id = attachment.getWorkId();
        this.accountId = transaction.getSenderId();
        this.dbKey = powAndBountyAnnouncementDbKeyFactory.newKey(id);
        this.hash = attachment.getHashAnnounced(); // FIXME TODO
        this.too_late = false;
    }
    private PowAndBountyAnnouncements(ResultSet rs, DbKey dbKey) throws SQLException {
    	this.id = rs.getLong("id");
        this.work_id = rs.getLong("work_id");
        this.accountId = rs.getLong("account_id");
        this.dbKey = dbKey;
        this.too_late = rs.getBoolean("too_late");
        this.hash = rs.getBytes("hash");
    }


	private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO pow_and_bounty_announcements (id, too_late, work_id, hash, account_id, "
                + " height) " + "KEY (id, height) "
                + "VALUES (?,  ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setBoolean(++i, this.too_late);
            pstmt.setLong(++i, this.work_id);
            DbUtils.setBytes(pstmt, ++i, this.hash);
            pstmt.setLong(++i, this.accountId);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getAccountId() {
        return accountId;
    }

	public JSONObject toJsonObject() {
		JSONObject response = new JSONObject();
		response.put("id",Convert.toUnsignedLong(this.id));
		Transaction t = TransactionDb.findTransaction(this.id);
		if(t != null){
			response.put("date",Convert.toUnsignedLong(t.getTimestamp()));
			response.put("hash_announcement",Arrays.toString(this.hash));
		}else{
			response.put("error","Transaction not found");
		}
		
		return response;
	}

}
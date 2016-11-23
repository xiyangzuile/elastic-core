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

package nxt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONObject;
import org.nevec.rjm.BigDecimalMath;

import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.VersionedEntityDbTable;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;

public final class Work {

    public enum Event {
        WORK_CREATED, WORK_POW_RECEIVED, WORK_BOUNTY_RECEIVED, WORK_CANCELLED, WORK_TIMEOUTED;
    }



    private static final Listeners<Work, Event> listeners = new Listeners<>();

    private static final DbKey.LongKeyFactory<Work> workDbKeyFactory = new DbKey.LongKeyFactory<Work>("id") {

        @Override
        public DbKey newKey(Work shuffling) {
            return shuffling.dbKey;
        }

    };

    public int getReceived_bounty_announcements() {
		return received_bounty_announcements;
	}

	private static final VersionedEntityDbTable<Work> workTable = new VersionedEntityDbTable<Work>("work", workDbKeyFactory) {

        @Override
        protected Work load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new Work(rs, dbKey);
        }

        @Override
        protected void save(Connection con, Work shuffling) throws SQLException {
            shuffling.save(con);
        }

    };

    static {
        Nxt.getBlockchainProcessor().addListener(block -> {
            List<Work> shufflings = new ArrayList<>();
            try (DbIterator<Work> iterator = getActiveAndPendingWorks(0, -1)) {
                for (Work shuffling : iterator) {
                        shufflings.add(shuffling);
                }
            }
            shufflings.forEach(shuffling -> {
                if (shuffling.close_pending || --shuffling.blocksRemaining <= 0) {
                	// Work has timed out natually
                    shuffling.natural_timeout(block);
                } else {
                	shuffling.updatePowTarget(block);
                    workTable.insert(shuffling);
                }
            });
        }, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
    }

    public static boolean addListener(Listener<Work> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public void natural_timeout(Block bl) {
    	
    	
    	if(closed == true){
    		return;
    	}
    	
		
		if(this.close_pending == false && this.closed == false){
			// Check if cancelled or timedout
			if(this.blocksRemaining == 0 && this.balance_pow_fund>=this.xel_per_pow && this.received_bounties<this.bounty_limit && this.received_bounty_announcements<this.bounty_limit){
				// timedout with money remaining and bounty slots remaining
				this.timedout = true;
				this.closing_timestamp = Nxt.getEpochTime();
				if(this.received_bounties==this.received_bounty_announcements){
					this.closed = true;
					// Now create ledger event for "refund" what is left in the pow and bounty funds
			        AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_CANCELLATION;
			        Account participantAccount = Account.getAccount(this.sender_account_id);
			        participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, this.balance_pow_fund+this.balance_bounty_fund);  
			        
				}else{
					this.close_pending = true;
				}
				
			}else if(this.blocksRemaining > 0 && (this.balance_pow_fund<this.xel_per_pow || this.received_bounties==this.bounty_limit || this.received_bounty_announcements==this.bounty_limit)) {
				// closed regularily, nothing to bother about
				this.closing_timestamp = Nxt.getEpochTime();
				if(this.received_bounties==this.received_bounty_announcements){
					this.closed = true;
					// Now create ledger event for "refund" what is left in the pow and bounty funds
			        AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_CANCELLATION;
			        Account participantAccount = Account.getAccount(this.sender_account_id);
			        participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, this.balance_pow_fund+this.balance_bounty_fund);  
			        
				}else{
					this.close_pending = true;
				}
			}else{
				// manual cancellation
				this.cancelled = true;
				this.closing_timestamp = Nxt.getEpochTime();
				if(this.received_bounties==this.received_bounty_announcements){
					this.closed = true;
					// Now create ledger event for "refund" what is left in the pow and bounty funds
			        AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_CANCELLATION;
			        Account participantAccount = Account.getAccount(this.sender_account_id);
			        participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, this.balance_pow_fund+this.balance_bounty_fund);  
			        
				}else{
					this.close_pending = true;
				}
			}
		}
		else if(this.close_pending == true && this.closed == false){
			if(bl.getTimestamp()-this.closing_timestamp >= Constants.DEPOSIT_GRACE_PERIOD || this.received_bounty_announcements == this.received_bounties){
				this.closed = true;
				this.close_pending = false;
				
				int refundAnnouncements = 0;
				if(received_bounty_announcements>this.received_bounties){
					refundAnnouncements = received_bounty_announcements-received_bounties;
				}
				// Now create ledger event for "refund" what is left in the pow and bounty funds
		        AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_CANCELLATION;
		        Account participantAccount = Account.getAccount(this.sender_account_id);
		        participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, this.balance_pow_fund+this.balance_bounty_fund+refundAnnouncements*Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION); 
			}else{
				// pass through
			}
		}
		
		        
		workTable.insert(this);
		
		
		// notify
		listeners.notify(this, Event.WORK_CANCELLED);
		
	}

	public static boolean removeListener(Listener<Work> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    public static int getCount() {
        return workTable.getCount();
    }

    public static int getActiveCount() {
        return workTable.getCount(new DbClause.NotNullClause("blocks_remaining"));
    }

    public static DbIterator<Work> getAll(int from, int to) {
        return workTable.getAll(from, to, " ORDER BY blocks_remaining NULLS LAST, height DESC ");
    }
    
    public static DbIterator<Work> getLastTenClosed() {
    	 Connection con = null;
         try {
             con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT work.* FROM work WHERE work.closed = TRUE AND work.latest = TRUE ORDER BY closing_timestamp DESC LIMIT 10");
             DbIterator<Work> it = workTable.getManyBy(con, pstmt, true);
             return it;
             
         } catch (SQLException e) {
             throw new RuntimeException(e.toString(), e);
         }
    }

    

    public static Work getWork(long id) {
        return workTable.get(workDbKeyFactory.newKey(id));
    }

    public static Work getWork(byte[] fullHash) {
        long shufflingId = Convert.fullHashToId(fullHash);
        Work shuffling = workTable.get(workDbKeyFactory.newKey(shufflingId));
        if (shuffling != null && !Arrays.equals(shuffling.getFullHash(), fullHash)) {
            Logger.logDebugMessage("Shuffling with different hash %s but same id found for hash %s",
                    Convert.toHexString(shuffling.getFullHash()), Convert.toHexString(fullHash));
            return null;
        }
        return shuffling;
    }

    static void addWork(Transaction transaction, Attachment.WorkCreation attachment) {
        Work shuffling = new Work(transaction, attachment);
        workTable.insert(shuffling);
        listeners.notify(shuffling, Event.WORK_CREATED);
    }
    
    public static Work getWorkByWorkId(long work_id) {
        Connection con = null;
        try {
            con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT work.* FROM work WHERE work.work_id = ? AND work.latest = TRUE");
            int i = 0;
            pstmt.setLong(++i, work_id);
            
            DbIterator<Work> it = workTable.getManyBy(con, pstmt, true);
            Work w = null;
            if(it.hasNext())
            	w = it.next();
            it.close();
            return w;
            
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }
    
    public static DbIterator<Work> getAccountWork(long accountId, boolean includeFinished, int from, int to, long onlyOneId) {
        Connection con = null;
        try {
            con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT work.* FROM work WHERE work.sender_account_id = ? "
                    + (includeFinished ? "" : "AND work.blocks_remaining IS NOT NULL ")
                    + (onlyOneId==0 ? "" : "AND work.work_id = ? ")
                    + "AND work.latest = TRUE ORDER BY closed, close_pending, originating_height DESC "
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            if(onlyOneId!=0){
            	pstmt.setLong(++i, onlyOneId);
            }
            DbUtils.setLimits(++i, pstmt, from, to);
            return workTable.getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void init() {}

    private final long id;
    private final DbKey dbKey;
    private final long work_id;
    private final long block_id;
    private final long sender_account_id;
    private boolean closed;
    private boolean close_pending;
    private boolean cancelled;
    private boolean timedout;
    private final String title;
    private String work_min_pow_target;
    private final long xel_per_pow;
    private final long xel_per_bounty;
    private final int bounty_limit;
    private long balance_pow_fund;
    private long balance_bounty_fund;
    private final long balance_pow_fund_orig;
    private final long balance_bounty_fund_orig;
    private int received_bounties;
    private int received_bounty_announcements;
    private int received_pows;
    private short blocksRemaining;
    private final int originating_height;
    private int closing_timestamp;

    private Work(Transaction transaction, Attachment.WorkCreation attachment) {
        this.id = transaction.getId();
        this.work_id = this.id;
        this.block_id = transaction.getBlockId();
        this.dbKey = workDbKeyFactory.newKey(this.id);
        this.xel_per_pow = attachment.getXelPerPow();
        this.title = attachment.getWorkTitle();
        this.blocksRemaining = (short) attachment.getDeadline();
        this.closed = false;
        this.close_pending = false;
        this.xel_per_bounty = attachment.getXelPerBounty();
        this.balance_pow_fund = (long)(transaction.getAmountNQT() - (attachment.getBountyLimit()*attachment.getXelPerBounty()));
        this.balance_bounty_fund = (attachment.getBountyLimit()*attachment.getXelPerBounty());
        this.balance_pow_fund_orig = balance_pow_fund;
        this.balance_bounty_fund_orig = balance_bounty_fund;
        this.received_bounties = 0;
        this.received_bounty_announcements = 0;
        this.received_pows = 0;        
        this.bounty_limit = attachment.getBountyLimit();
        this.sender_account_id = transaction.getSenderId();
        this.cancelled=false;
        this.timedout=false;
        this.originating_height = transaction.getBlock().getHeight();
        this.closing_timestamp=0;
        this.work_min_pow_target = "";
        updatePowTarget(transaction.getBlock());
    }
    
    private static final double LOG10 = Math.log(10.0);
    public static double logBigDecimal(BigDecimal val) {
        return logBigInteger(val.unscaledValue()) + val.scale() * Math.log(10.0);
    }
    private static final double LOG2 = Math.log(2.0);
    public static double logBigInteger(BigInteger val) {
        int blex = val.bitLength() - 1022; // any value in 60..1023 is ok
        if (blex > 0)
            val = val.shiftRight(blex);
        double res = Math.log(val.doubleValue());
        return blex > 0 ? res + blex * LOG2 : res;
    }
    
    public static BigInteger getFactor(BigInteger targetI){
    	
		BigDecimal target = new BigDecimal(targetI);
		
		BigInteger least_possible_targetBigint = Constants.least_possible_target;
		BigDecimal least_possible_target = new BigDecimal(least_possible_targetBigint);
		
		BigDecimal quotient = least_possible_target.divide(target,RoundingMode.FLOOR);
		quotient = BigDecimalMath.root(3, quotient);
		
		BigInteger quotientI = quotient.toBigInteger();
		
		return quotientI;
		
    }
    public void updatePowTarget(Block currentBlock){
    	
    	// Initialize with the blocks base target (this is set in BlockImpl::calculateNextMinPowTarget
    	// to the lowest difficulty over the last 1<=n<=10 closed jobs,
    	// or to the minimal possible difficulty if there aren't any closed jobs yet)
    	
    	BigInteger targetI = null;
    	if(work_min_pow_target.length()==0)
    		targetI = Nxt.getBlockchain().getBlock(this.getBlock_id()).getMinPowTarget();
    	else
    		targetI = new BigInteger(this.work_min_pow_target, 16);
    	
    	if( currentBlock.getId() != this.getBlock_id() ){   		
    		// Do standard retargeting (yet to be peer reviewed)
    		
    		long PastBlocksMass = 0;
    		int account_for_blocks_max=3;
    		long seconds_passed = 0;
    		int desired_pows = 0;
    		long PastBlocksTotalMass = 0;
    		
    		Block b = currentBlock;
    		int counter = 0;
    		while(true){
    			if(b==null || b.getId() == this.getBlock_id()) break;
    			counter=counter+1;
    			PastBlocksMass += b.countNumberPOWPerWorkId(this.getId());
    			PastBlocksTotalMass += b.countNumberPOW();
    			seconds_passed = currentBlock.getTimestamp()-b.getTimestamp();
    			if(seconds_passed<0) seconds_passed=60*counter; // Crippled timestamps, assume everything went smoothly! Should not happen anyway!
    			if(b.getPreviousBlock()==null || counter == account_for_blocks_max)
    				break;
    			b=b.getPreviousBlock();
    		}
    		
    		// Now see how we would scale the target, this is important because 3 blocks do not always take the same amount of time
    		if(seconds_passed<1) seconds_passed=1;
    		
    		// Normalize the time span so we always work with "60 second windows"
    		double pows_per_60_seconds = (PastBlocksMass * 60.0 / seconds_passed);
    		
    		// DIRTY HACK; Assume 0<x<=1 pow is equal to 1 pow, to avoid calculating with fractions
    		if(pows_per_60_seconds > 0 && pows_per_60_seconds<1)
    			pows_per_60_seconds = 1;
    		
    		double factor = 1;
    		
    		if(pows_per_60_seconds>0){
    			// We have received at least one POW in the last 60 seconds
    			System.out.println("*** RETARGETING ***");
    			System.out.println("Workid: " + this.getId());
    			System.out.println("Accounted last blocks: " + counter);
    			System.out.println("Blocks span how much time: " + seconds_passed);
    			System.out.println("How many seen POWs: " + PastBlocksMass);
    			System.out.println("Normalized # per 60s: " + pows_per_60_seconds);
    			System.out.println("Wanted # per 60s: " + 10);
    			factor = 10 / pows_per_60_seconds;
    			// round the factor to change the diff max 20% per block!
        		if(factor<0.80) factor=0.80;
        		if(factor>1.20) factor=1.20;
        		
        		System.out.println("Scalingfactor: " + factor);
    		}else if(pows_per_60_seconds == 0 && PastBlocksTotalMass==0){
    			// This job did not get any POWs but and others also didnt get any! Seems the diff is too high!
    			// The best way is to gradually decrease the difficulty (increase the target value) until the job is mineable again.
    			// As a safe guard, we do not allow "too high" changes in this case. Lets move by 5% at a time.
    			// Target value should double after ~15 blocks! Let's assume that this time span will never be reached
    			// as (in the case the job is too difficult) there will be at least one POW at some point, causing the
    			// above branch of the if statement to apply
    			System.out.println("*** RETARGETING ***");
    			System.out.println("Workid: " + this.getId());
    			System.out.println("Accounted last blocks: " + counter+"\nNO POW SUBMISSIONS IN LAST " + counter + " BLOCKS!");
    			factor=1.05;
    			System.out.println("Scalingfactor: " + factor);
    		}else{
    			// This job is just too boring, others still get POWs
    			System.out.println("*** RETARGETING ***");
    			System.out.println("Workid: " + this.getId());
    			System.out.println("Skipped retargeting, no POW received for this job but others!");
    		}
    	
    		BigDecimal intermediate = new BigDecimal(targetI);
    		System.out.println("Factor is: " + factor);
    		intermediate = intermediate.multiply(BigDecimal.valueOf(factor));
    		targetI = intermediate.toBigInteger();
    		if(targetI.compareTo(Constants.least_possible_target)==1){
    			targetI = Constants.least_possible_target;
    		}else if(targetI.compareTo(BigInteger.valueOf(1L))==-1){ // safe guard, should never happen at all
    			targetI = BigInteger.valueOf(1L);
    		}
    		System.out.println("New target: " + targetI.toString(16));

    	}else{
    		// do nothing, especially when its the block where the work was included
    	}
    	this.work_min_pow_target = targetI.toString(16);
    	
    }

    private Work(ResultSet rs, DbKey dbKey) throws SQLException {

        
        this.id = rs.getLong("id");
        this.work_id = rs.getLong("work_id");
        this.block_id = rs.getLong("block_id");
        this.dbKey = dbKey;
        this.xel_per_pow = rs.getLong("xel_per_pow");
        this.title = rs.getString("title");
        this.blocksRemaining = rs.getShort("blocks_remaining");
        this.closed = rs.getBoolean("closed");
        this.close_pending = rs.getBoolean("close_pending");
        this.cancelled = rs.getBoolean("cancelled");
        this.timedout = rs.getBoolean("timedout");
        this.xel_per_bounty = rs.getLong("xel_per_bounty");
        this.balance_pow_fund = rs.getLong("balance_pow_fund");
        this.balance_bounty_fund = rs.getLong("balance_bounty_fund");
        this.balance_pow_fund_orig = rs.getLong("balance_pow_fund_orig");
        this.balance_bounty_fund_orig = rs.getLong("balance_bounty_fund_orig");
        this.received_bounties = rs.getInt("received_bounties");
        this.received_pows = rs.getInt("received_pows");        
        this.bounty_limit = rs.getInt("bounty_limit");
        this.sender_account_id = rs.getLong("sender_account_id");
        this.originating_height = rs.getInt("originating_height");
        this.received_bounty_announcements = rs.getInt("received_bounty_announcements");
        this.closing_timestamp=rs.getInt("closing_timestamp");
        this.work_min_pow_target = rs.getString("work_min_pow_target");
    }

    public static DbIterator<Work> getActiveWorks(int from, int to) {
        return workTable.getManyBy(new DbClause.BooleanClause("closed",false).and(new DbClause.BooleanClause("latest",true)).and(new DbClause.BooleanClause("close_pending",false)), from, to, " ORDER BY blocks_remaining, height DESC ");
    }
    
    public static DbIterator<Work> getActiveAndPendingWorks(int from, int to) {
        return workTable.getManyBy(new DbClause.BooleanClause("closed",false).and(new DbClause.BooleanClause("latest",true)), from, to, " ORDER BY blocks_remaining, height DESC ");
    }
    
    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO work (id, closing_timestamp, work_id, block_id, sender_account_id, xel_per_pow, title, blocks_remaining, closed, close_pending, cancelled, timedout, xel_per_bounty, balance_pow_fund, balance_bounty_fund, balance_pow_fund_orig, balance_bounty_fund_orig, received_bounties, received_bounty_announcements, received_pows, bounty_limit, originating_height, height, work_min_pow_target, latest) "
                + "KEY (id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setInt(++i, this.closing_timestamp);
            pstmt.setLong(++i, this.work_id);
            pstmt.setLong(++i, this.block_id);
            pstmt.setLong(++i, this.sender_account_id);
            pstmt.setLong(++i, this.xel_per_pow);
            pstmt.setString(++i, this.title);
            pstmt.setShort(++i, this.blocksRemaining);
            pstmt.setBoolean(++i, this.closed);
            pstmt.setBoolean(++i, this.close_pending);
            pstmt.setBoolean(++i, this.cancelled);
            pstmt.setBoolean(++i, this.timedout);
            pstmt.setLong(++i, this.xel_per_bounty);
            pstmt.setLong(++i, this.balance_pow_fund);
            pstmt.setLong(++i, this.balance_bounty_fund);
            pstmt.setLong(++i, this.balance_pow_fund_orig);
            pstmt.setLong(++i, this.balance_bounty_fund_orig);
            pstmt.setInt(++i, this.received_bounties);
            pstmt.setInt(++i, this.received_bounty_announcements);
            pstmt.setInt(++i, this.received_pows);
            pstmt.setInt(++i, this.bounty_limit);
            pstmt.setInt(++i, this.originating_height);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.setString(++i, this.work_min_pow_target);

            pstmt.executeUpdate();
        }
    }

    public boolean isClose_pending() {
		return close_pending;
	}

	public void setClose_pending(boolean close_pending) {
		this.close_pending = close_pending;
	}

	public DbKey getDbKey() {
		return dbKey;
	}

	public long getWork_id() {
		return work_id;
	}

	public long getBlock_id() {
		return block_id;
	}

	public boolean isClosed() {
		return closed;
	}

	public String getTitle() {
		return title;
	}

	public long getXel_per_pow() {
		return xel_per_pow;
	}

	public long getSender_account_id() {
		return sender_account_id;
	}

	public long getXel_per_bounty() {
		return xel_per_bounty;
	}

	public int getBounty_limit() {
		return bounty_limit;
	}

	public long getBalance_pow_fund() {
		return balance_pow_fund;
	}

	public long getBalance_bounty_fund() {
		return balance_bounty_fund;
	}

	public int getReceived_bounties() {
		return received_bounties;
	}

	public int getReceived_pows() {
		return received_pows;
	}

	public long getBalance_pow_fund_orig() {
		return balance_pow_fund_orig;
	}

	public long getBalance_bounty_fund_orig() {
		return balance_bounty_fund_orig;
	}

	public void setBlocksRemaining(short blocksRemaining) {
		this.blocksRemaining = blocksRemaining;
	}

	public long getId() {
        return id;
    }

   
    public short getBlocksRemaining() {
        return blocksRemaining;
    }



    public byte[] getFullHash() {
        return TransactionDb.getFullHash(id);
    }

    private void cancel(Block block) {
       /* AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.SHUFFLING_CANCELLATION;
        long blamedAccountId = blame();
        try (DbIterator<ShufflingParticipant> participants = ShufflingParticipant.getParticipants(id)) {
            for (ShufflingParticipant participant : participants) {
                Account participantAccount = Account.getAccount(participant.getAccountId());
                holdingType.addToUnconfirmedBalance(participantAccount, event, this.id, this.holdingId, this.amount);
                if (participantAccount.getId() != blamedAccountId) {
                    if (holdingType != HoldingType.NXT) {
                        participantAccount.addToUnconfirmedBalanceNQT(event, this.id, Constants.SHUFFLING_DEPOSIT_NQT);
                    }
                } else {
                    if (holdingType == HoldingType.NXT) {
                        participantAccount.addToUnconfirmedBalanceNQT(event, this.id, -Constants.SHUFFLING_DEPOSIT_NQT);
                    }
                    participantAccount.addToBalanceNQT(event, this.id, -Constants.SHUFFLING_DEPOSIT_NQT);
                }
            }
        }
        if (blamedAccountId != 0) {
            // as a penalty the deposit goes to the generators of the finish block and previous 3 blocks
            long fee = Constants.SHUFFLING_DEPOSIT_NQT / 4;
            for (int i = 0; i < 3; i++) {
                Account previousGeneratorAccount = Account.getAccount(BlockDb.findBlockAtHeight(block.getHeight() - i - 1).getGeneratorId());
                previousGeneratorAccount.addToBalanceAndUnconfirmedBalanceNQT(AccountLedger.LedgerEvent.BLOCK_GENERATED, block.getId(), fee);
                previousGeneratorAccount.addToForgedBalanceNQT(fee);
                Logger.logDebugMessage("Shuffling penalty %f NXT awarded to forger at height %d", ((double)fee) / Constants.ONE_NXT, block.getHeight() - i - 1);
            }
            fee = Constants.SHUFFLING_DEPOSIT_NQT - 3 * fee;
            Account blockGeneratorAccount = Account.getAccount(block.getGeneratorId());
            blockGeneratorAccount.addToBalanceAndUnconfirmedBalanceNQT(AccountLedger.LedgerEvent.BLOCK_GENERATED, block.getId(), fee);
            blockGeneratorAccount.addToForgedBalanceNQT(fee);
            Logger.logDebugMessage("Shuffling penalty %f NXT awarded to forger at height %d", ((double)fee) / Constants.ONE_NXT, block.getHeight());
        }
        setStage(Stage.CANCELLED, blamedAccountId, (short)0);
        shufflingTable.insert(this);
        listeners.notify(this, Event.SHUFFLING_CANCELLED);
        if (deleteFinished) {
            delete();
        }
        Logger.logDebugMessage("Shuffling %s was cancelled, blaming account %s", Long.toUnsignedString(id), Long.toUnsignedString(blamedAccountId));
        */
    }

	public JSONObject toJsonObject() {
		JSONObject response = new JSONObject();
		response.put("id",Convert.toUnsignedLong(this.id));
		response.put("work_id",Convert.toUnsignedLong(this.work_id));
		response.put("block_id",Convert.toUnsignedLong(this.block_id));
		response.put("xel_per_pow",this.xel_per_pow);
		response.put("title",this.title);
		response.put("originating_height",this.originating_height);
		response.put("blocksRemaining",this.blocksRemaining);
		response.put("closed",this.closed);
		response.put("closing_timestamp",this.closing_timestamp);
		response.put("close_pending",this.close_pending);
		response.put("cancelled",this.cancelled);
		response.put("timedout",this.timedout);
		response.put("xel_per_bounty",this.getXel_per_bounty());
		response.put("balance_pow_fund",this.balance_pow_fund);
		response.put("balance_bounty_fund",this.balance_bounty_fund);
		response.put("balance_pow_fund_orig",this.balance_pow_fund_orig);
		response.put("balance_bounty_fund_orig",this.balance_bounty_fund_orig);
		response.put("received_bounties",this.received_bounties);
		response.put("received_bounty_announcements",this.received_bounty_announcements);

		response.put("received_pows",this.received_pows);    
		response.put("bounty_limit",this.bounty_limit);
		response.put("sender_account_id",Convert.toUnsignedLong(this.sender_account_id));
		//response.put("height",this.height);
		return response;
	}
	

public JSONObject toJsonObjectWithSource() {
	JSONObject obj = toJsonObject();
	
	PrunableSourceCode p = PrunableSourceCode.getPrunableSourceCodeByWorkId(this.work_id);
	if(p==null)
		obj.put("source","");
	else
		obj.put("source",Ascii85.encode(p.getSource()));
	
	return obj;
}

	public boolean isCancelled() {
		return cancelled;
	}

	public boolean isTimedout() {
		return timedout;
	}

	public void reduce_one_pow_submission(Block bl) {
		if(this.isClosed() == false && this.isClose_pending() == false){
			
			if(balance_pow_fund>=this.xel_per_pow){
				this.balance_pow_fund -= this.xel_per_pow;
				this.received_pows++;
			}
			
			if(balance_pow_fund<this.xel_per_pow){
				// all was paid out, close it!
				this.natural_timeout(bl);
			}else{
				workTable.insert(this);
			}
		}
		
	}
	
public void kill_bounty_fund(Block bl) {
		
	if(this.isClosed() == false){
		if(balance_bounty_fund>=this.xel_per_bounty){
			this.balance_bounty_fund -= this.xel_per_bounty;
			this.received_bounties++;
		}
		
		if(balance_bounty_fund<this.xel_per_bounty){
			// all was paid out, close it!
			this.natural_timeout(bl);
		}else{
			workTable.insert(this);
		}
	}
}
public void register_bounty_announcement(Block bl) {
	
	if(this.isClosed() == false && this.isClose_pending() == false){
		this.received_bounty_announcements++;
		if(this.received_bounty_announcements==this.bounty_limit){
			// all was paid out, close it!
			this.natural_timeout(bl);
		}else{
			workTable.insert(this);
		}
	}
}

public static int countAccountWork(long accountId, boolean onlyOpen) {
	if(onlyOpen){
		return workTable.getCount(new DbClause.BooleanClause("closed",false).and(new DbClause.LongClause("sender_account_id",accountId).and(new DbClause.BooleanClause("close_pending",false))));
	}else{
		return workTable.getCount(new DbClause.LongClause("sender_account_id",accountId));
	}
}

public String getWork_min_pow_target() {
	return work_min_pow_target;
}
public BigInteger getWork_min_pow_target_bigint() {
	return new BigInteger(this.getWork_min_pow_target(), 16);
}


}
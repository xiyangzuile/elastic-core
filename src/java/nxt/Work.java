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

import nxt.crypto.AnonymouslyEncryptedData;
import nxt.crypto.Crypto;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.VersionedEntityDbTable;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            /*List<Work> shufflings = new ArrayList<>();
            try (DbIterator<Work> iterator = getActiveShufflings(0, -1)) {
                for (Work shuffling : iterator) {
                    if (!shuffling.isFull(block)) {
                        shufflings.add(shuffling);
                    }
                }
            }
            shufflings.forEach(shuffling -> {
                if (--shuffling.blocksRemaining <= 0) {
                    shuffling.cancel(block);
                } else {
                    workTable.insert(shuffling);
                }
            });*/
        	System.out.println("WORK CALLBACK FIRED!");
        }, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
    }

    public static boolean addListener(Listener<Work> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
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

    public static DbIterator<Work> getActiveShufflings(int from, int to) {
        return workTable.getManyBy(new DbClause.NotNullClause("blocks_remaining"), from, to, " ORDER BY blocks_remaining, height DESC ");
    }

    public static DbIterator<Work> getFinishedShufflings(int from, int to) {
        return workTable.getManyBy(new DbClause.NullClause("blocks_remaining"), from, to, " ORDER BY height DESC ");
    }

    public static Work getWork(long work_id) {
        return workTable.get(workDbKeyFactory.newKey(work_id));
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

    static void init() {}

    private final long id;
    private final DbKey dbKey;
    private final long work_id;
    private final boolean closed;
    private final String title;
    private final long xel_per_pow;
    private final int percentage_powfund;
    private final int bounty_limit;
    private final long balance_pow_fund;
    private final long balance_bounty_fund;
    private final int received_bounties;
    private final int received_pows;
    private short blocksRemaining;

    private Work(Transaction transaction, Attachment.WorkCreation attachment) {
        this.id = transaction.getId();
        this.work_id = this.id;
        this.dbKey = workDbKeyFactory.newKey(this.id);
        this.xel_per_pow = attachment.getXelPerPow();
        this.title = attachment.getWorkTitle();
        this.blocksRemaining = (short) attachment.getDeadline();
        this.closed = false;
        this.percentage_powfund = attachment.getPercentage_pow_fund();
        this.balance_pow_fund = (long)(transaction.getAmountNQT() * 100.0 / this.percentage_powfund + 0.5);
        this.balance_bounty_fund = transaction.getAmountNQT() - balance_pow_fund;
        this.received_bounties = 0;
        this.received_pows = 0;        
        this.bounty_limit = attachment.getBountyLimit();
    }

    private Work(ResultSet rs, DbKey dbKey) throws SQLException {

        
        this.id = rs.getLong("id");
        this.work_id = rs.getLong("work_id");
        this.dbKey = dbKey;
        this.xel_per_pow = rs.getLong("xel_per_pow");
        this.title = rs.getString("title");
        this.blocksRemaining = rs.getShort("blocks_remaining");
        this.closed = rs.getBoolean("closed");
        this.percentage_powfund = rs.getInt("percentage_powfund");
        this.balance_pow_fund = rs.getLong("balance_pow_fund");
        this.balance_bounty_fund = rs.getLong("balance_bounty_fund");
        this.received_bounties = rs.getInt("received_bounties");
        this.received_pows = rs.getInt("received_pows");        
        this.bounty_limit = rs.getInt("bounty_limit");
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO work (id, work_id, xel_per_pow, title, blocks_remaining, closed, percentage_powfund, balance_pow_fund, balance_bounty_fund, received_bounties, received_pows, bounty_limit, height) "
                + "KEY (id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.work_id);
            pstmt.setLong(++i, this.xel_per_pow);
            pstmt.setString(++i, this.title);
            pstmt.setShort(++i, this.blocksRemaining);
            pstmt.setBoolean(++i, this.closed);
            pstmt.setInt(++i, this.percentage_powfund);
            pstmt.setLong(++i, this.balance_pow_fund);
            pstmt.setLong(++i, this.balance_bounty_fund);
            pstmt.setInt(++i, this.received_bounties);
            pstmt.setInt(++i, this.received_pows);
            pstmt.setInt(++i, this.bounty_limit);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public DbKey getDbKey() {
		return dbKey;
	}

	public long getWork_id() {
		return work_id;
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

	public int getPercentage_powfund() {
		return percentage_powfund;
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

}
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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.json.simple.JSONObject;

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
		public DbKey newKey(final Work shuffling) {
			return shuffling.dbKey;
		}

	};

	private static final VersionedEntityDbTable<Work> workTable = new VersionedEntityDbTable<Work>("work",
			Work.workDbKeyFactory) {

		@Override
		protected Work load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
			return new Work(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final Work shuffling) throws SQLException {
			shuffling.save(con);
		}

	};

	static {
		Nxt.getBlockchainProcessor().addListener(block -> {
			final List<Work> shufflings = new ArrayList<>();
			try (DbIterator<Work> iterator = Work.getActiveAndPendingWorks(0, -1)) {
				for (final Work shuffling : iterator) {
					shufflings.add(shuffling);
				}
			}
			shufflings.forEach(shuffling -> {
				if (shuffling.close_pending || (--shuffling.blocksRemaining <= 0)) {
					// Work has timed out natually
					shuffling.natural_timeout(block);
				} else {
					shuffling.updatePowTarget(block);
					Work.workTable.insert(shuffling);
				}
			});
		}, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
	}

	private static final double LOG2 = Math.log(2.0);

	public static boolean addListener(final Listener<Work> listener, final Event eventType) {
		return Work.listeners.addListener(listener, eventType);
	}

	static void addWork(final Transaction transaction, final Attachment.WorkCreation attachment) {
		final Work shuffling = new Work(transaction, attachment);
		Work.workTable.insert(shuffling);
		Work.listeners.notify(shuffling, Event.WORK_CREATED);
	}

	public static int countAccountWork(final long accountId, final boolean onlyOpen) {
		if (onlyOpen) {
			return Work.workTable.getCount(new DbClause.BooleanClause("closed", false)
					.and(new DbClause.LongClause("sender_account_id", accountId)
							.and(new DbClause.BooleanClause("close_pending", false))));
		} else {
			return Work.workTable.getCount(new DbClause.LongClause("sender_account_id", accountId));
		}
	}

	public static List<Work> getAccountWork(final long accountId, final boolean includeFinished, final int from,
			final int to, final long onlyOneId) {
		final List<Work> ret = new ArrayList<>();

		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT work.* FROM work WHERE work.sender_account_id = ? "
								+ (includeFinished ? "" : "AND work.blocks_remaining IS NOT NULL ")
								+ (onlyOneId == 0 ? "" : "AND work.work_id = ? ")
								+ "AND work.latest = TRUE ORDER BY closed, close_pending, originating_height DESC "
								+ DbUtils.limitsClause(from, to))) {
			int i = 0;
			pstmt.setLong(++i, accountId);
			if (onlyOneId != 0) {
				pstmt.setLong(++i, onlyOneId);
			}
			DbUtils.setLimits(++i, pstmt, from, to);
			try (DbIterator<Work> w_it = Work.workTable.getManyBy(con, pstmt, true)) {
				while (w_it.hasNext()) {
					ret.add(w_it.next());
				}
			} catch (final Exception e) {

			}
			return ret;
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static DbIterator<Work> getActiveAndPendingWorks(final int from, final int to) {
		return Work.workTable.getManyBy(
				new DbClause.BooleanClause("closed", false).and(new DbClause.BooleanClause("latest", true)), from, to,
				" ORDER BY blocks_remaining, height DESC ");
	}

	public static int getActiveCount() {
		return Work.workTable.getCount(new DbClause.BooleanClause("closed", false));
	}

	public static long getActiveMoney() {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"SELECT SUM(balance_pow_fund+balance_bounty_fund) as summ FROM work WHERE work.closed = FALSE AND work.latest = TRUE")) {
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return rs.getLong("summ");
				} else {
					return 0;
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static DbIterator<Work> getActiveWorks(final int from, final int to) {
		return Work.workTable.getManyBy(
				new DbClause.BooleanClause("closed", false).and(new DbClause.BooleanClause("latest", true))
						.and(new DbClause.BooleanClause("close_pending", false)),
				from, to, " ORDER BY blocks_remaining, height DESC ");
	}

	public static DbIterator<Work> getAll(final int from, final int to) {
		return Work.workTable.getAll(from, to, " ORDER BY blocks_remaining NULLS LAST, height DESC ");
	}

	public static DbIterator<Work> getAllActive() {
		return Work.workTable.getManyBy(
				new DbClause.BooleanClause("closed", false).and(new DbClause.BooleanClause("close_pending", false)), 0,
				Integer.MAX_VALUE);
	}

	public static int getCount() {
		return Work.workTable.getCount();
	}

	public static DbIterator<Work> getLastTenClosed() {
		return Work.workTable.getManyBy(new DbClause.BooleanClause("closed", true), 0, 10,
				" ORDER BY closing_timestamp DESC");
	}

	public static Work getWork(final byte[] fullHash) {
		final long shufflingId = Convert.fullHashToId(fullHash);
		final Work shuffling = Work.workTable.get(Work.workDbKeyFactory.newKey(shufflingId));
		if ((shuffling != null) && !Arrays.equals(shuffling.getFullHash(), fullHash)) {
			Logger.logDebugMessage("Shuffling with different hash %s but same id found for hash %s",
					Convert.toHexString(shuffling.getFullHash()), Convert.toHexString(fullHash));
			return null;
		}
		return shuffling;
	}

	public static Work getWork(final long id) {
		return Work.workTable.get(Work.workDbKeyFactory.newKey(id));
	}

	public static Work getWorkByWorkId(final long work_id) {

		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT work.* FROM work WHERE work.work_id = ? AND work.latest = TRUE")) {
			int i = 0;
			pstmt.setLong(++i, work_id);
			final DbIterator<Work> it = Work.workTable.getManyBy(con, pstmt, true);
			Work w = null;
			if (it.hasNext()) {
				w = it.next();
			}
			it.close();
			return w;
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	static void init() {
	}

	public static double logBigDecimal(final BigDecimal val) {
		return Work.logBigInteger(val.unscaledValue()) + (val.scale() * Math.log(10.0));
	}

	public static double logBigInteger(BigInteger val) {
		final int blex = val.bitLength() - 1022; // any value in 60..1023 is ok
		if (blex > 0) {
			val = val.shiftRight(blex);
		}
		final double res = Math.log(val.doubleValue());
		return blex > 0 ? res + (blex * Work.LOG2) : res;
	}

	public static boolean removeListener(final Listener<Work> listener, final Event eventType) {
		return Work.listeners.removeListener(listener, eventType);
	}

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
	private BigInteger work_min_pow_target;
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

	private Work(final ResultSet rs, final DbKey dbKey) throws SQLException {

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
		this.closing_timestamp = rs.getInt("closing_timestamp");
		this.work_min_pow_target = new BigInteger(rs.getBytes("work_min_pow_target"));
	}

	private Work(final Transaction transaction, final Attachment.WorkCreation attachment) {
		this.id = transaction.getId();
		this.work_id = this.id;
		this.block_id = transaction.getBlockId();
		this.dbKey = Work.workDbKeyFactory.newKey(this.id);
		this.xel_per_pow = attachment.getXelPerPow();
		this.title = attachment.getWorkTitle();
		this.blocksRemaining = (short) attachment.getDeadline();
		this.closed = false;
		this.close_pending = false;
		this.xel_per_bounty = attachment.getXelPerBounty();
		this.balance_pow_fund = transaction.getAmountNQT()
				- (attachment.getBountyLimit() * attachment.getXelPerBounty());
		this.balance_bounty_fund = (attachment.getBountyLimit() * attachment.getXelPerBounty());
		this.balance_pow_fund_orig = this.balance_pow_fund;
		this.balance_bounty_fund_orig = this.balance_bounty_fund;
		this.received_bounties = 0;
		this.received_bounty_announcements = 0;
		this.received_pows = 0;
		this.bounty_limit = attachment.getBountyLimit();
		this.sender_account_id = transaction.getSenderId();
		this.cancelled = false;
		this.timedout = false;
		this.originating_height = transaction.getBlock().getHeight();
		this.closing_timestamp = 0;
		this.work_min_pow_target = BigInteger.ZERO;
		this.updatePowTarget(transaction.getBlock());
	}

	public long getBalance_bounty_fund() {
		return this.balance_bounty_fund;
	}

	public long getBalance_bounty_fund_orig() {
		return this.balance_bounty_fund_orig;
	}

	public long getBalance_pow_fund() {
		return this.balance_pow_fund;
	}

	public long getBalance_pow_fund_orig() {
		return this.balance_pow_fund_orig;
	}

	public long getBlock_id() {
		return this.block_id;
	}

	public short getBlocksRemaining() {
		return this.blocksRemaining;
	}

	public int getBounty_limit() {
		return this.bounty_limit;
	}

	public DbKey getDbKey() {
		return this.dbKey;
	}

	public byte[] getFullHash() {
		return TransactionDb.getFullHash(this.id);
	}

	public long getId() {
		return this.id;
	}

	public int getReceived_bounties() {
		return this.received_bounties;
	}

	public int getReceived_bounty_announcements() {
		return this.received_bounty_announcements;
	}

	public int getReceived_pows() {
		return this.received_pows;
	}

	public long getSender_account_id() {
		return this.sender_account_id;
	}

	public String getTitle() {
		return this.title;
	}

	public long getWork_id() {
		return this.work_id;
	}

	public BigInteger getWork_min_pow_target() {
		return this.work_min_pow_target;
	}

	public long getXel_per_bounty() {
		return this.xel_per_bounty;
	}

	public long getXel_per_pow() {
		return this.xel_per_pow;
	}

	public boolean isCancelled() {
		return this.cancelled;
	}

	public boolean isClose_pending() {
		return this.close_pending;
	}

	public boolean isClosed() {
		return this.closed;
	}

	public boolean isTimedout() {
		return this.timedout;
	}

	public void kill_bounty_fund(final Block bl) {

		if (this.isClosed() == false) {
			if (this.balance_bounty_fund >= this.xel_per_bounty) {
				this.balance_bounty_fund -= this.xel_per_bounty;
				this.received_bounties++;
			}

			if (this.balance_bounty_fund < this.xel_per_bounty) {
				// all was paid out, close it!
				this.natural_timeout(bl);
			} else {
				Work.workTable.insert(this);
			}
		}
	}

	public void natural_timeout(final Block bl) {

		if (this.closed == true) {
			return;
		}

		if ((this.close_pending == false) && (this.closed == false)) {
			// Check if cancelled or timedout
			if ((this.blocksRemaining == 0) && (this.balance_pow_fund >= this.xel_per_pow)
					&& (this.received_bounties < this.bounty_limit)
					&& (this.received_bounty_announcements < this.bounty_limit)) {
				// timedout with money remaining and bounty slots remaining
				this.timedout = true;
				this.closing_timestamp = bl.getTimestamp();
				if (this.received_bounties == this.received_bounty_announcements) {
					this.closed = true;
					// Now create ledger event for "refund" what is left in the
					// pow and bounty funds
					final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_CANCELLATION;
					final Account participantAccount = Account.getAccount(this.sender_account_id);
					participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id,
							this.balance_pow_fund + this.balance_bounty_fund);

				} else {
					this.close_pending = true;
				}

			} else if ((this.blocksRemaining > 0)
					&& ((this.balance_pow_fund < this.xel_per_pow) || (this.received_bounties == this.bounty_limit)
							|| (this.received_bounty_announcements == this.bounty_limit))) {
				// closed regularily, nothing to bother about
				this.closing_timestamp = bl.getTimestamp();
				if (this.received_bounties == this.received_bounty_announcements) {
					this.closed = true;
					// Now create ledger event for "refund" what is left in the
					// pow and bounty funds
					final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_CANCELLATION;
					final Account participantAccount = Account.getAccount(this.sender_account_id);
					participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id,
							this.balance_pow_fund + this.balance_bounty_fund);

				} else {
					this.close_pending = true;
				}
			} else {
				// manual cancellation
				this.cancelled = true;
				this.closing_timestamp = bl.getTimestamp();
				if (this.received_bounties == this.received_bounty_announcements) {
					this.closed = true;
					// Now create ledger event for "refund" what is left in the
					// pow and bounty funds
					final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_CANCELLATION;
					final Account participantAccount = Account.getAccount(this.sender_account_id);
					participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id,
							this.balance_pow_fund + this.balance_bounty_fund);

				} else {
					this.close_pending = true;
				}
			}
		} else if ((this.close_pending == true) && (this.closed == false)) {
			if (((bl.getTimestamp() - this.closing_timestamp) >= Constants.DEPOSIT_GRACE_PERIOD)
					|| (this.received_bounty_announcements == this.received_bounties)) {
				this.closed = true;
				this.close_pending = false;

				int refundAnnouncements = 0;
				if (this.received_bounty_announcements > this.received_bounties) {
					refundAnnouncements = this.received_bounty_announcements - this.received_bounties;
				}
				// Now create ledger event for "refund" what is left in the pow
				// and bounty funds
				final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_CANCELLATION;
				final Account participantAccount = Account.getAccount(this.sender_account_id);
				participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id,
						this.balance_pow_fund + this.balance_bounty_fund);

				// And move forfeited deposits to FORFEITED_DEPOSITS_ACCOUNT account
				final Account forfeitedAccount = Account.addOrGetAccount(Constants.FORFEITED_DEPOSITS_ACCOUNT);
				final Account depositAccount = Account.addOrGetAccount(Constants.DEPOSITS_ACCOUNT);
				if (depositAccount.getUnconfirmedBalanceNQT() < (refundAnnouncements * Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION)) {
					// Deposit is just lost, no idea what happened (but it should never happen)
				}else {

					depositAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id,
							-1 * (refundAnnouncements * Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION));
					forfeitedAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id,
							(refundAnnouncements * Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION));
				}

			} else {
				// pass through
			}
		}

		Work.workTable.insert(this);

		// notify
		Work.listeners.notify(this, Event.WORK_CANCELLED);

	}

	public void reduce_one_pow_submission(final Block bl) {
		if ((this.isClosed() == false) && (this.isClose_pending() == false)) {

			if (this.balance_pow_fund >= this.xel_per_pow) {
				this.balance_pow_fund -= this.xel_per_pow;
				this.received_pows++;
			}

			if (this.balance_pow_fund < this.xel_per_pow) {
				// all was paid out, close it!
				this.natural_timeout(bl);
			} else {
				Work.workTable.insert(this);
			}
		}

	}

	public void register_bounty_announcement(final Block bl) {

		if ((this.isClosed() == false) && (this.isClose_pending() == false)) {
			this.received_bounty_announcements++;
			if (this.received_bounty_announcements == this.bounty_limit) {
				// all was paid out, close it!
				this.natural_timeout(bl);
			} else {
				Work.workTable.insert(this);
			}
		}
	}

	private void save(final Connection con) throws SQLException {
		try (PreparedStatement pstmt = con.prepareStatement(
				"MERGE INTO work (id, closing_timestamp, work_id, block_id, sender_account_id, xel_per_pow, title, blocks_remaining, closed, close_pending, cancelled, timedout, xel_per_bounty, balance_pow_fund, balance_bounty_fund, balance_pow_fund_orig, balance_bounty_fund_orig, received_bounties, received_bounty_announcements, received_pows, bounty_limit, originating_height, height, work_min_pow_target, latest) "
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
			pstmt.setInt(++i, Nxt.getBlockchain().getBlock(this.block_id).getHeight());
			pstmt.setBytes(++i, this.work_min_pow_target.toByteArray());

			pstmt.executeUpdate();
		}
	}

	public void setBlocksRemaining(final short blocksRemaining) {
		this.blocksRemaining = blocksRemaining;
	}

	public void setClose_pending(final boolean close_pending) {
		this.close_pending = close_pending;
	}

	public JSONObject toJsonObject() {
		final JSONObject response = new JSONObject();
		response.put("id", Convert.toUnsignedLong(this.id));
		response.put("work_id", Convert.toUnsignedLong(this.work_id));
		response.put("block_id", Convert.toUnsignedLong(this.block_id));
		response.put("xel_per_pow", this.xel_per_pow);
		response.put("title", this.title);
		response.put("originating_height", this.originating_height);
		response.put("blocksRemaining", this.blocksRemaining);
		response.put("closed", this.closed);
		response.put("closing_timestamp", this.closing_timestamp);
		response.put("close_pending", this.close_pending);
		response.put("cancelled", this.cancelled);
		response.put("timedout", this.timedout);
		response.put("xel_per_bounty", this.getXel_per_bounty());
		response.put("balance_pow_fund", this.balance_pow_fund);
		response.put("balance_bounty_fund", this.balance_bounty_fund);
		response.put("balance_pow_fund_orig", this.balance_pow_fund_orig);
		response.put("balance_bounty_fund_orig", this.balance_bounty_fund_orig);
		response.put("received_bounties", this.received_bounties);
		response.put("received_bounty_announcements", this.received_bounty_announcements);

		response.put("received_pows", this.received_pows);
		response.put("bounty_limit", this.bounty_limit);
		response.put("sender_account_id", Convert.toUnsignedLong(this.sender_account_id));
		// response.put("height",this.height);
		response.put("target", Convert.toHexString(this.work_min_pow_target.toByteArray()));
		return response;
	}

	public JSONObject toJsonObjectWithSource() {
		final JSONObject obj = this.toJsonObject();

		final PrunableSourceCode p = PrunableSourceCode.getPrunableSourceCodeByWorkId(this.work_id);
		if (p == null) {
			obj.put("source", "");
		} else {
			obj.put("source", Ascii85.encode(Convert.uncompress(p.getSource())));
		}

		return obj;
	}
	
	public double kimoto(double x){
	    return 1 + (0.7084 * Math.pow(((x)/(28.0)), -1.228));
	}
	
	public void updatePowTarget(final Block currentBlock) {

		// Initialize with the blocks base target (this is set in
		// BlockImpl::calculateNextMinPowTarget
		// to the lowest difficulty over the last 1<=n<=10 closed jobs,
		// or to the minimal possible difficulty if there aren't any closed jobs
		// yet)

		BigInteger targetI = null;
		if (Objects.equals(this.work_min_pow_target, BigInteger.ZERO)) {
			targetI = Nxt.getBlockchain().getBlock(this.getBlock_id()).getMinPowTarget();
		} else {
			targetI = this.work_min_pow_target;
		}

		if (currentBlock.getId() != this.getBlock_id()) {
			// Do standard retargeting (yet to be peer reviewed)

			long PastBlocksMass = 0;
			final int account_for_blocks_max = 28;
			final int account_for_blocks_min = 1;
			final int max_full_check_depth = 10;
			long seconds_passed = 0;
			long PastBlocksTotalMass = 0;
			boolean isFull = true;
			boolean isEmpty = true;
			int fullCnt = 0;
			int emptyCnt = 0;
			double local_adjustment = 1;
			Block b = currentBlock;
			int counter = 0;
			int current_timestamp = b.getTimestamp();
			double trs_per_second = 1, target_per_second = 1;
			while (isFull || isEmpty) {
				if ((b == null) || (b.getId() == this.getBlock_id())) {
					break;
				}
				counter = counter + 1;
				long num = b.countNumberPOWPerWorkId(this.getId());
				
				if (isFull && num == 20)
					fullCnt+=1;
				else
					isFull = false;
				
				if (isEmpty && num == 0)
					emptyCnt+=1;
				else
					isEmpty = false;				
				
				if ((b.getPreviousBlock() == null) || (counter == account_for_blocks_max)) {
					break;
				}
				b = b.getPreviousBlock();
			}
			b = currentBlock;
			counter = 0;
			while (true) {
				if ((b == null) || (b.getId() == this.getBlock_id())) {
					break;
				}
				counter = counter + 1;
				PastBlocksMass += b.countNumberPOWPerWorkId(this.getId());
				PastBlocksTotalMass += b.countNumberPOW();
				
				
				if(b.getTimestamp()>current_timestamp)
					current_timestamp = b.getTimestamp(); // HERE USE PREV BLOCK TIMESTAMP
				
				seconds_passed = current_timestamp - b.getTimestampPrevious();
				
				if (seconds_passed < 1) {
					seconds_passed = 1;
				}
				
				trs_per_second = (double)PastBlocksMass / (double)seconds_passed;
				target_per_second = 10.0/60.0;
				
				if(trs_per_second > 0)
				{
					local_adjustment = target_per_second / trs_per_second;
					double kim = kimoto(PastBlocksMass * 30);
					
					if (counter >= account_for_blocks_min && (local_adjustment > kim || local_adjustment < 1/kim)){
						Logger.logKomotoMessage("Komoto: kim = " + kim + ", 1/kim = " + (1/kim) + ", trs_per_second = " + trs_per_second + ", adjustment = " + local_adjustment);
						break;
					}
				}else{
				}
				if ((b.getPreviousBlock() == null) || (counter == account_for_blocks_max)) {
					break;
				}
				b = b.getPreviousBlock();
			}
			

			if (!(emptyCnt == 10 && PastBlocksTotalMass>0)) {
				// We have received at least one POW in the last 60 seconds
				Logger.logKomotoMessage("\n\n*** RETARGETING ***");
				Logger.logKomotoMessage("Workid: " + this.getId());
				Logger.logKomotoMessage("Accounted last blocks: " + counter);
				Logger.logKomotoMessage("Blocks span how much time: " + seconds_passed);
				Logger.logKomotoMessage("How many seen POWs: " + PastBlocksMass);
				Logger.logKomotoMessage("Scalingfactor: " + local_adjustment + "\n\n");
				
			} else {
				// This job is just too boring, others still get POWs
				local_adjustment = 1;
				Logger.logKomotoMessage("\n\n*** RETARGETING ***");
				Logger.logKomotoMessage("Workid: " + this.getId());
				Logger.logKomotoMessage("Skipped retargeting, no POW received for this job but others!\n\n");

			}
			
			if(fullCnt>1)
				local_adjustment = local_adjustment / (1<<fullCnt);
			if(emptyCnt>1)
				local_adjustment = local_adjustment * (1<<emptyCnt);
			
			BigDecimal intermediate = new BigDecimal(targetI);
			intermediate = intermediate.multiply(BigDecimal.valueOf(local_adjustment));
			targetI = intermediate.toBigInteger();
			
			
			if (targetI.compareTo(Constants.least_possible_target) == 1) {
				targetI = Constants.least_possible_target;
			} else if (targetI.compareTo(BigInteger.valueOf(1L)) == -1) { 
				targetI = BigInteger.valueOf(1L);
			}
			Logger.logKomotoMessage("New target: " + Convert.toHexString(targetI.toByteArray()));


		} else {
			// do nothing, especially when its the block where the work was
			// included
		}
		this.work_min_pow_target = targetI;

	}

}
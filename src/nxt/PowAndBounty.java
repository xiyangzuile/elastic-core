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
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

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

	private static final DbKey.LongKeyFactory<PowAndBounty> powAndBountyDbKeyFactory = new DbKey.LongKeyFactory<PowAndBounty>(
			"id") {

		@Override
		public DbKey newKey(final PowAndBounty participant) {
			return participant.dbKey;
		}

	};

	private static final VersionedEntityDbTable<PowAndBounty> powAndBountyTable = new VersionedEntityDbTable<PowAndBounty>(
			"pow_and_bounty", PowAndBounty.powAndBountyDbKeyFactory) {

		@Override
		protected PowAndBounty load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
			return new PowAndBounty(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final PowAndBounty participant) throws SQLException {
			participant.save(con);
		}

	};


	static void addBounty(final Transaction transaction, final Attachment.PiggybackedProofOfBounty attachment) {
		final PowAndBounty shuffling = new PowAndBounty(transaction, attachment);
		PowAndBounty.powAndBountyTable.insert(shuffling);
		PowAndBounty.listeners.notify(shuffling, Event.BOUNTY_SUBMITTED);
	}

	public static boolean addListener(final Listener<PowAndBounty> listener, final Event eventType) {
		return PowAndBounty.listeners.addListener(listener, eventType);
	}

	static void addPow(final Transaction transaction, final Attachment.PiggybackedProofOfWork attachment) {
		final PowAndBounty shuffling = new PowAndBounty(transaction, attachment);
		PowAndBounty.powAndBountyTable.insert(shuffling);

		PowAndBounty.listeners.notify(shuffling, Event.POW_SUBMITTED);
	}


	static Map<Long, Integer> GetAccountBountyMap(final long wid) {
		final DbIterator<PowAndBounty> it = PowAndBounty.getBounties(wid);
		final Map<Long, Integer> map = new HashMap<>();
		while (it.hasNext()) {
			final PowAndBounty p = it.next();
			if (map.containsKey(p.getAccountId()) == false) {
				map.put(p.getAccountId(), 1);
			} else {
				final int ik = map.get(p.getAccountId());
				map.put(p.getAccountId(), ik + 1);
			}
		}
		it.close();
		return map;
	}

	public static DbIterator<PowAndBounty> getBounties(final long wid) {
		return PowAndBounty.powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid)
				.and(new DbClause.BooleanClause("is_pow", false)).and(new DbClause.BooleanClause("latest", true)), 0,
				-1, "");
	}

	public static DbIterator<PowAndBounty> getBounties(final long wid, final long aid) {
		return PowAndBounty.powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid)
				.and(new DbClause.BooleanClause("is_pow", false)).and(new DbClause.LongClause("account_id", aid))
				.and(new DbClause.BooleanClause("latest", true)), 0, -1, "");
	}

	static int getBountyCount(final long wid) {
		return PowAndBounty.powAndBountyTable
				.getCount(new DbClause.LongClause("work_id", wid).and(new DbClause.BooleanClause("is_pow", false)));
	}

	static int getPowCount(final long wid) {
		return PowAndBounty.powAndBountyTable
				.getCount(new DbClause.LongClause("work_id", wid).and(new DbClause.BooleanClause("is_pow", true)));
	}

	public static PowAndBounty getPowOrBountyById(final long id) {
		return PowAndBounty.powAndBountyTable.get(PowAndBounty.powAndBountyDbKeyFactory.newKey(id));
	}

	public static DbIterator<PowAndBounty> getPows(final long wid) {
		return PowAndBounty.powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid)
				.and(new DbClause.BooleanClause("is_pow", true)).and(new DbClause.BooleanClause("latest", true)), 0, -1,
				"");
	}

	public static DbIterator<PowAndBounty> getPows(final long wid, final long aid) {
		return PowAndBounty.powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid)
				.and(new DbClause.BooleanClause("is_pow", true)).and(new DbClause.LongClause("account_id", aid))
				.and(new DbClause.BooleanClause("latest", true)), 0, -1, "");
	}

	static boolean hasHash(final long workId, final byte[] hash) {
		return PowAndBounty.powAndBountyTable
				.getCount(new DbClause.BytesClause("hash", hash).and(new DbClause.LongClause("work_id", workId))) > 0;
	}

	static void init() {
	}

	public static boolean removeListener(final Listener<PowAndBounty> listener, final Event eventType) {
		return PowAndBounty.listeners.removeListener(listener, eventType);
	}

	private final long id;
	private final boolean is_pow;
	private boolean too_late;
	private final long work_id;
	private final long accountId;
	private final DbKey dbKey;

	private final byte[] multiplicator;
	private final byte[] hash;

	private PowAndBounty(final ResultSet rs, final DbKey dbKey) throws SQLException {
		this.id = rs.getLong("id");
		this.work_id = rs.getLong("work_id");
		this.accountId = rs.getLong("account_id");
		this.is_pow = rs.getBoolean("is_pow");
		this.dbKey = dbKey;
		this.multiplicator = rs.getBytes("multiplicator");
		this.too_late = rs.getBoolean("too_late");
		this.hash = rs.getBytes("hash");
	}

	private PowAndBounty(final Transaction transaction, final Attachment.PiggybackedProofOfBounty attachment) {
		this.id = transaction.getId();
		this.work_id = attachment.getWorkId();
		this.accountId = transaction.getSenderId();
		this.dbKey = PowAndBounty.powAndBountyDbKeyFactory.newKey(this.id);
		this.multiplicator = attachment.getMultiplicator();
		this.is_pow = false;
		this.hash = attachment.getHash(); // FIXME TODO
		this.too_late = false;
	}

	private PowAndBounty(final Transaction transaction, final Attachment.PiggybackedProofOfWork attachment) {
		this.id = transaction.getId();
		this.work_id = attachment.getWorkId();
		this.accountId = transaction.getSenderId();
		this.dbKey = PowAndBounty.powAndBountyDbKeyFactory.newKey(this.id);
		this.multiplicator = attachment.getMultiplicator();
		this.is_pow = true;
		this.hash = attachment.getHash(); // FIXME TODO
		this.too_late = false;
	}

	public void applyBounty(final Block bl, long supernodeId) throws NxtException.NotValidException {
		final Work w = Work.getWorkByWorkId(this.work_id);
		if (w == null) {
			throw new NxtException.NotValidException("No such work found");
		}
		if (w.isClosed() == false) {
			// Immediate payout incl. the bounty deposit
			final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_BOUNTY_PAYOUT;
			final Account participantAccount = Account.getAccount(this.accountId);
			final Account depositAccount = Account.addOrGetAccount(Constants.DEPOSITS_ACCOUNT);
			final Account snAccount = Account.getAccount(supernodeId);
			if (depositAccount.getUnconfirmedBalanceNQT() < Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION) {
				throw new NxtException.NotValidException("Something went wrong with the deposit account, shouldn't happen");
			}

			long payUser = w.getXel_per_bounty();
			long paySn = (payUser * Constants.SUPERNODE_PERCENTAGE_EARNINGS) / 100;
			payUser = payUser - paySn;
			participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id,
					payUser + Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION);
			depositAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id,
					-1*Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION);
			snAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, paySn);
			w.kill_bounty_fund(bl);
		} else {
			this.too_late = true;
			PowAndBounty.powAndBountyTable.insert(this);
		}
	}

	public void applyPowPayment(final Block bl, long supernodeId) throws NxtException.NotValidException {
		final Work w = Work.getWorkByWorkId(this.work_id);

		if (w == null) {
			throw new NxtException.NotValidException("Work not found");
		}

		if ((w.isClosed() == false) && (w.isClose_pending() == false)) {
			// Now create ledger event for "bounty submission"
			final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_POW;
			final Account participantAccount = Account.getAccount(this.accountId);
			final Account snAccount = Account.getAccount(supernodeId);
			long payUser =  w.getXel_per_pow();
			long paySn = (payUser * Constants.SUPERNODE_PERCENTAGE_EARNINGS) / 100;
			payUser = payUser - paySn;
			participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, payUser);
			snAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, paySn);

			// Reduce work remaining xel
			w.reduce_one_pow_submission(bl);
		} else {
			this.too_late = true;
			PowAndBounty.powAndBountyTable.insert(this);
		}

	}

	public long getAccountId() {
		return this.accountId;
	}

	public byte[] getMultiplicator() {
		return this.multiplicator;
	}

	private void save(final Connection con) throws SQLException {
		try (PreparedStatement pstmt = con.prepareStatement(
				"MERGE INTO pow_and_bounty (id, too_late, work_id, hash, account_id, multiplicator, is_pow,"
						+ " height) " + "KEY (id, height) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
			int i = 0;
			pstmt.setLong(++i, this.id);
			pstmt.setBoolean(++i, this.too_late);
			pstmt.setLong(++i, this.work_id);
			DbUtils.setBytes(pstmt, ++i, this.hash);
			pstmt.setLong(++i, this.accountId);
			pstmt.setBytes(++i, this.multiplicator);
			pstmt.setBoolean(++i, this.is_pow);
			pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
			pstmt.executeUpdate();
		}
	}

	public JSONObject toJsonObject() {
		final JSONObject response = new JSONObject();
		response.put("id", Convert.toUnsignedLong(this.id));
		final Transaction t = TransactionDb.findTransaction(this.id);
		if (t != null) {
			response.put("date", Convert.toUnsignedLong(t.getTimestamp()));
			response.put("multiplicator", Arrays.toString(this.multiplicator));
		} else {
			response.put("error", "Transaction not found");
		}
		return response;
	}

}
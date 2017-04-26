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

// LEAVE THIS OUT FOR NOW
/*
package nxt;

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

	private static final DbKey.LongKeyFactory<PowAndBountyAnnouncements> powAndBountyAnnouncementDbKeyFactory = new DbKey.LongKeyFactory<PowAndBountyAnnouncements>(
			"id") {

		@Override
		public DbKey newKey(final PowAndBountyAnnouncements participant) {
			return participant.dbKey;
		}

	};

	private static final VersionedEntityDbTable<PowAndBountyAnnouncements> powAndBountyAnnouncementTable = new VersionedEntityDbTable<PowAndBountyAnnouncements>(
			"pow_and_bounty_announcements", PowAndBountyAnnouncements.powAndBountyAnnouncementDbKeyFactory) {

		@Override
		protected PowAndBountyAnnouncements load(final Connection con, final ResultSet rs, final DbKey dbKey)
				throws SQLException {
			return new PowAndBountyAnnouncements(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final PowAndBountyAnnouncements participant) throws SQLException {
			participant.save(con);
		}

	};

	static PowAndBountyAnnouncements addBountyAnnouncement(final Transaction transaction,
			final Attachment.PiggybackedProofOfBountyAnnouncement attachment) {
		final PowAndBountyAnnouncements shuffling = new PowAndBountyAnnouncements(transaction, attachment);
		PowAndBountyAnnouncements.powAndBountyAnnouncementTable.insert(shuffling);
		PowAndBountyAnnouncements.listeners.notify(shuffling, Event.BOUNTY_ANNOUNCEMENT_SUBMITTED);
		return shuffling;
	}

	public static boolean addListener(final Listener<PowAndBountyAnnouncements> listener, final Event eventType) {
		return PowAndBountyAnnouncements.listeners.addListener(listener, eventType);
	}

	public static PowAndBountyAnnouncements getPowOrBountyById(final long id) {
		return PowAndBountyAnnouncements.powAndBountyAnnouncementTable
				.get(PowAndBountyAnnouncements.powAndBountyAnnouncementDbKeyFactory.newKey(id));
	}

	public static boolean hasHash(final long work_id, final byte[] hash) {
		return PowAndBountyAnnouncements.powAndBountyAnnouncementTable
				.getCount(new DbClause.BytesClause("hash", hash).and(new DbClause.LongClause("work_id", work_id))) > 0;
	}

	public static boolean hasValidHash(final long work_id, final byte[] hash) {
		return PowAndBountyAnnouncements.powAndBountyAnnouncementTable
				.getCount(new DbClause.BytesClause("hash", hash).and(new DbClause.LongClause("work_id", work_id))
						.and(new DbClause.BooleanClause("too_late", false))) > 0;
	}

	static void init() {
	}

	public static boolean removeListener(final Listener<PowAndBountyAnnouncements> listener, final Event eventType) {
		return PowAndBountyAnnouncements.listeners.removeListener(listener, eventType);
	}

	private final long id;
	private boolean too_late;
	private final long work_id;
	private final long accountId;
	private final DbKey dbKey;
	private final byte[] hash;

	private PowAndBountyAnnouncements(final ResultSet rs, final DbKey dbKey) throws SQLException {
		this.id = rs.getLong("id");
		this.work_id = rs.getLong("work_id");
		this.accountId = rs.getLong("account_id");
		this.dbKey = dbKey;
		this.too_late = rs.getBoolean("too_late");
		this.hash = rs.getBytes("hash");
	}

	private PowAndBountyAnnouncements(final Transaction transaction,
			final Attachment.PiggybackedProofOfBountyAnnouncement attachment) {
		this.id = transaction.getId();
		this.work_id = attachment.getWorkId();
		this.accountId = transaction.getSenderId();
		this.dbKey = PowAndBountyAnnouncements.powAndBountyAnnouncementDbKeyFactory.newKey(this.id);
		this.hash = attachment.getHashAnnounced(); // FIXME TODO
		this.too_late = false;
	}

	public void applyBountyAnnouncement(final Block bl) throws NxtException.NotValidException  {
		final Work w = Work.getWorkByWorkId(this.work_id);
		if (w == null) throw new NxtException.NotValidException("Unknown work id!");
		if ((!w.isClosed()) && (!w.isClose_pending())) {
			// Now create ledger event for "bounty submission"
			final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_BOUNTY_ANNOUNCEMENT;
			final Account participantAccount = Account.getAccount(this.accountId);
			final Account depositAccount = Account.addOrGetAccount(Constants.DEPOSITS_ACCOUNT);

			if (participantAccount.getBalanceNQT() < Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION)
				throw new NxtException.NotValidException("Insufficient funds for deposit");

			participantAccount.addToBalanceNQT(event, this.id,
					-1 * Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION);
			depositAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id,
					Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION);
			w.register_bounty_announcement(bl);
		} else {
			this.too_late = true;
			PowAndBountyAnnouncements.powAndBountyAnnouncementTable.insert(this);
		}

	}

	public long getAccountId() {
		return this.accountId;
	}

	private void save(final Connection con) throws SQLException {
		try (PreparedStatement pstmt = con
				.prepareStatement("MERGE INTO pow_and_bounty_announcements (id, too_late, work_id, hash, account_id, "
						+ " height) " + "KEY (id, height) " + "VALUES (?,  ?, ?, ?, ?, ?)")) {
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

	public JSONObject toJsonObject() {
		final JSONObject response = new JSONObject();
		response.put("id", Convert.toUnsignedLong(this.id));
		final Transaction t = TransactionDb.findTransaction(this.id);
		if (t != null) {
			response.put("date", Convert.toUnsignedLong(t.getTimestamp()));
			response.put("hash_announcement", Arrays.toString(this.hash));
		} else response.put("error", "Transaction not found");

		return response;
	}

}*/
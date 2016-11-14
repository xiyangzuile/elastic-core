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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.PrunableDbTable;

public final class Redeem {

	Map<String, Long> allowedRedeems = new HashMap<String, Long>();
	
	public static String[] listOfAddresses = {"1JwSSubhmg6iPtRjtyqhUYYH7bZg3Lfy1T"};
	public static Long[] amounts = {1419299300000L};

	
	public static boolean hasAddress(String targetValue) {
		for(String s: listOfAddresses){
			if(s.equals(targetValue))
				return true;
		}
		return false;
	}
	
	public static Long getClaimableAmount(String targetValue) {
		int cntr = 0;
		for(String s: listOfAddresses){
			if(s.equals(targetValue))
				return amounts[cntr];
			cntr += 1;
		}
		return 0L;
	}
	
	private static final DbKey.LongKeyFactory<Redeem> redeemKeyFactory = new DbKey.LongKeyFactory<Redeem>("id") {
		@Override
		public DbKey newKey(Redeem prunableSourceCode) {
			return prunableSourceCode.dbKey;
		}
	};

	private static final PrunableDbTable<Redeem> redeemTable = new PrunableDbTable<Redeem>("redeems",
			redeemKeyFactory) {
		@Override
		protected Redeem load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
			return new Redeem(rs, dbKey);
		}

		@Override
		protected void save(Connection con, Redeem prunableSourceCode) throws SQLException {
			prunableSourceCode.save(con);
		}

		@Override
		protected String defaultSort() {
			return " ORDER BY block_timestamp DESC, db_id DESC ";
		}
	};

	public static int getCount() {
		return redeemTable.getCount();
	}

	public static DbIterator<Redeem> getAll(int from, int to) {
		return redeemTable.getAll(from, to);
	}

	static void init() {
	}

	private final long id;
	private final DbKey dbKey;
	private String address;
	private String secp_signatures;
	private long receiver_id;
	private long amount;
	private final int transactionTimestamp;
	private final int blockTimestamp;
	private final int height;

	private Redeem(Transaction transaction, int blockTimestamp, int height) {
		this.id = transaction.getId();
		this.dbKey = redeemKeyFactory.newKey(this.id);
		this.blockTimestamp = blockTimestamp;
		this.height = height;
		this.transactionTimestamp = transaction.getTimestamp();

		Attachment.RedeemAttachment r = (Attachment.RedeemAttachment) transaction.getAttachment();
		this.address = r.getAddress();
		this.receiver_id = transaction.getRecipientId();
		this.secp_signatures = r.getSecp_signatures();
		this.amount = transaction.getAmountNQT();
	}

	private void update(Transaction tx) {
		Attachment.RedeemAttachment r = (Attachment.RedeemAttachment) tx.getAttachment();
		this.address = r.getAddress();
		this.receiver_id = tx.getRecipientId();
		this.secp_signatures = r.getSecp_signatures();
		this.amount = tx.getAmountNQT();
	}

	private Redeem(ResultSet rs, DbKey dbKey) throws SQLException {
		this.id = rs.getLong("id");
		this.dbKey = dbKey;
		this.address = rs.getString("address");
		this.secp_signatures = rs.getString("secp_signatures");
		this.receiver_id = rs.getLong("receiver_id");
		this.blockTimestamp = rs.getInt("block_timestamp");
		this.transactionTimestamp = rs.getInt("transaction_timestamp");
		this.height = rs.getInt("height");
		this.amount = rs.getLong("amount");
	}

	private void save(Connection con) throws SQLException {

		try (PreparedStatement pstmt = con.prepareStatement(
				"MERGE INTO redeems (id, address, secp_signatures, receiver_id, amount, block_timestamp, transaction_timestamp, height, language) "
						+ "KEY (id) " + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
			int i = 0;
			pstmt.setLong(++i, this.id);
			pstmt.setString(++i, this.address);
			pstmt.setString(++i, this.secp_signatures);
			pstmt.setLong(++i, this.receiver_id);
			pstmt.setLong(++i, this.amount);
			pstmt.setInt(++i, this.blockTimestamp);
			pstmt.setInt(++i, this.transactionTimestamp);
			pstmt.setInt(++i, this.height);
			pstmt.executeUpdate();
		}
	}

	public long getId() {
		return id;
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

	static void add(TransactionImpl transaction) {
		add(transaction, Nxt.getBlockchain().getLastBlockTimestamp(), Nxt.getBlockchain().getHeight());
	}

	static void add(TransactionImpl transaction, int blockTimestamp, int height) {

		boolean was_fresh = false;

		Redeem prunableSourceCode = redeemTable.get(transaction.getDbKey());
		if (prunableSourceCode == null) {
			was_fresh = true;
			prunableSourceCode = new Redeem(transaction, blockTimestamp, height);
		} else if (prunableSourceCode.height != height) {
			throw new RuntimeException("Attempt to modify prunable source code from height " + prunableSourceCode.height
					+ " at height " + height);
		}
		prunableSourceCode.update(transaction);
		redeemTable.insert(prunableSourceCode);

		// Credit the redeemer account
		AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.REDEEM_PAYMENT;
		Account participantAccount = Account.addOrGetAccount(prunableSourceCode.receiver_id);
		if (participantAccount == null) { // should never happen
			participantAccount = Account.getAccount(Genesis.FUCKED_TX_ID);
		}
		if(was_fresh)
			participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, transaction.getId(), prunableSourceCode.amount);
	}

	static boolean isAlreadyRedeemed(String address) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT receipient_id FROM redeems WHERE address = ?")) {
			pstmt.setString(1, address);
			try (ResultSet rs = pstmt.executeQuery()) {
				return !rs.next();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

}

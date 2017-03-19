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

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import nxt.AccountLedger.LedgerEntry;
import nxt.AccountLedger.LedgerEvent;
import nxt.AccountLedger.LedgerHolding;
import nxt.crypto.Crypto;
import nxt.crypto.EncryptedData;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.DerivedDbTable;
import nxt.db.VersionedEntityDbTable;
import nxt.db.VersionedPersistentDbTable;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;

public final class Account {


	public boolean isGuardNode() {
		boolean guardNode = false;
		long myId = this.getId();
		for(long g : Constants.GUARD_NODES){
			if(g == myId){
				guardNode=true;
				break;
			}
		}

		return guardNode;
	}

	public int supernodeExpires() {
		Pair<Integer, Integer> pf_tf = Nxt.getSnAccount().getSupernodeTimeframe();
		return pf_tf.getB() - Nxt.getBlockchain().getHeight();
	}

	public static final class AccountInfo {

		private final long accountId;
		private final DbKey dbKey;
		private String name;
		private String description;

		private AccountInfo(final long accountId, final String name, final String description) {
			this.accountId = accountId;
			this.dbKey = Account.accountInfoDbKeyFactory.newKey(this.accountId);
			this.name = name;
			this.description = description;
		}

		private AccountInfo(final ResultSet rs, final DbKey dbKey) throws SQLException {
			this.accountId = rs.getLong("account_id");
			this.dbKey = dbKey;
			this.name = rs.getString("name");
			this.description = rs.getString("description");
		}

		public long getAccountId() {
			return this.accountId;
		}

		public String getDescription() {
			return this.description;
		}

		public String getName() {
			return this.name;
		}

		private void save() {
			if ((this.name != null) || (this.description != null)) {
				Account.accountInfoTable.insert(this);
			} else {
				Account.accountInfoTable.delete(this);
			}
		}

		private void save(final Connection con) throws SQLException {
			try (PreparedStatement pstmt = con
					.prepareStatement("MERGE INTO account_info " + "(account_id, name, description, height, latest) "
							+ "KEY (account_id, height) VALUES (?, ?, ?, ?, TRUE)")) {
				int i = 0;
				pstmt.setLong(++i, this.accountId);
				DbUtils.setString(pstmt, ++i, this.name);
				DbUtils.setString(pstmt, ++i, this.description);
				pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
				pstmt.executeUpdate();
			}
		}

	}

	public static final class AccountLease {

		private final long lessorId;
		private final DbKey dbKey;
		private long currentLesseeId;
		private int currentLeasingHeightFrom;
		private int currentLeasingHeightTo;
		private long nextLesseeId;
		private int nextLeasingHeightFrom;
		private int nextLeasingHeightTo;

		private AccountLease(final long lessorId, final int currentLeasingHeightFrom, final int currentLeasingHeightTo,
				final long currentLesseeId) {
			this.lessorId = lessorId;
			this.dbKey = Account.accountLeaseDbKeyFactory.newKey(this.lessorId);
			this.currentLeasingHeightFrom = currentLeasingHeightFrom;
			this.currentLeasingHeightTo = currentLeasingHeightTo;
			this.currentLesseeId = currentLesseeId;
		}

		private AccountLease(final ResultSet rs, final DbKey dbKey) throws SQLException {
			this.lessorId = rs.getLong("lessor_id");
			this.dbKey = dbKey;
			this.currentLeasingHeightFrom = rs.getInt("current_leasing_height_from");
			this.currentLeasingHeightTo = rs.getInt("current_leasing_height_to");
			this.currentLesseeId = rs.getLong("current_lessee_id");
			this.nextLeasingHeightFrom = rs.getInt("next_leasing_height_from");
			this.nextLeasingHeightTo = rs.getInt("next_leasing_height_to");
			this.nextLesseeId = rs.getLong("next_lessee_id");
		}

		public int getCurrentLeasingHeightFrom() {
			return this.currentLeasingHeightFrom;
		}

		public int getCurrentLeasingHeightTo() {
			return this.currentLeasingHeightTo;
		}

		public long getCurrentLesseeId() {
			return this.currentLesseeId;
		}

		public long getLessorId() {
			return this.lessorId;
		}

		public int getNextLeasingHeightFrom() {
			return this.nextLeasingHeightFrom;
		}

		public int getNextLeasingHeightTo() {
			return this.nextLeasingHeightTo;
		}

		public long getNextLesseeId() {
			return this.nextLesseeId;
		}

		private void save(final Connection con) throws SQLException {
			try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_lease "
					+ "(lessor_id, current_leasing_height_from, current_leasing_height_to, current_lessee_id, "
					+ "next_leasing_height_from, next_leasing_height_to, next_lessee_id, height, latest) "
					+ "KEY (lessor_id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
				int i = 0;
				pstmt.setLong(++i, this.lessorId);
				DbUtils.setIntZeroToNull(pstmt, ++i, this.currentLeasingHeightFrom);
				DbUtils.setIntZeroToNull(pstmt, ++i, this.currentLeasingHeightTo);
				DbUtils.setLongZeroToNull(pstmt, ++i, this.currentLesseeId);
				DbUtils.setIntZeroToNull(pstmt, ++i, this.nextLeasingHeightFrom);
				DbUtils.setIntZeroToNull(pstmt, ++i, this.nextLeasingHeightTo);
				DbUtils.setLongZeroToNull(pstmt, ++i, this.nextLesseeId);
				pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
				pstmt.executeUpdate();
			}
		}

	}

	public static final class AccountSupernodeDeposit {

		private final long lessorId;
		private final DbKey dbKey;
		private ArrayList<String> uris;
		private int currentDepositHeightFrom;
		private int currentDepositHeightTo;


		private AccountSupernodeDeposit(final long lessorId, final int currentDepositHeightFrom, final int currentDepositHeightTo, final String[] uris) {
			this.lessorId = lessorId;
			this.dbKey = Account.accountSupernodeDepositDbKeyFactory.newKey(this.lessorId);
			this.currentDepositHeightFrom = currentDepositHeightFrom;
			this.currentDepositHeightTo = currentDepositHeightTo;
			this.uris = new ArrayList<>();
			for(String x : uris){
				this.uris.add(x);
			}
		}

		private void setUris(String[] uris){
            this.uris = new ArrayList<>();
            for(String x : uris){
                this.uris.add(x);
            }
        }

		private AccountSupernodeDeposit(final ResultSet rs, final DbKey dbKey) throws SQLException {
			this.lessorId = rs.getLong("lessor_id");
			this.dbKey = dbKey;
			this.currentDepositHeightFrom = rs.getInt("current_deposit_height_from");
			this.currentDepositHeightTo = rs.getInt("current_deposit_height_to");
			this.uris = new ArrayList<>();
			String a = rs.getString("uris");
			for(String x : a.split(",")){
				this.uris.add(x);
			}


		}

		public ArrayList<String> getUris() {
			return uris;
		}

		public long getLessorId() {
			return this.lessorId;
		}

		public int getCurrentDepositHeightFrom() {
			return currentDepositHeightFrom;
		}

		public int getCurrentDepositHeightTo() {
			return currentDepositHeightTo;
		}

		private void save(final Connection con) throws SQLException {
			try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_supernode_deposit "
					+ "(lessor_id, current_deposit_height_from, current_deposit_height_to, uris, height, latest) "
					+ "KEY (lessor_id, height) VALUES (?, ?, ?, ?, ?, TRUE)")) {
				int i = 0;
				pstmt.setLong(++i, this.lessorId);
				DbUtils.setIntZeroToNull(pstmt, ++i, this.currentDepositHeightFrom);
				DbUtils.setIntZeroToNull(pstmt, ++i, this.currentDepositHeightTo);


				String uris_joined = String.join(",", this.uris);
				DbUtils.setString(pstmt, ++i, uris_joined);

				pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
				pstmt.executeUpdate();
			}
		}
	}

	public enum ControlType {
		PHASING_ONLY
	}

	static class DoubleSpendingException extends RuntimeException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -5542609852432703140L;

		DoubleSpendingException(final String message, final long accountId, final long confirmed,
				final long unconfirmed) {
			super(message + " account: " + Long.toUnsignedString(accountId) + " confirmed: " + confirmed
					+ " unconfirmed: " + unconfirmed);
		}
	}

	public enum Event {
		BALANCE, UNCONFIRMED_BALANCE, ASSET_BALANCE, UNCONFIRMED_ASSET_BALANCE, CURRENCY_BALANCE, UNCONFIRMED_CURRENCY_BALANCE, LEASE_SCHEDULED, LEASE_STARTED, LEASE_ENDED, SET_PROPERTY, DELETE_PROPERTY, SUPERNODE_CHANGED, SUPERNODE_EXPIRED
	}

	public static final class PublicKey {

		private final long accountId;
		private final DbKey dbKey;
		private byte[] publicKey;
		private int height;

		private PublicKey(final long accountId, final byte[] publicKey) {
			this.accountId = accountId;
			this.dbKey = Account.publicKeyDbKeyFactory.newKey(accountId);
			this.publicKey = publicKey;
			this.height = Nxt.getBlockchain().getHeight();
		}

		private PublicKey(final ResultSet rs, final DbKey dbKey) throws SQLException {
			this.accountId = rs.getLong("account_id");
			this.dbKey = dbKey;
			this.publicKey = rs.getBytes("public_key");
			this.height = rs.getInt("height");
		}

		public long getAccountId() {
			return this.accountId;
		}

		public int getHeight() {
			return this.height;
		}

		public byte[] getPublicKey() {
			return this.publicKey;
		}

		private void save(final Connection con) throws SQLException {
			this.height = Nxt.getBlockchain().getHeight();
			try (PreparedStatement pstmt = con
					.prepareStatement("MERGE INTO public_key (account_id, public_key, height, latest) "
							+ "KEY (account_id, height) VALUES (?, ?, ?, TRUE)")) {
				int i = 0;
				pstmt.setLong(++i, this.accountId);
				DbUtils.setBytes(pstmt, ++i, this.publicKey);
				pstmt.setInt(++i, this.height);
				pstmt.executeUpdate();
			}
		}

	}

	private static final DbKey.LongKeyFactory<Account> accountDbKeyFactory = new DbKey.LongKeyFactory<Account>("id") {

		@Override
		public Account newEntity(final DbKey dbKey) {
			return new Account(((DbKey.LongKey) dbKey).getId());
		}

		@Override
		public DbKey newKey(final Account account) {
			return account.dbKey == null ? this.newKey(account.id) : account.dbKey;
		}

	};

	private static final VersionedEntityDbTable<Account> accountTable = new VersionedEntityDbTable<Account>("account",
			Account.accountDbKeyFactory) {

		@Override
		protected Account load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
			return new Account(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final Account account) throws SQLException {
			account.save(con);
		}

	};

	private static final DbKey.LongKeyFactory<AccountInfo> accountInfoDbKeyFactory = new DbKey.LongKeyFactory<AccountInfo>(
			"account_id") {

		@Override
		public DbKey newKey(final AccountInfo accountInfo) {
			return accountInfo.dbKey;
		}

	};

	private static final DbKey.LongKeyFactory<AccountLease> accountLeaseDbKeyFactory = new DbKey.LongKeyFactory<AccountLease>(
			"lessor_id") {

		@Override
		public DbKey newKey(final AccountLease accountLease) {
			return accountLease.dbKey;
		}

	};

	private static final VersionedEntityDbTable<AccountLease> accountLeaseTable = new VersionedEntityDbTable<AccountLease>(
			"account_lease", Account.accountLeaseDbKeyFactory) {

		@Override
		protected AccountLease load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
			return new AccountLease(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final AccountLease accountLease) throws SQLException {
			accountLease.save(con);
		}

	};

	private static final DbKey.LongKeyFactory<AccountSupernodeDeposit> accountSupernodeDepositDbKeyFactory = new DbKey.LongKeyFactory<AccountSupernodeDeposit>(
			"lessor_id") {

		@Override
		public DbKey newKey(final AccountSupernodeDeposit accountLease) {
			return accountLease.dbKey;
		}

	};

	private static final VersionedEntityDbTable<AccountSupernodeDeposit> accountSupernodeDepositTable = new VersionedEntityDbTable<AccountSupernodeDeposit>(
			"account_supernode_deposit", Account.accountSupernodeDepositDbKeyFactory) {

		@Override
		protected AccountSupernodeDeposit load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
			return new AccountSupernodeDeposit(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final AccountSupernodeDeposit accountLease) throws SQLException {
			accountLease.save(con);
		}

	};

	private static final VersionedEntityDbTable<AccountInfo> accountInfoTable = new VersionedEntityDbTable<AccountInfo>(
			"account_info", Account.accountInfoDbKeyFactory, "name,description") {

		@Override
		protected AccountInfo load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
			return new AccountInfo(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final AccountInfo accountInfo) throws SQLException {
			accountInfo.save(con);
		}

	};

	private static final DbKey.LongKeyFactory<PublicKey> publicKeyDbKeyFactory = new DbKey.LongKeyFactory<PublicKey>(
			"account_id") {

		@Override
		public PublicKey newEntity(final DbKey dbKey) {
			return new PublicKey(((DbKey.LongKey) dbKey).getId(), null);
		}

		@Override
		public DbKey newKey(final PublicKey publicKey) {
			return publicKey.dbKey;
		}

	};

	private static final VersionedPersistentDbTable<PublicKey> publicKeyTable = new VersionedPersistentDbTable<PublicKey>(
			"public_key", Account.publicKeyDbKeyFactory) {

		@Override
		protected PublicKey load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
			return new PublicKey(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final PublicKey publicKey) throws SQLException {
			publicKey.save(con);
		}

	};

	@SuppressWarnings("unused")
	private static final DerivedDbTable accountGuaranteedBalanceTable = new DerivedDbTable(
			"account_guaranteed_balance") {

		@Override
		public void trim(final int height) {
			try (Connection con = Db.db.getConnection();
					PreparedStatement pstmtDelete = con.prepareStatement(
							"DELETE FROM account_guaranteed_balance " + "WHERE height < ? AND height >= 0")) {
				pstmtDelete.setInt(1, height - Constants.GUARANTEED_BALANCE_CONFIRMATIONS);
				pstmtDelete.executeUpdate();
			} catch (final SQLException e) {
				throw new RuntimeException(e.toString(), e);
			}
		}

	};

	private static final ConcurrentMap<DbKey, byte[]> publicKeyCache = Nxt
			.getBooleanProperty("nxt.enablePublicKeyCache") ? new ConcurrentHashMap<>() : null;

	private static final Listeners<Account, Event> listeners = new Listeners<>();
	private static final Listeners<AccountLease, Event> leaseListeners = new Listeners<>();
	private static final Listeners<AccountSupernodeDeposit, Event> supernodeListeners = new Listeners<>();

	static {

		Nxt.getBlockchainProcessor().addListener(block -> {
			final int height = block.getHeight();

			final List<AccountLease> changingLeases = new ArrayList<>();
			try (DbIterator<AccountLease> leases = Account.getLeaseChangingAccounts(height)) {
				while (leases.hasNext()) {
					changingLeases.add(leases.next());
				}
			}
			for (final AccountLease lease : changingLeases) {
				final Account lessor = Account.getAccount(lease.lessorId);
				if (height == lease.currentLeasingHeightFrom) {
					lessor.activeLesseeId = lease.currentLesseeId;
					Account.leaseListeners.notify(lease, Event.LEASE_STARTED);
				} else if (height == lease.currentLeasingHeightTo) {
					Account.leaseListeners.notify(lease, Event.LEASE_ENDED);
					lessor.activeLesseeId = 0;
					if (lease.nextLeasingHeightFrom == 0) {
						lease.currentLeasingHeightFrom = 0;
						lease.currentLeasingHeightTo = 0;
						lease.currentLesseeId = 0;
						Account.accountLeaseTable.delete(lease);
					} else {
						lease.currentLeasingHeightFrom = lease.nextLeasingHeightFrom;
						lease.currentLeasingHeightTo = lease.nextLeasingHeightTo;
						lease.currentLesseeId = lease.nextLesseeId;
						lease.nextLeasingHeightFrom = 0;
						lease.nextLeasingHeightTo = 0;
						lease.nextLesseeId = 0;
						Account.accountLeaseTable.insert(lease);
						if (height == lease.currentLeasingHeightFrom) {
							lessor.activeLesseeId = lease.currentLesseeId;
							Account.leaseListeners.notify(lease, Event.LEASE_STARTED);
						}
					}
				}
				lessor.save();
			}

			// Now handle all supernode deposit events
			final List<AccountSupernodeDeposit> changingDeposits = new ArrayList<>();
			try (DbIterator<AccountSupernodeDeposit> deposits = Account.getRelevantSupernodeDepositEvents(height)) {
				while (deposits.hasNext()) {
					changingDeposits.add(deposits.next());
				}
			}

			for (final AccountSupernodeDeposit deposit : changingDeposits) {
				final Account lessor = Account.getAccount(deposit.lessorId);
				if (height == deposit.currentDepositHeightFrom) {
					lessor.supernodeDepositBlocked = true;
					Account.supernodeListeners.notify(deposit, Event.SUPERNODE_CHANGED);

				} else if (height == deposit.currentDepositHeightTo) {

					lessor.supernodeDepositBlocked = false;
					Account.supernodeListeners.notify(deposit, Event.SUPERNODE_EXPIRED);

					final Account participantAccount = Account.getAccount(lessor.getId());
					final Account depositAccount = Account.addOrGetAccount(Constants.DEPOSITS_ACCOUNT);

					if(!participantAccount.isGuardNode()) {
						if (depositAccount.getUnconfirmedBalanceNQT() < Constants.SUPERNODE_DEPOSIT_AMOUNT) {
							// Cannot give back SN deposit, this should not happen at all actually
						} else {
							final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.SUPERNODE_DEPOSIT;
							participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, lessor.getId(),
									1 * Constants.SUPERNODE_DEPOSIT_AMOUNT);
							depositAccount.addToBalanceAndUnconfirmedBalanceNQT(event, lessor.getId(),
									-1 * Constants.SUPERNODE_DEPOSIT_AMOUNT);
						}
					}
				}
				lessor.save();
			}
		}, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);

		if (Account.publicKeyCache != null) {

			Nxt.getBlockchainProcessor().addListener(block -> {
				Account.publicKeyCache.remove(Account.accountDbKeyFactory.newKey(block.getGeneratorId()));
				block.getTransactions().forEach(transaction -> {
					Account.publicKeyCache.remove(Account.accountDbKeyFactory.newKey(transaction.getSenderId()));
					if (!transaction
							.getAppendages(appendix -> (appendix instanceof Appendix.PublicKeyAnnouncement), false)
							.isEmpty()) {
						Account.publicKeyCache.remove(Account.accountDbKeyFactory.newKey(transaction.getRecipientId()));
					}

				});
			}, BlockchainProcessor.Event.BLOCK_POPPED);

			Nxt.getBlockchainProcessor().addListener(block -> Account.publicKeyCache.clear(),
					BlockchainProcessor.Event.RESCAN_BEGIN);

		}

	}

	public static boolean addLeaseListener(final Listener<AccountLease> listener, final Event eventType) {
		return Account.leaseListeners.addListener(listener, eventType);
	}

	public static boolean addSupernodeListener(final Listener<AccountSupernodeDeposit> listener, final Event eventType) {
		return Account.supernodeListeners.addListener(listener, eventType);
	}

	public static boolean addListener(final Listener<Account> listener, final Event eventType) {
		return Account.listeners.addListener(listener, eventType);
	}

	public static Account newAccount(DbKey dbKey){
		Account account = Account.accountTable.newEntity(dbKey);
		PublicKey publicKey = Account.publicKeyTable.get(dbKey);
		if (publicKey == null) {
			publicKey = Account.publicKeyTable.newEntity(dbKey);
			Account.publicKeyTable.insert(publicKey);
		}
		account.publicKey = publicKey;
		return account;
	}
	public static Account addOrGetAccount(final byte[] pubkey) {
		final long id = Account.getId(pubkey);
		return addOrGetAccount(id);
	}

	public static Account addOrGetAccount(final long id) {
		if (id == 0) {
			throw new IllegalArgumentException("Invalid accountId 0");
		}
		final DbKey dbKey = Account.accountDbKeyFactory.newKey(id);
		Account account = Account.accountTable.get(dbKey);
		if (account == null) account = newAccount(dbKey);
		return account;
	}

	private static void checkBalance(final long accountId, final long confirmed, final long unconfirmed) {
		if (accountId == Genesis.CREATOR_ID) {
			return;
		}
		if (confirmed < 0) {
			throw new DoubleSpendingException("Negative balance or quantity: ", accountId, confirmed, unconfirmed);
		}
		if (unconfirmed < 0) {
			throw new DoubleSpendingException("Negative unconfirmed balance or quantity: ", accountId, confirmed,
					unconfirmed);
		}
		if (unconfirmed > confirmed) {
			throw new DoubleSpendingException("Unconfirmed exceeds confirmed balance or quantity: ", accountId,
					confirmed, unconfirmed);
		}
	}

	public boolean isSuperNode() {
		return this.supernodeDepositBlocked; // todo if checking the height is required ... I guess not
	}


	public static byte[] decryptFrom(final byte[] publicKey, final EncryptedData encryptedData,
			final String recipientSecretPhrase, final boolean uncompress) {
		byte[] decrypted = encryptedData.decrypt(recipientSecretPhrase, publicKey);
		if (uncompress && (decrypted.length > 0)) {
			decrypted = Convert.uncompress(decrypted);
		}
		return decrypted;
	}

	public static EncryptedData encryptTo(final byte[] publicKey, byte[] data, final String senderSecretPhrase,
			final boolean compress) {
		if (compress && (data.length > 0)) {
			data = Convert.compress(data);
		}
		return EncryptedData.encrypt(data, senderSecretPhrase, publicKey);
	}

	public static Account getAccount(final byte[] publicKey) {
		final long accountId = Account.getId(publicKey);
		final Account account = Account.getAccount(accountId);
		if (account == null) {
			return null;
		}
		if (account.publicKey == null) {
			account.publicKey = Account.publicKeyTable.get(Account.accountDbKeyFactory.newKey(account));
		}
		if ((account.publicKey == null) || (account.publicKey.publicKey == null)
				|| Arrays.equals(account.publicKey.publicKey, publicKey)) {
			return account;
		}
		throw new RuntimeException("DUPLICATE KEY for account " + Long.toUnsignedString(accountId) + " existing key "
				+ Convert.toHexString(account.publicKey.publicKey) + " new key " + Convert.toHexString(publicKey));
	}

	public static Account getAccount(final long id) {
		final DbKey dbKey = Account.accountDbKeyFactory.newKey(id);
		Account account = Account.accountTable.get(dbKey);
		if (account == null) {
			final PublicKey publicKey = Account.publicKeyTable.get(dbKey);
			if (publicKey != null) {
				account = Account.accountTable.newEntity(dbKey);
				account.publicKey = publicKey;
			}
		}
		return account;
	}

	public static Account getAccount(final long id, final int height) {
		final DbKey dbKey = Account.accountDbKeyFactory.newKey(id);
		Account account = Account.accountTable.get(dbKey, height);
		if (account == null) {
			final PublicKey publicKey = Account.publicKeyTable.get(dbKey, height);
			if (publicKey != null) {
				account = Account.accountTable.newEntity(dbKey);
				account.publicKey = publicKey;
			}
		}
		return account;
	}

	public static int getAccountLeaseCount() {
		return Account.accountLeaseTable.getCount();
	}

	public static int getActiveLeaseCount() {
		return Account.accountTable.getCount(new DbClause.NotNullClause("active_lessee_id"));
	}

	public static int getCount() {
		return Account.publicKeyTable.getCount();
	}

	public static long getId(final byte[] publicKey) {
		final byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
		return Convert.fullHashToId(publicKeyHash);
	}

	private static DbIterator<AccountLease> getLeaseChangingAccounts(final int height) {
		Connection con = null;
		try {
			con = Db.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement(
					"SELECT * FROM account_lease WHERE current_leasing_height_from = ? AND latest = TRUE "
							+ "UNION ALL SELECT * FROM account_lease WHERE current_leasing_height_to = ? AND latest = TRUE "
							+ "ORDER BY current_lessee_id, lessor_id");
			int i = 0;
			pstmt.setInt(++i, height);
			pstmt.setInt(++i, height);
			return Account.accountLeaseTable.getManyBy(con, pstmt, true);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	private static DbIterator<AccountSupernodeDeposit> getRelevantSupernodeDepositEvents(final int height) {
		Connection con = null;
		try {
			con = Db.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement(
					"SELECT * FROM account_supernode_deposit WHERE current_deposit_height_from = ? AND latest = TRUE "
							+ "UNION ALL SELECT * FROM account_supernode_deposit WHERE current_deposit_height_to = ? AND latest = TRUE "
							+ "ORDER BY lessor_id");
			int i = 0;
			pstmt.setInt(++i, height);
			pstmt.setInt(++i, height);
			return Account.accountSupernodeDepositTable.getManyBy(con, pstmt, true);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static DbIterator<AccountSupernodeDeposit> getActiveSupernodes(final int height) {
		Connection con = null;
		try {
			con = Db.db.getConnection();
			final PreparedStatement pstmt = con.prepareStatement(
					"SELECT * FROM account_supernode_deposit WHERE current_deposit_height_from <= ? AND current_deposit_height_to >= ? AND latest = TRUE "
							+ "ORDER BY lessor_id");
			int i = 0;
			pstmt.setInt(++i, height);
			pstmt.setInt(++i, height);
			return Account.accountSupernodeDepositTable.getManyBy(con, pstmt, true);
		} catch (final SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static int countSuperNodes(final int height) {
		int count = 0;
		try{
			DbIterator<AccountSupernodeDeposit> it = getActiveSupernodes(height);
			while(it.hasNext()){
				count++;
				it.next();
			}
		}
		finally{
			return count;
		}
	}

	public static byte[] getPublicKey(final long id) {
		final DbKey dbKey = Account.publicKeyDbKeyFactory.newKey(id);
		byte[] key = null;
		if (Account.publicKeyCache != null) {
			key = Account.publicKeyCache.get(dbKey);
		}
		if (key == null) {
			final PublicKey publicKey = Account.publicKeyTable.get(dbKey);
			if ((publicKey == null) || ((key = publicKey.publicKey) == null)) {
				return null;
			}
			if (Account.publicKeyCache != null) {
				Account.publicKeyCache.put(dbKey, key);
			}
		}
		return key;
	}

	static void init() {
	}

	public static boolean removeLeaseListener(final Listener<AccountLease> listener, final Event eventType) {
		return Account.leaseListeners.removeListener(listener, eventType);
	}

	public static boolean removeSupernodeListener(final Listener<AccountSupernodeDeposit> listener, final Event eventType) {
		return Account.supernodeListeners.removeListener(listener, eventType);
	}

	public static boolean removeListener(final Listener<Account> listener, final Event eventType) {
		return Account.listeners.removeListener(listener, eventType);
	}

	public static DbIterator<AccountInfo> searchAccounts(final String query, final int from, final int to) {
		return Account.accountInfoTable.search(query, DbClause.EMPTY_CLAUSE, from, to);
	}

	static boolean setOrVerify(final long accountId, final byte[] key) {
		final DbKey dbKey = Account.publicKeyDbKeyFactory.newKey(accountId);
		PublicKey publicKey = Account.publicKeyTable.get(dbKey);
		if (publicKey == null) {
			publicKey = Account.publicKeyTable.newEntity(dbKey);
		}
		if (publicKey.publicKey == null) {
			publicKey.publicKey = key;
			publicKey.height = Nxt.getBlockchain().getHeight();
			return true;
		}
		return Arrays.equals(publicKey.publicKey, key);
	}

	private final long id;
	private final DbKey dbKey;
	private PublicKey publicKey;
	private long balanceNQT;

	private long unconfirmedBalanceNQT;
	private long forgedBalanceNQT;
	private long activeLesseeId;

	private boolean supernodeDepositBlocked;

	private Set<ControlType> controls;

	private Account(final long id) {
		if (id != Crypto.rsDecode(Crypto.rsEncode(id))) {
			Logger.logMessage("CRITICAL ERROR: Reed-Solomon encoding fails for " + id);
		}
		this.id = id;
		this.dbKey = Account.accountDbKeyFactory.newKey(this.id);
		this.controls = Collections.emptySet();
	}

	private Account(final ResultSet rs, final DbKey dbKey) throws SQLException {
		this.id = rs.getLong("id");
		this.dbKey = dbKey;
		this.balanceNQT = rs.getLong("balance");
		this.unconfirmedBalanceNQT = rs.getLong("unconfirmed_balance");
		this.forgedBalanceNQT = rs.getLong("forged_balance");
		this.activeLesseeId = rs.getLong("active_lessee_id");
		this.supernodeDepositBlocked = rs.getBoolean("supernode_deposit_blocked");
		if (rs.getBoolean("has_control_phasing")) {
			this.controls = Collections.unmodifiableSet(EnumSet.of(ControlType.PHASING_ONLY));
		} else {
			this.controls = Collections.emptySet();
		}
	}

	void addControl(final ControlType control) {
		if (this.controls.contains(control)) {
			return;
		}
		final EnumSet<ControlType> newControls = EnumSet.of(control);
		newControls.addAll(this.controls);
		this.controls = Collections.unmodifiableSet(newControls);
		Account.accountTable.insert(this);
	}

	void addToBalanceAndUnconfirmedBalanceNQT(final LedgerEvent event, final long eventId, final long amountNQT) {
		this.addToBalanceAndUnconfirmedBalanceNQT(event, eventId, amountNQT, 0);
	}

	void addToBalanceAndUnconfirmedBalanceNQT(final LedgerEvent event, final long eventId, final long amountNQT,
			final long feeNQT) {
		if ((amountNQT == 0) && (feeNQT == 0)) {
			return;
		}
		final long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
		this.balanceNQT = Math.addExact(this.balanceNQT, totalAmountNQT);
		this.unconfirmedBalanceNQT = Math.addExact(this.unconfirmedBalanceNQT, totalAmountNQT);
		this.addToGuaranteedBalanceNQT(totalAmountNQT);
		Account.checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
		this.save();
		Account.listeners.notify(this, Event.BALANCE);
		Account.listeners.notify(this, Event.UNCONFIRMED_BALANCE);
		if (AccountLedger.mustLogEntry(this.id, true)) {
			if (feeNQT != 0) {
				AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
						LedgerHolding.UNCONFIRMED_NXT_BALANCE, null, feeNQT, this.unconfirmedBalanceNQT - amountNQT));
			}
			if (amountNQT != 0) {
				AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id, LedgerHolding.UNCONFIRMED_NXT_BALANCE,
						null, amountNQT, this.unconfirmedBalanceNQT));
			}
		}
		if (AccountLedger.mustLogEntry(this.id, false)) {
			if (feeNQT != 0) {
				AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
						LedgerHolding.NXT_BALANCE, null, feeNQT, this.balanceNQT - amountNQT));
			}
			if (amountNQT != 0) {
				AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id, LedgerHolding.NXT_BALANCE, null,
						amountNQT, this.balanceNQT));
			}
		}
	}

	void addToBalanceNQT(final LedgerEvent event, final long eventId, final long amountNQT) {
		this.addToBalanceNQT(event, eventId, amountNQT, 0);
	}

	void addToBalanceNQT(final LedgerEvent event, final long eventId, final long amountNQT, final long feeNQT) {
		if ((amountNQT == 0) && (feeNQT == 0)) {
			return;
		}
		final long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
		this.balanceNQT = Math.addExact(this.balanceNQT, totalAmountNQT);
		this.addToGuaranteedBalanceNQT(totalAmountNQT);
		Account.checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
		this.save();
		Account.listeners.notify(this, Event.BALANCE);
		if (AccountLedger.mustLogEntry(this.id, false)) {
			if (feeNQT != 0) {
				AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
						LedgerHolding.NXT_BALANCE, null, feeNQT, this.balanceNQT - amountNQT));
			}
			if (amountNQT != 0) {
				AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id, LedgerHolding.NXT_BALANCE, null,
						amountNQT, this.balanceNQT));
			}
		}
	}

	void addToForgedBalanceNQT(final long amountNQT) {
		if (amountNQT == 0) {
			return;
		}
		this.forgedBalanceNQT = Math.addExact(this.forgedBalanceNQT, amountNQT);
		this.save();
	}

	private void addToGuaranteedBalanceNQT(final long amountNQT) {
		if (amountNQT <= 0) {
			return;
		}
		final int blockchainHeight = Nxt.getBlockchain().getHeight();
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmtSelect = con.prepareStatement(
						"SELECT additions FROM account_guaranteed_balance " + "WHERE account_id = ? and height = ?");
				PreparedStatement pstmtUpdate = con
						.prepareStatement("MERGE INTO account_guaranteed_balance (account_id, "
								+ " additions, height) KEY (account_id, height) VALUES(?, ?, ?)")) {
			pstmtSelect.setLong(1, this.id);
			pstmtSelect.setInt(2, blockchainHeight);
			try (ResultSet rs = pstmtSelect.executeQuery()) {
				long additions = amountNQT;
				if (rs.next()) {
					additions = Math.addExact(additions, rs.getLong("additions"));
				}
				pstmtUpdate.setLong(1, this.id);
				pstmtUpdate.setLong(2, additions);
				pstmtUpdate.setInt(3, blockchainHeight);
				pstmtUpdate.executeUpdate();
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	void addToUnconfirmedBalanceNQT(final LedgerEvent event, final long eventId, final long amountNQT) {
		this.addToUnconfirmedBalanceNQT(event, eventId, amountNQT, 0);
	}

	void addToUnconfirmedBalanceNQT(final LedgerEvent event, final long eventId, final long amountNQT,
			final long feeNQT) {
		if ((amountNQT == 0) && (feeNQT == 0)) {
			return;
		}
		final long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
		this.unconfirmedBalanceNQT = Math.addExact(this.unconfirmedBalanceNQT, totalAmountNQT);
		Account.checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
		this.save();
		Account.listeners.notify(this, Event.UNCONFIRMED_BALANCE);
		if (AccountLedger.mustLogEntry(this.id, true)) {
			if (feeNQT != 0) {
				AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
						LedgerHolding.UNCONFIRMED_NXT_BALANCE, null, feeNQT, this.unconfirmedBalanceNQT - amountNQT));
			}
			if (amountNQT != 0) {
				AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id, LedgerHolding.UNCONFIRMED_NXT_BALANCE,
						null, amountNQT, this.unconfirmedBalanceNQT));
			}
		}
	}

	void apply(final byte[] key) {
		PublicKey publicKey = Account.publicKeyTable.get(this.dbKey);
		if (publicKey == null) {
			publicKey = Account.publicKeyTable.newEntity(this.dbKey);
		}
		if (publicKey.publicKey == null) {
			publicKey.publicKey = key;
			Account.publicKeyTable.insert(publicKey);
		} else if (!Arrays.equals(publicKey.publicKey, key)) {
			throw new IllegalStateException("Public key mismatch");
		} else if (publicKey.height >= (Nxt.getBlockchain().getHeight() - 1)) {
			final PublicKey dbPublicKey = Account.publicKeyTable.get(this.dbKey, false);
			if ((dbPublicKey == null) || (dbPublicKey.publicKey == null)) {
				Account.publicKeyTable.insert(publicKey);
			}
		}
		if (Account.publicKeyCache != null) {
			Account.publicKeyCache.put(this.dbKey, key);
		}
		this.publicKey = publicKey;
	}

	public byte[] decryptFrom(final EncryptedData encryptedData, final String recipientSecretPhrase,
			final boolean uncompress) {
		final byte[] key = Account.getPublicKey(this.id);
		if (key == null) {
			throw new IllegalArgumentException("Sender account doesn't have a public key set");
		}
		return Account.decryptFrom(key, encryptedData, recipientSecretPhrase, uncompress);
	}

	public EncryptedData encryptTo(final byte[] data, final String senderSecretPhrase, final boolean compress) {
		final byte[] key = Account.getPublicKey(this.id);
		if (key == null) {
			throw new IllegalArgumentException("Recipient account doesn't have a public key set");
		}
		return Account.encryptTo(key, data, senderSecretPhrase, compress);
	}

	public AccountInfo getAccountInfo() {
		return Account.accountInfoTable.get(Account.accountDbKeyFactory.newKey(this));
	}

	public AccountLease getAccountLease() {
		return Account.accountLeaseTable.get(Account.accountDbKeyFactory.newKey(this));
	}

	public AccountSupernodeDeposit getAccountSupernodeDeposit() {
		return Account.accountSupernodeDepositTable.get(Account.accountDbKeyFactory.newKey(this));
	}

	public long getBalanceNQT() {
		return this.balanceNQT;
	}

	public Set<ControlType> getControls() {
		return this.controls;
	}

	public long getEffectiveBalanceNXT() {
		return this.getEffectiveBalanceNXT(Nxt.getBlockchain().getHeight());
	}

	public long getEffectiveBalanceNXT(final int height) {

		if (this.publicKey == null) {
			this.publicKey = Account.publicKeyTable.get(Account.accountDbKeyFactory.newKey(this));
		}

		if(height <= Constants.FIRST_X_BLOCKS_PSEUDO_EFFECTIVE_BALANCE){
			return this.getPseudoEffectiveBalanceNXT(height);
		}

		/*
		 * STRIPPED || height - this.publicKey.height <= 1440) { return 0; //
		 * cfb: Accounts with the public key revealed less than // 1440 blocks
		 * ago are not allowed to generate blocks }
		 */

		Nxt.getBlockchain().readLock();
		try {
			long effectiveBalanceNQT = this.getLessorsGuaranteedBalanceNQT(height);
			if (this.activeLesseeId == 0) {
				effectiveBalanceNQT += this.getGuaranteedBalanceNQT(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, height);
			}

			// Do not subtract the supernode deposit, because we do not want to lower the "forging weight".

			return (effectiveBalanceNQT < Constants.MIN_FORGING_BALANCE_NQT) ? 0
					: effectiveBalanceNQT / Constants.ONE_NXT;
		} finally {
			Nxt.getBlockchain().readUnlock();
		}
	}

	private long getPseudoEffectiveBalanceNXT(final int height) {

		Nxt.getBlockchain().readLock();
		try {
			long effectiveBalanceNQT = this.getLessorsGuaranteedBalanceNQT(height);
			if (this.activeLesseeId == 0) {
				effectiveBalanceNQT += this.getBalanceNQT();
			}
			return (effectiveBalanceNQT < Constants.MIN_FORGING_BALANCE_NQT) ? 0
					: effectiveBalanceNQT / Constants.ONE_NXT;
		} finally {
			Nxt.getBlockchain().readUnlock();
		}
	}

	public long getForgedBalanceNQT() {
		return this.forgedBalanceNQT;
	}

	public long getGuaranteedBalanceNQT() {
		return this.getGuaranteedBalanceNQT(Constants.GUARANTEED_BALANCE_CONFIRMATIONS,
				Nxt.getBlockchain().getHeight());
	}

	public long getGuaranteedBalanceNQT(final int numberOfConfirmations, final int currentHeight) {
		Nxt.getBlockchain().readLock();
		try {
			final int height = currentHeight - numberOfConfirmations;
			if (height + Constants.GUARANTEED_BALANCE_CONFIRMATIONS < Nxt.getBlockchainProcessor()
					.getMinRollbackHeight() || height > Nxt.getBlockchain().getHeight())
                throw new IllegalArgumentException(
                        "Height " + height + " not available for guaranteed balance calculation");
			try (Connection con = Db.db.getConnection();
					PreparedStatement pstmt = con.prepareStatement("SELECT SUM (additions) AS additions "
							+ "FROM account_guaranteed_balance WHERE account_id = ? AND height > ? AND height <= ?")) {
				pstmt.setLong(1, this.id);
				pstmt.setInt(2, height);
				pstmt.setInt(3, currentHeight);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (!rs.next()) {
						return this.balanceNQT;
					}
					return Math.max(Math.subtractExact(this.balanceNQT, rs.getLong("additions")), 0);
				}
			} catch (final SQLException e) {
				throw new RuntimeException(e.toString(), e);
			}
		} finally {
			Nxt.getBlockchain().readUnlock();
		}
	}

	public long getId() {
		return this.id;
	}

	public DbIterator<Account> getLessors() {
		return Account.accountTable.getManyBy(new DbClause.LongClause("active_lessee_id", this.id), 0, -1,
				" ORDER BY id ASC ");
	}

	public DbIterator<Account> getLessors(final int height) {
		return Account.accountTable.getManyBy(new DbClause.LongClause("active_lessee_id", this.id), height, 0, -1,
				" ORDER BY id ASC ");
	}

	private long getLessorsGuaranteedBalanceNQT(final int height) {
		final List<Account> lessors = new ArrayList<>();
		try (DbIterator<Account> iterator = this.getLessors(height)) {
			while (iterator.hasNext()) {
				lessors.add(iterator.next());
			}
		}
		final Long[] lessorIds = new Long[lessors.size()];
		final long[] balances = new long[lessors.size()];
		for (int i = 0; i < lessors.size(); i++) {
			lessorIds[i] = lessors.get(i).getId();
			balances[i] = lessors.get(i).getBalanceNQT();
		}
		final int blockchainHeight = Nxt.getBlockchain().getHeight();
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT account_id, SUM (additions) AS additions "
						+ "FROM account_guaranteed_balance, TABLE (id BIGINT=?) T WHERE account_id = T.id AND height > ? "
						+ (height < blockchainHeight ? " AND height <= ? " : "")
						+ " GROUP BY account_id ORDER BY account_id")) {
			pstmt.setObject(1, lessorIds);
			pstmt.setInt(2, height - Constants.GUARANTEED_BALANCE_CONFIRMATIONS);
			if (height < blockchainHeight) {
				pstmt.setInt(3, height);
			}
			long total = 0;
			int i = 0;
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					final long accountId = rs.getLong("account_id");
					while ((lessorIds[i] < accountId) && (i < lessorIds.length)) {
						total += balances[i++];
					}
					if (lessorIds[i] == accountId) {
						total += Math.max(balances[i++] - rs.getLong("additions"), 0);
					}
				}
			}
			while (i < balances.length) {
				total += balances[i++];
			}
			return total;
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public long getUnconfirmedBalanceNQT() {
		return this.unconfirmedBalanceNQT;
	}

	void leaseEffectiveBalance(final long lesseeId, final int period) {
		final int height = Nxt.getBlockchain().getHeight();
		AccountLease accountLease = Account.accountLeaseTable.get(Account.accountDbKeyFactory.newKey(this));
		if (accountLease == null) {
			accountLease = new AccountLease(this.id, height + Constants.LEASING_DELAY,
					height + Constants.LEASING_DELAY + period, lesseeId);
		} else if (accountLease.currentLesseeId == 0) {
			accountLease.currentLeasingHeightFrom = height + Constants.LEASING_DELAY;
			accountLease.currentLeasingHeightTo = height + Constants.LEASING_DELAY + period;
			accountLease.currentLesseeId = lesseeId;
		} else {
			accountLease.nextLeasingHeightFrom = height + Constants.LEASING_DELAY;
			if (accountLease.nextLeasingHeightFrom < accountLease.currentLeasingHeightTo) {
				accountLease.nextLeasingHeightFrom = accountLease.currentLeasingHeightTo;
			}
			accountLease.nextLeasingHeightTo = accountLease.nextLeasingHeightFrom + period;
			accountLease.nextLesseeId = lesseeId;
		}
		Account.accountLeaseTable.insert(accountLease);
		Account.leaseListeners.notify(accountLease, Event.LEASE_SCHEDULED);
	}


	Pair<Integer, Integer> getSupernodeTimeframe(){
		if(this.isSuperNode()==false){
		    return new Pair<>(0,0);
        }else{
            AccountSupernodeDeposit deposit = Account.accountSupernodeDepositTable.get(Account.accountDbKeyFactory.newKey(this));
            if (deposit == null) {
                return new Pair<>(0,0);
            }else{
                return new Pair<>(deposit.currentDepositHeightFrom, deposit.currentDepositHeightTo);
            }
        }
	}


	public void invalidateSupernodeDeposit() {
		if(this.isSuperNode() == false || this.isGuardNode() == true){
			return;
		}

		final int height = Nxt.getBlockchain().getHeight();
		AccountSupernodeDeposit deposit = Account.accountSupernodeDepositTable.get(Account.accountDbKeyFactory.newKey(this));
		if (deposit == null) {
			// nothing to do here, should never happen though, since every SN has a linked deposit
		}else{


			// Deposit forfeited

			this.supernodeDepositBlocked = false;
			this.save(); // TODO: is this the correct way to save?

			final Account depositAccount = Account.addOrGetAccount(Constants.DEPOSITS_ACCOUNT);
			final Account collectorAccount = Account.addOrGetAccount(Constants.FORFEITED_DEPOSITS_ACCOUNT);
			final AccountLedger.LedgerEvent event = LedgerEvent.SUPERNODE_FORFEIT;
			depositAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.getId(),
					-1 * Constants.SUPERNODE_DEPOSIT_AMOUNT);
			collectorAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.getId(),
					1 * Constants.SUPERNODE_DEPOSIT_AMOUNT);
			Account.accountSupernodeDepositTable.delete(deposit);


		}
	}


	boolean canBecomeSupernode(){
		if(this.getUnconfirmedBalanceNQT()<Constants.SUPERNODE_DEPOSIT_AMOUNT){
			return false;
		}
		return true;
	}
	void refreshSupernodeDeposit(String[] uris) throws IOException {

		// Do nothing if the current guaranteed balance is lower than the minimum amount of supernode deposit
		// Since supernode already have their balance deducted, only do this checks for guys that do not refresh
		// but who become new supernodes
		if(this.isSuperNode() == false && this.getUnconfirmedBalanceNQT()<Constants.SUPERNODE_DEPOSIT_AMOUNT){
			return;
		}


		final int height = Nxt.getBlockchain().getHeight();
		AccountSupernodeDeposit deposit = Account.accountSupernodeDepositTable.get(Account.accountDbKeyFactory.newKey(this));
		if (deposit == null) {
			deposit = new AccountSupernodeDeposit(this.id, height,height + Constants.SUPERNODE_DEPOSIT_BINDING_PERIOD, uris);

			final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.SUPERNODE_DEPOSIT;
            final Account participantAccount = Account.getAccount(this.getId());
            final Account depositAccount = Account.addOrGetAccount(Constants.DEPOSITS_ACCOUNT);

            if(this.isGuardNode() == false) {
				if (participantAccount.getUnconfirmedBalanceNQT() < Constants.SUPERNODE_DEPOSIT_AMOUNT) {
					// cannot afford this
					throw new IOException("Not enough funds for supernode deposit");

				} else {

					// Workaround, do not do this for guard nodes that become SN
					participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.getId(),
							-1 * Constants.SUPERNODE_DEPOSIT_AMOUNT);
					depositAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.getId(),
							1 * Constants.SUPERNODE_DEPOSIT_AMOUNT);
				}
			}
		}
		else{
			// Only update height if the other supernode thing already times out, otherwise it is just an extension which does not need a "begin" event triggered
			if(deposit.currentDepositHeightTo < height) // TODO: < or <= ??
			{
				final AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.SUPERNODE_DEPOSIT;
				final Account participantAccount = Account.getAccount(this.getId());
				final Account depositAccount = Account.addOrGetAccount(Constants.DEPOSITS_ACCOUNT);

				if(this.isGuardNode() == false) {
					if (participantAccount.getUnconfirmedBalanceNQT() < Constants.SUPERNODE_DEPOSIT_AMOUNT) {
						// cannot afford this
						throw new IOException("Not enough funds for supernode deposit");

					} else {
						participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.getId(),
								-1 * Constants.SUPERNODE_DEPOSIT_AMOUNT);
						depositAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.getId(),
								1 * Constants.SUPERNODE_DEPOSIT_AMOUNT);

					}
				}
				deposit.currentDepositHeightFrom = height;
				deposit.setUris(uris);
			}

			deposit.currentDepositHeightTo = height + Constants.SUPERNODE_DEPOSIT_BINDING_PERIOD;
		}
		Account.accountSupernodeDepositTable.insert(deposit);
		Account.supernodeListeners.notify(deposit, Event.SUPERNODE_CHANGED);
	}

	void removeControl(final ControlType control) {
		if (!this.controls.contains(control)) {
			return;
		}
		final EnumSet<ControlType> newControls = EnumSet.copyOf(this.controls);
		newControls.remove(control);
		this.controls = Collections.unmodifiableSet(newControls);
		Account.accountTable.insert(this);
	}

	private boolean isRelevant(){
		boolean relevant = false;
		long thisid = this.getId();

		for(long sn : Constants.GUARD_NODES){
			if(thisid == sn){
				relevant = true;
				break;
			}
		}

		if(!relevant && (thisid == Constants.DEPOSITS_ACCOUNT || thisid == Constants.FORFEITED_DEPOSITS_ACCOUNT)){
			relevant = true;
		}

		return relevant;
	}

	private void save() {
		if ((!isRelevant()) && ((this.balanceNQT == 0) && (this.unconfirmedBalanceNQT == 0) && (this.forgedBalanceNQT == 0)
				&& (this.activeLesseeId == 0) && (this.supernodeDepositBlocked == false) && this.controls.isEmpty())) {
			Account.accountTable.delete(this, true);
		} else {
			Account.accountTable.insert(this);
		}
	}

	private void save(final Connection con) throws SQLException {
		try (PreparedStatement pstmt = con
				.prepareStatement("MERGE INTO account (id, " + "balance, unconfirmed_balance, forged_balance, "
						+ "active_lessee_id, supernode_deposit_blocked, has_control_phasing, height, latest) "
						+ "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
			int i = 0;
			pstmt.setLong(++i, this.id);
			pstmt.setLong(++i, this.balanceNQT);
			pstmt.setLong(++i, this.unconfirmedBalanceNQT);
			pstmt.setLong(++i, this.forgedBalanceNQT);
			DbUtils.setLongZeroToNull(pstmt, ++i, this.activeLesseeId);
            pstmt.setBoolean(++i, this.supernodeDepositBlocked);
            pstmt.setBoolean(++i, this.controls.contains(ControlType.PHASING_ONLY));
			pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
			pstmt.executeUpdate();
		}
	}

	void setAccountInfo(String name, String description) {
		name = Convert.emptyToNull(name.trim());
		description = Convert.emptyToNull(description.trim());
		AccountInfo accountInfo = this.getAccountInfo();
		if (accountInfo == null) {
			accountInfo = new AccountInfo(this.id, name, description);
		} else {
			accountInfo.name = name;
			accountInfo.description = description;
		}
		accountInfo.save();
	}

	@Override
	public String toString() {
		return "Account " + Long.toUnsignedString(this.getId());
	}
}

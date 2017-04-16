/******************************************************************************
 * Copyright © 2013-2016 The XEL Core Developers.                             *
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.EntityDbTable;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;
import nxt.util.ThreadPool;

public final class TransactionProcessorImpl implements TransactionProcessor {

	private static final boolean enableTransactionRebroadcasting = Nxt
			.getBooleanProperty("nxt.enableTransactionRebroadcasting");
	private static final boolean testUnconfirmedTransactions = Nxt
			.getBooleanProperty("nxt.testUnconfirmedTransactions");
	private static final int maxUnconfirmedTransactions;
	static {
		final int n = Nxt.getIntProperty("nxt.maxUnconfirmedTransactions");
		maxUnconfirmedTransactions = n <= 0 ? Integer.MAX_VALUE : n;
	}

	private static final TransactionProcessorImpl instance = new TransactionProcessorImpl();

	private static final Comparator<UnconfirmedTransaction> cachedUnconfirmedTransactionComparator = (t1, t2) -> {
		int compare;
		// Sort by transaction_height ASC
		compare = Integer.compare(t1.getHeight(), t2.getHeight());
		if (compare != 0) return compare;
		// Sort by fee_per_byte DESC
		compare = Long.compare(t1.getFeePerByte(), t2.getFeePerByte());
		if (compare != 0) return -compare;
		// Sort by arrival_timestamp ASC
		compare = Long.compare(t1.getArrivalTimestamp(), t2.getArrivalTimestamp());
		if (compare != 0) return compare;
		// Sort by transaction ID ASC
		return Long.compare(t1.getId(), t2.getId());
	};

	public static TransactionProcessorImpl getInstance() {
		return TransactionProcessorImpl.instance;
	}

	private final Map<DbKey, UnconfirmedTransaction> transactionCache = new HashMap<>();
	private final Map<Long, UnconfirmedTransaction> SNCleantransactionCache = new HashMap<>();

	private volatile boolean cacheInitialized = false;

	final DbKey.LongKeyFactory<UnconfirmedTransaction> unconfirmedTransactionDbKeyFactory = new DbKey.LongKeyFactory<UnconfirmedTransaction>(
			"id") {

		@Override
		public DbKey newKey(final UnconfirmedTransaction unconfirmedTransaction) {
			return unconfirmedTransaction.getTransaction().getDbKey();
		}

	};

	private final EntityDbTable<UnconfirmedTransaction> unconfirmedTransactionTable = new EntityDbTable<UnconfirmedTransaction>(
			"unconfirmed_transaction", this.unconfirmedTransactionDbKeyFactory) {

		@Override
		protected String defaultSort() {
			return " ORDER BY transaction_height ASC, fee_per_byte DESC, arrival_timestamp ASC, id ASC ";
		}

		@Override
		protected UnconfirmedTransaction load(final Connection con, final ResultSet rs, final DbKey dbKey)
				throws SQLException {
			return new UnconfirmedTransaction(rs);
		}

		@Override
		public void rollback(final int height) {
			try (Connection con = Db.db.getConnection();
					PreparedStatement pstmt = con
							.prepareStatement("SELECT * FROM unconfirmed_transaction WHERE height > ?")) {
				//noinspection SuspiciousNameCombination
				pstmt.setInt(1, height);
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						final UnconfirmedTransaction unconfirmedTransaction = this.load(con, rs, null);
						TransactionProcessorImpl.this.waitingTransactions.add(unconfirmedTransaction);
						TransactionProcessorImpl.this.transactionCache.remove(unconfirmedTransaction.getDbKey());
						TransactionProcessorImpl.this.SNCleantransactionCache.remove(unconfirmedTransaction.getSNCleanedId());
					}
				}
			} catch (final SQLException e) {
				throw new RuntimeException(e.toString(), e);
			}
			super.rollback(height);
			TransactionProcessorImpl.this.unconfirmedDuplicates.clear();
		}

		@Override
		protected void save(final Connection con, final UnconfirmedTransaction unconfirmedTransaction)
				throws SQLException {
			unconfirmedTransaction.save(con);
			if (TransactionProcessorImpl.this.transactionCache
					.size() < TransactionProcessorImpl.maxUnconfirmedTransactions) {
				TransactionProcessorImpl.this.transactionCache.put(unconfirmedTransaction.getDbKey(),
						unconfirmedTransaction);
				TransactionProcessorImpl.this.SNCleantransactionCache.put(unconfirmedTransaction.getSNCleanedId(),
						unconfirmedTransaction);
			}
		}

		@Override
		public void truncate() {
			super.truncate();
			this.clearCache();
		}

	};
	private final Set<TransactionImpl> broadcastedTransactions = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private final Listeners<List<? extends Transaction>, Event> transactionListeners = new Listeners<>();

	private final PriorityQueue<UnconfirmedTransaction> waitingTransactions = new PriorityQueue<UnconfirmedTransaction>(
			(o1, o2) -> {
				int result;
				if ((result = Integer.compare(o2.getHeight(), o1.getHeight())) != 0) return result;
				if ((result = Boolean.compare(o2.getTransaction().referencedTransactionFullHash() != null,
						o1.getTransaction().referencedTransactionFullHash() != null)) != 0) return result;
				if ((result = Long.compare(o1.getFeePerByte(), o2.getFeePerByte())) != 0) return result;
				if ((result = Long.compare(o2.getArrivalTimestamp(), o1.getArrivalTimestamp())) != 0) return result;
				return Long.compare(o2.getId(), o1.getId());
			}) {

		/**
		 * 
		 */
		private static final long serialVersionUID = 8657634203718539725L;

		@Override
		public boolean add(final UnconfirmedTransaction unconfirmedTransaction) {
			if (!super.add(unconfirmedTransaction)) return false;
			if (this.size() > TransactionProcessorImpl.maxUnconfirmedTransactions) this.remove();
			return true;
		}

	};

	private final Map<TransactionType, Map<String, Integer>> unconfirmedDuplicates = new HashMap<>();

	private TransactionProcessorImpl() {
		if (!Constants.isLightClient) {
			if (!Constants.isOffline) {
				Runnable processTransactionsThread = () -> {

					try {
						try {
							if (Nxt.getBlockchainProcessor().isDownloading()
									&& !TransactionProcessorImpl.testUnconfirmedTransactions) return;
							final Peer peer = Peers.getAnyPeer(Peer.State.CONNECTED, true);
							if (peer == null) return;
							final JSONObject request = new JSONObject();
							request.put("requestType", "getUnconfirmedTransactions");
							final JSONArray exclude = new JSONArray();
							this.getAllUnconfirmedTransactionIds()
									.forEach(transactionId -> exclude.add(Long.toUnsignedString(transactionId)));
							Collections.sort(exclude);
							request.put("exclude", exclude);
							final JSONObject response = peer.send(JSON.prepareRequest(request), 10 * 1024 * 1024);
							if (response == null) return;
							final JSONArray transactionsData = (JSONArray) response.get("unconfirmedTransactions");
							if ((transactionsData == null) || (transactionsData.size() == 0)) return;
							try {
								this.processPeerTransactions(transactionsData);
							} catch (NxtException.ValidationException | RuntimeException e) {
								peer.blacklist(e);
							}
						} catch (final Exception e) {
							Logger.logMessage("Error processing unconfirmed transactions", e);
						}
					} catch (final Throwable t) {
						Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
						t.printStackTrace();
						System.exit(1);
					}

				};
				ThreadPool.scheduleThread("ProcessTransactions", processTransactionsThread, 5);
				ThreadPool.runAfterStart(this::rebroadcastAllUnconfirmedTransactions);
				Runnable rebroadcastTransactionsThread = () -> {

					try {
						try {
							if (Nxt.getBlockchainProcessor().isDownloading()
									&& !TransactionProcessorImpl.testUnconfirmedTransactions) return;
							final List<Transaction> transactionList = new ArrayList<>();
							final int curTime = Nxt.getEpochTime();

							final Iterator<TransactionImpl> iterator = this.broadcastedTransactions.iterator();
							while (iterator.hasNext()) {
								final TransactionImpl transaction = iterator.next();
								if ((transaction.getExpiration() < curTime) || TransactionDb.hasTransaction(transaction.getId()))
									iterator.remove();
								else if (transaction.getTimestamp() < (curTime - 30))
									transactionList.add(transaction);

							}

							if (transactionList.size() > 0) Peers.sendToSomePeers(transactionList);

						} catch (final Exception e) {
							Logger.logMessage("Error in transaction re-broadcasting thread", e);
						}
					} catch (final Throwable t) {
						Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
						t.printStackTrace();
						System.exit(1);
					}

				}; ThreadPool.scheduleThread("RebroadcastTransactions", rebroadcastTransactionsThread, 23);
			}
			Runnable removeUnconfirmedTransactionsThread = () -> {

				try {
					try {
						if (Nxt.getBlockchainProcessor().isDownloading()
								&& !TransactionProcessorImpl.testUnconfirmedTransactions) return;
						final List<UnconfirmedTransaction> expiredTransactions = new ArrayList<>();
						try (DbIterator<UnconfirmedTransaction> iterator = this.unconfirmedTransactionTable.getManyBy(
								new DbClause.IntClause("expiration", DbClause.Op.LT, Nxt.getEpochTime()), 0, -1, "")) {
							while (iterator.hasNext()) expiredTransactions.add(iterator.next());
						}
						if (expiredTransactions.size() > 0) {
							BlockchainImpl.getInstance().writeLock();
							try {
								try {
									Db.db.beginTransaction();
									for (final UnconfirmedTransaction unconfirmedTransaction : expiredTransactions)
										this.removeUnconfirmedTransaction(unconfirmedTransaction.getTransaction());
									Db.db.commitTransaction();
								} catch (final Exception e) {
									Logger.logErrorMessage(e.toString(), e);
									Db.db.rollbackTransaction();
									throw e;
								} finally {
									Db.db.endTransaction();
								}
							} finally {
								BlockchainImpl.getInstance().writeUnlock();
							}
						}
					} catch (final Exception e) {
						Logger.logMessage("Error removing unconfirmed transactions", e);
					}
				} catch (final Throwable t) {
					Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
					t.printStackTrace();
					System.exit(1);
				}

			}; ThreadPool.scheduleThread("RemoveUnconfirmedTransactions", removeUnconfirmedTransactionsThread, 20);
			Runnable processWaitingTransactionsThread = () -> {

				try {
					try {
						if (Nxt.getBlockchainProcessor().isDownloading()
								&& !TransactionProcessorImpl.testUnconfirmedTransactions) return;
						this.processWaitingTransactions();
					} catch (final Exception e) {
						Logger.logMessage("Error processing waiting transactions", e);
					}
				} catch (final Throwable t) {
					Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
					t.printStackTrace();
					System.exit(1);
				}

			};
			ThreadPool.scheduleThread("ProcessWaitingTransactions", processWaitingTransactionsThread, 1);
		}
	}

	@Override
	public boolean addListener(final Listener<List<? extends Transaction>> listener, final Event eventType) {
		return this.transactionListeners.addListener(listener, eventType);
	}

	private void broadcast_specialcase_supernode(final Transaction transaction) throws NxtException.ValidationException {
		if(!Peers.hasConnectedSnPeers(1))
			throw new NxtException.NotCurrentlyValidException("You are not connected to any supernode");
		List<Transaction> lst = new ArrayList<>();
		lst.add(transaction);
		Peers.sendToSomeSnPeers(lst);
	}
	@Override
	public void broadcast(final Transaction transaction) throws NxtException.ValidationException {

		Logger.logSignMessage("Broadcasting TX: "+Convert.toHexString(transaction.getBytes()));

		// Relay SN relevant transactions to the connected supernodes.
		// If none are present, just go ahead and cancel this request

		if(transaction.getType().mustHaveSupernodeSignature() && transaction.getSupernodeSig()==null){
			Logger.logInfoMessage("Transaction " + transaction.getStringId()
					+ " is relayed to SN.");
			broadcast_specialcase_supernode(transaction);
			return;
		}

		BlockchainImpl.getInstance().writeLock();
		try {
			if (TransactionDb.hasTransaction(transaction.getId())) {
				Logger.logInfoMessage("Transaction " + transaction.getStringId()
						+ " already in blockchain, will not broadcast again");
				return;
			}
			if (this.getUnconfirmedTransaction(((TransactionImpl) transaction).getDbKey()) != null) {
				if (TransactionProcessorImpl.enableTransactionRebroadcasting) {
					this.broadcastedTransactions.add((TransactionImpl) transaction);
					Logger.logInfoMessage("Transaction " + transaction.getStringId()
							+ " already in unconfirmed pool, will re-broadcast");
				} else Logger.logInfoMessage("Transaction " + transaction.getStringId()
						+ " already in unconfirmed pool, will not broadcast again");
				return;
			}
			transaction.validate();
			final UnconfirmedTransaction unconfirmedTransaction = new UnconfirmedTransaction(
					(TransactionImpl) transaction, System.currentTimeMillis());
			final boolean broadcastLater = BlockchainProcessorImpl.getInstance().isProcessingBlock();
			if (broadcastLater) {
				this.waitingTransactions.add(unconfirmedTransaction);
				this.broadcastedTransactions.add((TransactionImpl) transaction);
				Logger.logInfoMessage("Will broadcast new transaction later " + transaction.getStringId());
			} else {
				this.processTransaction(unconfirmedTransaction);
				Logger.logInfoMessage("Accepted new transaction " + transaction.getStringId());
				final List<Transaction> acceptedTransactions = Collections.singletonList(transaction);
				Peers.sendToSomePeers(acceptedTransactions);
				this.transactionListeners.notify(acceptedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
				this.transactionListeners.notify(acceptedTransactions, Event.BROADCASTED_OWN_TRANSACTION);
				if (TransactionProcessorImpl.enableTransactionRebroadcasting)
					this.broadcastedTransactions.add((TransactionImpl) transaction);
			}
		} finally {
			BlockchainImpl.getInstance().writeUnlock();
		}
	}

	@Override
	public void clearUnconfirmedThatGotInvalidLately() {

		final DbIterator<UnconfirmedTransaction> it = this.getAllUnconfirmedTransactions();
		while (it.hasNext()) {
			final UnconfirmedTransaction u = it.next();
			final TransactionImpl tImpl = u.getTransaction();

			// re-validate POW and proof of bounty
			if (u.getType() == TransactionType.WorkControl.BOUNTY) {
				final Attachment.PiggybackedProofOfBounty b = (Attachment.PiggybackedProofOfBounty) u.getAttachment();
				try {
					b.validate(tImpl);
				} catch (final Exception e) {
					// this tx became invalid! Purge it from the memory pool
					// immediately
					this.removeUnconfirmedTransaction(tImpl);
					System.err.println(
							"[!!] removing TX (bounty) from mem-pool that later became invalid: " + tImpl.getId());
				}

			}
			if (u.getType() == TransactionType.WorkControl.PROOF_OF_WORK) {
				final Attachment.PiggybackedProofOfWork b = (Attachment.PiggybackedProofOfWork) u.getAttachment();
				try {
					b.validate(tImpl);
				} catch (final Exception e) {
					// this tx became invalid! Purge it from the memory pool
					// immediately
					this.removeUnconfirmedTransaction(tImpl);
					System.err.println(
							"[!!] removing TX (pow) from mem-pool that later became invalid: " + tImpl.getId());
				}
			}
			if (u.getType() == TransactionType.WorkControl.CANCEL_TASK_REQUEST) {
				final Attachment.WorkIdentifierCancellationRequest b = (Attachment.WorkIdentifierCancellationRequest) u
						.getAttachment();
				try {
					b.validate(tImpl);
				} catch (final Exception e) {
					// this tx became invalid! Purge it from the memory pool
					// immediately
					this.removeUnconfirmedTransaction(tImpl);
					System.err.println(
							"[!!] removing TX (pow) from mem-pool that later became invalid: " + tImpl.getId());
				}
			}

		}

	}

	@Override
	public void clearUnconfirmedTransactions() {
		BlockchainImpl.getInstance().writeLock();
		try {
			final List<Transaction> removed = new ArrayList<>();
			try {
				Db.db.beginTransaction();
				try (DbIterator<UnconfirmedTransaction> unconfirmedTransactions = this
						.getAllUnconfirmedTransactions()) {
					for (final UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
						unconfirmedTransaction.getTransaction().undoUnconfirmed();
						removed.add(unconfirmedTransaction.getTransaction());
					}
				}
				this.unconfirmedTransactionTable.truncate();
				Db.db.commitTransaction();
			} catch (final Exception e) {
				Logger.logErrorMessage(e.toString(), e);
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
			this.unconfirmedDuplicates.clear();
			this.waitingTransactions.clear();
			this.broadcastedTransactions.clear();
			this.transactionCache.clear();
			this.SNCleantransactionCache.clear();
			this.transactionListeners.notify(removed, Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
		} finally {
			BlockchainImpl.getInstance().writeUnlock();
		}
	}

	@Override
	public TransactionImpl[] getAllBroadcastedTransactions() {
		BlockchainImpl.getInstance().readLock();
		try {
			return this.broadcastedTransactions.toArray(new TransactionImpl[this.broadcastedTransactions.size()]);
		} finally {
			BlockchainImpl.getInstance().readUnlock();
		}
	}

	private List<Long> getAllUnconfirmedTransactionIds() {
		final List<Long> result = new ArrayList<>();
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("SELECT id FROM unconfirmed_transaction");
				ResultSet rs = pstmt.executeQuery()) {
			while (rs.next()) result.add(rs.getLong("id"));
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return result;
	}

	@Override
	public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions() {
		return this.unconfirmedTransactionTable.getAll(0, -1);
	}

	@Override
	public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(final String sort) {
		return this.unconfirmedTransactionTable.getAll(0, -1, sort);
	}

	@Override
	public UnconfirmedTransaction[] getAllWaitingTransactions() {
		UnconfirmedTransaction[] transactions;
		BlockchainImpl.getInstance().readLock();
		try {
			transactions = this.waitingTransactions
					.toArray(new UnconfirmedTransaction[this.waitingTransactions.size()]);
		} finally {
			BlockchainImpl.getInstance().readUnlock();
		}
		Arrays.sort(transactions, this.waitingTransactions.comparator());
		return transactions;
	}

	/**
	 * Get the cached unconfirmed transactions
	 *
	 * @param exclude
	 *            List of transaction identifiers to exclude
	 */
	@Override
	public SortedSet<? extends Transaction> getCachedUnconfirmedTransactions(final List<String> exclude) {
		final SortedSet<UnconfirmedTransaction> transactionSet = new TreeSet<>(
				TransactionProcessorImpl.cachedUnconfirmedTransactionComparator);
		Nxt.getBlockchain().readLock();
		try {
			//
			// Initialize the unconfirmed transaction cache if it hasn't been
			// done yet
			//
			synchronized (this.transactionCache) {
				if (!this.cacheInitialized) {
					final DbIterator<UnconfirmedTransaction> it = this.getAllUnconfirmedTransactions();
					while (it.hasNext()) {
						final UnconfirmedTransaction unconfirmedTransaction = it.next();
						this.transactionCache.put(unconfirmedTransaction.getDbKey(), unconfirmedTransaction);
						this.SNCleantransactionCache.put(unconfirmedTransaction.getSNCleanedId(), unconfirmedTransaction);
					}
					this.cacheInitialized = true;
				}
			}
			//
			// Build the result set
			//
			this.transactionCache.values().forEach(transaction -> {
				if (Collections.binarySearch(exclude, transaction.getStringId()) < 0) transactionSet.add(transaction);
			});
		} finally {
			Nxt.getBlockchain().readUnlock();
		}
		return transactionSet;
	}

	private Transaction getUnconfirmedTransaction(final DbKey dbKey) {
		Nxt.getBlockchain().readLock();
		try {
			final Transaction transaction = this.transactionCache.get(dbKey);
			if (transaction != null) return transaction;
		} finally {
			Nxt.getBlockchain().readUnlock();
		}
		return this.unconfirmedTransactionTable.get(dbKey);
	}

	@Override
	public Transaction getUnconfirmedSNCleanTransaction(final long snclean) {
		Nxt.getBlockchain().readLock();
		try {
			final Transaction transaction = this.SNCleantransactionCache.get(snclean);
			if (transaction != null) return transaction;
		} finally {
			Nxt.getBlockchain().readUnlock();
		}
		return this.unconfirmedTransactionTable.getBy(new DbClause.LongClause("sncleanid",snclean));
	}

	@Override
	public Transaction getUnconfirmedTransaction(final long transactionId) {
		final DbKey dbKey = this.unconfirmedTransactionDbKeyFactory.newKey(transactionId);
		return this.getUnconfirmedTransaction(dbKey);
	}

	Collection<UnconfirmedTransaction> getWaitingTransactions() {
		return Collections.unmodifiableCollection(this.waitingTransactions);
	}

	void notifyListeners(final List<? extends Transaction> transactions, final Event eventType) {
		this.transactionListeners.notify(transactions, eventType);
	}

	@Override
	public void processLater(final Collection<? extends Transaction> transactions) {
		final long currentTime = System.currentTimeMillis();
		BlockchainImpl.getInstance().writeLock();
		try {
			for (final Transaction transaction : transactions) {
				((TransactionImpl) transaction).unsetBlock();
				this.waitingTransactions.add(new UnconfirmedTransaction((TransactionImpl) transaction,
						Math.min(currentTime, Convert.fromEpochTime(transaction.getTimestamp()))));
			}
		} finally {
			BlockchainImpl.getInstance().writeUnlock();
		}
	}

	private void processPeerTransactions(final JSONArray transactionsData) throws NxtException.NotValidException {
		if ((Nxt.getBlockchain().getHeight() < Constants.LAST_KNOWN_BLOCK)
				&& !TransactionProcessorImpl.testUnconfirmedTransactions) return;
		if ((transactionsData == null) || transactionsData.isEmpty()) return;
		final long arrivalTimestamp = System.currentTimeMillis();
		final List<TransactionImpl> receivedTransactions = new ArrayList<>();
		final List<TransactionImpl> sendToPeersTransactions = new ArrayList<>();
		final List<TransactionImpl> addedUnconfirmedTransactions = new ArrayList<>();
		final List<Exception> exceptions = new ArrayList<>();
		for (final Object transactionData : transactionsData)
			try {
				final TransactionImpl transaction = TransactionImpl.parseTransaction((JSONObject) transactionData);
				receivedTransactions.add(transaction);
				if ((this.getUnconfirmedTransaction(transaction.getDbKey()) != null)
						|| TransactionDb.hasTransaction(transaction.getId())) continue;

				if (transaction.getType().mustHaveSupernodeSignature() && (this.getUnconfirmedSNCleanTransaction(transaction.getSNCleanedId()) != null || TransactionDb.hasSNCleanTransaction(transaction.getSNCleanedId())))
					continue;

				transaction.validate();
				final UnconfirmedTransaction unconfirmedTransaction = new UnconfirmedTransaction(transaction,
						arrivalTimestamp);
				this.processTransaction(unconfirmedTransaction);
				if (this.broadcastedTransactions.contains(transaction))
					Logger.logInfoMessage("Received back transaction " + transaction.getStringId()
							+ " that we broadcasted, will not forward again to peers");
				else sendToPeersTransactions.add(transaction);
				addedUnconfirmedTransactions.add(transaction);

			} catch (final NxtException.NotCurrentlyValidException ignore) {
			} catch (NxtException.ValidationException | RuntimeException e) {
				Logger.logInfoMessage(String.format("Invalid transaction from peer: %s",
						((JSONObject) transactionData).toJSONString()), e);
				exceptions.add(e);
			}
		if (sendToPeersTransactions.size() > 0) Peers.sendToSomePeers(sendToPeersTransactions);
		if (addedUnconfirmedTransactions.size() > 0)
			this.transactionListeners.notify(addedUnconfirmedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
		this.broadcastedTransactions.removeAll(receivedTransactions);
		if (!exceptions.isEmpty()) {
			Logger.logInfoMessage("Peer sends invalid transactions: " + exceptions.toString());
			throw new NxtException.NotValidException("Peer sends invalid transactions: " + exceptions.toString());
		}
	}

	@Override
	public void processPeerTransactions(final JSONObject request) throws NxtException.ValidationException {
		final JSONArray transactionsData = (JSONArray) request.get("transactions");
		this.processPeerTransactions(transactionsData);
	}

	private void processTransaction(final UnconfirmedTransaction unconfirmedTransaction)
			throws NxtException.ValidationException {
		final TransactionImpl transaction = unconfirmedTransaction.getTransaction();
		final int curTime = Nxt.getEpochTime();
		if ((transaction.getTimestamp() > (curTime + Constants.MAX_TIMEDRIFT))
				|| ((transaction.getExpiration() < curTime) && !Objects.equals(transaction.getAttachment().getTransactionType(), TransactionType.Payment.REDEEM)))
			throw new NxtException.NotCurrentlyValidException("Invalid transaction timestamp: Bigger than timedrift = " + ((transaction.getTimestamp() > (curTime + Constants.MAX_TIMEDRIFT))) + ", expiration lower than currTime = " + (transaction.getExpiration() < curTime));
		if (transaction.getVersion() < 1) throw new NxtException.NotValidException("Invalid transaction version");
		if (transaction.getId() == 0L) throw new NxtException.NotValidException("Invalid transaction id 0");

		BlockchainImpl.getInstance().writeLock();
		try {
			try {
				Db.db.beginTransaction();
				if ((Nxt.getBlockchain().getHeight() < Constants.LAST_KNOWN_BLOCK)
						&& !TransactionProcessorImpl.testUnconfirmedTransactions)
					throw new NxtException.NotCurrentlyValidException(
							"Blockchain not ready to accept transactions: last block " + Nxt.getBlockchain().getHeight()
									+ " < " + Constants.LAST_KNOWN_BLOCK);

				if ((this.getUnconfirmedTransaction(transaction.getDbKey()) != null)
						|| TransactionDb.hasTransaction(transaction.getId()))
					throw new NxtException.ExistingTransactionException("Transaction already processed");

				if(transaction.getType().mustHaveSupernodeSignature() && (this.getUnconfirmedSNCleanTransaction(transaction.getSNCleanedId()) != null || TransactionDb.hasSNCleanTransaction(transaction.getSNCleanedId())))
					throw new NxtException.ExistingTransactionException("Core-Transaction (SN Cleaned) already processed");

				if (!transaction.verifySignature()) if (Account.getAccount(transaction.getSenderId()) != null)
					throw new NxtException.NotValidException("Transaction signature verification failed");
				else throw new NxtException.NotCurrentlyValidException("Unknown transaction sender");

				if (!transaction.applyUnconfirmed())
					throw new NxtException.InsufficientBalanceException("Insufficient balance");

				if (transaction.isUnconfirmedDuplicate(this.unconfirmedDuplicates))
					if (transaction.getExtraInfo().length() > 0) throw new NxtException.NotCurrentlyValidException(
							"Duplicate unconfirmed transaction: " + transaction.getExtraInfo());
					else throw new NxtException.NotCurrentlyValidException("Duplicate unconfirmed transaction");

				this.unconfirmedTransactionTable.insert(unconfirmedTransaction);

				Db.db.commitTransaction();
			} catch (final Exception e) {
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
		} finally {
			BlockchainImpl.getInstance().writeUnlock();
		}
	}

	void processWaitingTransactions() {
		BlockchainImpl.getInstance().writeLock();
		try {
			if (this.waitingTransactions.size() > 0) {
				final int currentTime = Nxt.getEpochTime();
				final List<Transaction> addedUnconfirmedTransactions = new ArrayList<>();
				final Iterator<UnconfirmedTransaction> iterator = this.waitingTransactions.iterator();
				while (iterator.hasNext()) {
					final UnconfirmedTransaction unconfirmedTransaction = iterator.next();
					try {
						this.processTransaction(unconfirmedTransaction);
						iterator.remove();
						addedUnconfirmedTransactions.add(unconfirmedTransaction.getTransaction());
					} catch (final NxtException.ExistingTransactionException e) {
						iterator.remove();
					} catch (final NxtException.NotCurrentlyValidException e) {
						if ((unconfirmedTransaction.getExpiration() < currentTime) || ((currentTime
								- Convert.toEpochTime(unconfirmedTransaction.getArrivalTimestamp())) > 3600))
							iterator.remove();
					} catch (NxtException.ValidationException | RuntimeException e) {
						iterator.remove();
					}
				}
				if (addedUnconfirmedTransactions.size() > 0)
					this.transactionListeners.notify(addedUnconfirmedTransactions,
							Event.ADDED_UNCONFIRMED_TRANSACTIONS);
			}
		} finally {
			BlockchainImpl.getInstance().writeUnlock();
		}
	}

	@Override
	public void rebroadcastAllUnconfirmedTransactions() {
		BlockchainImpl.getInstance().writeLock();
		try {
			try (DbIterator<UnconfirmedTransaction> oldNonBroadcastedTransactions = this
					.getAllUnconfirmedTransactions()) {
				for (final UnconfirmedTransaction unconfirmedTransaction : oldNonBroadcastedTransactions)
					if (unconfirmedTransaction.getTransaction().isUnconfirmedDuplicate(this.unconfirmedDuplicates))
						Logger.logDebugMessage("Skipping duplicate unconfirmed transaction "
								+ unconfirmedTransaction.getTransaction().getJSONObject().toString());
					else if (TransactionProcessorImpl.enableTransactionRebroadcasting)
						this.broadcastedTransactions.add(unconfirmedTransaction.getTransaction());
			}
		} finally {
			BlockchainImpl.getInstance().writeUnlock();
		}
	}

	@Override
	public boolean removeListener(final Listener<List<? extends Transaction>> listener, final Event eventType) {
		return this.transactionListeners.removeListener(listener, eventType);
	}

	void removeUnconfirmedTransaction(final TransactionImpl transaction) {
		if (!Db.db.isInTransaction()) {
			try {
				Db.db.beginTransaction();
				this.removeUnconfirmedTransaction(transaction);
				Db.db.commitTransaction();
			} catch (final Exception e) {
				Logger.logErrorMessage(e.toString(), e);
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
			return;
		}
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement("DELETE FROM unconfirmed_transaction WHERE id = ?")) {
			pstmt.setLong(1, transaction.getId());
			final int deleted = pstmt.executeUpdate();
			if (deleted > 0) {
				transaction.undoUnconfirmed();
				this.transactionCache.remove(transaction.getDbKey());
				this.SNCleantransactionCache.remove(transaction.getSNCleanedId());
				this.transactionListeners.notify(Collections.singletonList(transaction),
						Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
			}
		} catch (final SQLException e) {
			Logger.logErrorMessage(e.toString(), e);
			throw new RuntimeException(e.toString(), e);
		}
	}

	@Override
	public void requeueAllUnconfirmedTransactions() {
		BlockchainImpl.getInstance().writeLock();
		try {
			if (!Db.db.isInTransaction()) {
				try {
					Db.db.beginTransaction();
					this.requeueAllUnconfirmedTransactions();
					Db.db.commitTransaction();
				} catch (final Exception e) {
					Logger.logErrorMessage(e.toString(), e);
					Db.db.rollbackTransaction();
					throw e;
				} finally {
					Db.db.endTransaction();
				}
				return;
			}
			final List<Transaction> removed = new ArrayList<>();
			try (DbIterator<UnconfirmedTransaction> unconfirmedTransactions = this.getAllUnconfirmedTransactions()) {
				for (final UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
					unconfirmedTransaction.getTransaction().undoUnconfirmed();
					if (removed.size() < TransactionProcessorImpl.maxUnconfirmedTransactions)
						removed.add(unconfirmedTransaction.getTransaction());
					this.waitingTransactions.add(unconfirmedTransaction);
				}
			}
			this.unconfirmedTransactionTable.truncate();
			this.unconfirmedDuplicates.clear();
			this.transactionCache.clear();
			this.SNCleantransactionCache.clear();
			this.transactionListeners.notify(removed, Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
		} finally {
			BlockchainImpl.getInstance().writeUnlock();
		}
	}

	/**
	 * Restore expired prunable data
	 *
	 * @param transactions
	 *            Transactions containing prunable data
	 * @return Processed transactions
	 * @throws NxtException.NotValidException
	 *             Transaction is not valid
	 */
	@Override
	public List<Transaction> restorePrunableData(final JSONArray transactions) throws NxtException.NotValidException {
		final List<Transaction> processed = new ArrayList<>();
		Nxt.getBlockchain().readLock();
		try {
			Db.db.beginTransaction();
			try {
				//
				// Check each transaction returned by the archive peer
				//
				for (final Object transactionJSON : transactions) {
					final TransactionImpl transaction = TransactionImpl.parseTransaction((JSONObject) transactionJSON);
					final TransactionImpl myTransaction = TransactionDb
							.findTransactionByFullHash(transaction.fullHash());
					if (myTransaction != null) {
						boolean foundAllData = true;
						//
						// Process each prunable appendage
						//
						appendageLoop: for (final Appendix.AbstractAppendix appendage : transaction.getAppendages())
							if ((appendage instanceof Appendix.Prunable)) {
								//
								// Don't load the prunable data if we already
								// have the data
								//
								for (final Appendix.AbstractAppendix myAppendage : myTransaction.getAppendages())
									if (myAppendage.getClass() == appendage.getClass()) {
										myAppendage.loadPrunable(myTransaction, true);
										if (((Appendix.Prunable) myAppendage).hasPrunableData()) {
											Logger.logDebugMessage(String.format(
													"Already have prunable data for transaction %s %s appendage",
													myTransaction.getStringId(), myAppendage.getAppendixName()));
											continue appendageLoop;
										}
										break;
									}
								//
								// Load the prunable data
								//
								if (((Appendix.Prunable) appendage).hasPrunableData()) {
									Logger.logDebugMessage(String.format(
											"Loading prunable data for transaction %s %s appendage",
											Long.toUnsignedString(transaction.getId()), appendage.getAppendixName()));
									((Appendix.Prunable) appendage).restorePrunableData(transaction,
											myTransaction.getBlockTimestamp(), myTransaction.getHeight());
								} else foundAllData = false;
							}
						if (foundAllData) processed.add(myTransaction);
						Db.db.clearCache();
						Db.db.commitTransaction();
					}
				}
				Db.db.commitTransaction();
			} catch (final Exception e) {
				Db.db.rollbackTransaction();
				processed.clear();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
		} finally {
			Nxt.getBlockchain().readUnlock();
		}
		return processed;
	}

}

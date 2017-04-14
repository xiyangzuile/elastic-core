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

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;

import nxt.NxtException.NotValidException;
import nxt.crypto.Crypto;
import nxt.db.DerivedDbTable;
import nxt.db.FilteringIterator;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;
import nxt.util.ThreadPool;

public final class BlockchainProcessorImpl implements BlockchainProcessor {

	/**
	 * Callable method to get the next block segment from the selected peer
	 */
	private static class GetNextBlocks implements Callable<List<BlockImpl>> {

		/** Callable future */
		private Future<List<BlockImpl>> future;

		/** Peer */
		private Peer peer;

		/** Block identifier list */
		private final List<Long> blockIds;

		/** Start index */
		private int start;

		/** Stop index */
		private int stop;

		/** Request count */
		private int requestCount;

		/** Time it took to return getNextBlocks */
		private long responseTime;

		/**
		 * Create the callable future
		 *
		 * @param blockIds
		 *            Block identifier list
		 * @param start
		 *            Start index within the list
		 * @param stop
		 *            Stop index within the list
		 */
		public GetNextBlocks(final List<Long> blockIds, final int start, final int stop) {
			this.blockIds = blockIds;
			this.start = start;
			this.stop = stop;
			this.requestCount = 0;
		}

		/**
		 * Return the result
		 *
		 * @return List of blocks or null if an error occurred
		 */
		@Override
		public List<BlockImpl> call() {
			this.requestCount++;
			//
			// Build the block request list
			//
			final JSONArray idList = new JSONArray();
			for (int i = this.start + 1; i <= this.stop; i++) {
				idList.add(Long.toUnsignedString(this.blockIds.get(i)));
			}
			final JSONObject request = new JSONObject();
			request.put("requestType", "getNextBlocks");
			request.put("blockIds", idList);
			request.put("blockId", Long.toUnsignedString(this.blockIds.get(this.start)));
			final long startTime = System.currentTimeMillis();
			final JSONObject response = this.peer.send(JSON.prepareRequest(request), 10 * 1024 * 1024);
			this.responseTime = System.currentTimeMillis() - startTime;
			if (response == null) {
				return null;
			}
			//
			// Get the list of blocks. We will stop parsing blocks if we
			// encounter
			// an invalid block. We will return the valid blocks and reset the
			// stop
			// index so no more blocks will be processed.
			//
			final List<JSONObject> nextBlocks = (List<JSONObject>) response.get("nextBlocks");
			if (nextBlocks == null) {
				return null;
			}
			if (nextBlocks.size() > 36) {
				Logger.logDebugMessage(
						"Obsolete or rogue peer " + this.peer.getHost() + " sends too many nextBlocks, blacklisting");
				this.peer.blacklist("Too many nextBlocks");
				return null;
			}
			final List<BlockImpl> blockList = new ArrayList<>(nextBlocks.size());
			try {
				int count = this.stop - this.start;
				for (final JSONObject blockData : nextBlocks) {
					blockList.add(BlockImpl.parseBlock(blockData));
					if (--count <= 0) {
						break;
					}
				}
			} catch (RuntimeException | NxtException.NotValidException e) {
				Logger.logDebugMessage("Failed to parse block: " + e.toString(), e);
				this.peer.blacklist(e);
				this.stop = this.start + blockList.size();
			}
			return blockList;
		}

		/**
		 * Return the callable future
		 *
		 * @return Callable future
		 */
		public Future<List<BlockImpl>> getFuture() {
			return this.future;
		}

		/**
		 * Return the peer
		 *
		 * @return Peer
		 */
		public Peer getPeer() {
			return this.peer;
		}

		/**
		 * Return the request count
		 *
		 * @return Request count
		 */
		public int getRequestCount() {
			return this.requestCount;
		}

		/**
		 * Return the response time
		 *
		 * @return Response time
		 */
		public long getResponseTime() {
			return this.responseTime;
		}

		/**
		 * Return the start index
		 *
		 * @return Start index
		 */
		public int getStart() {
			return this.start;
		}

		/**
		 * Return the stop index
		 *
		 * @return Stop index
		 */
		public int getStop() {
			return this.stop;
		}

		/**
		 * Set the callable future
		 *
		 * @param future
		 *            Callable future
		 */
		public void setFuture(final Future<List<BlockImpl>> future) {
			this.future = future;
		}

		/**
		 * Set the peer
		 *
		 * @param peer
		 *            Peer
		 */
		public void setPeer(final Peer peer) {
			this.peer = peer;
		}

		/**
		 * Set the start index
		 *
		 * @param start
		 *            Start index
		 */
		public void setStart(final int start) {
			this.start = start;
		}
	}

	/**
	 * Block returned by a peer
	 */
	private static class PeerBlock {

		/** Peer */
		private final Peer peer;

		/** Block */
		private final BlockImpl block;

		/**
		 * Create the peer block
		 *
		 * @param peer
		 *            Peer
		 * @param block
		 *            Block
		 */
		public PeerBlock(final Peer peer, final BlockImpl block) {
			this.peer = peer;
			this.block = block;
		}

		/**
		 * Return the block
		 *
		 * @return Block
		 */
		public BlockImpl getBlock() {
			return this.block;
		}

		/**
		 * Return the peer
		 *
		 * @return Peer
		 */
		public Peer getPeer() {
			return this.peer;
		}
	}

	/**
	 * Task to restore prunable data for downloaded blocks
	 */
	private class RestorePrunableDataTask implements Runnable {

		@Override
		public void run() {
			Peer peer = null;
			try {
				//
				// Locate an archive peer
				//
				final List<Peer> peers = Peers.getPeers(chkPeer -> chkPeer.providesService(Peer.Service.PRUNABLE)
						&& !chkPeer.isBlacklisted() && (chkPeer.getAnnouncedAddress() != null));
				while (!peers.isEmpty()) {
					final Peer chkPeer = peers.get(ThreadLocalRandom.current().nextInt(peers.size()));
					if (chkPeer.getState() != Peer.State.CONNECTED) {
						Peers.connectPeer(chkPeer);
					}
					if (chkPeer.getState() == Peer.State.CONNECTED) {
						peer = chkPeer;
						break;
					}
				}
				if (peer == null) {
					Logger.logDebugMessage("Cannot find any archive peers");
					return;
				}
				Logger.logDebugMessage("Connected to archive peer " + peer.getHost());
				//
				// Make a copy of the prunable transaction list so we can remove
				// entries
				// as we process them while still retaining the entry if we need
				// to
				// retry later using a different archive peer
				//
				Set<Long> processing;
				synchronized (BlockchainProcessorImpl.this.prunableTransactions) {
					processing = new HashSet<>(BlockchainProcessorImpl.this.prunableTransactions.size());
					processing.addAll(BlockchainProcessorImpl.this.prunableTransactions);
				}
				Logger.logDebugMessage("Need to restore " + processing.size() + " pruned data");
				//
				// Request transactions in batches of 100 until all transactions
				// have been processed
				//
				while (!processing.isEmpty()) {
					//
					// Get the pruned transactions from the archive peer
					//
					final JSONObject request = new JSONObject();
					final JSONArray requestList = new JSONArray();
					synchronized (BlockchainProcessorImpl.this.prunableTransactions) {
						final Iterator<Long> it = processing.iterator();
						while (it.hasNext()) {
							final long id = it.next();
							requestList.add(Long.toUnsignedString(id));
							it.remove();
							if (requestList.size() == 100) {
								break;
							}
						}
					}
					request.put("requestType", "getTransactions");
					request.put("transactionIds", requestList);
					final JSONObject response = peer.send(JSON.prepareRequest(request), 10 * 1024 * 1024);
					if (response == null) {
						return;
					}
					//
					// Restore the prunable data
					//
					final JSONArray transactions = (JSONArray) response.get("transactions");
					if ((transactions == null) || transactions.isEmpty()) {
						return;
					}
					final List<Transaction> processed = Nxt.getTransactionProcessor().restorePrunableData(transactions);
					//
					// Remove transactions that have been successfully processed
					//
					synchronized (BlockchainProcessorImpl.this.prunableTransactions) {
						processed.forEach(transaction -> BlockchainProcessorImpl.this.prunableTransactions
								.remove(transaction.getId()));
					}
				}
				Logger.logDebugMessage("Done retrieving prunable transactions from " + peer.getHost());
			} catch (final NxtException.ValidationException e) {
				Logger.logErrorMessage("Peer " + peer.getHost() + " returned invalid prunable transaction", e);
				peer.blacklist(e);
			} catch (final RuntimeException e) {
				Logger.logErrorMessage("Unable to restore prunable data", e);
			} finally {
				BlockchainProcessorImpl.this.isRestoring = false;
				Logger.logDebugMessage("Remaining " + BlockchainProcessorImpl.this.prunableTransactions.size()
						+ " pruned transactions");
			}
		}
	}

	private static final BlockchainProcessorImpl instance = new BlockchainProcessorImpl();
	/*
	 * private static final Comparator<Transaction>
	 * finishingTransactionsComparator = Comparator
	 * .comparingInt(Transaction::getHeight)
	 * .thenComparingInt(Transaction::getIndex)
	 * .thenComparingLong(Transaction::getId);
	 */
	private static final Comparator<UnconfirmedTransaction> transactionArrivalComparator = Comparator
			.comparingLong(UnconfirmedTransaction::getArrivalTimestamp)
			.thenComparingInt(UnconfirmedTransaction::getHeight).thenComparingLong(UnconfirmedTransaction::getId);

	public static BlockchainProcessorImpl getInstance() {
		return BlockchainProcessorImpl.instance;
	}

	private final BlockchainImpl blockchain = BlockchainImpl.getInstance();

	private final ExecutorService networkService = Executors.newCachedThreadPool();
	private final List<DerivedDbTable> derivedTables = new CopyOnWriteArrayList<>();
	private final boolean trimDerivedTables = Nxt.getBooleanProperty("nxt.trimDerivedTables");
	private final int defaultNumberOfForkConfirmations = Nxt.getIntProperty(
			Constants.isTestnet ? "nxt.testnetNumberOfForkConfirmations" : "nxt.numberOfForkConfirmations");

	private final boolean simulateEndlessDownload = Nxt.getBooleanProperty("nxt.simulateEndlessDownload");
	private int initialScanHeight;
	private volatile int lastTrimHeight;
	private volatile int lastRestoreTime = 0;

	private final Set<Long> prunableTransactions = new HashSet<>();
	public final Listeners<Block, Event> blockListeners = new Listeners<>();
	private volatile Peer lastBlockchainFeeder;
	private volatile int lastBlockchainFeederHeight;
	private volatile boolean getMoreBlocks = true;
	private volatile boolean isTrimming;

	private volatile boolean isScanning;

	private volatile boolean isDownloading;

	private volatile boolean isProcessingBlock;

	private volatile boolean isRestoring;

	private volatile boolean alreadyInitialized = false;

	private final Runnable getMoreBlocksThread = new Runnable() {

		private final JSONStreamAware getCumulativeDifficultyRequest;

		{
			final JSONObject request = new JSONObject();
			request.put("requestType", "getCumulativeDifficulty");
			this.getCumulativeDifficultyRequest = JSON.prepareRequest(request);
		}

		private boolean peerHasMore;
		private List<Peer> connectedPublicPeers;
		private List<Long> chainBlockIds;
		private long totalTime = 1;
		private int totalBlocks;

		/**
		 * Download the block chain
		 *
		 * @param feederPeer
		 *            Peer supplying the blocks list
		 * @param commonBlock
		 *            Common block
		 * @throws InterruptedException
		 *             Download interrupted
		 */
		private void downloadBlockchain(final Peer feederPeer, final Block commonBlock, final int startHeight)
				throws InterruptedException {
			final Map<Long, PeerBlock> blockMap = new HashMap<>();
			//
			// Break the download into multiple segments. The first block in
			// each segment
			// is the common block for that segment.
			//
			Logger.logDebugMessage("Download Blockchain: feeder peer's address: " + feederPeer.getAnnouncedAddress());
			final List<GetNextBlocks> getList = new ArrayList<>();
			final int segSize = 36;
			final int stop = this.chainBlockIds.size() - 1;
			for (int start = 0; start < stop; start += segSize) {
				getList.add(new GetNextBlocks(this.chainBlockIds, start, Math.min(start + segSize, stop)));
			}

			int nextPeerIndex = ThreadLocalRandom.current().nextInt(this.connectedPublicPeers.size());
			long maxResponseTime = 0;
			Peer slowestPeer = null;
			//
			// Issue the getNextBlocks requests and get the results. We will
			// repeat
			// a request if the peer didn't respond or returned a partial block
			// list.
			// The download will be aborted if we are unable to get a segment
			// after
			// retrying with different peers.
			//
			download: while (!getList.isEmpty()) {
				//
				// Submit threads to issue 'getNextBlocks' requests. The first
				// segment
				// will always be sent to the feeder peer. Subsequent segments
				// will
				// be sent to the feeder peer if we failed trying to download
				// the blocks
				// from another peer. We will stop the download and process any
				// pending
				// blocks if we are unable to download a segment from the feeder
				// peer.
				//
				for (final GetNextBlocks nextBlocks : getList) {
					Peer peer;
					if (nextBlocks.getRequestCount() > 1) {
						break download;
					}
					if ((nextBlocks.getStart() == 0) || (nextBlocks.getRequestCount() != 0)) {
						peer = feederPeer;
					} else {
						if (nextPeerIndex >= this.connectedPublicPeers.size()) {
							nextPeerIndex = 0;
						}
						peer = this.connectedPublicPeers.get(nextPeerIndex++);
					}
					if (nextBlocks.getPeer() == peer) {
						break download;
					}
					nextBlocks.setPeer(peer);
					final Future<List<BlockImpl>> future = BlockchainProcessorImpl.this.networkService
							.submit(nextBlocks);
					nextBlocks.setFuture(future);
				}
				//
				// Get the results. A peer is on a different fork if a returned
				// block is not in the block identifier list.
				//
				final Iterator<GetNextBlocks> it = getList.iterator();
				while (it.hasNext()) {
					final GetNextBlocks nextBlocks = it.next();
					List<BlockImpl> blockList;
					try {
						blockList = nextBlocks.getFuture().get();
					} catch (final ExecutionException exc) {
						throw new RuntimeException(exc.getMessage(), exc);
					}
					if (blockList == null) {
						nextBlocks.getPeer().deactivate();
						continue;
					}
					final Peer peer = nextBlocks.getPeer();
					int index = nextBlocks.getStart() + 1;
					Logger.logDebugMessage("Received blocklist size: " + blockList.size() + " form peer "
							+ peer.getAnnouncedAddress() + ", index = " + index);
					for (final BlockImpl block : blockList) {
						if (block.getId() != this.chainBlockIds.get(index)) {
							Logger.logDebugMessage("... ignoring " + block.getId());
							break;
						}
						Logger.logDebugMessage("... blockmapping " + block.getId() + " (was in original GetNextBlock? "
								+ (nextBlocks.blockIds.indexOf(block.getId()) >= 0) + ")");
						blockMap.put(block.getId(), new PeerBlock(peer, block));
						index++;
					}
					if (index > nextBlocks.getStop()) {
						it.remove();
					} else {
						nextBlocks.setStart(index - 1);
					}
					if (nextBlocks.getResponseTime() > maxResponseTime) {
						maxResponseTime = nextBlocks.getResponseTime();
						slowestPeer = nextBlocks.getPeer();
					}
				}

			}
			if ((slowestPeer != null) && (this.connectedPublicPeers.size() >= Peers.maxNumberOfConnectedPublicPeers)
					&& (this.chainBlockIds.size() > 360)) {
				Logger.logDebugMessage(slowestPeer.getHost() + " took " + maxResponseTime + " ms, disconnecting");
				slowestPeer.deactivate();
			}
			//
			// Add the new blocks to the blockchain. We will stop if we
			// encounter
			// a missing block (this will happen if an invalid block is
			// encountered
			// when downloading the blocks)
			//

			Logger.logDebugMessage("Download Blockchain: Connecting blocks ... received size = "
					+ this.chainBlockIds.size() + ", local chain height: "
					+ BlockchainProcessorImpl.this.blockchain.getHeight() + ", start height = " + startHeight);

			BlockchainProcessorImpl.this.blockchain.writeLock();
			try {
				final List<BlockImpl> forkBlocks = new ArrayList<>();
				for (int index = 1; (index < this.chainBlockIds.size())
						&& ((BlockchainProcessorImpl.this.blockchain.getHeight() - startHeight) < 720); index++) {
					Logger.logDebugMessage("... inspecing chain block id " + this.chainBlockIds.get(index));
					final PeerBlock peerBlock = blockMap.get(this.chainBlockIds.get(index));
					if (peerBlock == null) {
						Logger.logDebugMessage("... crippled, block not in blockMap!");
						break;
					}
					final BlockImpl block = peerBlock.getBlock();
					if (BlockchainProcessorImpl.this.blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
						try {
							Logger.logDebugMessage("About to push peer " + feederPeer.getAnnouncedAddress()
									+ "'s block " + block.getId() + " with prevId = " + block.getPreviousBlockId());
							BlockchainProcessorImpl.this.pushBlock(block);

						} catch (final BlockNotAcceptedException e) {
							//e.printStackTrace();
							Logger.logDebugMessage("Will blacklist peer " + feederPeer.getAnnouncedAddress()
									+ " soon, block was not accepted!");
							peerBlock.getPeer().blacklist(e);
						}
					} else {
						forkBlocks.add(block);
					}
				}
				//
				// Process a fork
				//
				final int myForkSize = BlockchainProcessorImpl.this.blockchain.getHeight() - startHeight;
				if (!forkBlocks.isEmpty() && (myForkSize < 720)) {
					Logger.logDebugMessage(
							"Will process a fork of " + forkBlocks.size() + " blocks, mine is " + myForkSize);
					this.processFork(feederPeer, forkBlocks, commonBlock);
				} else {
					Logger.logDebugMessage("Skipping fork, since forksize is " + myForkSize
							+ " and forkBlocks is empty? = " + forkBlocks.isEmpty());
				}
			} finally {
				BlockchainProcessorImpl.this.blockchain.writeUnlock();
			}

		}

		private void downloadPeer() throws InterruptedException {
			try {
				final long startTime = System.currentTimeMillis();
				final int numberOfForkConfirmations = BlockchainProcessorImpl.this.blockchain
						.getHeight() > (Constants.LAST_CHECKSUM_BLOCK - 720)
								? BlockchainProcessorImpl.this.defaultNumberOfForkConfirmations
								: Math.min(1, BlockchainProcessorImpl.this.defaultNumberOfForkConfirmations);
				this.connectedPublicPeers = Peers.getPublicPeers(Peer.State.CONNECTED, true);
				if (this.connectedPublicPeers.size() <= numberOfForkConfirmations) {
					return;
				}
				this.peerHasMore = true;
				final Peer peer = Peers.getWeightedPeer(this.connectedPublicPeers);
				if (peer == null) {
					return;
				}
				final JSONObject response = peer.send(this.getCumulativeDifficultyRequest);
				if (response == null) {
					return;
				}
				final BigInteger curCumulativeDifficulty = BlockchainProcessorImpl.this.blockchain.getLastBlock()
						.getCumulativeDifficulty();
				final String peerCumulativeDifficulty = (String) response.get("cumulativeDifficulty");
				if (peerCumulativeDifficulty == null) {
					return;
				}
				final BigInteger betterCumulativeDifficulty = new BigInteger(peerCumulativeDifficulty);
				if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) < 0) {
					return;
				}
				if (response.get("blockchainHeight") != null) {
					Logger.logDebugMessage(
							"Peer reported blockchain height: " + ((Long) response.get("blockchainHeight")).intValue());
					BlockchainProcessorImpl.this.lastBlockchainFeeder = peer;
					BlockchainProcessorImpl.this.lastBlockchainFeederHeight = ((Long) response.get("blockchainHeight"))
							.intValue();
				}
				if (betterCumulativeDifficulty.equals(curCumulativeDifficulty)) {
					return;
				}

				long commonMilestoneBlockId = Genesis.GENESIS_BLOCK_ID;

				if (BlockchainProcessorImpl.this.blockchain.getLastBlock().getId() != Genesis.GENESIS_BLOCK_ID) {
					commonMilestoneBlockId = this.getCommonMilestoneBlockId(peer);
				}
				if ((commonMilestoneBlockId == 0) || !this.peerHasMore) {
					Logger.logDebugMessage("Peer has crippled common milestone!");
					return;
				}
				Logger.logDebugMessage("Peer has common milestone: " + commonMilestoneBlockId);

				this.chainBlockIds = this.getBlockIdsAfterCommon(peer, commonMilestoneBlockId, false);
				if ((this.chainBlockIds.size() < 2) || !this.peerHasMore) {
					return;
				}

				final long commonBlockId = this.chainBlockIds.get(0);
				final Block commonBlock = BlockchainProcessorImpl.this.blockchain.getBlock(commonBlockId);
				if ((commonBlock == null)
						|| ((BlockchainProcessorImpl.this.blockchain.getHeight() - commonBlock.getHeight()) >= 720)) {
					Logger.logDebugMessage("Peers fork is older than 720 blocks, dropping: commonblockId = "
							+ commonBlockId + ", block is null? " + (commonBlock == null));
					return;
				}
				if (BlockchainProcessorImpl.this.simulateEndlessDownload) {
					Logger.logDebugMessage("Aborting, simulating endless download!");
					BlockchainProcessorImpl.this.isDownloading = true;
					return;
				}
				if (!BlockchainProcessorImpl.this.isDownloading
						&& ((BlockchainProcessorImpl.this.lastBlockchainFeederHeight - commonBlock.getHeight()) > 10)) {
					Logger.logMessage("Blockchain download in progress");
					BlockchainProcessorImpl.this.isDownloading = true;
				}

				BlockchainProcessorImpl.this.blockchain.updateLock();
				try {
					if (betterCumulativeDifficulty.compareTo(
							BlockchainProcessorImpl.this.blockchain.getLastBlock().getCumulativeDifficulty()) <= 0) {
						Logger.logDebugMessage("Cancelled in cumulative difficulty check");
						return;
					}
					final long lastBlockId = BlockchainProcessorImpl.this.blockchain.getLastBlock().getId();
					this.downloadBlockchain(peer, commonBlock, commonBlock.getHeight());
					/*
					 * if (blockchain.getHeight() - commonBlock.getHeight() <=
					 * 10) { Logger.
					 * logInfoMessage("Cancelled in common block check, common block height = "
					 * + commonBlock.getHeight() + ", local chain height = " +
					 * blockchain.getHeight() + ", diff is = " +
					 * (blockchain.getHeight() - commonBlock.getHeight()));
					 * return; }
					 */

					int confirmations = 0;
					for (final Peer otherPeer : this.connectedPublicPeers) {

						Logger.logDebugMessage(
								"Probing peer for fork confirmations: " + otherPeer.getAnnouncedAddress());
						if (confirmations >= numberOfForkConfirmations) {
							break;
						}
						if (peer.getHost().equals(otherPeer.getHost())) {
							continue;
						}
						this.chainBlockIds = this.getBlockIdsAfterCommon(otherPeer, commonBlockId, true);
						if (this.chainBlockIds.isEmpty()) {
							continue;
						}
						final long otherPeerCommonBlockId = this.chainBlockIds.get(0);
						if (otherPeerCommonBlockId == BlockchainProcessorImpl.this.blockchain.getLastBlock().getId()) {
							confirmations++;
							continue;
						}
						final Block otherPeerCommonBlock = BlockchainProcessorImpl.this.blockchain
								.getBlock(otherPeerCommonBlockId);
						if ((BlockchainProcessorImpl.this.blockchain.getHeight()
								- otherPeerCommonBlock.getHeight()) >= 720) {
							continue;
						}
						String otherPeerCumulativeDifficulty;
						final JSONObject otherPeerResponse = peer.send(this.getCumulativeDifficultyRequest);
						if ((otherPeerResponse == null) || ((otherPeerCumulativeDifficulty = (String) response
								.get("cumulativeDifficulty")) == null)) {
							continue;
						}
						if (new BigInteger(otherPeerCumulativeDifficulty)
								.compareTo(BlockchainProcessorImpl.this.blockchain.getLastBlock()
										.getCumulativeDifficulty()) <= 0) {
							continue;
						}
						Logger.logInfoMessage("Found a peer with better difficulty");
						this.downloadBlockchain(otherPeer, otherPeerCommonBlock, commonBlock.getHeight());
					}
					Logger.logDebugMessage("Got " + confirmations + " confirmations");

					if (BlockchainProcessorImpl.this.blockchain.getLastBlock().getId() != lastBlockId) {
						final long time = System.currentTimeMillis() - startTime;
						this.totalTime += time;
						final int numBlocks = BlockchainProcessorImpl.this.blockchain.getHeight()
								- commonBlock.getHeight();
						this.totalBlocks += numBlocks;
						Logger.logMessage("Downloaded " + numBlocks + " blocks in " + (time / 1000) + " s, "
								+ ((this.totalBlocks * 1000) / this.totalTime) + " per s, "
								+ ((this.totalTime * (BlockchainProcessorImpl.this.lastBlockchainFeederHeight
										- BlockchainProcessorImpl.this.blockchain.getHeight()))
										/ ((long) this.totalBlocks * 1000 * 60))
								+ " min left");
					} else {
						Logger.logDebugMessage("Did not accept peer's blocks, back to our own fork (my last id: " + BlockchainProcessorImpl.this.blockchain.getLastBlock().getId() + ", their last id: " + lastBlockId + ")");
					}
				} finally {
					BlockchainProcessorImpl.this.blockchain.updateUnlock();
				}

			} catch (final NxtException.StopException e) {
				Logger.logMessage("Blockchain download stopped: " + e.getMessage());
				throw new InterruptedException("Blockchain download stopped");
			} catch (final Exception e) {
				Logger.logMessage("Error in blockchain download thread", e);
			}
		}

		private List<Long> getBlockIdsAfterCommon(final Peer peer, final long startBlockId,
				final boolean countFromStart) {
			long matchId = startBlockId;
			final List<Long> blockList = new ArrayList<>(720);
			boolean matched = false;
			final int limit = countFromStart ? 720 : 1440;
			while (true) {
				final JSONObject request = new JSONObject();
				request.put("requestType", "getNextBlockIds");
				request.put("blockId", Long.toUnsignedString(matchId));
				request.put("limit", limit);
				final JSONObject response = peer.send(JSON.prepareRequest(request));
				if (response == null) {
					return Collections.emptyList();
				}
				final JSONArray nextBlockIds = (JSONArray) response.get("nextBlockIds");
				if ((nextBlockIds == null) || (nextBlockIds.size() == 0)) {
					break;
				}
				// prevent overloading with blockIds
				if (nextBlockIds.size() > limit) {
					Logger.logDebugMessage(
							"Obsolete or rogue peer " + peer.getHost() + " sends too many nextBlockIds, blacklisting");
					peer.blacklist("Too many nextBlockIds");
					return Collections.emptyList();
				}
				boolean matching = true;
				int count = 0;
				for (final Object nextBlockId : nextBlockIds) {
					final long blockId = Convert.parseUnsignedLong((String) nextBlockId);
					if (matching) {
						if (BlockDb.hasBlock(blockId)) {
							matchId = blockId;
							matched = true;
						} else {
							blockList.add(matchId);
							blockList.add(blockId);
							matching = false;
						}
					} else {
						blockList.add(blockId);
						if (blockList.size() >= 720) {
							break;
						}
					}
					if (countFromStart && (++count >= 720)) {
						break;
					}
				}
				if (!matching || countFromStart) {
					break;
				}
			}
			if (blockList.isEmpty() && matched) {
				blockList.add(matchId);
			}
			return blockList;
		}

		private long getCommonMilestoneBlockId(final Peer peer) {

			String lastMilestoneBlockId = null;

			while (true) {
				final JSONObject milestoneBlockIdsRequest = new JSONObject();
				milestoneBlockIdsRequest.put("requestType", "getMilestoneBlockIds");
				if (lastMilestoneBlockId == null) {
					milestoneBlockIdsRequest.put("lastBlockId",
							BlockchainProcessorImpl.this.blockchain.getLastBlock().getStringId());
				} else {
					milestoneBlockIdsRequest.put("lastMilestoneBlockId", lastMilestoneBlockId);
				}

				final JSONObject response = peer.send(JSON.prepareRequest(milestoneBlockIdsRequest));
				if (response == null) {
					return 0;
				}
				final JSONArray milestoneBlockIds = (JSONArray) response.get("milestoneBlockIds");
				if (milestoneBlockIds == null) {
					return 0;
				}
				if (milestoneBlockIds.isEmpty()) {
					return Genesis.GENESIS_BLOCK_ID;
				}
				// prevent overloading with blockIds
				if (milestoneBlockIds.size() > 20) {
					Logger.logDebugMessage("Obsolete or rogue peer " + peer.getHost()
							+ " sends too many milestoneBlockIds, blacklisting");
					peer.blacklist("Too many milestoneBlockIds");
					return 0;
				}
				if (Boolean.TRUE.equals(response.get("last"))) {
					this.peerHasMore = false;
				}
				for (final Object milestoneBlockId : milestoneBlockIds) {
					final long blockId = Convert.parseUnsignedLong((String) milestoneBlockId);
					if (BlockDb.hasBlock(blockId)) {
						if ((lastMilestoneBlockId == null) && (milestoneBlockIds.size() > 1)) {
							this.peerHasMore = false;
						}
						return blockId;
					}
					lastMilestoneBlockId = (String) milestoneBlockId;
				}
			}

		}

		private void processFork(final Peer peer, final List<BlockImpl> forkBlocks, final Block commonBlock) {

			final BigInteger curCumulativeDifficulty = BlockchainProcessorImpl.this.blockchain.getLastBlock()
					.getCumulativeDifficulty();

			final List<BlockImpl> myPoppedOffBlocks = BlockchainProcessorImpl.this.popOffTo(commonBlock);

			int pushedForkBlocks = 0;
			if (BlockchainProcessorImpl.this.blockchain.getLastBlock().getId() == commonBlock.getId()) {
				for (final BlockImpl block : forkBlocks) {
					if (BlockchainProcessorImpl.this.blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
						try {
							BlockchainProcessorImpl.this.pushBlock(block);
							pushedForkBlocks += 1;
						} catch (final BlockNotAcceptedException e) {
							//e.printStackTrace();
							Logger.logDebugMessage("Will blacklist peer " + peer.getAnnouncedAddress()
									+ " soon, block was not accepted!");
							peer.blacklist(e);
							break;
						}
					}
				}
			}
			Logger.logDebugMessage("Pushed forked blocks size = " + pushedForkBlocks);

			if ((pushedForkBlocks > 0) && (BlockchainProcessorImpl.this.blockchain.getLastBlock()
					.getCumulativeDifficulty().compareTo(curCumulativeDifficulty) < 0)) {
				Logger.logDebugMessage("Pop off caused by peer " + peer.getHost() + ", blacklisting");
				peer.blacklist("Pop off");
				final List<BlockImpl> peerPoppedOffBlocks = BlockchainProcessorImpl.this.popOffTo(commonBlock);
				pushedForkBlocks = 0;
				for (final BlockImpl block : peerPoppedOffBlocks) {
					TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
				}
			}

			if (pushedForkBlocks == 0) {
				Logger.logDebugMessage("Didn't accept any blocks, pushing back my previous blocks");
				for (int i = myPoppedOffBlocks.size() - 1; i >= 0; i--) {
					final BlockImpl block = myPoppedOffBlocks.remove(i);
					try {
						BlockchainProcessorImpl.this.pushBlock(block);
					} catch (final BlockNotAcceptedException e) {
						Logger.logErrorMessage(
								"Popped off block no longer acceptable: " + block.getJSONObject().toJSONString(), e);
						break;
					}
				}
			} else {
				Logger.logDebugMessage("Switched to peer's fork");
				for (final BlockImpl block : myPoppedOffBlocks) {
					TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
				}
			}

		}

		@Override
		public void run() {
			try {
				//
				// Download blocks until we are up-to-date
				//
				while (true) {
					if (!BlockchainProcessorImpl.this.getMoreBlocks) {
						return;
					}
					final int chainHeight = BlockchainProcessorImpl.this.blockchain.getHeight();
					this.downloadPeer();
					//System.out.println("Check if still downloading: myheight = " + BlockchainProcessorImpl.this.blockchain.getHeight() + " their height = " + chainHeight);
					if (BlockchainProcessorImpl.this.blockchain.getHeight() == chainHeight) {
						//System.out.println(" -> seems finished: isDownloading = " + BlockchainProcessorImpl.this.isDownloading + ", endlessDownload = " + BlockchainProcessorImpl.this.simulateEndlessDownload);
						if (BlockchainProcessorImpl.this.isDownloading
								&& !BlockchainProcessorImpl.this.simulateEndlessDownload) {
							Logger.logMessage("Finished blockchain download");
							BlockchainProcessorImpl.this.isDownloading = false;
						}
						break;
					}
				}
				//
				// Restore prunable data
				//
				final int now = Nxt.getEpochTime();
				if (!BlockchainProcessorImpl.this.isRestoring
						&& !BlockchainProcessorImpl.this.prunableTransactions.isEmpty()
						&& ((now - BlockchainProcessorImpl.this.lastRestoreTime) > (60 * 60))) {
					BlockchainProcessorImpl.this.isRestoring = true;
					BlockchainProcessorImpl.this.lastRestoreTime = now;
					BlockchainProcessorImpl.this.networkService.submit(new RestorePrunableDataTask());
				}
			} catch (final InterruptedException e) {
				Logger.logDebugMessage("Blockchain download thread interrupted");
			} catch (final Throwable t) {
				Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString(), t);
				System.exit(1);
			}
		}

	};

	private final Listener<Block> checksumListener = block -> {
		/* no checksums here yet */
	};

	private BlockchainProcessorImpl() {
		final int trimFrequency = Nxt.getIntProperty("nxt.trimFrequency");
		this.blockListeners.addListener(block -> {
			if ((block.getHeight() % 5000) == 0) {
				Logger.logMessage("processed block " + block.getHeight());
			}
			if (this.trimDerivedTables && ((block.getHeight() % trimFrequency) == 0)) {
				this.doTrimDerivedTables();
			}
		}, Event.BLOCK_SCANNED);

		this.blockListeners.addListener(block -> {
			if (this.trimDerivedTables && ((block.getHeight() % trimFrequency) == 0) && !this.isTrimming) {
				this.isTrimming = true;
				this.networkService.submit(() -> {
					this.trimDerivedTables();
					this.isTrimming = false;
				});
			}
			if ((block.getHeight() % 5000) == 0) {
				Logger.logMessage("received block " + block.getHeight());
				if (!this.isDownloading || ((block.getHeight() % 50000) == 0)) {
					this.networkService.submit(Db.db::analyzeTables);
				}
			}
		}, Event.BLOCK_PUSHED);

		this.blockListeners.addListener(this.checksumListener, Event.BLOCK_PUSHED);

		this.blockListeners.addListener(block -> Db.db.analyzeTables(), Event.RESCAN_END);

		ThreadPool.runBeforeStart(() -> {



			this.alreadyInitialized = true;
			if (this.addGenesisBlock()) {
				this.scan(0, false);
			} else if (Nxt.getBooleanProperty("nxt.forceScan")) {
				this.scan(0, Nxt.getBooleanProperty("nxt.forceValidate"));
			} else {
				boolean rescan;
				boolean validate;
				int height;
				try (Connection con = Db.db.getConnection();
						Statement stmt = con.createStatement();
						ResultSet rs = stmt.executeQuery("SELECT * FROM scan")) {
					rs.next();
					rescan = rs.getBoolean("rescan");
					validate = rs.getBoolean("validate");
					height = rs.getInt("height");
				} catch (final SQLException e) {
					throw new RuntimeException(e.toString(), e);
				}
				if (rescan) {
					this.scan(height, validate);
				}
			}

			// Also here, ensure important accounts are there
			try {
				Db.db.beginTransaction();
				// Create accounts for guard and deposit nodes
				for (long h : Constants.GUARD_NODES)
					Account.addOrGetAccount(h);
				Account.addOrGetAccount(Constants.FORFEITED_DEPOSITS_ACCOUNT);
				Account.addOrGetAccount(Constants.DEPOSITS_ACCOUNT);
			}catch(Exception e){
				// For consensus reasons, this must work!!!
				Db.db.endTransaction();
				e.printStackTrace();
				System.exit(1);
			} finally {
				Db.db.commitTransaction();
				Db.db.endTransaction();
			}

		}, false);

		if (!Constants.isLightClient && !Constants.isOffline) {
			ThreadPool.scheduleThread("GetMoreBlocks", this.getMoreBlocksThread, 1);
		}

	}

	private void accept(final BlockImpl block, final List<TransactionImpl> validPhasedTransactions,
			final List<TransactionImpl> invalidPhasedTransactions,
			final Map<TransactionType, Map<String, Integer>> duplicates)
			throws TransactionNotAcceptedException, NotValidException {
		try {
			this.isProcessingBlock = true;
			for (final TransactionImpl transaction : block.getTransactions()) {
				if (!transaction.applyUnconfirmed()) {
					throw new TransactionNotAcceptedException("Double spending", transaction);
				}
			}
			this.blockListeners.notify(block, Event.BEFORE_BLOCK_APPLY);
			block.apply();

			final int fromTimestamp = Nxt.getEpochTime() - Constants.MAX_PRUNABLE_LIFETIME;
			for (final TransactionImpl transaction : block.getTransactions()) {
				try {
					transaction.apply();
					if (transaction.getTimestamp() > fromTimestamp) {
						for (final Appendix.AbstractAppendix appendage : transaction.getAppendages(true)) {
							if ((appendage instanceof Appendix.Prunable)
									&& !((Appendix.Prunable) appendage).hasPrunableData()) {
								synchronized (this.prunableTransactions) {
									this.prunableTransactions.add(transaction.getId());
								}
								this.lastRestoreTime = 0;
								break;
							}
						}
					}
				} catch (final RuntimeException e) {
					Logger.logErrorMessage(e.toString(), e);
					throw new BlockchainProcessor.TransactionNotAcceptedException(e, transaction);
				}
			}

			this.blockListeners.notify(block, Event.AFTER_BLOCK_APPLY);
			if (block.getTransactions().size() > 0) {
				TransactionProcessorImpl.getInstance().notifyListeners(block.getTransactions(),
						TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);
			}
			AccountLedger.commitEntries();
		} finally {
			this.isProcessingBlock = false;
			AccountLedger.clearEntries();
		}
	}

	private void addBlock(final BlockImpl block) {
		try (Connection con = Db.db.getConnection()) {
			BlockDb.saveBlock(con, block);
			this.blockchain.setLastBlock(block);
			
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		
		// Get statistics
		final float scaledHitTIme = Redeem.getRedeemedPercentage();
		Logger.logDebugMessage("[!!] Redeem Statistics: " + String.valueOf(scaledHitTIme) + " % of all XEL have been redeemed.");

		// in any case, trigger the TX-processor cleanup routine. Some tx may
		// have become invalid!
		TransactionProcessorImpl.getInstance().clearUnconfirmedThatGotInvalidLately();
	}

	private boolean addGenesisBlock() {

		if (BlockDb.hasBlock(Genesis.GENESIS_BLOCK_ID, 0)) {
			Logger.logMessage("Genesis block already in database");
			final BlockImpl lastBlock = BlockDb.findLastBlock();
			this.blockchain.setLastBlock(lastBlock);
			this.popOffTo(lastBlock);
			Logger.logMessage("Last block height: " + lastBlock.getHeight());
			return false;
		}
		Logger.logMessage("Genesis block (" + Genesis.GENESIS_BLOCK_ID + ") not in database, starting from scratch");

/*
		try {
			Genesis.mineGenesis();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.exit(0);*/


		try {
			final List<TransactionImpl> transactions = new ArrayList<>();
			for (int i = 0; i < Genesis.GENESIS_RECIPIENTS.length; i++) {
				final TransactionImpl transaction = new TransactionImpl.BuilderImpl((byte) 0,
						Genesis.CREATOR_PUBLIC_KEY, Genesis.GENESIS_AMOUNTS[i] * Constants.ONE_NXT, 0, (short) 0,
						Attachment.ORDINARY_PAYMENT).timestamp(0).recipientId(Genesis.GENESIS_RECIPIENTS[i])
								.signature(Genesis.GENESIS_SIGNATURES[i]).height(0).ecBlockHeight(0).ecBlockId(0)
								.build();
				transactions.add(transaction);
			}
			Collections.sort(transactions, Comparator.comparingLong(Transaction::getId));
			final MessageDigest digest = Crypto.sha256();
			for (final TransactionImpl transaction : transactions) {
				digest.update(transaction.getBytes());
			}

			final BlockImpl genesisBlock = new BlockImpl(0, 0, 0, Constants.MAX_BALANCE_NQT, 0,
					transactions.size() * 128, digest.digest(), Genesis.CREATOR_PUBLIC_KEY,
					Genesis.GENESIS_GENERATION_SIGNATURE, Genesis.GENESIS_BLOCK_SIGNATURE, new byte[32], transactions,
					Constants.least_possible_target);
			genesisBlock.setPrevious(null);
			this.addBlock(genesisBlock);
			Logger.logMessage("Genesis block (" + genesisBlock.getId() + ") recreated and added to our fresh blockchain");
			return true;
		} catch (final NxtException.ValidationException e) {
			Logger.logMessage(e.getMessage());
			throw new RuntimeException(e.toString(), e);
		}


	}

	@Override
	public boolean addListener(final Listener<Block> listener, final BlockchainProcessor.Event eventType) {
		return this.blockListeners.addListener(listener, eventType);
	}

	private void doTrimDerivedTables() {
		this.lastTrimHeight = Math.max(this.blockchain.getHeight() - Constants.MAX_ROLLBACK, 0);
		if (this.lastTrimHeight > 0) {
			for (final DerivedDbTable table : this.derivedTables) {
				this.blockchain.readLock();
				try {
					table.trim(this.lastTrimHeight);
					Db.db.commitTransaction();
				} finally {
					this.blockchain.readUnlock();
				}
			}
		}
	}

	@Override
	public void fullReset() {
		this.blockchain.writeLock();
		try {
			try {
				this.setGetMoreBlocks(false);
				this.scheduleScan(0, false);
				// BlockDb.deleteBlock(Genesis.GENESIS_BLOCK_ID); // fails with
				// stack overflow in H2
				BlockDb.deleteAll();
				if (this.addGenesisBlock()) {
					this.scan(0, false);
				}
			} finally {
				this.setGetMoreBlocks(true);
			}
		} finally {
			this.blockchain.writeUnlock();
		}
	}

	@Override
	public void fullScanWithShutdown() {
		this.scan(0, true, true);
	}

	public void generateBlock(final byte[] publicKey, final int blockTimestamp, String secretPhrase) throws BlockNotAcceptedException {

		final Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();

		final BlockImpl previousBlock = this.blockchain.getLastBlock();
		TransactionProcessorImpl.getInstance().processWaitingTransactions();
		final SortedSet<UnconfirmedTransaction> sortedTransactions = this.selectUnconfirmedTransactions(duplicates,
				previousBlock, blockTimestamp);
		final List<TransactionImpl> blockTransactions = new ArrayList<>();
		final MessageDigest digest = Crypto.sha256();
		long totalAmountNQT = 0;
		long totalFeeNQT = 0;
		int payloadLength = 0;
		for (final UnconfirmedTransaction unconfirmedTransaction : sortedTransactions) {
			final TransactionImpl transaction = unconfirmedTransaction.getTransaction();
			blockTransactions.add(transaction);
			digest.update(transaction.getBytes());
			totalAmountNQT += transaction.getAmountNQT();
			totalFeeNQT += transaction.getFeeNQT();
			payloadLength += transaction.getFullSize();
		}
		final byte[] payloadHash = digest.digest();
		digest.update(previousBlock.getGenerationSignature());
		final byte[] generationSignature = digest.digest(publicKey);
		final byte[] previousBlockHash = Crypto.sha256().digest(previousBlock.bytes());

		final BlockImpl block = new BlockImpl(this.getBlockVersion(previousBlock.getHeight()), blockTimestamp,
				previousBlock.getId(), totalAmountNQT, totalFeeNQT, payloadLength, payloadHash, publicKey,
				generationSignature, previousBlockHash, blockTransactions, secretPhrase,
				BlockImpl.calculateNextMinPowTarget(previousBlock.getId()));

		try {
			this.pushBlock(block);
			this.blockListeners.notify(block, Event.BLOCK_GENERATED);
			Logger.logDebugMessage("Account " + Long.toUnsignedString(block.getGeneratorId()) + " generated block "
					+ block.getStringId() + " at height " + block.getHeight() + " timestamp " + block.getTimestamp()
					+ " fee " + (((float) block.getTotalFeeNQT()) / Constants.ONE_NXT));
		} catch (final TransactionNotAcceptedException e) {
			Logger.logDebugMessage("Generate block failed: " + e.getMessage());
			TransactionProcessorImpl.getInstance().processWaitingTransactions();
			final TransactionImpl transaction = e.getTransaction();
			Logger.logDebugMessage("Removing invalid transaction: " + transaction.getStringId());
			this.blockchain.writeLock();
			try {
				TransactionProcessorImpl.getInstance().removeUnconfirmedTransaction(transaction);
			} finally {
				this.blockchain.writeUnlock();
			}
			throw e;
		} catch (final BlockNotAcceptedException e) {
			Logger.logDebugMessage("Generate block failed: " + e.getMessage());
			throw e;
		}
	}

	void generateBlock(final String secretPhrase, int blockTimestamp) throws BlockNotAcceptedException {

		final Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();

		final BlockImpl previousBlock = this.blockchain.getLastBlock();
		TransactionProcessorImpl.getInstance().processWaitingTransactions();
		final SortedSet<UnconfirmedTransaction> sortedTransactions = this.selectUnconfirmedTransactions(duplicates,
				previousBlock, blockTimestamp);
		final List<TransactionImpl> blockTransactions = new ArrayList<>();
		final MessageDigest digest = Crypto.sha256();
		long totalAmountNQT = 0;
		long totalFeeNQT = 0;
		int payloadLength = 0;

		// Safeguard for timestamp crippling
		if(blockTimestamp <= previousBlock.getTimestamp()){
			blockTimestamp = previousBlock.getTimestamp() + 1; // This is useful for fake forging
		}

		for (final UnconfirmedTransaction unconfirmedTransaction : sortedTransactions) {
			final TransactionImpl transaction = unconfirmedTransaction.getTransaction();
			blockTransactions.add(transaction);
			digest.update(transaction.getBytes());
			totalAmountNQT += transaction.getAmountNQT();
			totalFeeNQT += transaction.getFeeNQT();
			payloadLength += transaction.getFullSize();
		}
		final byte[] payloadHash = digest.digest();
		digest.update(previousBlock.getGenerationSignature());
		final byte[] publicKey = Crypto.getPublicKey(secretPhrase);
		final byte[] generationSignature = digest.digest(publicKey);
		final byte[] previousBlockHash = Crypto.sha256().digest(previousBlock.bytes());

		final BlockImpl block = new BlockImpl(this.getBlockVersion(previousBlock.getHeight()), blockTimestamp,
				previousBlock.getId(), totalAmountNQT, totalFeeNQT, payloadLength, payloadHash, publicKey,
				generationSignature, previousBlockHash, blockTransactions, secretPhrase,
				BlockImpl.calculateNextMinPowTarget(previousBlock.getId()));

		try {
			this.pushBlock(block);
			this.blockListeners.notify(block, Event.BLOCK_GENERATED);
			Logger.logDebugMessage("Account " + Long.toUnsignedString(block.getGeneratorId()) + " generated block "
					+ block.getStringId() + " at height " + block.getHeight() + " timestamp " + block.getTimestamp()
					+ " fee " + (((float) block.getTotalFeeNQT()) / Constants.ONE_NXT));
		} catch (final TransactionNotAcceptedException e) {
			Logger.logDebugMessage("Generate block failed: " + e.getMessage());
			TransactionProcessorImpl.getInstance().processWaitingTransactions();
			final TransactionImpl transaction = e.getTransaction();
			Logger.logDebugMessage("Removing invalid transaction: " + transaction.getStringId());
			this.blockchain.writeLock();
			try {
				TransactionProcessorImpl.getInstance().removeUnconfirmedTransaction(transaction);
			} finally {
				this.blockchain.writeUnlock();
			}
			throw e;
		} catch (final BlockNotAcceptedException e) {
			Logger.logDebugMessage("Generate block failed: " + e.getMessage());
			throw e;
		}
	}

	private int getBlockVersion(final int previousBlockHeight) {
		return 1;
	}

	List<DerivedDbTable> getDerivedTables() {
		return this.derivedTables;
	}

	@Override
	public int getInitialScanHeight() {
		return this.initialScanHeight;
	}

	@Override
	public Peer getLastBlockchainFeeder() {
		return this.lastBlockchainFeeder;
	}

	@Override
	public int getLastBlockchainFeederHeight() {
		return this.lastBlockchainFeederHeight;
	}

	@Override
	public int getMinRollbackHeight() {
		return this.trimDerivedTables ? (this.lastTrimHeight > 0 ? this.lastTrimHeight
				: Math.max(this.blockchain.getHeight() - Constants.MAX_ROLLBACK, 0)) : 0;
	}

	private int getTransactionVersion(final int previousBlockHeight) {
		return 1;
	}

	boolean hasAllReferencedTransactions(final TransactionImpl transaction, final int timestamp, final int count) {
		if (transaction.referencedTransactionFullHash() == null) {
			return ((timestamp - transaction.getTimestamp()) < Constants.MAX_REFERENCED_TRANSACTION_TIMESPAN)
					&& (count < 10);
		}
		final TransactionImpl referencedTransaction = TransactionDb
				.findTransactionByFullHash(transaction.referencedTransactionFullHash());
		return (referencedTransaction != null) && (referencedTransaction.getHeight() < transaction.getHeight())
				&& this.hasAllReferencedTransactions(referencedTransaction, timestamp, count + 1);
	}

	@Override
	public boolean isDownloading() {
		return this.isDownloading;
	}

	@Override
	public boolean isProcessingBlock() {
		return this.isProcessingBlock;
	}

	@Override
	public boolean isScanning() {
		return this.isScanning;
	}

	private BlockImpl popLastBlock() {
		final BlockImpl block = this.blockchain.getLastBlock();
		if (block.getId() == Genesis.GENESIS_BLOCK_ID) {
			throw new RuntimeException("Cannot pop off genesis block");
		}
		final BlockImpl previousBlock = BlockDb.deleteBlocksFrom(block.getId());
		previousBlock.loadTransactions();
		this.blockchain.setLastBlock(previousBlock);
		this.blockListeners.notify(block, Event.BLOCK_POPPED);
		return previousBlock;
	}

	List<BlockImpl> popOffTo(final Block commonBlock) {
		this.blockchain.writeLock();
		try {
			if (!Db.db.isInTransaction()) {
				try {
					Db.db.beginTransaction();
					return this.popOffTo(commonBlock);
				} finally {
					Db.db.endTransaction();
				}
			}
			if (commonBlock.getHeight() < this.getMinRollbackHeight()) {
				Logger.logMessage(
						"Rollback to height " + commonBlock.getHeight() + " not supported, will do a full rescan");
				this.popOffWithRescan(commonBlock.getHeight() + 1);
				return Collections.emptyList();
			}
			if (!this.blockchain.hasBlock(commonBlock.getId())) {
				Logger.logDebugMessage(
						"Block " + commonBlock.getStringId() + " not found in blockchain, nothing to pop off");
				return Collections.emptyList();
			}
			final List<BlockImpl> poppedOffBlocks = new ArrayList<>();
			try {
				BlockImpl block = this.blockchain.getLastBlock();
				block.loadTransactions();
				Logger.logDebugMessage("Rollback from block " + block.getStringId() + " at height " + block.getHeight()
						+ " to " + commonBlock.getStringId() + " at " + commonBlock.getHeight());
				while ((block.getId() != commonBlock.getId()) && (block.getId() != Genesis.GENESIS_BLOCK_ID)) {
					poppedOffBlocks.add(block);
					block = this.popLastBlock();
				}
				for (final DerivedDbTable table : this.derivedTables) {
					table.rollback(commonBlock.getHeight());
				}
				Db.db.clearCache();
				Db.db.commitTransaction();
			} catch (final RuntimeException e) {
				Logger.logErrorMessage("Error popping off to " + commonBlock.getHeight() + ", " + e.toString());
				Db.db.rollbackTransaction();
				final BlockImpl lastBlock = BlockDb.findLastBlock();
				this.blockchain.setLastBlock(lastBlock);
				this.popOffTo(lastBlock);
				throw e;
			}
			return poppedOffBlocks;
		} finally {
			this.blockchain.writeUnlock();
		}
	}

	@Override
	public List<BlockImpl> popOffTo(final int height) {
		if (height <= 0) {
			this.fullReset();
		} else if (height < this.blockchain.getHeight()) {
			return this.popOffTo(this.blockchain.getBlockAtHeight(height));
		}
		return Collections.emptyList();
	}

	private void popOffWithRescan(final int height) {
		this.blockchain.writeLock();
		try {
			try {
				this.scheduleScan(0, false);
				final BlockImpl lastBLock = BlockDb.deleteBlocksFrom(BlockDb.findBlockIdAtHeight(height));
				this.blockchain.setLastBlock(lastBLock);
				Logger.logDebugMessage("Deleted blocks starting from height %s", height);
			} finally {
				this.scan(0, false);
			}
		} finally {
			this.blockchain.writeUnlock();
		}
	}

	@Override
	public void processPeerBlock(final JSONObject request) throws NxtException {
		final BlockImpl block = BlockImpl.parseBlock(request);
		BlockImpl lastBlock = this.blockchain.getLastBlock();
		if (block.getPreviousBlockId() == lastBlock.getId()) {
			this.pushBlock(block);
		} else if ((block.getPreviousBlockId() == lastBlock.getPreviousBlockId())
				&& (block.getTimestamp() < lastBlock.getTimestamp())) {
			this.blockchain.writeLock();
			try {
				if (lastBlock.getId() != this.blockchain.getLastBlock().getId()) {
					return; // blockchain changed, ignore the block
				}
				final BlockImpl previousBlock = this.blockchain.getBlock(lastBlock.getPreviousBlockId());
				lastBlock = this.popOffTo(previousBlock).get(0);
				try {
					this.pushBlock(block);
					TransactionProcessorImpl.getInstance().processLater(lastBlock.getTransactions());
					Logger.logDebugMessage(
							"Last block " + lastBlock.getStringId() + " was replaced by " + block.getStringId());
				} catch (final BlockNotAcceptedException e) {
					Logger.logDebugMessage("Replacement block failed to be accepted, pushing back our last block");
					this.pushBlock(lastBlock);
					TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
				}
			} finally {
				this.blockchain.writeUnlock();
			}
		} // else ignore the block
	}

	private void pushBlock(final BlockImpl block) throws BlockNotAcceptedException {

		final int curTime = Nxt.getEpochTime();

		this.blockchain.writeLock();
		try {
			BlockImpl previousLastBlock = null;
			try {
				Db.db.beginTransaction();
				previousLastBlock = this.blockchain.getLastBlock();

				this.validate(block, previousLastBlock, curTime);

				final long nextHitTime = Generator.getNextHitTime(previousLastBlock.getId(), curTime);
				if ((nextHitTime > 0) && (block.getTimestamp() > (nextHitTime + 1))) {
					final String msg = "Rejecting block " + block.getStringId() + " at height "
							+ previousLastBlock.getHeight() + " block timestamp " + block.getTimestamp()
							+ " next hit time " + nextHitTime + " current time " + curTime;
					Logger.logDebugMessage(msg);
					Generator.setDelay(-Constants.FORGING_SPEEDUP);
					throw new BlockOutOfOrderException(msg, block);
				}

				final Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
				final List<TransactionImpl> validPhasedTransactions = new ArrayList<>();
				final List<TransactionImpl> invalidPhasedTransactions = new ArrayList<>();
				this.validateTransactions(block, previousLastBlock, curTime, duplicates,
						previousLastBlock.getHeight() >= Constants.LAST_CHECKSUM_BLOCK);

				block.setPrevious(previousLastBlock);
				this.blockListeners.notify(block, Event.BEFORE_BLOCK_ACCEPT);
				TransactionProcessorImpl.getInstance().requeueAllUnconfirmedTransactions();
				this.addBlock(block);
				this.accept(block, validPhasedTransactions, invalidPhasedTransactions, duplicates);

				Db.db.commitTransaction();
			} catch (final Exception e) {
				Db.db.rollbackTransaction();
				this.blockchain.setLastBlock(previousLastBlock);
				e.printStackTrace();
				throw new BlockNotAcceptedException(e.getMessage(), block);
			} finally {
				Db.db.endTransaction();
			}
			this.blockListeners.notify(block, Event.AFTER_BLOCK_ACCEPT);
		} finally {
			this.blockchain.writeUnlock();
		}

		if (block.getTimestamp() >= (curTime - 600)) {
			Peers.sendToSomePeers(block);
		}

		this.blockListeners.notify(block, Event.BLOCK_PUSHED);

	}

	@Override
	public void registerDerivedTable(final DerivedDbTable table) {
		if (this.alreadyInitialized) {
			throw new IllegalStateException("Too late to register table " + table + ", must have done it in Nxt.Init");
		}
		this.derivedTables.add(table);
	}

	@Override
	public boolean removeListener(final Listener<Block> listener, final Event eventType) {
		return this.blockListeners.removeListener(listener, eventType);
	}

	@Override
	public int restorePrunedData() {
		Db.db.beginTransaction();
		try (Connection con = Db.db.getConnection()) {
			final int now = Nxt.getEpochTime();
			final int minTimestamp = Math.max(1, now - Constants.MAX_PRUNABLE_LIFETIME);
			final int maxTimestamp = Math.max(minTimestamp, now - Constants.MIN_PRUNABLE_LIFETIME) - 1;
			final List<TransactionDb.PrunableTransaction> transactionList = TransactionDb.findPrunableTransactions(con,
					minTimestamp, maxTimestamp);
			transactionList.forEach(prunableTransaction -> {
				final long id = prunableTransaction.getId();
				if ((prunableTransaction.hasPrunableSourceCode()
						&& prunableTransaction.getTransactionType().isPruned(id))
						|| PrunableSourceCode.isPruned(id, prunableTransaction.hasPrunableSourceCode())) {
					synchronized (this.prunableTransactions) {
						this.prunableTransactions.add(id);
					}
				}
			});
			if (!this.prunableTransactions.isEmpty()) {
				this.lastRestoreTime = 0;
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		} finally {
			Db.db.endTransaction();
		}
		synchronized (this.prunableTransactions) {
			return this.prunableTransactions.size();
		}
	}

	@Override
	public Transaction restorePrunedTransaction(final long transactionId) {
		final TransactionImpl transaction = TransactionDb.findTransaction(transactionId);
		if (transaction == null) {
			throw new IllegalArgumentException("Transaction not found");
		}
		boolean isPruned = false;
		for (final Appendix.AbstractAppendix appendage : transaction.getAppendages(true)) {
			if ((appendage instanceof Appendix.Prunable) && !((Appendix.Prunable) appendage).hasPrunableData()) {
				isPruned = true;
				break;
			}
		}
		if (!isPruned) {
			return transaction;
		}
		final List<Peer> peers = Peers.getPeers(chkPeer -> chkPeer.providesService(Peer.Service.PRUNABLE)
				&& !chkPeer.isBlacklisted() && (chkPeer.getAnnouncedAddress() != null));
		if (peers.isEmpty()) {
			Logger.logDebugMessage("Cannot find any archive peers");
			return null;
		}
		final JSONObject json = new JSONObject();
		final JSONArray requestList = new JSONArray();
		requestList.add(Long.toUnsignedString(transactionId));
		json.put("requestType", "getTransactions");
		json.put("transactionIds", requestList);
		final JSONStreamAware request = JSON.prepareRequest(json);
		for (final Peer peer : peers) {
			if (peer.getState() != Peer.State.CONNECTED) {
				Peers.connectPeer(peer);
			}
			if (peer.getState() != Peer.State.CONNECTED) {
				continue;
			}
			Logger.logDebugMessage("Connected to archive peer " + peer.getHost());
			final JSONObject response = peer.send(request);
			if (response == null) {
				continue;
			}
			final JSONArray transactions = (JSONArray) response.get("transactions");
			if ((transactions == null) || transactions.isEmpty()) {
				continue;
			}
			try {
				final List<Transaction> processed = Nxt.getTransactionProcessor().restorePrunableData(transactions);
				if (processed.isEmpty()) {
					continue;
				}
				synchronized (this.prunableTransactions) {
					this.prunableTransactions.remove(transactionId);
				}
				return processed.get(0);
			} catch (final NxtException.NotValidException e) {
				Logger.logErrorMessage("Peer " + peer.getHost() + " returned invalid prunable transaction", e);
				peer.blacklist(e);
			}
		}
		return null;
	}

	@Override
	public void scan(final int height, final boolean validate) {
		this.scan(height, validate, false);
	}

	private void scan(int height, final boolean validate, final boolean shutdown) {
		this.blockchain.writeLock();
		try {
			if (!Db.db.isInTransaction()) {
				try {
					Db.db.beginTransaction();
					if (validate) {
						this.blockListeners.addListener(this.checksumListener, Event.BLOCK_SCANNED);
					}
					this.scan(height, validate, shutdown);
					Db.db.commitTransaction();
				} catch (final Exception e) {
					Db.db.rollbackTransaction();
					throw e;
				} finally {
					Db.db.endTransaction();
					this.blockListeners.removeListener(this.checksumListener, Event.BLOCK_SCANNED);
				}
				return;
			}
			this.scheduleScan(height, validate);
			if ((height > 0) && (height < this.getMinRollbackHeight())) {
				Logger.logMessage("Rollback to height less than " + this.getMinRollbackHeight()
						+ " not supported, will do a full scan");
				height = 0;
			}
			if (height < 0) {
				height = 0;
			}
			Logger.logMessage("Scanning blockchain starting from height " + height + "...");
			if (validate) {
				Logger.logDebugMessage("Also verifying signatures and validating transactions...");
			}
			try (Connection con = Db.db.getConnection();
					PreparedStatement pstmtSelect = con.prepareStatement("SELECT * FROM block WHERE "
							+ (height > 0 ? "height >= ? AND " : "") + " db_id >= ? ORDER BY db_id ASC LIMIT 50000");
					PreparedStatement pstmtDone = con
							.prepareStatement("UPDATE scan SET rescan = FALSE, height = 0, validate = FALSE")) {
				this.isScanning = true;
				this.initialScanHeight = this.blockchain.getHeight();
				if (height > (this.blockchain.getHeight() + 1)) {
					Logger.logMessage("Rollback height " + (height - 1) + " exceeds current blockchain height of "
							+ this.blockchain.getHeight() + ", no scan needed");
					pstmtDone.executeUpdate();
					Db.db.commitTransaction();
					return;
				}

				for (final DerivedDbTable table : this.derivedTables) {
					if (height == 0) {
						table.truncate();
					} else {
						table.rollback(height - 1);
					}
				}
				Db.db.clearCache();
				Db.db.commitTransaction();
				Logger.logDebugMessage("Rolled back derived tables");
				BlockImpl currentBlock = BlockDb.findBlockAtHeight(height);
				this.blockListeners.notify(currentBlock, Event.RESCAN_BEGIN);
				long currentBlockId = currentBlock.getId();
				if (height == 0) {
					this.blockchain.setLastBlock(currentBlock); // special case
																// to avoid no
																// last block
					Account.addOrGetAccount(Genesis.CREATOR_ID).apply(Genesis.CREATOR_PUBLIC_KEY);
				} else {
					this.blockchain.setLastBlock(BlockDb.findBlockAtHeight(height - 1));
				}
				if (shutdown) {
					Logger.logMessage("Scan will be performed at next start");
					new Thread(() -> {
						System.exit(0);
					}).start();
					return;
				}
				int pstmtSelectIndex = 1;
				if (height > 0) {
					pstmtSelect.setInt(pstmtSelectIndex++, height);
				}
				long dbId = Long.MIN_VALUE;
				boolean hasMore = true;
				outer: while (hasMore) {
					hasMore = false;
					pstmtSelect.setLong(pstmtSelectIndex, dbId);
					try (ResultSet rs = pstmtSelect.executeQuery()) {
						while (rs.next()) {
							try {
								dbId = rs.getLong("db_id");
								currentBlock = BlockDb.loadBlock(con, rs, true);
								currentBlock.loadTransactions();
								if ((currentBlock.getId() != currentBlockId)
										|| (currentBlock.getHeight() > (this.blockchain.getHeight() + 1))) {
									throw new NxtException.NotValidException("Database blocks in the wrong order!");
								}
								final Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
								final List<TransactionImpl> validPhasedTransactions = new ArrayList<>();
								final List<TransactionImpl> invalidPhasedTransactions = new ArrayList<>();
								if (validate && (currentBlockId != Genesis.GENESIS_BLOCK_ID)) {
									final int curTime = Nxt.getEpochTime();
									this.validate(currentBlock, this.blockchain.getLastBlock(), curTime);
									final byte[] blockBytes = currentBlock.bytes();
									final JSONObject blockJSON = (JSONObject) JSONValue
											.parse(currentBlock.getJSONObject().toJSONString());
									if (!Arrays.equals(blockBytes, BlockImpl.parseBlock(blockJSON).bytes())) {
										throw new NxtException.NotValidException(
												"Block JSON cannot be parsed back to the same block");
									}
									this.validateTransactions(currentBlock, this.blockchain.getLastBlock(), curTime,
											duplicates, true);
									for (final TransactionImpl transaction : currentBlock.getTransactions()) {
										final byte[] transactionBytes = transaction.getBytes();
										if (!Arrays.equals(transactionBytes, TransactionImpl
												.newTransactionBuilder(transactionBytes).build().getBytes())) {
											throw new NxtException.NotValidException(
													"Transaction bytes cannot be parsed back to the same transaction: "
															+ transaction.getJSONObject().toJSONString());
										}
										final JSONObject transactionJSON = (JSONObject) JSONValue
												.parse(transaction.getJSONObject().toJSONString());
										if (!Arrays.equals(transactionBytes, TransactionImpl
												.newTransactionBuilder(transactionJSON).build().getBytes())) {
											throw new NxtException.NotValidException(
													"Transaction JSON cannot be parsed back to the same transaction: "
															+ transaction.getJSONObject().toJSONString());
										}
									}
								}
								this.blockListeners.notify(currentBlock, Event.BEFORE_BLOCK_ACCEPT);
								this.blockchain.setLastBlock(currentBlock);
								this.accept(currentBlock, validPhasedTransactions, invalidPhasedTransactions,
										duplicates);
								currentBlockId = currentBlock.getNextBlockId();
								Db.db.clearCache();
								Db.db.commitTransaction();
								this.blockListeners.notify(currentBlock, Event.AFTER_BLOCK_ACCEPT);
							} catch (NxtException | RuntimeException e) {
								Db.db.rollbackTransaction();
								Logger.logDebugMessage(e.toString(), e);
								Logger.logDebugMessage("Applying block " + Long.toUnsignedString(currentBlockId)
										+ " at height " + (currentBlock == null ? 0 : currentBlock.getHeight())
										+ " failed, deleting from database");
								if (currentBlock != null) {
									currentBlock.loadTransactions();
									TransactionProcessorImpl.getInstance().processLater(currentBlock.getTransactions());
								}
								while (rs.next()) {
									try {
										currentBlock = BlockDb.loadBlock(con, rs, true);
										currentBlock.loadTransactions();
										TransactionProcessorImpl.getInstance()
												.processLater(currentBlock.getTransactions());
									} catch (final RuntimeException e2) {
										Logger.logErrorMessage(e2.toString(), e);
										break;
									}
								}
								final BlockImpl lastBlock = BlockDb.deleteBlocksFrom(currentBlockId);
								this.blockchain.setLastBlock(lastBlock);
								this.popOffTo(lastBlock);
								break outer;
							}
							this.blockListeners.notify(currentBlock, Event.BLOCK_SCANNED);
							hasMore = true;
						}
						dbId = dbId + 1;
					}
				}
				if (height == 0) {
					for (final DerivedDbTable table : this.derivedTables) {
						table.createSearchIndex(con);
					}
				}
				pstmtDone.executeUpdate();
				Db.db.commitTransaction();
				this.blockListeners.notify(currentBlock, Event.RESCAN_END);
				Logger.logMessage("...done at height " + this.blockchain.getHeight());
				if ((height == 0) && validate) {
					Logger.logMessage("SUCCESSFULLY PERFORMED FULL RESCAN WITH VALIDATION");
				}
				this.lastRestoreTime = 0;
			} catch (final SQLException e) {
				throw new RuntimeException(e.toString(), e);
			} finally {
				this.isScanning = false;
			}
		} finally {
			this.blockchain.writeUnlock();
		}
	}

	void scheduleScan(final int height, final boolean validate) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("UPDATE scan SET rescan = TRUE, height = ?, validate = ?")) {
			pstmt.setInt(1, height);
			pstmt.setBoolean(2, validate);
			pstmt.executeUpdate();
			Logger.logDebugMessage(
					"Scheduled scan starting from height " + height + (validate ? ", with validation" : ""));
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	SortedSet<UnconfirmedTransaction> selectUnconfirmedTransactions(
			final Map<TransactionType, Map<String, Integer>> duplicates, final Block previousBlock,
			final int blockTimestamp) {
		final List<UnconfirmedTransaction> orderedUnconfirmedTransactions = new ArrayList<>();
		try (FilteringIterator<UnconfirmedTransaction> unconfirmedTransactions = new FilteringIterator<>(
				TransactionProcessorImpl.getInstance().getAllUnconfirmedTransactions(), transaction -> this
						.hasAllReferencedTransactions(transaction.getTransaction(), transaction.getTimestamp(), 0))) {
			for (final UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
				orderedUnconfirmedTransactions.add(unconfirmedTransaction);
			}
		}
		final SortedSet<UnconfirmedTransaction> sortedTransactions = new TreeSet<>(
				BlockchainProcessorImpl.transactionArrivalComparator);
		int payloadLength = 0;
		while ((payloadLength <= Constants.MAX_PAYLOAD_LENGTH)
				&& (sortedTransactions.size() <= Constants.MAX_NUMBER_OF_TRANSACTIONS)) {
			final int prevNumberOfNewTransactions = sortedTransactions.size();
			for (final UnconfirmedTransaction unconfirmedTransaction : orderedUnconfirmedTransactions) {
				final int transactionLength = unconfirmedTransaction.getTransaction().getFullSize();
				if (sortedTransactions.contains(unconfirmedTransaction)
						|| ((payloadLength + transactionLength) > Constants.MAX_PAYLOAD_LENGTH)) {
					continue;
				}
				if (unconfirmedTransaction.getVersion() != this.getTransactionVersion(previousBlock.getHeight())) {
					continue;
				}
				if ((blockTimestamp > 0)
						&& ( (!Generator.allowsFakeForgingInPrincipal() && ((unconfirmedTransaction.getTimestamp() > (blockTimestamp + Constants.MAX_TIMEDRIFT)))||(!Objects.equals(unconfirmedTransaction.getAttachment().getTransactionType(), TransactionType.Payment.REDEEM) && (unconfirmedTransaction.getExpiration() < blockTimestamp))))) {
					continue;
				}
				try {
					unconfirmedTransaction.getTransaction().validate();
				} catch (final NxtException.ValidationException e) {
					continue;
				}
				if (unconfirmedTransaction.getTransaction().attachmentIsDuplicate(duplicates, true)) {
					continue;
				}
				sortedTransactions.add(unconfirmedTransaction);
				payloadLength += transactionLength;
			}
			if (sortedTransactions.size() == prevNumberOfNewTransactions) {
				break;
			}
		}
		return sortedTransactions;
	}

	@Override
	public void setGetMoreBlocks(final boolean getMoreBlocks) {
		this.getMoreBlocks = getMoreBlocks;
	}

	@Override
	public void trimDerivedTables() {
		try {
			Db.db.beginTransaction();
			this.doTrimDerivedTables();
			Db.db.commitTransaction();
		} catch (final Exception e) {
			Logger.logMessage(e.toString(), e);
			Db.db.rollbackTransaction();
			throw e;
		} finally {
			Db.db.endTransaction();
		}
	}

	private void validate(final BlockImpl block, final BlockImpl previousLastBlock, final int curTime)
			throws BlockNotAcceptedException {
		if (previousLastBlock.getId() != block.getPreviousBlockId()) {
			throw new BlockOutOfOrderException("Previous block id doesn't match", block);
		}
		if (block.getVersion() != this.getBlockVersion(previousLastBlock.getHeight())) {
			throw new BlockNotAcceptedException("Invalid version " + block.getVersion(), block);
		}
		if (block.getTimestamp() > (curTime + Constants.MAX_TIMEDRIFT)) {
			Logger.logWarningMessage("Received block " + block.getStringId() + " from the future, timestamp "
					+ block.getTimestamp() + " generator " + Long.toUnsignedString(block.getGeneratorId())
					+ " current time " + curTime + ", system clock may be off");
			throw new BlockOutOfOrderException(
					"Invalid timestamp: " + block.getTimestamp() + " current time is " + curTime, block);
		}
		if (block.getTimestamp() <= previousLastBlock.getTimestamp()) {
			throw new BlockNotAcceptedException("Block timestamp " + block.getTimestamp()
					+ " is before previous block timestamp " + previousLastBlock.getTimestamp(), block);
		}
		if (!Arrays.equals(Crypto.sha256().digest(previousLastBlock.bytes()), block.getPreviousBlockHash())) {
			throw new BlockNotAcceptedException("Previous block hash doesn't match", block);
		}
		if ((block.getId() == 0L) || BlockDb.hasBlock(block.getId(), previousLastBlock.getHeight())) {
			throw new BlockNotAcceptedException("Duplicate block or invalid id", block);
		}

		if (!block.ensureNoRealOrSNCleanDuplicates()) {
			throw new BlockNotAcceptedException("Block contains duplicate entries (includes SN Clean elements)", block);
		}

		if (!block.verifyGenerationSignature() && !Generator.allowsFakeForging(block.getGeneratorPublicKey())) {
			final Account generatorAccount = Account.getAccount(block.getGeneratorId());
			final long generatorBalance = generatorAccount == null ? 0 : generatorAccount.getEffectiveBalanceNXT();
			throw new BlockNotAcceptedException(
					"Generation signature verification failed, effective balance " + generatorBalance, block);
		}
		if (!block.verifyBlockSignature()) {
			throw new BlockNotAcceptedException("Block signature verification failed", block);
		}
		if (block.getTransactions().size() > Constants.MAX_NUMBER_OF_TRANSACTIONS) {
			throw new BlockNotAcceptedException("Invalid block transaction count " + block.getTransactions().size(),
					block);
		}
		if ((block.getPayloadLength() > Constants.MAX_PAYLOAD_LENGTH) || (block.getPayloadLength() < 0)) {
			throw new BlockNotAcceptedException("Invalid block payload length " + block.getPayloadLength(), block);
		}
	}

	private void validateTransactions(final BlockImpl block, final BlockImpl previousLastBlock, final int curTime,
			final Map<TransactionType, Map<String, Integer>> duplicates, final boolean fullValidation)
			throws BlockNotAcceptedException {
		long payloadLength = 0;
		long calculatedTotalAmount = 0;
		long calculatedTotalFee = 0;
		final MessageDigest digest = Crypto.sha256();
		boolean hasPrunedTransactions = false;

		// Here, make sure that not too many POW transactions end up in this block!
		// The unconf/conf duplicate barrier does not guarantee that.
		HashMap<Long, Integer> powCounterMap = new HashMap<>();

		for (final TransactionImpl transaction : block.getTransactions()) {

			if (transaction.getAttachment().getTransactionType() == TransactionType.WorkControl.PROOF_OF_WORK){
				try {
					Attachment.PiggybackedProofOfWork att = (Attachment.PiggybackedProofOfWork) transaction.getAttachment();
					long workId = att.getWorkId();
					if (powCounterMap.containsKey(workId) == false){
						powCounterMap.put(workId, 1);
					}
					else{
						Integer cnt = powCounterMap.get(workId);
						cnt ++;
						if(cnt > 20){
							throw new TransactionNotAcceptedException(
									"Invalid block, too many POW for work " + Convert.toUnsignedLong(workId),
									transaction);
						}else{
							powCounterMap.put(workId, cnt);
						}
					}
				}catch(Exception e){
					throw new TransactionNotAcceptedException(
							"Invalid attachment: " + e.getMessage(), transaction);
				}
			}

			if (transaction.getTimestamp() > (curTime + Constants.MAX_TIMEDRIFT)) {
				throw new BlockOutOfOrderException(
						"Invalid transaction timestamp: " + transaction.getTimestamp() + ", current time is " + curTime,
						block);
			}
			if (!transaction.verifySignature()) {
				throw new TransactionNotAcceptedException(
						"Transaction signature verification failed at height " + previousLastBlock.getHeight(),
						transaction);
			}
			if (fullValidation) {
				if (( !Generator.allowsFakeForgingInPrincipal() && (transaction.getTimestamp() > (block.getTimestamp() + Constants.MAX_TIMEDRIFT))
				) || (!Objects.equals(transaction.getAttachment().getTransactionType(), TransactionType.Payment.REDEEM) && (transaction.getExpiration() < block.getTimestamp()))) {
					throw new TransactionNotAcceptedException(
							"Invalid transaction timestamp " + transaction.getTimestamp() + ", current time is "
									+ curTime + ", block timestamp is " + block.getTimestamp(),
							transaction);
				}
				if (TransactionDb.hasTransaction(transaction.getId(), previousLastBlock.getHeight())) {
					throw new TransactionNotAcceptedException("Transaction is already in the blockchain", transaction);
				}
				if (transaction.referencedTransactionFullHash() != null) {
					if (!this.hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0)) {
						throw new TransactionNotAcceptedException("Missing or invalid referenced transaction "
								+ transaction.getReferencedTransactionFullHash(), transaction);
					}
				}
				if (transaction.getVersion() != this.getTransactionVersion(previousLastBlock.getHeight())) {
					throw new TransactionNotAcceptedException("Invalid transaction version " + transaction.getVersion()
							+ " at height " + previousLastBlock.getHeight(), transaction);
				}
				if (transaction.getId() == 0L) {
					throw new TransactionNotAcceptedException("Invalid transaction id 0", transaction);
				}
				try {
					transaction.validate();
				} catch (final NxtException.ValidationException e) {
					throw new TransactionNotAcceptedException(e.getMessage(), transaction);
				}
			}
			if (transaction.attachmentIsDuplicate(duplicates, true)) {
				throw new TransactionNotAcceptedException("Transaction is a duplicate", transaction);
			}
			if (!hasPrunedTransactions) {
				for (final Appendix.AbstractAppendix appendage : transaction.getAppendages()) {
					if ((appendage instanceof Appendix.Prunable)
							&& !((Appendix.Prunable) appendage).hasPrunableData()) {
						hasPrunedTransactions = true;
						break;
					}
				}
			}
			calculatedTotalAmount += transaction.getAmountNQT();
			calculatedTotalFee += transaction.getFeeNQT();
			payloadLength += transaction.getFullSize();
			digest.update(transaction.getBytes());
		}
		if ((calculatedTotalAmount != block.getTotalAmountNQT()) || (calculatedTotalFee != block.getTotalFeeNQT())) {
			throw new BlockNotAcceptedException("Total amount or fee don't match transaction totals", block);
		}
		if (!Arrays.equals(digest.digest(), block.getPayloadHash())) {
			throw new BlockNotAcceptedException("Payload hash doesn't match", block);
		}
		if (hasPrunedTransactions ? payloadLength > block.getPayloadLength()
				: payloadLength != block.getPayloadLength()) {
			throw new BlockNotAcceptedException("Transaction payload length " + payloadLength
					+ " does not match block payload length " + block.getPayloadLength(), block);
		}
	}
}

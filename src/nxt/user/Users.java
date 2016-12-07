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

package nxt.user;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Block;
import nxt.BlockchainProcessor;
import nxt.Constants;
import nxt.Generator;
import nxt.Nxt;
import nxt.Transaction;
import nxt.TransactionProcessor;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.util.ThreadPool;

public final class Users {

	private static final int TESTNET_UI_PORT = 2875;

	private static final ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
	private static final Collection<User> allUsers = Collections.unmodifiableCollection(Users.users.values());

	private static final AtomicInteger peerCounter = new AtomicInteger();
	private static final ConcurrentMap<String, Integer> peerIndexMap = new ConcurrentHashMap<>();
	private static final ConcurrentMap<Integer, String> peerAddressMap = new ConcurrentHashMap<>();

	private static final AtomicInteger blockCounter = new AtomicInteger();
	private static final ConcurrentMap<Long, Integer> blockIndexMap = new ConcurrentHashMap<>();

	private static final AtomicInteger transactionCounter = new AtomicInteger();
	private static final ConcurrentMap<Long, Integer> transactionIndexMap = new ConcurrentHashMap<>();

	static final Set<String> allowedUserHosts;

	private static final Server userServer;

	static {

		final List<String> allowedUserHostsList = Nxt.getStringListProperty("nxt.allowedUserHosts");
		if (!allowedUserHostsList.contains("*")) {
			allowedUserHosts = Collections.unmodifiableSet(new HashSet<>(allowedUserHostsList));
		} else {
			allowedUserHosts = null;
		}

		final boolean enableUIServer = Nxt.getBooleanProperty("nxt.enableUIServer");
		if (enableUIServer) {
			final int port = Constants.isTestnet ? Users.TESTNET_UI_PORT : Nxt.getIntProperty("nxt.uiServerPort");
			final String host = Nxt.getStringProperty("nxt.uiServerHost");
			userServer = new Server();
			ServerConnector connector;

			final boolean enableSSL = Nxt.getBooleanProperty("nxt.uiSSL");
			if (enableSSL) {
				Logger.logMessage("Using SSL (https) for the user interface server");
				final HttpConfiguration https_config = new HttpConfiguration();
				https_config.setSecureScheme("https");
				https_config.setSecurePort(port);
				https_config.addCustomizer(new SecureRequestCustomizer());
				final SslContextFactory sslContextFactory = new SslContextFactory();
				sslContextFactory.setKeyStorePath(Nxt.getStringProperty("nxt.keyStorePath"));
				sslContextFactory.setKeyStorePassword(Nxt.getStringProperty("nxt.keyStorePassword", null, true));
				sslContextFactory.addExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA",
						"SSL_DHE_DSS_WITH_DES_CBC_SHA", "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
						"SSL_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
						"SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
				sslContextFactory.addExcludeProtocols("SSLv3");
				connector = new ServerConnector(Users.userServer,
						new SslConnectionFactory(sslContextFactory, "http/1.1"),
						new HttpConnectionFactory(https_config));
			} else {
				connector = new ServerConnector(Users.userServer);
			}

			connector.setPort(port);
			connector.setHost(host);
			connector.setIdleTimeout(Nxt.getIntProperty("nxt.uiServerIdleTimeout"));
			connector.setReuseAddress(true);
			Users.userServer.addConnector(connector);

			final HandlerList userHandlers = new HandlerList();

			final ResourceHandler userFileHandler = new ResourceHandler();
			userFileHandler.setDirectoriesListed(false);
			userFileHandler.setWelcomeFiles(new String[] { "index.html" });
			userFileHandler.setResourceBase(Nxt.getStringProperty("nxt.uiResourceBase"));

			userHandlers.addHandler(userFileHandler);

			final String javadocResourceBase = Nxt.getStringProperty("nxt.javadocResourceBase");
			if (javadocResourceBase != null) {
				final ContextHandler contextHandler = new ContextHandler("/doc");
				final ResourceHandler docFileHandler = new ResourceHandler();
				docFileHandler.setDirectoriesListed(false);
				docFileHandler.setWelcomeFiles(new String[] { "index.html" });
				docFileHandler.setResourceBase(javadocResourceBase);
				contextHandler.setHandler(docFileHandler);
				userHandlers.addHandler(contextHandler);
			}

			final ServletHandler userHandler = new ServletHandler();
			final ServletHolder userHolder = userHandler.addServletWithMapping(UserServlet.class, "/nxt");
			userHolder.setAsyncSupported(true);

			if (Nxt.getBooleanProperty("nxt.uiServerCORS")) {
				final FilterHolder filterHolder = userHandler.addFilterWithMapping(CrossOriginFilter.class, "/*",
						FilterMapping.DEFAULT);
				filterHolder.setInitParameter("allowedHeaders", "*");
				filterHolder.setAsyncSupported(true);
			}

			userHandlers.addHandler(userHandler);

			userHandlers.addHandler(new DefaultHandler());

			Users.userServer.setHandler(userHandlers);
			Users.userServer.setStopAtShutdown(true);

			ThreadPool.runBeforeStart(() -> {
				try {
					Users.userServer.start();
					Logger.logMessage("Started user interface server at " + host + ":" + port);
				} catch (final Exception e) {
					Logger.logErrorMessage("Failed to start user interface server", e);
					throw new RuntimeException(e.toString(), e);
				}
			}, true);

		} else {
			userServer = null;
			Logger.logMessage("User interface server not enabled");
		}

	}

	static {
		if (Users.userServer != null) {
			Account.addListener(account -> {
				final JSONObject response = new JSONObject();
				response.put("response", "setBalance");
				response.put("balanceNQT", account.getUnconfirmedBalanceNQT());
				final byte[] accountPublicKey = Account.getPublicKey(account.getId());
				Users.users.values().forEach(user -> {
					if ((user.getSecretPhrase() != null) && Arrays.equals(user.getPublicKey(), accountPublicKey)) {
						user.send(response);
					}
				});
			}, Account.Event.UNCONFIRMED_BALANCE);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray removedActivePeers = new JSONArray();
				final JSONObject removedActivePeer = new JSONObject();
				removedActivePeer.put("index", Users.getIndex(peer));
				removedActivePeers.add(removedActivePeer);
				response.put("removedActivePeers", removedActivePeers);
				final JSONArray removedKnownPeers = new JSONArray();
				final JSONObject removedKnownPeer = new JSONObject();
				removedKnownPeer.put("index", Users.getIndex(peer));
				removedKnownPeers.add(removedKnownPeer);
				response.put("removedKnownPeers", removedKnownPeers);
				final JSONArray addedBlacklistedPeers = new JSONArray();
				final JSONObject addedBlacklistedPeer = new JSONObject();
				addedBlacklistedPeer.put("index", Users.getIndex(peer));
				addedBlacklistedPeer.put("address", peer.getHost());
				addedBlacklistedPeer.put("announcedAddress",
						Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
				addedBlacklistedPeer.put("software", peer.getSoftware());
				addedBlacklistedPeers.add(addedBlacklistedPeer);
				response.put("addedBlacklistedPeers", addedBlacklistedPeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.BLACKLIST);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray removedActivePeers = new JSONArray();
				final JSONObject removedActivePeer = new JSONObject();
				removedActivePeer.put("index", Users.getIndex(peer));
				removedActivePeers.add(removedActivePeer);
				response.put("removedActivePeers", removedActivePeers);
				final JSONArray addedKnownPeers = new JSONArray();
				final JSONObject addedKnownPeer = new JSONObject();
				addedKnownPeer.put("index", Users.getIndex(peer));
				addedKnownPeer.put("address", peer.getHost());
				addedKnownPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
				addedKnownPeer.put("software", peer.getSoftware());
				addedKnownPeers.add(addedKnownPeer);
				response.put("addedKnownPeers", addedKnownPeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.DEACTIVATE);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray removedBlacklistedPeers = new JSONArray();
				final JSONObject removedBlacklistedPeer = new JSONObject();
				removedBlacklistedPeer.put("index", Users.getIndex(peer));
				removedBlacklistedPeers.add(removedBlacklistedPeer);
				response.put("removedBlacklistedPeers", removedBlacklistedPeers);
				final JSONArray addedKnownPeers = new JSONArray();
				final JSONObject addedKnownPeer = new JSONObject();
				addedKnownPeer.put("index", Users.getIndex(peer));
				addedKnownPeer.put("address", peer.getHost());
				addedKnownPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
				addedKnownPeer.put("software", peer.getSoftware());
				addedKnownPeers.add(addedKnownPeer);
				response.put("addedKnownPeers", addedKnownPeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.UNBLACKLIST);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray removedKnownPeers = new JSONArray();
				final JSONObject removedKnownPeer = new JSONObject();
				removedKnownPeer.put("index", Users.getIndex(peer));
				removedKnownPeers.add(removedKnownPeer);
				response.put("removedKnownPeers", removedKnownPeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.REMOVE);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray changedActivePeers = new JSONArray();
				final JSONObject changedActivePeer = new JSONObject();
				changedActivePeer.put("index", Users.getIndex(peer));
				changedActivePeer.put("downloaded", peer.getDownloadedVolume());
				changedActivePeers.add(changedActivePeer);
				response.put("changedActivePeers", changedActivePeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.DOWNLOADED_VOLUME);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray changedActivePeers = new JSONArray();
				final JSONObject changedActivePeer = new JSONObject();
				changedActivePeer.put("index", Users.getIndex(peer));
				changedActivePeer.put("uploaded", peer.getUploadedVolume());
				changedActivePeers.add(changedActivePeer);
				response.put("changedActivePeers", changedActivePeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.UPLOADED_VOLUME);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray changedActivePeers = new JSONArray();
				final JSONObject changedActivePeer = new JSONObject();
				changedActivePeer.put("index", Users.getIndex(peer));
				changedActivePeer.put("weight", peer.getWeight());
				changedActivePeers.add(changedActivePeer);
				response.put("changedActivePeers", changedActivePeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.WEIGHT);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray removedKnownPeers = new JSONArray();
				final JSONObject removedKnownPeer = new JSONObject();
				removedKnownPeer.put("index", Users.getIndex(peer));
				removedKnownPeers.add(removedKnownPeer);
				response.put("removedKnownPeers", removedKnownPeers);
				final JSONArray addedActivePeers = new JSONArray();
				final JSONObject addedActivePeer = new JSONObject();
				addedActivePeer.put("index", Users.getIndex(peer));
				if (peer.getState() != Peer.State.CONNECTED) {
					addedActivePeer.put("disconnected", true);
				}
				addedActivePeer.put("address", peer.getHost());
				addedActivePeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
				addedActivePeer.put("weight", peer.getWeight());
				addedActivePeer.put("downloaded", peer.getDownloadedVolume());
				addedActivePeer.put("uploaded", peer.getUploadedVolume());
				addedActivePeer.put("software", peer.getSoftware());
				addedActivePeers.add(addedActivePeer);
				response.put("addedActivePeers", addedActivePeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.ADDED_ACTIVE_PEER);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray changedActivePeers = new JSONArray();
				final JSONObject changedActivePeer = new JSONObject();
				changedActivePeer.put("index", Users.getIndex(peer));
				changedActivePeer.put(peer.getState() == Peer.State.CONNECTED ? "connected" : "disconnected", true);
				changedActivePeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
				changedActivePeers.add(changedActivePeer);
				response.put("changedActivePeers", changedActivePeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.CHANGED_ACTIVE_PEER);

			Peers.addListener(peer -> {
				final JSONObject response = new JSONObject();
				final JSONArray addedKnownPeers = new JSONArray();
				final JSONObject addedKnownPeer = new JSONObject();
				addedKnownPeer.put("index", Users.getIndex(peer));
				addedKnownPeer.put("address", peer.getHost());
				addedKnownPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
				addedKnownPeer.put("software", peer.getSoftware());
				addedKnownPeers.add(addedKnownPeer);
				response.put("addedKnownPeers", addedKnownPeers);
				Users.sendNewDataToAll(response);
			}, Peers.Event.NEW_PEER);

			Nxt.getTransactionProcessor().addListener(transactions -> {
				final JSONObject response = new JSONObject();
				final JSONArray removedUnconfirmedTransactions = new JSONArray();
				for (final Transaction transaction : transactions) {
					final JSONObject removedUnconfirmedTransaction = new JSONObject();
					removedUnconfirmedTransaction.put("index", Users.getIndex(transaction));
					removedUnconfirmedTransactions.add(removedUnconfirmedTransaction);
				}
				response.put("removedUnconfirmedTransactions", removedUnconfirmedTransactions);
				Users.sendNewDataToAll(response);
			}, TransactionProcessor.Event.REMOVED_UNCONFIRMED_TRANSACTIONS);

			Nxt.getTransactionProcessor().addListener(transactions -> {
				final JSONObject response = new JSONObject();
				final JSONArray addedUnconfirmedTransactions = new JSONArray();
				for (final Transaction transaction : transactions) {
					final JSONObject addedUnconfirmedTransaction = new JSONObject();
					addedUnconfirmedTransaction.put("index", Users.getIndex(transaction));
					addedUnconfirmedTransaction.put("timestamp", transaction.getTimestamp());
					addedUnconfirmedTransaction.put("deadline", transaction.getDeadline());
					addedUnconfirmedTransaction.put("recipient", Long.toUnsignedString(transaction.getRecipientId()));
					addedUnconfirmedTransaction.put("amountNQT", transaction.getAmountNQT());
					addedUnconfirmedTransaction.put("feeNQT", transaction.getFeeNQT());
					addedUnconfirmedTransaction.put("sender", Long.toUnsignedString(transaction.getSenderId()));
					addedUnconfirmedTransaction.put("id", transaction.getStringId());
					addedUnconfirmedTransactions.add(addedUnconfirmedTransaction);
				}
				response.put("addedUnconfirmedTransactions", addedUnconfirmedTransactions);
				Users.sendNewDataToAll(response);
			}, TransactionProcessor.Event.ADDED_UNCONFIRMED_TRANSACTIONS);

			Nxt.getTransactionProcessor().addListener(transactions -> {
				final JSONObject response = new JSONObject();
				final JSONArray addedConfirmedTransactions = new JSONArray();
				for (final Transaction transaction : transactions) {
					final JSONObject addedConfirmedTransaction = new JSONObject();
					addedConfirmedTransaction.put("index", Users.getIndex(transaction));
					addedConfirmedTransaction.put("blockTimestamp", transaction.getBlockTimestamp());
					addedConfirmedTransaction.put("transactionTimestamp", transaction.getTimestamp());
					addedConfirmedTransaction.put("sender", Long.toUnsignedString(transaction.getSenderId()));
					addedConfirmedTransaction.put("recipient", Long.toUnsignedString(transaction.getRecipientId()));
					addedConfirmedTransaction.put("amountNQT", transaction.getAmountNQT());
					addedConfirmedTransaction.put("feeNQT", transaction.getFeeNQT());
					addedConfirmedTransaction.put("id", transaction.getStringId());
					addedConfirmedTransactions.add(addedConfirmedTransaction);
				}
				response.put("addedConfirmedTransactions", addedConfirmedTransactions);
				Users.sendNewDataToAll(response);
			}, TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);

			Nxt.getBlockchainProcessor().addListener(block -> {
				final JSONObject response = new JSONObject();
				final JSONArray addedOrphanedBlocks = new JSONArray();
				final JSONObject addedOrphanedBlock = new JSONObject();
				addedOrphanedBlock.put("index", Users.getIndex(block));
				addedOrphanedBlock.put("timestamp", block.getTimestamp());
				addedOrphanedBlock.put("numberOfTransactions", block.getTransactions().size());
				addedOrphanedBlock.put("totalAmountNQT", block.getTotalAmountNQT());
				addedOrphanedBlock.put("totalFeeNQT", block.getTotalFeeNQT());
				addedOrphanedBlock.put("payloadLength", block.getPayloadLength());
				addedOrphanedBlock.put("generator", Long.toUnsignedString(block.getGeneratorId()));
				addedOrphanedBlock.put("height", block.getHeight());
				addedOrphanedBlock.put("version", block.getVersion());
				addedOrphanedBlock.put("block", block.getStringId());
				addedOrphanedBlock.put("baseTarget",
						BigInteger.valueOf(block.getBaseTarget()).multiply(BigInteger.valueOf(100000))
								.divide(BigInteger.valueOf(Constants.INITIAL_BASE_TARGET)));
				addedOrphanedBlocks.add(addedOrphanedBlock);
				response.put("addedOrphanedBlocks", addedOrphanedBlocks);
				Users.sendNewDataToAll(response);
			}, BlockchainProcessor.Event.BLOCK_POPPED);

			Nxt.getBlockchainProcessor().addListener(block -> {
				final JSONObject response = new JSONObject();
				final JSONArray addedRecentBlocks = new JSONArray();
				final JSONObject addedRecentBlock = new JSONObject();
				addedRecentBlock.put("index", Users.getIndex(block));
				addedRecentBlock.put("timestamp", block.getTimestamp());
				addedRecentBlock.put("numberOfTransactions", block.getTransactions().size());
				addedRecentBlock.put("totalAmountNQT", block.getTotalAmountNQT());
				addedRecentBlock.put("totalFeeNQT", block.getTotalFeeNQT());
				addedRecentBlock.put("payloadLength", block.getPayloadLength());
				addedRecentBlock.put("generator", Long.toUnsignedString(block.getGeneratorId()));
				addedRecentBlock.put("height", block.getHeight());
				addedRecentBlock.put("version", block.getVersion());
				addedRecentBlock.put("block", block.getStringId());
				addedRecentBlock.put("baseTarget",
						BigInteger.valueOf(block.getBaseTarget()).multiply(BigInteger.valueOf(100000))
								.divide(BigInteger.valueOf(Constants.INITIAL_BASE_TARGET)));
				addedRecentBlocks.add(addedRecentBlock);
				response.put("addedRecentBlocks", addedRecentBlocks);
				Users.sendNewDataToAll(response);
			}, BlockchainProcessor.Event.BLOCK_PUSHED);

			Generator.addListener(generator -> {
				final JSONObject response = new JSONObject();
				response.put("response", "setBlockGenerationDeadline");
				response.put("deadline", generator.getDeadline());
				Users.users.values().forEach(user -> {
					if (Arrays.equals(generator.getPublicKey(), user.getPublicKey())) {
						user.send(response);
					}
				});
			}, Generator.Event.GENERATION_DEADLINE);
		}

	}

	static Collection<User> getAllUsers() {
		return Users.allUsers;
	}

	static int getIndex(final Block block) {
		Integer index = Users.blockIndexMap.get(block.getId());
		if (index == null) {
			index = Users.blockCounter.incrementAndGet();
			Users.blockIndexMap.put(block.getId(), index);
		}
		return index;
	}

	static int getIndex(final Peer peer) {
		Integer index = Users.peerIndexMap.get(peer.getHost());
		if (index == null) {
			index = Users.peerCounter.incrementAndGet();
			Users.peerIndexMap.put(peer.getHost(), index);
			Users.peerAddressMap.put(index, peer.getHost());
		}
		return index;
	}

	static int getIndex(final Transaction transaction) {
		Integer index = Users.transactionIndexMap.get(transaction.getId());
		if (index == null) {
			index = Users.transactionCounter.incrementAndGet();
			Users.transactionIndexMap.put(transaction.getId(), index);
		}
		return index;
	}

	static Peer getPeer(final int index) {
		final String peerAddress = Users.peerAddressMap.get(index);
		if (peerAddress == null) {
			return null;
		}
		return Peers.getPeer(peerAddress);
	}

	static User getUser(final String userId) {
		User user = Users.users.get(userId);
		if (user == null) {
			user = new User(userId);
			final User oldUser = Users.users.putIfAbsent(userId, user);
			if (oldUser != null) {
				user = oldUser;
				user.setInactive(false);
			}
		} else {
			user.setInactive(false);
		}
		return user;
	}

	public static void init() {
	}

	static User remove(final User user) {
		return Users.users.remove(user.getUserId());
	}

	private static void sendNewDataToAll(final JSONObject response) {
		response.put("response", "processNewData");
		Users.sendToAll(response);
	}

	private static void sendToAll(final JSONStreamAware response) {
		for (final User user : Users.users.values()) {
			user.send(response);
		}
	}

	public static void shutdown() {
		if (Users.userServer != null) {
			try {
				Users.userServer.stop();
			} catch (final Exception e) {
				Logger.logShutdownMessage("Failed to stop user interface server", e);
			}
		}
	}

	private Users() {
	} // never

}

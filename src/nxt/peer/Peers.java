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

package nxt.peer;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.DispatcherType;

import nxt.db.DbIterator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.DoSFilter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Block;
import nxt.Constants;
import nxt.Db;
import nxt.Nxt;
import nxt.Transaction;
import nxt.http.API;
import nxt.http.APIEnum;
import nxt.util.Convert;
import nxt.util.Filter;
import nxt.util.JSON;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;
import nxt.util.QueuedThreadPool;
import nxt.util.ThreadPool;
import nxt.util.UPnP;

public final class Peers {

	private static List<String> cachedSnPeers = null;
	private static int lastCacheTime = 0;



	public enum Event {
		BLACKLIST, UNBLACKLIST, DEACTIVATE, REMOVE, DOWNLOADED_VOLUME, UPLOADED_VOLUME, WEIGHT, ADDED_ACTIVE_PEER, CHANGED_ACTIVE_PEER, NEW_PEER, ADD_INBOUND, REMOVE_INBOUND, CHANGED_SERVICES
	}

	private static class Init {

		private final static Server peerServer;

		static {
			if (Peers.shareMyAddress) {
				peerServer = new Server();
				final ServerConnector connector = new ServerConnector(Init.peerServer);
				final int port = Constants.isTestnet ? Peers.TESTNET_PEER_PORT : Peers.myPeerServerPort;
				connector.setPort(port);
				final String host = Nxt.getStringProperty("nxt.peerServerHost");
				connector.setHost(host);
				connector.setIdleTimeout(Nxt.getIntProperty("nxt.peerServerIdleTimeout"));
				connector.setReuseAddress(true);
				Init.peerServer.addConnector(connector);

				final ServletContextHandler ctxHandler = new ServletContextHandler();
				ctxHandler.setContextPath("/");

				final ServletHolder peerServletHolder = new ServletHolder(new PeerServlet());
				ctxHandler.addServlet(peerServletHolder, "/*");

				if (Nxt.getBooleanProperty("nxt.enablePeerServerDoSFilter")) {
					final FilterHolder dosFilterHolder = ctxHandler.addFilter(DoSFilter.class, "/*",
							EnumSet.of(DispatcherType.REQUEST));
					dosFilterHolder.setInitParameter("maxRequestsPerSec",
							Nxt.getStringProperty("nxt.peerServerDoSFilter.maxRequestsPerSec"));
					dosFilterHolder.setInitParameter("delayMs",
							Nxt.getStringProperty("nxt.peerServerDoSFilter.delayMs"));
					dosFilterHolder.setInitParameter("maxRequestMs",
							Nxt.getStringProperty("nxt.peerServerDoSFilter.maxRequestMs"));
					dosFilterHolder.setInitParameter("trackSessions", "false");
					dosFilterHolder.setAsyncSupported(true);
				}

				if (Peers.isGzipEnabled) {
					final GzipHandler gzipHandler = new GzipHandler();
					gzipHandler.setIncludedMethods("GET", "POST");
					gzipHandler.setIncludedPaths("/*");
					gzipHandler.setMinGzipSize(Peers.MIN_COMPRESS_SIZE);
					ctxHandler.setGzipHandler(gzipHandler);
				}

				Init.peerServer.setHandler(ctxHandler);
				Init.peerServer.setStopAtShutdown(true);
				ThreadPool.runBeforeStart(() -> {
					try {
						if (Peers.enablePeerUPnP) {
							final Connector[] peerConnectors = Init.peerServer.getConnectors();
							for (final Connector peerConnector : peerConnectors) {
								if (peerConnector instanceof ServerConnector) {
									UPnP.addPort(((ServerConnector) peerConnector).getPort());
								}
							}
						}
						Init.peerServer.start();
						Logger.logMessage("Started peer networking server at " + host + ":" + port);
					} catch (final Exception e) {
						Logger.logErrorMessage("Failed to start peer networking server", e);
						throw new RuntimeException(e.toString(), e);
					}
				}, true);
			} else {
				peerServer = null;
				Logger.logMessage("shareMyAddress is disabled, will not start peer networking server");
			}
		}

		private static void init() {
		}

		private Init() {
		}

	}

	static final int LOGGING_MASK_EXCEPTIONS = 1;
	static final int LOGGING_MASK_NON200_RESPONSES = 2;
	static final int LOGGING_MASK_200_RESPONSES = 4;

	static volatile int communicationLoggingMask;
	private static final List<String> wellKnownPeers;

	static final Set<String> knownBlacklistedPeers;
	static final int connectTimeout;
	static final int readTimeout;
	static final int blacklistingPeriod;
	static final boolean getMorePeers;
	static final int MAX_REQUEST_SIZE = 1024 * 1024;
	static final int MAX_RESPONSE_SIZE = 1024 * 1024;
	static final int MAX_MESSAGE_SIZE = 10 * 1024 * 1024;
	public static final int MIN_COMPRESS_SIZE = 256;
	static final boolean useWebSockets;
	static final int webSocketIdleTimeout;
	static final boolean useProxy = (System.getProperty("socksProxyHost") != null)
			|| (System.getProperty("http.proxyHost") != null);

	static final boolean isGzipEnabled;
	private static final int DEFAULT_PEER_PORT = 7874;
	private static final int TESTNET_PEER_PORT = 6874;
	private static final String myPlatform;
	private static final String myAddress;
	private static final int myPeerServerPort;
	private static final String myHallmark;
	private static final boolean shareMyAddress;
	private static final boolean enablePeerUPnP;
	private static final int maxNumberOfInboundConnections;
	private static final int maxNumberOfOutboundConnections;
	public static final int maxNumberOfConnectedPublicPeers;
	private static final int maxNumberOfKnownPeers;
	private static final int minNumberOfKnownPeers;
	private static final boolean enableHallmarkProtection;
	private static final int pushThreshold;
	private static final int pullThreshold;
	private static final int sendToPeersLimit;
	private static final int sendToSnPeersLimit;
	private static final boolean usePeersDb;
	private static final boolean savePeers;
	static final boolean ignorePeerAnnouncedAddress;
	static final boolean cjdnsOnly;
	static final int MAX_VERSION_LENGTH = 10;
	static final int MAX_APPLICATION_LENGTH = 20;
	static final int MAX_PLATFORM_LENGTH = 30;
	static final int MAX_ANNOUNCED_ADDRESS_LENGTH = 100;

	static final boolean hideErrorDetails = Nxt.getBooleanProperty("nxt.hideErrorDetails");
	private static final JSONObject myPeerInfo;
	private static final List<Peer.Service> myServices;
	private static volatile Peer.BlockchainState currentBlockchainState;
	private static volatile JSONStreamAware myPeerInfoRequest;

	private static volatile JSONStreamAware myPeerInfoResponse;

	private static final Listeners<Peer, Event> listeners = new Listeners<>();
	private static final ConcurrentMap<String, PeerImpl> peers = new ConcurrentHashMap<>();

	private static final ConcurrentMap<String, String> selfAnnouncedAddresses = new ConcurrentHashMap<>();

	static final Collection<PeerImpl> allPeers = Collections.unmodifiableCollection(Peers.peers.values());
	static final ExecutorService peersService = new QueuedThreadPool(2, 15);

	private static final ExecutorService sendingService = Executors.newFixedThreadPool(10);

	static {

		String platform = Nxt.getStringProperty("nxt.myPlatform",
				System.getProperty("os.name") + " " + System.getProperty("os.arch"));
		if (platform.length() > Peers.MAX_PLATFORM_LENGTH) {
			platform = platform.substring(0, Peers.MAX_PLATFORM_LENGTH);
		}
		myPlatform = platform;
		myAddress = Convert.emptyToNull(Nxt.getStringProperty("nxt.myAddress", "").trim());
		if ((Peers.myAddress != null) && Peers.myAddress.endsWith(":" + Peers.TESTNET_PEER_PORT)
				&& !Constants.isTestnet) {
			throw new RuntimeException("Port " + Peers.TESTNET_PEER_PORT + " should only be used for testnet!!!");
		}
		String myHost = null;
		int myPort = -1;
		if (Peers.myAddress != null) {
			try {
				final URI uri = new URI("http://" + Peers.myAddress);
				myHost = uri.getHost();
				myPort = (uri.getPort() == -1 ? Peers.getDefaultPeerPort() : uri.getPort());
				final InetAddress[] myAddrs = InetAddress.getAllByName(myHost);
				boolean addrValid = false;
				final Enumeration<NetworkInterface> intfs = NetworkInterface.getNetworkInterfaces();
				chkAddr: while (intfs.hasMoreElements()) {
					final NetworkInterface intf = intfs.nextElement();
					final List<InterfaceAddress> intfAddrs = intf.getInterfaceAddresses();
					for (final InterfaceAddress intfAddr : intfAddrs) {
						final InetAddress extAddr = intfAddr.getAddress();
						for (final InetAddress myAddr : myAddrs) {
							if (extAddr.equals(myAddr)) {
								addrValid = true;
								break chkAddr;
							}
						}
					}
				}
				if (!addrValid) {
					final InetAddress extAddr = UPnP.getExternalAddress();
					if (extAddr != null) {
						for (final InetAddress myAddr : myAddrs) {
							if (extAddr.equals(myAddr)) {
								addrValid = true;
								break;
							}
						}
					}
				}
				if (!addrValid) {
					Logger.logWarningMessage("Your announced address does not match your external address");
				}
			} catch (final SocketException e) {
				Logger.logErrorMessage("Unable to enumerate the network interfaces :" + e.toString());
			} catch (URISyntaxException | UnknownHostException e) {
				Logger.logWarningMessage("Your announced address is not valid: " + e.toString());
			}
		}
		myPeerServerPort = Nxt.getIntProperty("nxt.peerServerPort");
		if ((Peers.myPeerServerPort == Peers.TESTNET_PEER_PORT) && !Constants.isTestnet) {
			throw new RuntimeException("Port " + Peers.TESTNET_PEER_PORT + " should only be used for testnet!!!");
		}
		shareMyAddress = Nxt.getBooleanProperty("nxt.shareMyAddress") && !Constants.isOffline;
		enablePeerUPnP = Nxt.getBooleanProperty("nxt.enablePeerUPnP");
		myHallmark = Convert.emptyToNull(Nxt.getStringProperty("nxt.myHallmark", "").trim());
		if ((Peers.myHallmark != null) && (Peers.myHallmark.length() > 0)) {
			try {
				final Hallmark hallmark = Hallmark.parseHallmark(Peers.myHallmark);
				if (!hallmark.isValid()) {
					throw new RuntimeException("Hallmark is not valid");
				}
				if (Peers.myAddress != null) {
					if (!hallmark.getHost().equals(myHost)) {
						throw new RuntimeException("Invalid hallmark host");
					}
					if (myPort != hallmark.getPort()) {
						throw new RuntimeException("Invalid hallmark port");
					}
				}
			} catch (final RuntimeException e) {
				Logger.logErrorMessage(
						"Your hallmark is invalid: " + Peers.myHallmark + " for your address: " + Peers.myAddress);
				throw new RuntimeException(e.toString(), e);
			}
		}
		final List<Peer.Service> servicesList = new ArrayList<>();
		final JSONObject json = new JSONObject();
		if (Peers.myAddress != null) {
			try {
				final URI uri = new URI("http://" + Peers.myAddress);
				final String host = uri.getHost();
				final int port = uri.getPort();
				String announcedAddress;
				if (!Constants.isTestnet) {
					if (port >= 0) {
						announcedAddress = Peers.myAddress;
					} else {
						announcedAddress = host + (Peers.myPeerServerPort != Peers.DEFAULT_PEER_PORT
								? ":" + Peers.myPeerServerPort : "");
					}
				} else {
					announcedAddress = host;
				}
				if ((announcedAddress == null) || (announcedAddress.length() > Peers.MAX_ANNOUNCED_ADDRESS_LENGTH)) {
					throw new RuntimeException("Invalid announced address length: " + announcedAddress);
				}
				json.put("announcedAddress", announcedAddress);
			} catch (final URISyntaxException e) {
				Logger.logMessage("Your announce address is invalid: " + Peers.myAddress);
				throw new RuntimeException(e.toString(), e);
			}
		}
		if ((Peers.myHallmark != null) && (Peers.myHallmark.length() > 0)) {
			json.put("hallmark", Peers.myHallmark);
			servicesList.add(Peer.Service.HALLMARK);
		}
		json.put("application", Nxt.APPLICATION);
		json.put("version", Nxt.VERSION);
		json.put("platform", Peers.myPlatform);
		json.put("shareAddress", Peers.shareMyAddress);
		if (!Constants.ENABLE_PRUNING && Constants.INCLUDE_EXPIRED_PRUNABLE) {
			servicesList.add(Peer.Service.PRUNABLE);
		}
		if (API.openAPIPort > 0) {
			json.put("apiPort", API.openAPIPort);
			servicesList.add(Peer.Service.API);
		}
		if (API.openAPISSLPort > 0) {
			json.put("apiSSLPort", API.openAPISSLPort);
			servicesList.add(Peer.Service.API_SSL);
		}

		if ((API.openAPIPort > 0) || (API.openAPISSLPort > 0)) {
			final EnumSet<APIEnum> disabledAPISet = EnumSet.noneOf(APIEnum.class);

			API.disabledAPIs.forEach(apiName -> {
				final APIEnum api = APIEnum.fromName(apiName);
				if (api != null) {
					disabledAPISet.add(api);
				}
			});
			API.disabledAPITags.forEach(apiTag -> {
				for (final APIEnum api : APIEnum.values()) {
					if ((api.getHandler() != null) && api.getHandler().getAPITags().contains(apiTag)) {
						disabledAPISet.add(api);
					}
				}
			});
			json.put("disabledAPIs", APIEnum.enumSetToBase64String(disabledAPISet));

			json.put("apiServerIdleTimeout", API.apiServerIdleTimeout);

			if (API.apiServerCORS) {
				servicesList.add(Peer.Service.CORS);
			}
		}

		long services = 0;
		for (final Peer.Service service : servicesList) {
			services |= service.getCode();
		}
		json.put("services", Long.toUnsignedString(services));
		myServices = Collections.unmodifiableList(servicesList);
		Logger.logDebugMessage("My peer info:\n" + json.toJSONString());
		myPeerInfo = json;

		final List<String> defaultPeers = Constants.isTestnet ? Nxt.getStringListProperty("nxt.defaultTestnetPeers")
				: Nxt.getStringListProperty("nxt.defaultPeers");
		wellKnownPeers = Collections.unmodifiableList(Constants.isTestnet
				? Nxt.getStringListProperty("nxt.testnetPeers") : Nxt.getStringListProperty("nxt.wellKnownPeers"));

		final List<String> knownBlacklistedPeersList = Nxt.getStringListProperty("nxt.knownBlacklistedPeers");
		if (knownBlacklistedPeersList.isEmpty()) {
			knownBlacklistedPeers = Collections.emptySet();
		} else {
			knownBlacklistedPeers = Collections.unmodifiableSet(new HashSet<>(knownBlacklistedPeersList));
		}

		maxNumberOfInboundConnections = Nxt.getIntProperty("nxt.maxNumberOfInboundConnections");
		maxNumberOfOutboundConnections = Nxt.getIntProperty("nxt.maxNumberOfOutboundConnections");
		maxNumberOfConnectedPublicPeers = Math.min(Nxt.getIntProperty("nxt.maxNumberOfConnectedPublicPeers"),
				Peers.maxNumberOfOutboundConnections);
		maxNumberOfKnownPeers = Nxt.getIntProperty("nxt.maxNumberOfKnownPeers");
		minNumberOfKnownPeers = Nxt.getIntProperty("nxt.minNumberOfKnownPeers");
		connectTimeout = Nxt.getIntProperty("nxt.connectTimeout");
		readTimeout = Nxt.getIntProperty("nxt.readTimeout");
		enableHallmarkProtection = Nxt.getBooleanProperty("nxt.enableHallmarkProtection") && !Constants.isLightClient;
		pushThreshold = Nxt.getIntProperty("nxt.pushThreshold");
		pullThreshold = Nxt.getIntProperty("nxt.pullThreshold");
		useWebSockets = Nxt.getBooleanProperty("nxt.useWebSockets");
		webSocketIdleTimeout = Nxt.getIntProperty("nxt.webSocketIdleTimeout");
		isGzipEnabled = Nxt.getBooleanProperty("nxt.enablePeerServerGZIPFilter");
		blacklistingPeriod = Nxt.getIntProperty("nxt.blacklistingPeriod") / 1000;
		Peers.communicationLoggingMask = Nxt.getIntProperty("nxt.communicationLoggingMask");
		sendToPeersLimit = Nxt.getIntProperty("nxt.sendToPeersLimit");
		sendToSnPeersLimit = Constants.Supernode_Push_Limit;
		usePeersDb = Nxt.getBooleanProperty("nxt.usePeersDb") && !Constants.isOffline;
		savePeers = Peers.usePeersDb && Nxt.getBooleanProperty("nxt.savePeers");
		getMorePeers = Nxt.getBooleanProperty("nxt.getMorePeers");
		cjdnsOnly = Nxt.getBooleanProperty("nxt.cjdnsOnly");
		ignorePeerAnnouncedAddress = Nxt.getBooleanProperty("nxt.ignorePeerAnnouncedAddress");
		if (Peers.useWebSockets && Peers.useProxy) {
			Logger.logMessage("Using a proxy, will not create outbound websockets.");
		}

		final List<Future<String>> unresolvedPeers = Collections.synchronizedList(new ArrayList<>());

		if (!Constants.isOffline) {
			ThreadPool.runBeforeStart(new Runnable() {

				private final Set<PeerDb.Entry> entries = new HashSet<>();

				@Override
				public void run() {
					final int now = Nxt.getEpochTime();
					Peers.wellKnownPeers.forEach(address -> this.entries.add(new PeerDb.Entry(address, 0, now)));
					if (Peers.usePeersDb) {
						Logger.logDebugMessage("Loading known peers from the database...");
						defaultPeers.forEach(address -> this.entries.add(new PeerDb.Entry(address, 0, now)));
						if (Peers.savePeers) {
							final List<PeerDb.Entry> dbPeers = PeerDb.loadPeers();
							dbPeers.forEach(entry -> {
								if (!this.entries.add(entry)) {
									// Database entries override entries from
									// nxt.properties
									this.entries.remove(entry);
									this.entries.add(entry);
								}
							});
						}
					}
					this.entries.forEach(entry -> {
						final Future<String> unresolvedAddress = Peers.peersService.submit(() -> {
							final PeerImpl peer = Peers.findOrCreatePeer(entry.getAddress(), true);
							if (peer != null) {
								peer.setLastUpdated(entry.getLastUpdated());
								peer.setServices(entry.getServices());
								Peers.addPeer(peer);
								return null;
							}
							return entry.getAddress();
						});
						unresolvedPeers.add(unresolvedAddress);
					});
				}
			}, false);
		}

		ThreadPool.runAfterStart(() -> {
			for (final Future<String> unresolvedPeer : unresolvedPeers) {
				try {
					final String badAddress = unresolvedPeer.get(5, TimeUnit.SECONDS);
					if (badAddress != null) {
						Logger.logDebugMessage("Failed to resolve peer address: " + badAddress);
					}
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (final ExecutionException e) {
					Logger.logDebugMessage("Failed to add peer", e);
				} catch (final TimeoutException ignore) {
				}
			}
			Logger.logDebugMessage("Known peers: " + Peers.peers.size());
		});

	}

	private static final Runnable peerUnBlacklistingThread = () -> {

		try {
			try {

				final int curTime = Nxt.getEpochTime();
				for (final PeerImpl peer : Peers.peers.values()) {
					peer.updateBlacklistedStatus(curTime);
				}

			} catch (final Exception e) {
				Logger.logDebugMessage("Error un-blacklisting peer", e);
			}
		} catch (final Throwable t) {
			Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
			System.exit(1);
		}

	};

	private static final Runnable peerConnectingThread = () -> {

		try {
			try {

				final int now = Nxt.getEpochTime();

				

				if (!Peers.hasEnoughConnectedPublicPeers(Peers.maxNumberOfConnectedPublicPeers)) {
					final List<Future<?>> futures = new ArrayList<>();
					final List<Peer> hallmarkedPeers = Peers.getPeers(peer1 -> !peer1.isBlacklisted()
							&& (peer1.getAnnouncedAddress() != null) && (peer1.getState() != Peer.State.CONNECTED)
							&& ((now - peer1.getLastConnectAttempt()) > 600)
							&& peer1.providesService(Peer.Service.HALLMARK));
					final List<Peer> nonhallmarkedPeers = Peers.getPeers(peer2 -> !peer2.isBlacklisted()
							&& (peer2.getAnnouncedAddress() != null) && (peer2.getState() != Peer.State.CONNECTED)
							&& ((now - peer2.getLastConnectAttempt()) > 600)
							&& !peer2.providesService(Peer.Service.HALLMARK));
					if (!hallmarkedPeers.isEmpty() || !nonhallmarkedPeers.isEmpty()) {
						final Set<PeerImpl> connectSet = new HashSet<>();
						for (int i = 0; i < 10; i++) {
							List<Peer> peerList;
							if (hallmarkedPeers.isEmpty()) {
								peerList = nonhallmarkedPeers;
							} else if (nonhallmarkedPeers.isEmpty()) {
								peerList = hallmarkedPeers;
							} else {
								peerList = (ThreadLocalRandom.current().nextInt(2) == 0 ? hallmarkedPeers
										: nonhallmarkedPeers);
							}
							connectSet
									.add((PeerImpl) peerList.get(ThreadLocalRandom.current().nextInt(peerList.size())));
						}
						connectSet.forEach(peer3 -> futures.add(Peers.peersService.submit(() -> {
							peer3.connect();
							if ((peer3.getState() == Peer.State.CONNECTED) && Peers.enableHallmarkProtection
									&& (peer3.getWeight() == 0) && Peers.hasTooManyOutboundConnections()) {
								Logger.logDebugMessage(
										"Too many outbound connections, deactivating peer " + peer3.getHost());
								peer3.deactivate();
							}
							return null;
						})));
						for (final Future<?> future : futures) {
							future.get();
						}
					}
				}

				Peers.peers.values().forEach(peer4 -> {
					if ((peer4.getState() == Peer.State.CONNECTED) && ((now - peer4.getLastUpdated()) > 3600)
							&& ((now - peer4.getLastConnectAttempt()) > 600)) {
						Peers.peersService.submit(peer4::connect);
					}
					if ((peer4.getLastInboundRequest() != 0)
							&& ((now - peer4.getLastInboundRequest()) > (Peers.webSocketIdleTimeout / 1000))) {
						peer4.setLastInboundRequest(0);
						Peers.notifyListeners(peer4, Event.REMOVE_INBOUND);
					}
				});

				if (Peers.hasTooManyKnownPeers()
						&& Peers.hasEnoughConnectedPublicPeers(Peers.maxNumberOfConnectedPublicPeers)) {
					final int initialSize = Peers.peers.size();
					for (final PeerImpl peer5 : Peers.peers.values()) {
						if ((now - peer5.getLastUpdated()) > (24 * 3600)) {
							peer5.remove();
						}
						if (Peers.hasTooFewKnownPeers()) {
							break;
						}
					}
					if (Peers.hasTooManyKnownPeers()) {
						final PriorityQueue<PeerImpl> sortedPeers = new PriorityQueue<>(Peers.peers.values());
						int skipped = 0;
						while (skipped < Peers.minNumberOfKnownPeers) {
							if (sortedPeers.poll() == null) {
								break;
							}
							skipped += 1;
						}
						while (!sortedPeers.isEmpty()) {
							sortedPeers.poll().remove();
						}
					}
					Logger.logDebugMessage("Reduced peer pool size from " + initialSize + " to " + Peers.peers.size());
				}

				for (final String wellKnownPeer : Peers.wellKnownPeers) {
					final PeerImpl peer6 = Peers.findOrCreatePeer(wellKnownPeer, true);
					if ((peer6 != null) && ((now - peer6.getLastUpdated()) > 3600)
							&& ((now - peer6.getLastConnectAttempt()) > 600)) {
						Peers.peersService.submit(() -> {
							Peers.addPeer(peer6);
							Peers.connectPeer(peer6);
						});
					}
				}

				// Handle SN Peers in the same way we handle well known peers
				if(Nxt.connectToSupernodes && !Peers.hasEnoughSupernodePeers(Constants.SUPERNODE_CONNECTED_NODES_ARE_ENOUGH)){
					// Our goal are #SUPERNODE_CONNECTED_NODES_ARE_ENOUGH SN connections. Let us try to connect to more
					for (final String snPeer : Peers.getPotentialSNPeers()) {
						final PeerImpl peer6 = Peers.findOrCreatePeer(snPeer, true);
						if ((peer6 != null) && ((now - peer6.getLastConnectAttempt()) > 600)) {
							Peers.peersService.submit(() -> {
								Peers.addPeer(peer6);
								Peers.connectPeer(peer6);
							});
						}
					}
				}

			} catch (final Exception e) {
				Logger.logDebugMessage("Error connecting to peer", e);
			}
		} catch (final Throwable t) {
			Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
			System.exit(1);
		}

	};

	private static final Runnable getMorePeersThread = new Runnable() {

		private final JSONStreamAware getPeersRequest;
		{
			final JSONObject request = new JSONObject();
			request.put("requestType", "getPeers");
			this.getPeersRequest = JSON.prepareRequest(request);
		}

		private volatile boolean updatedPeer;

		@Override
		public void run() {

			try {
				try {
					if (Peers.hasTooManyKnownPeers()) {
						return;
					}
					final Peer peer = Peers.getAnyPeer(Peer.State.CONNECTED, true);
					if (peer == null) {
						return;
					}
					final JSONObject response = peer.send(this.getPeersRequest, 10 * 1024 * 1024);
					if (response == null) {
						return;
					}
					final JSONArray peers = (JSONArray) response.get("peers");
					final Set<String> addedAddresses = new HashSet<>();
					if (peers != null) {
						final JSONArray services = (JSONArray) response.get("services");
						final boolean setServices = ((services != null) && (services.size() == peers.size()));
						final int now = Nxt.getEpochTime();
						for (int i = 0; i < peers.size(); i++) {
							final String announcedAddress = (String) peers.get(i);
							final PeerImpl newPeer = Peers.findOrCreatePeer(announcedAddress, true);
							if (newPeer != null) {
								if ((now - newPeer.getLastUpdated()) > (24 * 3600)) {
									newPeer.setLastUpdated(now);
									this.updatedPeer = true;
								}
								if (Peers.addPeer(newPeer) && setServices) {
									newPeer.setServices(Long.parseUnsignedLong((String) services.get(i)));
								}
								addedAddresses.add(announcedAddress);
								if (Peers.hasTooManyKnownPeers()) {
									break;
								}
							}
						}
						if (Peers.savePeers && this.updatedPeer) {
							this.updateSavedPeers();
							this.updatedPeer = false;
						}
					}

					final JSONArray myPeers = new JSONArray();
					final JSONArray myServices = new JSONArray();
					Peers.getAllPeers().forEach(myPeer -> {
						if (!myPeer.isBlacklisted() && (myPeer.getAnnouncedAddress() != null)
								&& (myPeer.getState() == Peer.State.CONNECTED) && myPeer.shareAddress()
								&& !addedAddresses.contains(myPeer.getAnnouncedAddress())
								&& !myPeer.getAnnouncedAddress().equals(peer.getAnnouncedAddress())) {
							myPeers.add(myPeer.getAnnouncedAddress());
							myServices.add(Long.toUnsignedString(((PeerImpl) myPeer).getServices()));
						}
					});
					if (myPeers.size() > 0) {
						final JSONObject request = new JSONObject();
						request.put("requestType", "addPeers");
						request.put("peers", myPeers);
						request.put("services", myServices); // Separate array
																// for backwards
																// compatibility
						peer.send(JSON.prepareRequest(request), 0);
					}

				} catch (final Exception e) {
					Logger.logDebugMessage("Error requesting peers from a peer", e);
				}
			} catch (final Throwable t) {
				Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
				System.exit(1);
			}

		}

		private void updateSavedPeers() {
			final int now = Nxt.getEpochTime();
			//
			// Load the current database entries and map announced address to
			// database entry
			//
			final List<PeerDb.Entry> oldPeers = PeerDb.loadPeers();
			final Map<String, PeerDb.Entry> oldMap = new HashMap<>(oldPeers.size());
			oldPeers.forEach(entry -> oldMap.put(entry.getAddress(), entry));
			//
			// Create the current peer map (note that there can be duplicate
			// peer entries with
			// the same announced address)
			//
			final Map<String, PeerDb.Entry> currentPeers = new HashMap<>();
			Peers.peers.values().forEach(peer -> {
				if ((peer.getAnnouncedAddress() != null) && !peer.isBlacklisted()
						&& ((now - peer.getLastUpdated()) < (7 * 24 * 3600))) {
					currentPeers.put(peer.getAnnouncedAddress(),
							new PeerDb.Entry(peer.getAnnouncedAddress(), peer.getServices(), peer.getLastUpdated()));
				}
			});
			//
			// Build toDelete and toUpdate lists
			//
			final List<PeerDb.Entry> toDelete = new ArrayList<>(oldPeers.size());
			oldPeers.forEach(entry -> {
				if (currentPeers.get(entry.getAddress()) == null) {
					toDelete.add(entry);
				}
			});
			final List<PeerDb.Entry> toUpdate = new ArrayList<>(currentPeers.size());
			currentPeers.values().forEach(entry -> {
				final PeerDb.Entry oldEntry = oldMap.get(entry.getAddress());
				if ((oldEntry == null) || ((entry.getLastUpdated() - oldEntry.getLastUpdated()) > (24 * 3600))) {
					toUpdate.add(entry);
				}
			});
			//
			// Nothing to do if all of the lists are empty
			//
			if (toDelete.isEmpty() && toUpdate.isEmpty()) {
				return;
			}
			//
			// Update the peer database
			//
			try {
				Db.db.beginTransaction();
				PeerDb.deletePeers(toDelete);
				PeerDb.updatePeers(toUpdate);
				Db.db.commitTransaction();
			} catch (final Exception e) {
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
		}

	};

	static {
		Peers.addListener(peer -> Peers.peersService.submit(() -> {
			if ((peer.getAnnouncedAddress() != null) && !peer.isBlacklisted()) {
				try {
					Db.db.beginTransaction();
					PeerDb.updatePeer((PeerImpl) peer);
					Db.db.commitTransaction();
				} catch (final RuntimeException e) {
					Logger.logErrorMessage("Unable to update peer database", e);
					Db.db.rollbackTransaction();
				} finally {
					Db.db.endTransaction();
				}
			}
		}), Peers.Event.CHANGED_SERVICES);
	}

	static {
		Account.addListener(account -> Peers.peers.values().forEach(peer -> {
			if ((peer.getHallmark() != null) && (peer.getHallmark().getAccountId() == account.getId())) {
				Peers.listeners.notify(peer, Event.WEIGHT);
			}
		}), Account.Event.BALANCE);
	}

	static {
		if (!Constants.isOffline) {
			ThreadPool.scheduleThread("PeerConnecting", Peers.peerConnectingThread, 20);
			ThreadPool.scheduleThread("PeerUnBlacklisting", Peers.peerUnBlacklistingThread, 60);
			if (Peers.getMorePeers) {
				ThreadPool.scheduleThread("GetMorePeers", Peers.getMorePeersThread, 20);
			}
		}
	}

	private static final int sendTransactionsBatchSize = 10;

	private static final int[] MAX_VERSION;

	static {
		String version = Nxt.VERSION;
		if (version.endsWith("e")) {
			version = version.substring(0, version.length() - 1);
		}
		final String[] versions = version.split("\\.");
		MAX_VERSION = new int[versions.length];
		for (int i = 0; i < versions.length; i++) {
			Peers.MAX_VERSION[i] = Integer.parseInt(versions[i]);
		}
	}

	public static boolean addListener(final Listener<Peer> listener, final Event eventType) {
		return Peers.listeners.addListener(listener, eventType);
	}

	public static boolean addPeer(final Peer peer) {
		if (Peers.peers.put(peer.getHost(), (PeerImpl) peer) == null) {
			Peers.listeners.notify(peer, Event.NEW_PEER);
			return true;
		}
		return false;
	}

	public static boolean addPeer(final Peer peer, final String newAnnouncedAddress) {
		Peers.setAnnouncedAddress((PeerImpl) peer, newAnnouncedAddress.toLowerCase());
		return Peers.addPeer(peer);
	}

	static String addressWithPort(final String address) {
		if (address == null) {
			return null;
		}
		try {
			final URI uri = new URI("http://" + address);
			final String host = uri.getHost();
			final int port = uri.getPort();
			return (port > 0) && (port != Peers.getDefaultPeerPort()) ? host + ":" + port : host;
		} catch (final URISyntaxException e) {
			return null;
		}
	}

	private static void checkBlockchainState() {
		final Peer.BlockchainState state = Constants.isLightClient ? Peer.BlockchainState.LIGHT_CLIENT
				: (Nxt.getBlockchainProcessor().isDownloading()
						|| (Nxt.getBlockchain().getLastBlockTimestamp() < (Nxt.getEpochTime() - 600)))
								? Peer.BlockchainState.DOWNLOADING
								: (((Nxt.getBlockchain().getLastBlock().getBaseTarget()
										/ Constants.INITIAL_BASE_TARGET) > 10) && !Constants.isTestnet)
												? Peer.BlockchainState.FORK : Peer.BlockchainState.UP_TO_DATE;
		if (state != Peers.currentBlockchainState) {
			final JSONObject json = new JSONObject(Peers.myPeerInfo);
			json.put("blockchainState", state.ordinal());
			Peers.myPeerInfoResponse = JSON.prepare(json);
			json.put("requestType", "getInfo");
			Peers.myPeerInfoRequest = JSON.prepareRequest(json);
			Peers.currentBlockchainState = state;
		}
	}

	public static void connectPeer(final Peer peer) {
		peer.unBlacklist();
		((PeerImpl) peer).connect();
	}

	static PeerImpl findOrCreatePeer(final InetAddress inetAddress, final String announcedAddress,
			final boolean create) {

		if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
			return null;
		}

		String host = inetAddress.getHostAddress();
		if (Peers.cjdnsOnly && !host.substring(0, 2).equals("fc")) {
			return null;
		}
		// re-add the [] to ipv6 addresses lost in getHostAddress() above
		if (host.split(":").length > 2) {
			host = "[" + host + "]";
		}

		PeerImpl peer;
		if ((peer = Peers.peers.get(host)) != null) {
			return peer;
		}
		if (!create) {
			return null;
		}

		if ((Peers.myAddress != null) && Peers.myAddress.equalsIgnoreCase(announcedAddress)) {
			return null;
		}
		if ((announcedAddress != null) && (announcedAddress.length() > Peers.MAX_ANNOUNCED_ADDRESS_LENGTH)) {
			return null;
		}
		peer = new PeerImpl(host, announcedAddress);
		if (Constants.isTestnet && (peer.getPort() != Peers.TESTNET_PEER_PORT)) {
			Logger.logDebugMessage(
					"Peer " + host + " on testnet is not using port " + Peers.TESTNET_PEER_PORT + ", ignoring");
			return null;
		}
		if (!Constants.isTestnet && (peer.getPort() == Peers.TESTNET_PEER_PORT)) {
			Logger.logDebugMessage("Peer " + host + " is using testnet port " + peer.getPort() + ", ignoring");
			return null;
		}
		return peer;
	}

	static PeerImpl findOrCreatePeer(final String host) {
		try {
			final InetAddress inetAddress = InetAddress.getByName(host);
			return Peers.findOrCreatePeer(inetAddress, null, true);
		} catch (final UnknownHostException e) {
			return null;
		}
	}

	public static PeerImpl findOrCreatePeer(String announcedAddress, final boolean create) {
		if (announcedAddress == null) {
			return null;
		}
		announcedAddress = announcedAddress.trim().toLowerCase();
		PeerImpl peer;
		if ((peer = Peers.peers.get(announcedAddress)) != null) {
			return peer;
		}
		String host = Peers.selfAnnouncedAddresses.get(announcedAddress);
		if ((host != null) && ((peer = Peers.peers.get(host)) != null)) {
			return peer;
		}
		try {
			final URI uri = new URI("http://" + announcedAddress);
			host = uri.getHost();
			if (host == null) {
				return null;
			}
			if ((peer = Peers.peers.get(host)) != null) {
				return peer;
			}
			final String host2 = Peers.selfAnnouncedAddresses.get(host);
			if ((host2 != null) && ((peer = Peers.peers.get(host2)) != null)) {
				return peer;
			}
			final InetAddress inetAddress = InetAddress.getByName(host);
			return Peers.findOrCreatePeer(inetAddress, Peers.addressWithPort(announcedAddress), create);
		} catch (URISyntaxException | UnknownHostException e) {
			// Logger.logDebugMessage("Invalid peer address: " +
			// announcedAddress + ", " + e.toString());
			return null;
		}
	}


	public static List<Peer> getActivePeers() {
		return Peers.getPeers(peer -> peer.getState() != Peer.State.NON_CONNECTED);
	}

	public static List<Peer> getActiveSnPeers() {
		return Peers.getSnPeers(peer -> peer.getState() != Peer.State.NON_CONNECTED);
	}

	// The following two are implemented very shittily, but it works
	public static Collection<? extends Peer> getAllPeers() {
		return Peers.getPeers(peer -> peer.getState() != Peer.State.NON_CONNECTED || peer.getState() == Peer.State.NON_CONNECTED);
	}

	public static Collection<? extends Peer> getAllSNPeers() {
		return Peers.getSnPeers(peer -> peer.getState() != Peer.State.NON_CONNECTED || peer.getState() == Peer.State.NON_CONNECTED);
	}

	public static Peer getAnyPeer(final Peer.State state, final boolean applyPullThreshold) {
		return Peers.getWeightedPeer(Peers.getPublicPeers(state, applyPullThreshold));
	}

	public static int getDefaultPeerPort() {
		return Constants.isTestnet ? Peers.TESTNET_PEER_PORT : Peers.DEFAULT_PEER_PORT;
	}

	public static List<Peer> getInboundPeers() {
		return Peers.getPeers(Peer::isInbound);
	}

	public static JSONStreamAware getMyPeerInfoRequest() {
		Peers.checkBlockchainState();
		return Peers.myPeerInfoRequest;
	}

	public static JSONStreamAware getMyPeerInfoResponse() {
		Peers.checkBlockchainState();
		return Peers.myPeerInfoResponse;
	}

	public static Peer getPeer(final String host) {
		return Peers.peers.get(host);
	}

	public static List<Peer> getPeers(final Filter<Peer> filter) {
		return Peers.getPeers(filter, Integer.MAX_VALUE);
	}

	public static List<Peer> getPeers(final Filter<Peer> filter, final int limit) {
		final List<Peer> result = new ArrayList<>();
		for (final Peer peer : Peers.peers.values()) {
			if(peer.isSupernode()) continue;
			if (filter.ok(peer)) {
				result.add(peer);
				if (result.size() >= limit) {
					break;
				}
			}
		}
		return result;
	}

	public static List<Peer> getSnPeers(final Filter<Peer> filter) {
		return Peers.getSnPeers(filter, Integer.MAX_VALUE);
	}

	public static List<Peer> getSnPeers(final Filter<Peer> filter, final int limit) {
		final List<Peer> result = new ArrayList<>();
		for (final Peer peer : Peers.peers.values()) {
			if(!peer.isSupernode()) continue;
			if (filter.ok(peer)) {
				result.add(peer);
				if (result.size() >= limit) {
					break;
				}
			}
		}
		return result;
	}


	public static List<String> getPotentialSNPeersInternal(){
		List<String> rslt = new ArrayList<String>();
		try (DbIterator<? extends Account.AccountSupernodeDeposit> iterator = Account.getActiveSupernodes(Nxt.getBlockchain().getHeight());) {
			while (iterator.hasNext()) {
				final Account.AccountSupernodeDeposit b = iterator.next();
				for (String u : b.getUris()) {
					rslt.add(u);
				}
			}
		}
		Collections.shuffle(rslt);
		return rslt;
	}

	public static List<String> getPotentialSNPeers(){
		if(Peers.cachedSnPeers == null || Peers.lastCacheTime < Nxt.getBlockchain().getHeight()) {
			Peers.cachedSnPeers = Peers.getPotentialSNPeersInternal();
			Peers.lastCacheTime = Nxt.getBlockchain().getHeight();
		}
		return Peers.cachedSnPeers;
	}

	public static List<Peer> getPeers(final Peer.State state) {
		return Peers.getPeers(peer -> peer.getState() == state);
	}

	public static List<Peer> getSnPeers(final Peer.State state) {
		return Peers.getSnPeers(peer -> peer.getState() == state);
	}

	public static List<Peer> getPublicPeers(final Peer.State state, final boolean applyPullThreshold) {
		return Peers.getPeers(peer -> !peer.isBlacklisted() && (peer.getState() == state)
				&& (peer.getAnnouncedAddress() != null) && (!applyPullThreshold || !Peers.enableHallmarkProtection
						|| (peer.getWeight() >= Peers.pullThreshold)));
	}

	/**
	 * Return local peer services
	 *
	 * @return List of local peer services
	 */
	public static List<Peer.Service> getServices() {
		return Peers.myServices;
	}

	public static Peer getWeightedPeer(final List<Peer> selectedPeers) {
		if (selectedPeers.isEmpty()) {
			return null;
		}
		if (!Peers.enableHallmarkProtection || (ThreadLocalRandom.current().nextInt(3) == 0)) {
			return selectedPeers.get(ThreadLocalRandom.current().nextInt(selectedPeers.size()));
		}
		long totalWeight = 0;
		for (final Peer peer : selectedPeers) {
			long weight = peer.getWeight();
			if (weight == 0) {
				weight = 1;
			}
			totalWeight += weight;
		}
		long hit = ThreadLocalRandom.current().nextLong(totalWeight);
		for (final Peer peer : selectedPeers) {
			long weight = peer.getWeight();
			if (weight == 0) {
				weight = 1;
			}
			if ((hit -= weight) < 0) {
				return peer;
			}
		}
		return null;
	}

	private static boolean hasEnoughConnectedPublicPeers(final int limit) {
		return Peers.getPeers(peer -> !peer.isBlacklisted() && (peer.getState() == Peer.State.CONNECTED)
				&& (peer.getAnnouncedAddress() != null) && (!Peers.enableHallmarkProtection || (peer.getWeight() > 0)),
				limit).size() >= limit;
	}

	public static List<Peer> getConnectedSnPeers(){
		List<Peer> sn = Peers.getSnPeers(peer -> !peer.isBlacklisted() && (peer.getState() == Peer.State.CONNECTED));
		Collections.shuffle(sn);
		return sn;
		// We shuffle to achieve some sort of "load balancing"
	}

	public static boolean hasConnectedSnPeers(final int limit) {
		return Peers.getSnPeers(peer -> !peer.isBlacklisted() && (peer.getState() == Peer.State.CONNECTED),
				limit).size()>0;
	}

	private static boolean hasEnoughSupernodePeers(final int limit) {
		return Peers.getSnPeers(peer -> !peer.isBlacklisted() && (peer.getState() == Peer.State.CONNECTED),
				limit).size() >= limit;
	}

	public static boolean hasTooFewKnownPeers() {
		// Ugly hack (to mix SN and NON-SN Peers)
		return Peers.getPeers(peer -> !peer.isBlacklisted() || peer.isBlacklisted()).size() < Peers.minNumberOfKnownPeers;
	}

	public static boolean hasTooManyInboundPeers() {
		// This check is only active if we are NOT a supernode
		if(Nxt.isSupernode) return false;

		return Peers.getPeers(Peer::isInbound, Peers.maxNumberOfInboundConnections)
				.size() >= Peers.maxNumberOfInboundConnections;
	}

	public static boolean hasTooManyKnownPeers() {
		// Dirty hack
		return Peers.getPeers(peer -> !peer.isBlacklisted() || peer.isBlacklisted()).size() > Peers.maxNumberOfKnownPeers;
	}

	public static boolean hasTooManyOutboundConnections() {
		return Peers
				.getPeers(peer -> !peer.isBlacklisted() && (peer.getState() == Peer.State.CONNECTED)
						&& (peer.getAnnouncedAddress() != null), Peers.maxNumberOfOutboundConnections)
				.size() >= Peers.maxNumberOfOutboundConnections;
	}

	public static void init() {
		Init.init();
	}

	public static boolean isNewVersion(String version) {
		if (version == null) {
			return true;
		}
		if (version.endsWith("e")) {
			version = version.substring(0, version.length() - 1);
		}
		final String[] versions = version.split("\\.");
		for (int i = 0; (i < Peers.MAX_VERSION.length) && (i < versions.length); i++) {
			try {
				final int v = Integer.parseInt(versions[i]);
				if (v > Peers.MAX_VERSION[i]) {
					return true;
				} else if (v < Peers.MAX_VERSION[i]) {
					return false;
				}
			} catch (final NumberFormatException e) {
				return true;
			}
		}
		return versions.length > Peers.MAX_VERSION.length;
	}

	public static boolean isOldVersion(String version, final int[] minVersion) {
		if (version == null) {
			return true;
		}
		if (version.endsWith("e")) {
			version = version.substring(0, version.length() - 1);
		}
		final String[] versions = version.split("\\.");
		for (int i = 0; (i < minVersion.length) && (i < versions.length); i++) {
			try {
				final int v = Integer.parseInt(versions[i]);
				if (v > minVersion[i]) {
					return false;
				} else if (v < minVersion[i]) {
					return true;
				}
			} catch (final NumberFormatException e) {
				return true;
			}
		}
		return versions.length < minVersion.length;
	}

	static void notifyListeners(final Peer peer, final Event eventType) {
		Peers.listeners.notify(peer, eventType);
	}

	public static boolean removeListener(final Listener<Peer> listener, final Event eventType) {
		return Peers.listeners.removeListener(listener, eventType);
	}

	public static PeerImpl removePeer(final Peer peer) {
		if (peer.getAnnouncedAddress() != null) {
			Peers.selfAnnouncedAddresses.remove(peer.getAnnouncedAddress());
		}
		return Peers.peers.remove(peer.getHost());
	}

	public static void sendToSomePeers(final Block block) {
		final JSONObject request = block.getJSONObject();
		request.put("requestType", "processBlock");
		Peers.sendToSomePeers(request);
	}

	private static void sendToSomePeers(final JSONObject request) {
		Peers.sendingService.submit(() -> {
			final JSONStreamAware jsonRequest = JSON.prepareRequest(request);

			int successful = 0;
			final List<Future<JSONObject>> expectedResponses = new ArrayList<>();
			for (final Peer peer : Peers.peers.values()) {

				if (Peers.enableHallmarkProtection && (peer.getWeight() < Peers.pushThreshold)) {
					continue;
				}

				if (!peer.isBlacklisted() && (peer.getState() == Peer.State.CONNECTED)
						&& (peer.getAnnouncedAddress() != null)
						&& (peer.getBlockchainState() != Peer.BlockchainState.LIGHT_CLIENT)) {
					final Future<JSONObject> futureResponse = Peers.peersService.submit(() -> peer.send(jsonRequest));
					expectedResponses.add(futureResponse);
				}
				if (expectedResponses.size() >= (Peers.sendToPeersLimit - successful)) {
					for (final Future<JSONObject> future : expectedResponses) {
						try {
							final JSONObject response = future.get();
							if ((response != null) && (response.get("error") == null)) {
								successful += 1;
							}
						} catch (final InterruptedException e) {
							Thread.currentThread().interrupt();
						} catch (final ExecutionException e) {
							Logger.logDebugMessage("Error in sendToSomePeers", e);
						}

					}
					expectedResponses.clear();
				}
				if (successful >= Peers.sendToPeersLimit) {
					return;
				}
			}
		});
	}

	private static void sendToSomeSnPeers(final JSONObject request) {
		Peers.sendingService.submit(() -> {
			final JSONStreamAware jsonRequest = JSON.prepareRequest(request);

			int successful = 0;
			final List<Future<JSONObject>> expectedResponses = new ArrayList<>();
			for (final Peer peer : Peers.getConnectedSnPeers()) {
				if (!peer.isBlacklisted() && (peer.getState() == Peer.State.CONNECTED)
						&& (peer.getAnnouncedAddress() != null)) {
					final Future<JSONObject> futureResponse = Peers.peersService.submit(() -> peer.send(jsonRequest));
					expectedResponses.add(futureResponse);
				}

				if (expectedResponses.size() >= (Peers.sendToSnPeersLimit - successful)) {
					for (final Future<JSONObject> future : expectedResponses) {
						try {
							final JSONObject response = future.get();
							if ((response != null) && (response.get("error") == null)) {
								successful += 1;
							}
						} catch (final InterruptedException e) {
							Thread.currentThread().interrupt();
						} catch (final ExecutionException e) {
							Logger.logDebugMessage("Error in sendToSomePeers", e);
						}
					}
					expectedResponses.clear();
				}
				if (successful >= Peers.sendToSnPeersLimit) {
					return;
				}
			}
		});
	}

	public static void sendToSomePeers(final List<? extends Transaction> transactions) {
		int nextBatchStart = 0;
		while (nextBatchStart < transactions.size()) {
			final JSONObject request = new JSONObject();
			final JSONArray transactionsData = new JSONArray();
			for (int i = nextBatchStart; (i < (nextBatchStart + Peers.sendTransactionsBatchSize))
					&& (i < transactions.size()); i++) {
				transactionsData.add(transactions.get(i).getJSONObject());
			}
			request.put("requestType", "processTransactions");
			request.put("transactions", transactionsData);
			Peers.sendToSomePeers(request);
			nextBatchStart += Peers.sendTransactionsBatchSize;
		}
	}

	public static void sendToSomeSnPeers(final List<? extends Transaction> transactions) {
		int nextBatchStart = 0;
		while (nextBatchStart < transactions.size()) {
			final JSONObject request = new JSONObject();
			final JSONArray transactionsData = new JSONArray();
			for (int i = nextBatchStart; (i < (nextBatchStart + Peers.sendTransactionsBatchSize))
					&& (i < transactions.size()); i++) {
				transactionsData.add(transactions.get(i).getJSONObject());
			}
			request.put("requestType", "processSupernodeTransactions");
			request.put("transactions", transactionsData);
			Peers.sendToSomeSnPeers(request);
			nextBatchStart += Peers.sendTransactionsBatchSize;
		}
	}

	static void setAnnouncedAddress(final PeerImpl peer, final String newAnnouncedAddress) {
		Peer oldPeer = Peers.peers.get(peer.getHost());
		if (oldPeer != null) {
			final String oldAnnouncedAddress = oldPeer.getAnnouncedAddress();
			if ((oldAnnouncedAddress != null) && !oldAnnouncedAddress.equals(newAnnouncedAddress)) {
				Logger.logDebugMessage(
						"Removing old announced address " + oldAnnouncedAddress + " for peer " + oldPeer.getHost());
				Peers.selfAnnouncedAddresses.remove(oldAnnouncedAddress);
			}
		}
		if (newAnnouncedAddress != null) {
			final String oldHost = Peers.selfAnnouncedAddresses.put(newAnnouncedAddress, peer.getHost());
			if ((oldHost != null) && !peer.getHost().equals(oldHost)) {
				Logger.logDebugMessage("Announced address " + newAnnouncedAddress + " now maps to peer "
						+ peer.getHost() + ", removing old peer " + oldHost);
				oldPeer = Peers.peers.remove(oldHost);
				if (oldPeer != null) {
					Peers.notifyListeners(oldPeer, Event.REMOVE);
				}
			}
		}
		peer.setAnnouncedAddress(newAnnouncedAddress);
	}

	/**
	 * Set the communication logging mask
	 *
	 * @param events
	 *            Communication event list or null to reset communications
	 *            logging
	 * @return TRUE if the communication logging mask was updated
	 */
	public static boolean setCommunicationLoggingMask(final String[] events) {
		boolean updated = true;
		int mask = 0;
		if (events != null) {
			for (final String event : events) {
				switch (event) {
				case "EXCEPTION":
					mask |= Peers.LOGGING_MASK_EXCEPTIONS;
					break;
				case "HTTP-ERROR":
					mask |= Peers.LOGGING_MASK_NON200_RESPONSES;
					break;
				case "HTTP-OK":
					mask |= Peers.LOGGING_MASK_200_RESPONSES;
					break;
				default:
					updated = false;
				}
				if (!updated) {
					break;
				}
			}
		}
		if (updated) {
			Peers.communicationLoggingMask = mask;
		}
		return updated;
	}

	public static void shutdown() {
		if (Init.peerServer != null) {
			try {
				Init.peerServer.stop();
				if (Peers.enablePeerUPnP) {
					final Connector[] peerConnectors = Init.peerServer.getConnectors();
					for (final Connector peerConnector : peerConnectors) {
						if (peerConnector instanceof ServerConnector) {
							UPnP.deletePort(((ServerConnector) peerConnector).getPort());
						}
					}
				}
			} catch (final Exception e) {
				Logger.logShutdownMessage("Failed to stop peer server", e);
			}
		}
		ThreadPool.shutdownExecutor("sendingService", Peers.sendingService, 2);
		ThreadPool.shutdownExecutor("peersService", Peers.peersService, 5);
	}

	private Peers() {
	} // never

}

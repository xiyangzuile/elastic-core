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

package nxt.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import nxt.Constants;
import nxt.Nxt;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Logger;
import nxt.util.ThreadPool;

public class APIProxy {
	private static final APIProxy instance = new APIProxy();

	static final boolean enableAPIProxy = Constants.isLightClient ||
			(Nxt.getBooleanProperty("nxt.enableAPIProxy") && (API.openAPIPort == 0) && (API.openAPISSLPort == 0));
	private static final int blacklistingPeriod = Nxt.getIntProperty("nxt.apiProxyBlacklistingPeriod") / 1000;
	static final String forcedServerURL = Nxt.getStringProperty("nxt.forceAPIProxyServerURL", "");

	private static final Runnable peersUpdateThread = () -> {
		final int curTime = Nxt.getEpochTime();
		APIProxy.instance.blacklistedPeers.entrySet().removeIf((entry) -> {
			if (entry.getValue() < curTime) {
				Logger.logDebugMessage("Unblacklisting API peer " + entry.getKey());
				return true;
			}
			return false;
		});
		final List<String> currentPeersHosts = APIProxy.instance.peersHosts;
		if (currentPeersHosts != null) {
			for (final String host : currentPeersHosts) {
				final Peer peer = Peers.getPeer(host);
				if (peer != null) {
					Peers.connectPeer(peer);
				}
			}
		}
	};
	static {
		if (!Constants.isOffline && APIProxy.enableAPIProxy) {
			ThreadPool.scheduleThread("APIProxyPeersUpdate", APIProxy.peersUpdateThread, 60);
		}
	}
	public static APIProxy getInstance() {
		return APIProxy.instance;
	}

	public static void init() {}

	static boolean isActivated() {
		return Constants.isLightClient || (APIProxy.enableAPIProxy && Nxt.getBlockchainProcessor().isDownloading());
	}

	private volatile String forcedPeerHost;

	private volatile List<String> peersHosts = Collections.emptyList();

	private volatile String mainPeerAnnouncedAddress;

	private final ConcurrentHashMap<String, Integer> blacklistedPeers = new ConcurrentHashMap<>();

	private APIProxy() {

	}

	void blacklistHost(final String host) {
		this.blacklistedPeers.put(host, Nxt.getEpochTime() + APIProxy.blacklistingPeriod);
		if (this.peersHosts.contains(host)) {
			this.peersHosts = Collections.emptyList();
			this.getServingPeer(null);
		}
	}

	String getMainPeerAnnouncedAddress() {
		// The first client request GetBlockchainState is handled by the server
		// Not by the proxy. In order to report a peer to the client we have
		// To select some initial peer.
		if (this.mainPeerAnnouncedAddress == null) {
			final Peer peer = this.getServingPeer(null);
			if (peer != null) {
				this.mainPeerAnnouncedAddress = peer.getAnnouncedAddress();
			}
		}
		return this.mainPeerAnnouncedAddress;
	}

	private Peer getRandomAPIPeer(final List<Peer> peers) {
		if (peers.isEmpty()) {
			return null;
		}
		final int index = ThreadLocalRandom.current().nextInt(peers.size());
		return peers.remove(index);
	}

	Peer getServingPeer(final String requestType) {
		if (this.forcedPeerHost != null) {
			return Peers.getPeer(this.forcedPeerHost);
		}

		final APIEnum requestAPI = APIEnum.fromName(requestType);
		if (!this.peersHosts.isEmpty()) {
			for (final String host : this.peersHosts) {
				final Peer peer = Peers.getPeer(host);
				if ((peer != null) && peer.isApiConnectable() && !peer.getDisabledAPIs().contains(requestAPI)) {
					return peer;
				}
			}
		}

		final List<Peer> connectablePeers = Peers.getPeers(p -> p.isApiConnectable() && !this.blacklistedPeers.containsKey(p.getHost()));
		if (connectablePeers.isEmpty()) {
			return null;
		}
		// subset of connectable peers that have at least one new API enabled, which was disabled for the
		// The first peer (element 0 of peersHosts) is chosen at random. Next peers are chosen randomly from a
		// previously chosen peers. In worst case the size of peersHosts will be the number of APIs
		Peer peer = this.getRandomAPIPeer(connectablePeers);
		if (peer == null) {
			return null;
		}

		Peer resultPeer = null;
		final List<String> currentPeersHosts = new ArrayList<>();
		final EnumSet<APIEnum> disabledAPIs = EnumSet.noneOf(APIEnum.class);
		currentPeersHosts.add(peer.getHost());
		this.mainPeerAnnouncedAddress = peer.getAnnouncedAddress();
		if (!peer.getDisabledAPIs().contains(requestAPI)) {
			resultPeer = peer;
		}
		while (!disabledAPIs.isEmpty() && !connectablePeers.isEmpty()) {
			// remove all peers that do not introduce new enabled APIs
			connectablePeers.removeIf(p -> p.getDisabledAPIs().containsAll(disabledAPIs));
			peer = this.getRandomAPIPeer(connectablePeers);
			if (peer != null) {
				currentPeersHosts.add(peer.getHost());
				if (!peer.getDisabledAPIs().contains(requestAPI)) {
					resultPeer = peer;
				}
				disabledAPIs.retainAll(peer.getDisabledAPIs());
			}
		}
		this.peersHosts = Collections.unmodifiableList(currentPeersHosts);
		Logger.logInfoMessage("Selected API peer " + resultPeer + " peer hosts selected " + currentPeersHosts);
		return resultPeer;
	}

	Peer setForcedPeer(final Peer peer) {
		if (peer != null) {
			this.forcedPeerHost = peer.getHost();
			this.mainPeerAnnouncedAddress = peer.getAnnouncedAddress();
			return peer;
		} else {
			this.forcedPeerHost = null;
			this.mainPeerAnnouncedAddress = null;
			return this.getServingPeer(null);
		}
	}
}

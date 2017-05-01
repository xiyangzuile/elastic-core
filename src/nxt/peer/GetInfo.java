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

package nxt.peer;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Nxt;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Logger;

import java.util.Objects;

final class GetInfo extends PeerServlet.PeerRequestHandler {

	static final GetInfo instance = new GetInfo();

	private static final JSONStreamAware INVALID_ANNOUNCED_ADDRESS;
	static {
		final JSONObject response = new JSONObject();
		response.put("error", Errors.INVALID_ANNOUNCED_ADDRESS);
		INVALID_ANNOUNCED_ADDRESS = JSON.prepare(response);
	}

	private GetInfo() {
	}

	@Override
	JSONStreamAware processRequest(final JSONObject request, final Peer peer) {
		final PeerImpl peerImpl = (PeerImpl) peer;
		peerImpl.setLastUpdated(Nxt.getEpochTime());
		final long origServices = peerImpl.getServices();
		final String servicesString = (String) request.get("services");
		peerImpl.setServices(servicesString != null ? Long.parseUnsignedLong(servicesString) : 0);
		peerImpl.analyzeHallmark((String) request.get("hallmark"));
		if (!Peers.ignorePeerAnnouncedAddress) {
			String announcedAddress = Convert.emptyToNull((String) request.get("announcedAddress"));
			if (announcedAddress != null) {
				announcedAddress = Peers.addressWithPort(announcedAddress.toLowerCase());
				if (announcedAddress != null) {
					/*if (!peerImpl.verifyAnnouncedAddress(announcedAddress)) {
						Logger.logDebugMessage("GetInfo: ignoring invalid announced address for " + peerImpl.getHost());
						if (!peerImpl.verifyAnnouncedAddress(peerImpl.getAnnouncedAddress())) {
							Logger.logDebugMessage(
									"GetInfo: old announced address for " + peerImpl.getHost() + " no longer valid");
							Peers.setAnnouncedAddress(peerImpl, null);
						}
						peerImpl.setState(Peer.State.NON_CONNECTED);
						return GetInfo.INVALID_ANNOUNCED_ADDRESS;
					}*/ // TODO, TODO TODO TODO
					if (!Objects.equals(announcedAddress, peerImpl.getAnnouncedAddress())) {
						Logger.logDebugMessage("GetInfo: peer " + peer.getHost() + " changed announced address from "
								+ peer.getAnnouncedAddress() + " to " + announcedAddress);
						final int oldPort = peerImpl.getPort();
						Peers.setAnnouncedAddress(peerImpl, announcedAddress);
                        // force checking connectivity to new announced port
                        if (peerImpl.getPort() != oldPort) peerImpl.setState(Peer.State.NON_CONNECTED);
					}
				} else Peers.setAnnouncedAddress(peerImpl, null);
			}
		}
		String application = (String) request.get("application");
		if (application == null) application = "?";
		peerImpl.setApplication(application.trim());

		String version = (String) request.get("version");
		if (version == null) version = "?";
		peerImpl.setVersion(version.trim());

		String platform = (String) request.get("platform");
		if (platform == null) platform = "?";
		peerImpl.setPlatform(platform.trim());

		peerImpl.setShareAddress(Objects.equals(Boolean.TRUE, request.get("shareAddress")));

		peerImpl.setApiPort(request.get("apiPort"));
		peerImpl.setApiSSLPort(request.get("apiSSLPort"));
		peerImpl.setDisabledAPIs(request.get("disabledAPIs"));
		peerImpl.setApiServerIdleTimeout(request.get("apiServerIdleTimeout"));
		peerImpl.setBlockchainState(request.get("blockchainState"));

		if (peerImpl.getServices() != origServices) Peers.notifyListeners(peerImpl, Peers.Event.CHANGED_SERVICES);

		return Peers.getMyPeerInfoResponse();

	}

	@Override
	boolean rejectWhileDownloading() {
		return false;
	}

}

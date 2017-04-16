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

package nxt.http;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;

public final class GetPeers extends APIServlet.APIRequestHandler {

	static final GetPeers instance = new GetPeers();

	private GetPeers() {
		super(new APITag[] { APITag.NETWORK }, "active", "state", "service", "service", "service", "includePeerInfo");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		final boolean active = "true".equalsIgnoreCase(req.getParameter("active"));
		final String stateValue = Convert.emptyToNull(req.getParameter("state"));
		final String[] serviceValues = req.getParameterValues("service");
		final boolean includePeerInfo = "true".equalsIgnoreCase(req.getParameter("includePeerInfo"));
		Peer.State state;
		if (stateValue != null) try {
            state = Peer.State.valueOf(stateValue);
        } catch (final RuntimeException exc) {
            return JSONResponses.incorrect("state", "- '" + stateValue + "' is not defined");
        }
        else state = null;
		long serviceCodes = 0;
		if (serviceValues != null) for (final String serviceValue : serviceValues)
            try {
                serviceCodes |= Peer.Service.valueOf(serviceValue).getCode();
            } catch (final RuntimeException exc) {
                return JSONResponses.incorrect("service", "- '" + serviceValue + "' is not defined");
            }


		final Collection<? extends Peer> peers = active ? Peers.getActivePeers()
				: state != null ? Peers.getPeers(state) : Peers.getAllPeers();
		final JSONArray peersJSON = new JSONArray();
		if (serviceCodes != 0) {
			final long services = serviceCodes;
			if (includePeerInfo) peers.forEach(peer -> {
                if (peer.providesServices(services) && !peer.isSupernode()) peersJSON.add(JSONData.peer(peer));
            });
            else peers.forEach(peer -> {
                if (peer.providesServices(services) && !peer.isSupernode()) peersJSON.add(peer.getHost());
            });
		} else if (includePeerInfo) peers.forEach(peer -> {
            if (!peer.isSupernode()) peersJSON.add(JSONData.peer(peer));
        });
        else peers.forEach(peer -> {
                if (!peer.isSupernode()) peersJSON.add(peer.getHost());
            });

		// Also add all SN peers to the list
		final Collection<? extends Peer> peers_sn = active ? Peers.getActiveSnPeers()
				: state != null ? Peers.getSnPeers(state) : Peers.getAllSNPeers();
		if (serviceCodes != 0) {
			final long services = serviceCodes;
			if (includePeerInfo) peers_sn.forEach(peer -> {
                if (peer.providesServices(services)) peersJSON.add(JSONData.peer(peer));
            });
            else peers_sn.forEach(peer -> {
                if (peer.providesServices(services)) peersJSON.add(peer.getHost());
            });
		} else if (includePeerInfo) peers_sn.forEach(peer -> peersJSON.add(JSONData.peer(peer)));
        else peers_sn.forEach(peer -> peersJSON.add(peer.getHost()));



		final JSONObject response = new JSONObject();
		response.put("peers", peersJSON);
		return response;
	}

}

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

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
import nxt.http.APIServlet.APIRequestHandler;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;

public class AddPeer extends APIRequestHandler {

	static final AddPeer instance = new AddPeer();

	private AddPeer() {
		super(new APITag[] {APITag.NETWORK}, "peer");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest request)
			throws NxtException {
		final String peerAddress = Convert.emptyToNull(request.getParameter("peer"));
		if (peerAddress == null) {
			return JSONResponses.MISSING_PEER;
		}
		JSONObject response = new JSONObject();
		final Peer peer = Peers.findOrCreatePeer(peerAddress, true);
		if (peer != null) {
			final boolean isNewlyAdded = Peers.addPeer(peer, peerAddress);
			Peers.connectPeer(peer);
			response = JSONData.peer(peer);
			response.put("isNewlyAdded", isNewlyAdded);
		} else {
			response.put("errorCode", 8);
			response.put("errorDescription", "Failed to add peer");
		}
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

	@Override
	protected boolean requirePassword() {
		return true;
	}

	@Override
	protected final boolean requirePost() {
		return true;
	}

}

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

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
import nxt.http.APIServlet.APIRequestHandler;
import nxt.peer.Peer;
import nxt.peer.Peers;

public class BlacklistPeer extends APIRequestHandler {

	static final BlacklistPeer instance = new BlacklistPeer();

	private BlacklistPeer() {
		super(new APITag[] { APITag.NETWORK }, "peer");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest request) throws NxtException {
		final JSONObject response = new JSONObject();

		final String peerAddress = request.getParameter("peer");
		if (peerAddress == null) return JSONResponses.MISSING_PEER;
		final Peer peer = Peers.findOrCreatePeer(peerAddress, true);
		if (peer == null) return JSONResponses.UNKNOWN_PEER;
        else {
			Peers.addPeer(peer);
			peer.blacklist("Manual blacklist");
			response.put("done", true);
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

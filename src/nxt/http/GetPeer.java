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

import org.json.simple.JSONStreamAware;

import nxt.peer.Peer;
import nxt.peer.Peers;

public final class GetPeer extends APIServlet.APIRequestHandler {

	static final GetPeer instance = new GetPeer();

	private GetPeer() {
		super(new APITag[] { APITag.NETWORK }, "peer");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		final String peerAddress = req.getParameter("peer");
		if (peerAddress == null) return JSONResponses.MISSING_PEER;

		final Peer peer = Peers.findOrCreatePeer(peerAddress, false);
		if (peer == null) return JSONResponses.UNKNOWN_PEER;

		return JSONData.peer(peer);

	}

}

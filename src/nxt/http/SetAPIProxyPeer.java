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

import nxt.NxtException;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;

public class SetAPIProxyPeer extends APIServlet.APIRequestHandler {

	static final SetAPIProxyPeer instance = new SetAPIProxyPeer();

	private SetAPIProxyPeer() {
		super(new APITag[] { APITag.NETWORK }, "peer");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest request) throws NxtException {
		final String peerAddress = Convert.emptyToNull(request.getParameter("peer"));
		if (peerAddress == null) {
			final Peer peer = APIProxy.getInstance().setForcedPeer(null);
			return JSONData.peer(peer);
		}
		final Peer peer = Peers.findOrCreatePeer(peerAddress, false);
		if (peer == null) return JSONResponses.UNKNOWN_PEER;
		if (peer.getState() != Peer.State.CONNECTED) return JSONResponses.PEER_NOT_CONNECTED;
		if (!peer.isOpenAPI()) return JSONResponses.PEER_NOT_OPEN_API;
		APIProxy.getInstance().setForcedPeer(peer);
		return JSONData.peer(peer);
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
	protected boolean requirePost() {
		return true;
	}

}

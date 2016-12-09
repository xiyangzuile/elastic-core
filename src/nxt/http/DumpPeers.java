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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Constants;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;
import nxt.util.Logger;

public final class DumpPeers extends APIServlet.APIRequestHandler {

	static final DumpPeers instance = new DumpPeers();

	private DumpPeers() {
		super(new APITag[] { APITag.DEBUG }, "version", "weight", "connect", "adminPassword");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final String version = Convert.nullToEmpty(req.getParameter("version"));
		final int weight = ParameterParser.getInt(req, "weight", 0, (int) Constants.MAX_BALANCE_NXT, false);
		final boolean connect = "true".equalsIgnoreCase(req.getParameter("connect")) && API.checkPassword(req);
		if (connect) {
			final List<Callable<Object>> connects = new ArrayList<>();
			Peers.getAllPeers().forEach(peer -> connects.add(() -> {
				Peers.connectPeer(peer);
				return null;
			}));
			final ExecutorService service = Executors.newFixedThreadPool(10);
			try {
				service.invokeAll(connects);
			} catch (final InterruptedException e) {
				Logger.logMessage(e.toString(), e);
			}
		}
		final Set<String> addresses = new HashSet<>();
		Peers.getAllPeers().forEach(peer -> {
			if ((peer.getState() == Peer.State.CONNECTED) && peer.shareAddress() && !peer.isBlacklisted()
					&& (peer.getVersion() != null) && peer.getVersion().startsWith(version)
					&& ((weight == 0) || (peer.getWeight() > weight))) {
				addresses.add(peer.getAnnouncedAddress());
			}
		});
		final StringBuilder buf = new StringBuilder();
		for (final String address : addresses) {
			buf.append(address).append("; ");
		}
		final JSONObject response = new JSONObject();
		response.put("peers", buf.toString());
		response.put("count", addresses.size());
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

	@Override
	protected final boolean requirePost() {
		return true;
	}

}

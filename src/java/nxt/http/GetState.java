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

import java.net.InetAddress;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Constants;
import nxt.Generator;
import nxt.GigaflopEstimator;
import nxt.Nxt;
import nxt.PrunableSourceCode;
import nxt.Work;
import nxt.peer.Peers;
import nxt.util.UPnP;

public final class GetState extends APIServlet.APIRequestHandler {

	static final GetState instance = new GetState();

	private GetState() {
		super(new APITag[] {APITag.INFO}, "includeCounts", "adminPassword");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		final JSONObject response = GetBlockchainStatus.instance.processRequest(req);

		if ("true".equalsIgnoreCase(req.getParameter("includeCounts")) && API.checkPassword(req)) {
			response.put("numberOfTransactions", Nxt.getBlockchain().getTransactionCount());
			response.put("numberOfAccounts", Account.getCount());
			response.put("numberOfPrunableSourceCodes", PrunableSourceCode.getCount());
			response.put("numberOfAccountLeases", Account.getAccountLeaseCount());
			response.put("numberOfActiveAccountLeases", Account.getActiveLeaseCount());
		}
		response.put("numberOfPeers", Peers.getAllPeers().size());
		response.put("numberOfActivePeers", Peers.getActivePeers().size());
		response.put("numberOpenWorks", Work.getActiveCount());
		response.put("numberClosedWorks", Work.getCount()-Work.getActiveCount());
		response.put("estimatedComputationPower", GigaflopEstimator.estimateText());
		response.put("openWorkMoney", Work.getActiveMoney());

		response.put("numberOfUnlockedAccounts", Generator.getAllGenerators().size());
		response.put("availableProcessors", Runtime.getRuntime().availableProcessors());
		response.put("maxMemory", Runtime.getRuntime().maxMemory());
		response.put("totalMemory", Runtime.getRuntime().totalMemory());
		response.put("freeMemory", Runtime.getRuntime().freeMemory());
		response.put("peerPort", Peers.getDefaultPeerPort());
		response.put("isOffline", Constants.isOffline);
		response.put("needsAdminPassword", !API.disableAdminPassword);
		final InetAddress externalAddress = UPnP.getExternalAddress();
		if (externalAddress != null) {
			response.put("upnpExternalAddress", externalAddress.getHostAddress());
		}
		return response;
	}

}

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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import nxt.AccountLedger;
import nxt.Block;
import nxt.BlockchainProcessor;
import nxt.Constants;
import nxt.Nxt;
import nxt.peer.Peer;
import nxt.peer.Peers;

public final class GetBlockchainStatus extends APIServlet.APIRequestHandler {

	static final GetBlockchainStatus instance = new GetBlockchainStatus();

	private GetBlockchainStatus() {
		super(new APITag[] { APITag.BLOCKS, APITag.INFO });
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONObject processRequest(final HttpServletRequest req) {
		final JSONObject response = new JSONObject();
		response.put("application", Nxt.APPLICATION);
		response.put("version", Nxt.VERSION);
		response.put("time", Nxt.getEpochTime());
		final Block lastBlock = Nxt.getBlockchain().getLastBlock();
		response.put("lastBlock", lastBlock.getStringId());
		response.put("cumulativeDifficulty", lastBlock.getCumulativeDifficulty().toString());
		response.put("numberOfBlocks", lastBlock.getHeight() + 1);
		final BlockchainProcessor blockchainProcessor = Nxt.getBlockchainProcessor();
		final Peer lastBlockchainFeeder = blockchainProcessor.getLastBlockchainFeeder();
		response.put("lastBlockchainFeeder",
				lastBlockchainFeeder == null ? null : lastBlockchainFeeder.getAnnouncedAddress());
		response.put("lastBlockchainFeederHeight", blockchainProcessor.getLastBlockchainFeederHeight());
		response.put("isScanning", blockchainProcessor.isScanning());
		response.put("isDownloading", blockchainProcessor.isDownloading());
		response.put("maxRollback", Constants.MAX_ROLLBACK);
		response.put("currentMinRollbackHeight", Nxt.getBlockchainProcessor().getMinRollbackHeight());
		response.put("isTestnet", Constants.isTestnet);
		response.put("maxPrunableLifetime", Constants.MAX_PRUNABLE_LIFETIME);
		response.put("includeExpiredPrunable", Constants.INCLUDE_EXPIRED_PRUNABLE);
		response.put("correctInvalidFees", Constants.correctInvalidFees);
		response.put("ledgerTrimKeep", AccountLedger.trimKeep);
		final JSONArray servicesArray = new JSONArray();
		Peers.getServices().forEach(service -> servicesArray.add(service.name()));
		response.put("services", servicesArray);
		if (APIProxy.isActivated()) {
			final String servingPeer = APIProxy.getInstance().getMainPeerAnnouncedAddress();
			response.put("apiProxy", true);
			response.put("apiProxyPeer", servingPeer);
		} else {
			response.put("apiProxy", false);
		}
		response.put("isLightClient", Constants.isLightClient);
		return response;
	}

}

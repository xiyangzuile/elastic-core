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

package nxt.peer;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.Nxt;

final class GetCumulativeDifficulty extends PeerServlet.PeerRequestHandler {

	static final GetCumulativeDifficulty instance = new GetCumulativeDifficulty();

	private GetCumulativeDifficulty() {
	}

	@Override
	JSONStreamAware processRequest(final JSONObject request, final Peer peer) {

		final JSONObject response = new JSONObject();

		final Block lastBlock = Nxt.getBlockchain().getLastBlock();
		response.put("cumulativeDifficulty", lastBlock.getCumulativeDifficulty().toString());
		response.put("blockchainHeight", lastBlock.getHeight());
		return response;
	}

	@Override
	boolean rejectWhileDownloading() {
		return true;
	}

}

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

import nxt.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.Nxt;
import nxt.NxtException;
import nxt.util.Convert;
import nxt.util.JSON;

final class ProcessBlock extends PeerServlet.PeerRequestHandler {

	static final ProcessBlock instance = new ProcessBlock();

	private ProcessBlock() {
	}

	@Override
	JSONStreamAware processRequest(final JSONObject request, final Peer peer) {
		final String previousBlockId = (String) request.get("previousBlock");
		final Block lastBlock = Nxt.getBlockchain().getLastBlock();
		Logger.logDebugMessage("Received block from " + peer.getHost() + ", prev = " + previousBlockId + ", our_prev = " + lastBlock.getId());
		if (lastBlock.getStringId().equals(previousBlockId)
				|| ((Convert.parseUnsignedLong(previousBlockId) == lastBlock.getPreviousBlockId())
						&& (lastBlock.getTimestamp() > Convert.parseLong(request.get("timestamp"))))) {
			Logger.logDebugMessage("   -> enqueued to BC-Processor.");

			Peers.peersService.submit(() -> {
				try {
					Nxt.getBlockchainProcessor().processPeerBlock(request);
				} catch (NxtException | RuntimeException e) {
					if (peer != null) peer.blacklist(e);
				}
			});
		}
		return JSON.emptyJSON;
	}

	@Override
	boolean rejectWhileDownloading() {
		return true;
	}

}

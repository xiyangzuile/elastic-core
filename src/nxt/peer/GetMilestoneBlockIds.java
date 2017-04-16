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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.Nxt;
import nxt.util.Convert;
import nxt.util.Logger;

final class GetMilestoneBlockIds extends PeerServlet.PeerRequestHandler {

	static final GetMilestoneBlockIds instance = new GetMilestoneBlockIds();

	private GetMilestoneBlockIds() {
	}

	@Override
	JSONStreamAware processRequest(final JSONObject request, final Peer peer) {

		final JSONObject response = new JSONObject();
		try {

			final JSONArray milestoneBlockIds = new JSONArray();

			final String lastBlockIdString = (String) request.get("lastBlockId");
			if (lastBlockIdString != null) {
				final long lastBlockId = Convert.parseUnsignedLong(lastBlockIdString);
				final long myLastBlockId = Nxt.getBlockchain().getLastBlock().getId();
				if ((myLastBlockId == lastBlockId) || Nxt.getBlockchain().hasBlock(lastBlockId)) {
					milestoneBlockIds.add(lastBlockIdString);
					response.put("milestoneBlockIds", milestoneBlockIds);
					if (myLastBlockId == lastBlockId) response.put("last", Boolean.TRUE);
					return response;
				}
			}

			long blockId;
			int height;
			int jump;
			int limit = 10;
			final int blockchainHeight = Nxt.getBlockchain().getHeight();
			final String lastMilestoneBlockIdString = (String) request.get("lastMilestoneBlockId");
			if (lastMilestoneBlockIdString != null) {
				final Block lastMilestoneBlock = Nxt.getBlockchain()
						.getBlock(Convert.parseUnsignedLong(lastMilestoneBlockIdString));
				if (lastMilestoneBlock == null)
                    throw new IllegalStateException("Don't have block " + lastMilestoneBlockIdString);
				height = lastMilestoneBlock.getHeight();
				jump = Math.min(1440, Math.max(blockchainHeight - height, 1));
				height = Math.max(height - jump, 0);
			} else if (lastBlockIdString != null) {
				height = blockchainHeight;
				jump = 10;
			} else {
				peer.blacklist("Old getMilestoneBlockIds request");
				response.put("error", "Old getMilestoneBlockIds protocol not supported, please upgrade");
				return response;
			}
			blockId = Nxt.getBlockchain().getBlockIdAtHeight(height);

			while ((height > 0) && (limit-- > 0)) {
				milestoneBlockIds.add(Long.toUnsignedString(blockId));
				blockId = Nxt.getBlockchain().getBlockIdAtHeight(height);
				height = height - jump;
			}
			response.put("milestoneBlockIds", milestoneBlockIds);

		} catch (final RuntimeException e) {
			Logger.logDebugMessage(e.toString());
			return PeerServlet.error(e);
		}

		return response;
	}

	@Override
	boolean rejectWhileDownloading() {
		return true;
	}

}

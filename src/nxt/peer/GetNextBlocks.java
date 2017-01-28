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

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.Nxt;
import nxt.util.Convert;
import nxt.util.JSON;

final class GetNextBlocks extends PeerServlet.PeerRequestHandler {

	static final GetNextBlocks instance = new GetNextBlocks();

	static final JSONStreamAware TOO_MANY_BLOCKS_REQUESTED;
	static {
		final JSONObject response = new JSONObject();
		response.put("error", Errors.TOO_MANY_BLOCKS_REQUESTED);
		TOO_MANY_BLOCKS_REQUESTED = JSON.prepare(response);
	}

	private GetNextBlocks() {
	}

	@Override
	JSONStreamAware processRequest(final JSONObject request, final Peer peer) {

		final JSONObject response = new JSONObject();
		final JSONArray nextBlocksArray = new JSONArray();
		List<? extends Block> blocks;
		final long blockId = Convert.parseUnsignedLong((String) request.get("blockId"));
		final List<String> stringList = (List<String>) request.get("blockIds");
		if (stringList != null) {
			if (stringList.size() > 36) {
				return GetNextBlocks.TOO_MANY_BLOCKS_REQUESTED;
			}
			final List<Long> idList = new ArrayList<>();
			stringList.forEach(stringId -> idList.add(Convert.parseUnsignedLong(stringId)));
			blocks = Nxt.getBlockchain().getBlocksAfter(blockId, idList);
		} else {
			final long limit = Convert.parseLong(request.get("limit"));
			if (limit > 36) {
				return GetNextBlocks.TOO_MANY_BLOCKS_REQUESTED;
			}
			blocks = Nxt.getBlockchain().getBlocksAfter(blockId, limit > 0 ? (int) limit : 36);
		}
		blocks.forEach(block -> nextBlocksArray.add(block.getJSONObject()));
		response.put("nextBlocks", nextBlocksArray);

		return response;
	}

	@Override
	boolean rejectWhileDownloading() {
		return true;
	}

}
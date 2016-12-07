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

import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Nxt;
import nxt.util.Convert;

final class GetNextBlockIds extends PeerServlet.PeerRequestHandler {

	static final GetNextBlockIds instance = new GetNextBlockIds();

	private GetNextBlockIds() {
	}

	@Override
	JSONStreamAware processRequest(final JSONObject request, final Peer peer) {

		final JSONObject response = new JSONObject();

		final JSONArray nextBlockIds = new JSONArray();
		final long blockId = Convert.parseUnsignedLong((String) request.get("blockId"));
		final int limit = (int) Convert.parseLong(request.get("limit"));
		if (limit > 1440) {
			return GetNextBlocks.TOO_MANY_BLOCKS_REQUESTED;
		}
		final List<Long> ids = Nxt.getBlockchain().getBlockIdsAfter(blockId, limit > 0 ? limit : 1440);
		ids.forEach(id -> nextBlockIds.add(Long.toUnsignedString(id)));
		response.put("nextBlockIds", nextBlockIds);

		return response;
	}

	@Override
	boolean rejectWhileDownloading() {
		return true;
	}

}

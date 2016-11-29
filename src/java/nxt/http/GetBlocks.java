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

import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.BlockImpl;
import nxt.Nxt;
import nxt.NxtException;

public final class GetBlocks extends APIServlet.APIRequestHandler {

	static final GetBlocks instance = new GetBlocks();

	private GetBlocks() {
		super(new APITag[] { APITag.BLOCKS }, "firstIndex", "lastIndex", "timestamp", "includeTransactions",
				"includeExecutedPhased");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final int firstIndex = ParameterParser.getFirstIndex(req);
		final int lastIndex = ParameterParser.getLastIndex(req);
		final int timestamp = ParameterParser.getTimestamp(req);
		final boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));
		final boolean includeExecutedPhased = "true".equalsIgnoreCase(req.getParameter("includeExecutedPhased"));

		final JSONArray blocks = new JSONArray();
		final Iterator<BlockImpl> it = Nxt.getBlockchain().getBlocks(firstIndex, lastIndex).iterator();
		while (it.hasNext()) {
			final Block block = it.next();
			if (block.getTimestamp() < timestamp) {
				break;
			}
			blocks.add(JSONData.block(block, includeTransactions, includeExecutedPhased));

		}

		final JSONObject response = new JSONObject();
		response.put("blocks", blocks);

		return response;
	}

}

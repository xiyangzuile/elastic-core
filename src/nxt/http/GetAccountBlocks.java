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

import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.BlockImpl;
import nxt.Nxt;
import nxt.NxtException;

public final class GetAccountBlocks extends APIServlet.APIRequestHandler {

	static final GetAccountBlocks instance = new GetAccountBlocks();

	private GetAccountBlocks() {
		super(new APITag[] { APITag.ACCOUNTS }, "account", "timestamp", "firstIndex", "lastIndex",
				"includeTransactions");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final long accountId = ParameterParser.getAccountId(req, true);
		final int timestamp = ParameterParser.getTimestamp(req);
		final int firstIndex = ParameterParser.getFirstIndex(req);
		final int lastIndex = ParameterParser.getLastIndex(req);

		final boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));

		final JSONArray blocks = new JSONArray();
        for (BlockImpl block : Nxt.getBlockchain().getBlocks(accountId, timestamp, firstIndex, lastIndex))
            blocks.add(JSONData.block(block, includeTransactions, false));

		final JSONObject response = new JSONObject();
		response.put("blocks", blocks);

		return response;
	}

}

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
import nxt.Hub;
import nxt.Nxt;

public final class GetNextBlockGenerators extends APIServlet.APIRequestHandler {

	static final GetNextBlockGenerators instance = new GetNextBlockGenerators();

	private GetNextBlockGenerators() {
		super(new APITag[] { APITag.FORGING });
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		/*
		 * implement later, if needed Block curBlock;
		 * 
		 * String block = req.getParameter("block"); if (block == null) {
		 * curBlock = Nxt.getBlockchain().getLastBlock(); } else { try {
		 * curBlock =
		 * Nxt.getBlockchain().getBlock(Convert.parseUnsignedLong(block)); if
		 * (curBlock == null) { return UNKNOWN_BLOCK; } } catch
		 * (RuntimeException e) { return INCORRECT_BLOCK; } }
		 */

		final Block curBlock = Nxt.getBlockchain().getLastBlock();

		final JSONObject response = new JSONObject();
		response.put("time", Nxt.getEpochTime());
		response.put("lastBlock", Long.toUnsignedString(curBlock.getId()));
		final JSONArray hubs = new JSONArray();

		int limit;
		try {
			limit = Integer.parseInt(req.getParameter("limit"));
		} catch (final RuntimeException e) {
			limit = Integer.MAX_VALUE;
		}

		final Iterator<Hub.Hit> iterator = Hub.getHubHits(curBlock).iterator();
		while (iterator.hasNext() && (hubs.size() < limit)) {
			final JSONObject hub = new JSONObject();
			final Hub.Hit hit = iterator.next();
			hub.put("account", Long.toUnsignedString(hit.hub.getAccountId()));
			hub.put("minFeePerByteNQT", hit.hub.getMinFeePerByteNQT());
			hub.put("time", hit.hitTime);
			final JSONArray uris = new JSONArray();
			uris.addAll(hit.hub.getUris());
			hub.put("uris", uris);
			hubs.add(hub);
		}

		response.put("hubs", hubs);
		return response;
	}

}

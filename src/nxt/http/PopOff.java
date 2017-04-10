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

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.Nxt;

public final class PopOff extends APIServlet.APIRequestHandler {

	static final PopOff instance = new PopOff();

	private PopOff() {
		super(new APITag[] { APITag.DEBUG }, "numBlocks", "height", "keepTransactions");
	}

	@Override
	protected final boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		int numBlocks = 0;
		try {
			numBlocks = Integer.parseInt(req.getParameter("numBlocks"));
		} catch (final NumberFormatException ignored) {
		}
		int height = 0;
		try {
			height = Integer.parseInt(req.getParameter("height"));
		} catch (final NumberFormatException ignored) {
		}
		final boolean keepTransactions = "true".equalsIgnoreCase(req.getParameter("keepTransactions"));
		List<? extends Block> blocks;
		try {
			Nxt.getBlockchainProcessor().setGetMoreBlocks(false);
			if (numBlocks > 0) {
				blocks = Nxt.getBlockchainProcessor().popOffTo(Nxt.getBlockchain().getHeight() - numBlocks);
			} else if (height > 0) {
				blocks = Nxt.getBlockchainProcessor().popOffTo(height);
			} else {
				return JSONResponses.missing("numBlocks", "height");
			}
		} finally {
			Nxt.getBlockchainProcessor().setGetMoreBlocks(true);
		}
		final JSONArray blocksJSON = new JSONArray();
		blocks.forEach(block -> blocksJSON.add(JSONData.block(block, true, false)));
		final JSONObject response = new JSONObject();
		response.put("blocks", blocksJSON);
		if (keepTransactions) {
			blocks.forEach(block -> Nxt.getTransactionProcessor().processLater(block.getTransactions()));
		}
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

	@Override
	protected boolean requirePassword() {
		return true;
	}

	@Override
	protected final boolean requirePost() {
		return true;
	}

}

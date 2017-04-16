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

import nxt.*;
import nxt.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetSupernodes extends APIServlet.APIRequestHandler {

	static final GetSupernodes instance = new GetSupernodes();

	private GetSupernodes() {
		super(new APITag[] { APITag.NETWORK }, "active", "state", "service", "service", "service", "includePeerInfo");
	}


	public static int countSupernodes(){
		final Block lastBlock = Nxt.getBlockchain().getLastBlock();
		return Account.countSuperNodes(lastBlock.getHeight());
	}

	public static JSONStreamAware getSupernodes(){
		final JSONArray peersJSON = new JSONArray();
		final JSONObject response = new JSONObject();

		final Block lastBlock = Nxt.getBlockchain().getLastBlock();
		response.put("lastBlock", lastBlock.getStringId());
		response.put("lastBlockHeight", lastBlock.getHeight());

		try (DbIterator<? extends Account.AccountSupernodeDeposit> iterator = Account.getActiveSupernodes(lastBlock.getHeight());) {
			while (iterator.hasNext()) {
				final Account.AccountSupernodeDeposit b = iterator.next();
				JSONObject obj = JSONData.superNode(b);
				if(obj==null) continue;
				peersJSON.add(obj);
			}
		}
		response.put("supernodes", peersJSON);

		return response;
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {
		return getSupernodes();
	}

}

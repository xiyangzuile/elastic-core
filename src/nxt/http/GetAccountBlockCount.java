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

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Nxt;
import nxt.NxtException;

public final class GetAccountBlockCount extends APIServlet.APIRequestHandler {

	static final GetAccountBlockCount instance = new GetAccountBlockCount();

	private GetAccountBlockCount() {
		super(new APITag[] { APITag.ACCOUNTS }, "account");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final long accountId = ParameterParser.getAccountId(req, true);
		final JSONObject response = new JSONObject();
		response.put("numberOfBlocks", Nxt.getBlockchain().getBlockCount(accountId));

		return response;
	}

}

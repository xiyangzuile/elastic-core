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

import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Nxt;
import nxt.NxtException;

public final class GetBalance extends APIServlet.APIRequestHandler {

	static final GetBalance instance = new GetBalance();

	private GetBalance() {
		super(new APITag[] { APITag.ACCOUNTS }, "account", "includeEffectiveBalance", "height");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {
		final boolean includeEffectiveBalance = "true".equalsIgnoreCase(req.getParameter("includeEffectiveBalance"));
		final long accountId = ParameterParser.getAccountId(req, true);
		int height = ParameterParser.getHeight(req);
		if (height < 0) {
			height = Nxt.getBlockchain().getHeight();
		}
		final Account account = Account.getAccount(accountId, height);
		return JSONData.accountBalance(account, includeEffectiveBalance, height);
	}

}

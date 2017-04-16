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

import nxt.Account;
import nxt.Nxt;
import nxt.NxtException;

public final class GetGuaranteedBalance extends APIServlet.APIRequestHandler {

	static final GetGuaranteedBalance instance = new GetGuaranteedBalance();

	private GetGuaranteedBalance() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.FORGING }, "account", "numberOfConfirmations");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final Account account = ParameterParser.getAccount(req);
		final int numberOfConfirmations = ParameterParser.getNumberOfConfirmations(req);

		final JSONObject response = new JSONObject();
		if (account == null) response.put("guaranteedBalanceNQT", "0");
        else response.put("guaranteedBalanceNQT", String
                .valueOf(account.getGuaranteedBalanceNQT(numberOfConfirmations, Nxt.getBlockchain().getHeight())));

		return response;
	}

}

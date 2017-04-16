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

import nxt.util.Convert;

public final class RSConvert extends APIServlet.APIRequestHandler {

	static final RSConvert instance = new RSConvert();

	private RSConvert() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.UTILS }, "account");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {
		final String accountValue = Convert.emptyToNull(req.getParameter("account"));
		if (accountValue == null) return JSONResponses.MISSING_ACCOUNT;
		try {
			final long accountId = Convert.parseAccountId(accountValue);
			if (accountId == 0) return JSONResponses.INCORRECT_ACCOUNT;
			final JSONObject response = new JSONObject();
			JSONData.putAccount(response, "account", accountId);
			return response;
		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_ACCOUNT;
		}
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

}

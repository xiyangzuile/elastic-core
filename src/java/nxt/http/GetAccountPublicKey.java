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

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.NxtException;
import nxt.util.Convert;
import nxt.util.JSON;

public final class GetAccountPublicKey extends APIServlet.APIRequestHandler {

	static final GetAccountPublicKey instance = new GetAccountPublicKey();

	private GetAccountPublicKey() {
		super(new APITag[] {APITag.ACCOUNTS}, "account");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final long accountId = ParameterParser.getAccountId(req, true);
		final byte[] publicKey = Account.getPublicKey(accountId);
		if (publicKey != null) {
			final JSONObject response = new JSONObject();
			response.put("publicKey", Convert.toHexString(publicKey));
			return response;
		} else {
			return JSON.emptyJSON;
		}
	}

}

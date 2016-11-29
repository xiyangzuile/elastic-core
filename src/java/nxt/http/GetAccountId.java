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
import nxt.util.Convert;

public final class GetAccountId extends APIServlet.APIRequestHandler {

	static final GetAccountId instance = new GetAccountId();

	private GetAccountId() {
		super(new APITag[] {APITag.ACCOUNTS}, "secretPhrase", "publicKey");
	}

	@Override
	protected final boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final byte[] publicKey = ParameterParser.getPublicKey(req);
		final long accountId = Account.getId(publicKey);
		final JSONObject response = new JSONObject();
		JSONData.putAccount(response, "account", accountId);
		response.put("publicKey", Convert.toHexString(publicKey));

		return response;
	}

	@Override
	protected final boolean requireBlockchain() {
		return false;
	}

}

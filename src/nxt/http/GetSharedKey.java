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
import nxt.NxtException;
import nxt.crypto.Crypto;
import nxt.util.Convert;

public final class GetSharedKey extends APIServlet.APIRequestHandler {

	static final GetSharedKey instance = new GetSharedKey();

	private GetSharedKey() {
		super(new APITag[] { APITag.MESSAGES }, "account", "secretPhrase", "nonce");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final String secretPhrase = ParameterParser.getSecretPhrase(req, true);
		final byte[] nonce = ParameterParser.getBytes(req, "nonce", true);
		final long accountId = ParameterParser.getAccountId(req, "account", true);
		final byte[] publicKey = Account.getPublicKey(accountId);
		if (publicKey == null) return JSONResponses.INCORRECT_ACCOUNT;
		final byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), publicKey, nonce);
		final JSONObject response = new JSONObject();
		response.put("sharedKey", Convert.toHexString(sharedKey));
		return response;

	}

}

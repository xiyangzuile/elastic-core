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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Generator;
import nxt.Nxt;
import nxt.crypto.Crypto;

public final class GetForging extends APIServlet.APIRequestHandler {

	static final GetForging instance = new GetForging();

	private GetForging() {
		super(new APITag[] { APITag.FORGING }, "secretPhrase", "adminPassword");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final String secretPhrase = ParameterParser.getSecretPhrase(req, false);
		final int elapsedTime = Nxt.getEpochTime() - Nxt.getBlockchain().getLastBlock().getTimestamp();
		if (secretPhrase != null) {
			final Account account = Account.getAccount(Crypto.getPublicKey(secretPhrase));
			if (account == null) {
				return JSONResponses.UNKNOWN_ACCOUNT;
			}
			final Generator generator = Generator.getGenerator(secretPhrase);
			if (generator == null) {
				return JSONResponses.NOT_FORGING;
			}
			return JSONData.generator(generator, elapsedTime);
		} else {
			API.verifyPassword(req);
			final JSONObject response = new JSONObject();
			final JSONArray generators = new JSONArray();
			Generator.getSortedForgers()
					.forEach(generator -> generators.add(JSONData.generator(generator, elapsedTime)));
			response.put("generators", generators);
			return response;
		}
	}

	@Override
	protected boolean requireFullClient() {
		return true;
	}

}

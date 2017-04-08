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

import nxt.Token;
import nxt.util.Convert;

public final class GenerateToken extends APIServlet.APIRequestHandler {

	static final GenerateToken instance = new GenerateToken();

	private GenerateToken() {
		super(new APITag[] { APITag.TOKENS }, "website", "secretPhrase");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final String secretPhrase = ParameterParser.getSecretPhrase(req, true);
		final String website = Convert.emptyToNull(req.getParameter("website"));
		if (website == null) {
			return JSONResponses.MISSING_WEBSITE;
		}

		try {

			final String tokenString = Token.generateToken(secretPhrase, website.trim());

			final JSONObject response = JSONData.token(Token.parseToken(tokenString, website));
			response.put("token", tokenString);

			return response;

		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_WEBSITE;
		}

	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

	@Override
	protected boolean requirePost() {
		return true;
	}

}

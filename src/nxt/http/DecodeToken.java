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

import nxt.Token;

public final class DecodeToken extends APIServlet.APIRequestHandler {

	static final DecodeToken instance = new DecodeToken();

	private DecodeToken() {
		super(new APITag[] { APITag.TOKENS }, "website", "token");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	public JSONStreamAware processRequest(final HttpServletRequest req) {

		final String website = req.getParameter("website");
		final String tokenString = req.getParameter("token");
		if (website == null) return JSONResponses.MISSING_WEBSITE;
        else if (tokenString == null) return JSONResponses.MISSING_TOKEN;

		try {

			final Token token = Token.parseToken(tokenString, website.trim());

			return JSONData.token(token);

		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_WEBSITE;
		}
	}

}

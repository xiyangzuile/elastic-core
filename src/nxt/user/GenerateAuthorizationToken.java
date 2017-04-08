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

package nxt.user;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Token;

public final class GenerateAuthorizationToken extends UserServlet.UserRequestHandler {

	static final GenerateAuthorizationToken instance = new GenerateAuthorizationToken();

	private GenerateAuthorizationToken() {
	}

	@Override
	JSONStreamAware processRequest(final HttpServletRequest req, final User user) throws IOException {
		final String secretPhrase = req.getParameter("secretPhrase");
		if (!user.getSecretPhrase().equals(secretPhrase)) {
			return JSONResponses.INVALID_SECRET_PHRASE;
		}

		final String tokenString = Token.generateToken(secretPhrase, req.getParameter("website").trim());

		final JSONObject response = new JSONObject();
		response.put("response", "showAuthorizationToken");
		response.put("token", tokenString);

		return response;
	}

	@Override
	boolean requirePost() {
		return true;
	}

}

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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.json.simple.JSONStreamAware;

import nxt.Token;

public final class DecodeFileToken extends APIServlet.APIRequestHandler {

	static final DecodeFileToken instance = new DecodeFileToken();

	private DecodeFileToken() {
		super("file", new APITag[] { APITag.TOKENS }, "token");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	public JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {
		final String tokenString = req.getParameter("token");
		if (tokenString == null) return JSONResponses.MISSING_TOKEN;
		byte[] data;
		try {
			final Part part = req.getPart("file");
			if (part == null) throw new ParameterException(JSONResponses.INCORRECT_FILE);
			final ParameterParser.FileData fileData = new ParameterParser.FileData(part).invoke();
			data = fileData.getData();
		} catch (IOException | ServletException e) {
			throw new ParameterException(JSONResponses.INCORRECT_FILE);
		}

		try {
			final Token token = Token.parseToken(tokenString, data);
			return JSONData.token(token);
		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_TOKEN;
		}
	}

	@Override
	protected boolean requirePost() {
		return true;
	}

}

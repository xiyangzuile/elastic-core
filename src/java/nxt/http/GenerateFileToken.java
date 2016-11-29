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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Token;


public final class GenerateFileToken extends APIServlet.APIRequestHandler {

	static final GenerateFileToken instance = new GenerateFileToken();

	private GenerateFileToken() {
		super("file", new APITag[] {APITag.TOKENS}, "secretPhrase");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {
		final String secretPhrase = ParameterParser.getSecretPhrase(req, true);
		byte[] data;
		try {
			final Part part = req.getPart("file");
			if (part == null) {
				throw new ParameterException(JSONResponses.INCORRECT_FILE);
			}
			final ParameterParser.FileData fileData = new ParameterParser.FileData(part).invoke();
			data = fileData.getData();
		} catch (IOException | ServletException e) {
			throw new ParameterException(JSONResponses.INCORRECT_FILE);
		}
		try {
			final String tokenString = Token.generateToken(secretPhrase, data);
			final JSONObject response = JSONData.token(Token.parseToken(tokenString, data));
			response.put("token", tokenString);
			return response;
		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_TOKEN;
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

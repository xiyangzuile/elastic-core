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

import nxt.crypto.HashFunction;
import nxt.util.Convert;

public final class Hash extends APIServlet.APIRequestHandler {

	static final Hash instance = new Hash();

	private Hash() {
		super(new APITag[] { APITag.UTILS }, "hashAlgorithm", "secret", "secretIsText");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final byte algorithm = ParameterParser.getByte(req, "hashAlgorithm", (byte) 0, Byte.MAX_VALUE, false);
		HashFunction hashFunction = null;
		try {
			hashFunction = HashFunction.getHashFunction(algorithm);
		} catch (final IllegalArgumentException ignore) {
		}
		if (hashFunction == null) {
			return JSONResponses.INCORRECT_HASH_ALGORITHM;
		}

		final boolean secretIsText = "true".equalsIgnoreCase(req.getParameter("secretIsText"));
		byte[] secret;
		try {
			secret = secretIsText ? Convert.toBytes(req.getParameter("secret"))
					: Convert.parseHexString(req.getParameter("secret"));
		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_SECRET;
		}
		if ((secret == null) || (secret.length == 0)) {
			return JSONResponses.MISSING_SECRET;
		}

		final JSONObject response = new JSONObject();
		response.put("hash", Convert.toHexString(hashFunction.hash(secret)));
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

}

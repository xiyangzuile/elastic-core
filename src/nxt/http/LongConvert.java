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

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.util.Convert;
import nxt.util.JSON;

public final class LongConvert extends APIServlet.APIRequestHandler {

	static final LongConvert instance = new LongConvert();

	private LongConvert() {
		super(new APITag[] { APITag.UTILS }, "id");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {
		final String id = Convert.emptyToNull(req.getParameter("id"));
		if (id == null) {
			return JSON.emptyJSON;
		}
		final JSONObject response = new JSONObject();
		final BigInteger bigInteger = new BigInteger(id);
		if (bigInteger.signum() < 0) {
			if (bigInteger.negate().compareTo(Convert.two64) > 0) {
				return JSONResponses.OVERFLOW;
			} else {
				response.put("stringId", bigInteger.add(Convert.two64).toString());
				response.put("longId", String.valueOf(bigInteger.longValue()));
			}
		} else {
			if (bigInteger.compareTo(Convert.two64) >= 0) {
				return JSONResponses.OVERFLOW;
			} else {
				response.put("stringId", bigInteger.toString());
				response.put("longId", String.valueOf(bigInteger.longValue()));
			}
		}
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

}

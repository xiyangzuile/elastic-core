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

import nxt.util.Convert;

public final class FullHashToId extends APIServlet.APIRequestHandler {

	static final FullHashToId instance = new FullHashToId();

	private FullHashToId() {
		super(new APITag[] { APITag.UTILS }, "fullHash");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {
		final JSONObject response = new JSONObject();
		final long longId = Convert.fullHashToId(Convert.parseHexString(req.getParameter("fullHash")));
		response.put("longId", String.valueOf(longId));
		response.put("stringId", Long.toUnsignedString(longId));
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

}

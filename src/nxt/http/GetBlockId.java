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

import nxt.Nxt;
import nxt.util.Convert;

public final class GetBlockId extends APIServlet.APIRequestHandler {

	static final GetBlockId instance = new GetBlockId();

	private GetBlockId() {
		super(new APITag[] { APITag.BLOCKS }, "height");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		int height;
		try {
			final String heightValue = Convert.emptyToNull(req.getParameter("height"));
			if (heightValue == null) {
				return JSONResponses.MISSING_HEIGHT;
			}
			height = Integer.parseInt(heightValue);
		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_HEIGHT;
		}

		try {
			final JSONObject response = new JSONObject();
			response.put("block", Long.toUnsignedString(Nxt.getBlockchain().getBlockIdAtHeight(height)));
			return response;
		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_HEIGHT;
		}

	}

}
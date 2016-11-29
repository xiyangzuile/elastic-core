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

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Constants;
import nxt.peer.Hallmark;
import nxt.util.Convert;

public final class MarkHost extends APIServlet.APIRequestHandler {

	static final MarkHost instance = new MarkHost();

	private MarkHost() {
		super(new APITag[] { APITag.TOKENS }, "secretPhrase", "host", "weight", "date");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final String secretPhrase = ParameterParser.getSecretPhrase(req, true);
		final String host = Convert.emptyToNull(req.getParameter("host"));
		final String weightValue = Convert.emptyToNull(req.getParameter("weight"));
		final String dateValue = Convert.emptyToNull(req.getParameter("date"));
		if (host == null) {
			return JSONResponses.MISSING_HOST;
		} else if (weightValue == null) {
			return JSONResponses.MISSING_WEIGHT;
		} else if (dateValue == null) {
			return JSONResponses.MISSING_DATE;
		}

		if (host.length() > 100) {
			return JSONResponses.INCORRECT_HOST;
		}

		int weight;
		try {
			weight = Integer.parseInt(weightValue);
			if ((weight <= 0) || (weight > Constants.MAX_BALANCE_NXT)) {
				return JSONResponses.INCORRECT_WEIGHT;
			}
		} catch (final NumberFormatException e) {
			return JSONResponses.INCORRECT_WEIGHT;
		}

		try {

			final String hallmark = Hallmark.generateHallmark(secretPhrase, host, weight,
					Hallmark.parseDate(dateValue));

			final JSONObject response = new JSONObject();
			response.put("hallmark", hallmark);
			return response;

		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_DATE;
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

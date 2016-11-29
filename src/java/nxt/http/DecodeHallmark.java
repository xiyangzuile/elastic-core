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

import org.json.simple.JSONStreamAware;

import nxt.peer.Hallmark;

public final class DecodeHallmark extends APIServlet.APIRequestHandler {

	static final DecodeHallmark instance = new DecodeHallmark();

	private DecodeHallmark() {
		super(new APITag[] {APITag.TOKENS}, "hallmark");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		final String hallmarkValue = req.getParameter("hallmark");
		if (hallmarkValue == null) {
			return JSONResponses.MISSING_HALLMARK;
		}

		try {

			final Hallmark hallmark = Hallmark.parseHallmark(hallmarkValue);

			return JSONData.hallmark(hallmark);

		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_HALLMARK;
		}
	}

}

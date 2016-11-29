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

import nxt.Generator;


public final class StartForging extends APIServlet.APIRequestHandler {

	static final StartForging instance = new StartForging();

	private StartForging() {
		super(new APITag[] {APITag.FORGING}, "secretPhrase");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final String secretPhrase = ParameterParser.getSecretPhrase(req, true);
		final Generator generator = Generator.startForging(secretPhrase);

		final JSONObject response = new JSONObject();
		response.put("deadline", generator.getDeadline());
		response.put("hitTime", generator.getHitTime());
		return response;

	}

	@Override
	protected boolean requireFullClient() {
		return true;
	}

	@Override
	protected boolean requirePost() {
		return true;
	}

}

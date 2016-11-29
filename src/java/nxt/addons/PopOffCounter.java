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

package nxt.addons;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.BlockchainProcessor;
import nxt.Nxt;
import nxt.NxtException;
import nxt.http.APIServlet;
import nxt.http.APITag;

public final class PopOffCounter implements AddOn {

	private volatile int numberOfPopOffs = 0;

	@Override
	public APIServlet.APIRequestHandler getAPIRequestHandler() {
		return new APIServlet.APIRequestHandler(new APITag[]{APITag.ADDONS, APITag.BLOCKS}) {
			@Override
			protected boolean allowRequiredBlockParameters() {
				return false;
			}
			@Override
			protected JSONStreamAware processRequest(final HttpServletRequest request) throws NxtException {
				final JSONObject response = new JSONObject();
				response.put("numberOfPopOffs", PopOffCounter.this.numberOfPopOffs);
				return response;
			}
		};
	}

	@Override
	public String getAPIRequestType() {
		return "getNumberOfPopOffs";
	}

	@Override
	public void init() {
		Nxt.getBlockchainProcessor().addListener(block -> this.numberOfPopOffs += 1, BlockchainProcessor.Event.BLOCK_POPPED);
	}

}

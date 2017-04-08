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

import java.util.logging.Handler;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.util.MemoryHandler;

/**
 * <p>
 * The GetLog API will return log messages from the ring buffer maintained by
 * the MemoryHandler log handler. The most recent 'count' messages will be
 * returned. All log messages in the ring buffer will be returned if 'count' is
 * omitted.
 * </p>
 *
 * <p>
 * Request parameters:
 * </p>
 * <ul>
 * <li>count - The number of log messages to return</li>
 * </ul>
 *
 * <p>
 * Response parameters:
 * </p>
 * <ul>
 * <li>messages - An array of log messages</li>
 * </ul>
 */
public final class GetLog extends APIServlet.APIRequestHandler {

	/** GetLog instance */
	static final GetLog instance = new GetLog();

	/**
	 * Create the GetLog instance
	 */
	private GetLog() {
		super(new APITag[] { APITag.DEBUG }, "count");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	/**
	 * Process the GetLog API request
	 *
	 * @param req
	 *            API request
	 * @return API response
	 */
	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {
		//
		// Get the number of log messages to return
		//
		int count;
		final String value = req.getParameter("count");
		if (value != null) {
			count = Math.max(Integer.valueOf(value), 0);
		} else {
			count = Integer.MAX_VALUE;
		}
		//
		// Get the log messages
		//
		final JSONArray logJSON = new JSONArray();
		final Logger logger = Logger.getLogger("");
		final Handler[] handlers = logger.getHandlers();
		for (final Handler handler : handlers) {
			if (handler instanceof MemoryHandler) {
				logJSON.addAll(((MemoryHandler) handler).getMessages(count));
				break;
			}
		}
		//
		// Return the response
		//
		final JSONObject response = new JSONObject();
		response.put("messages", logJSON);
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

	/**
	 * Require the administrator password
	 *
	 * @return TRUE if the admin password is required
	 */
	@Override
	protected boolean requirePassword() {
		return true;
	}

}

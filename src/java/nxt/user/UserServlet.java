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

package nxt.user;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Nxt;
import nxt.NxtException;
import nxt.util.Logger;

public final class UserServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5287180407517490598L;

	abstract static class UserRequestHandler {
		abstract JSONStreamAware processRequest(HttpServletRequest request, User user) throws NxtException, IOException;

		boolean requirePost() {
			return false;
		}
	}

	private static final boolean enforcePost = Nxt.getBooleanProperty("nxt.uiServerEnforcePOST");

	private static final Map<String, UserRequestHandler> userRequestHandlers;

	static {
		final Map<String, UserRequestHandler> map = new HashMap<>();
		map.put("generateAuthorizationToken", GenerateAuthorizationToken.instance);
		map.put("getInitialData", GetInitialData.instance);
		map.put("getNewData", GetNewData.instance);
		map.put("lockAccount", LockAccount.instance);
		map.put("removeActivePeer", RemoveActivePeer.instance);
		map.put("removeBlacklistedPeer", RemoveBlacklistedPeer.instance);
		map.put("removeKnownPeer", RemoveKnownPeer.instance);
		map.put("sendMoney", SendMoney.instance);
		map.put("unlockAccount", UnlockAccount.instance);
		userRequestHandlers = Collections.unmodifiableMap(map);
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
			throws ServletException, IOException {
		this.process(req, resp);
	}

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
			throws ServletException, IOException {
		this.process(req, resp);
	}

	private void process(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {

		resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
		resp.setHeader("Pragma", "no-cache");
		resp.setDateHeader("Expires", 0);

		User user = null;

		try {

			final String userPasscode = req.getParameter("user");
			if (userPasscode == null) {
				return;
			}
			user = Users.getUser(userPasscode);

			if ((Users.allowedUserHosts != null) && !Users.allowedUserHosts.contains(req.getRemoteHost())) {
				user.enqueue(JSONResponses.DENY_ACCESS);
				return;
			}

			final String requestType = req.getParameter("requestType");
			if (requestType == null) {
				user.enqueue(JSONResponses.INCORRECT_REQUEST);
				return;
			}

			final UserRequestHandler userRequestHandler = UserServlet.userRequestHandlers.get(requestType);
			if (userRequestHandler == null) {
				user.enqueue(JSONResponses.INCORRECT_REQUEST);
				return;
			}

			if (UserServlet.enforcePost && userRequestHandler.requirePost() && !"POST".equals(req.getMethod())) {
				user.enqueue(JSONResponses.POST_REQUIRED);
				return;
			}

			final JSONStreamAware response = userRequestHandler.processRequest(req, user);
			if (response != null) {
				user.enqueue(response);
			}

		} catch (RuntimeException | NxtException e) {

			Logger.logMessage("Error processing GET request", e);
			if (user != null) {
				final JSONObject response = new JSONObject();
				response.put("response", "showMessage");
				response.put("message", e.toString());
				user.enqueue(response);
			}

		} finally {

			if (user != null) {
				user.processPendingResponses(req, resp);
			}

		}

	}

}

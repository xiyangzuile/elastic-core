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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Constants;
import nxt.Db;
import nxt.Nxt;
import nxt.NxtException;
import nxt.addons.AddOns;
import nxt.util.JSON;
import nxt.util.Logger;

public final class APIServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3609256188919710481L;

	public abstract static class APIRequestHandler {

		private final List<String> parameters;
		private final String fileParameter;
		private final Set<APITag> apiTags;

		protected APIRequestHandler(final APITag[] apiTags, final String... parameters) {
			this(null, apiTags, parameters);
		}

		protected APIRequestHandler(final String fileParameter, final APITag[] apiTags,
				final String... origParameters) {
			final List<String> parameters = new ArrayList<>();
			Collections.addAll(parameters, origParameters);
			if ((this.requirePassword() || parameters.contains("lastIndex")) && !API.disableAdminPassword) {
				parameters.add("adminPassword");
			}
			if (this.allowRequiredBlockParameters()) {
				parameters.add("requireBlock");
				parameters.add("requireLastBlock");
			}
			this.parameters = Collections.unmodifiableList(parameters);
			this.apiTags = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(apiTags)));
			this.fileParameter = fileParameter;
		}

		protected boolean allowRequiredBlockParameters() {
			return true;
		}

		public final Set<APITag> getAPITags() {
			return this.apiTags;
		}

		public final String getFileParameter() {
			return this.fileParameter;
		}

		public final List<String> getParameters() {
			return this.parameters;
		}

		protected abstract JSONStreamAware processRequest(HttpServletRequest request) throws NxtException;

		protected JSONStreamAware processRequest(final HttpServletRequest request, final HttpServletResponse response)
				throws NxtException {
			return this.processRequest(request);
		}

		protected boolean requireBlockchain() {
			return true;
		}

		protected boolean requireFullClient() {
			return false;
		}

		protected boolean requirePassword() {
			return false;
		}

		protected boolean requirePost() {
			return false;
		}

		protected boolean startDbTransaction() {
			return false;
		}

	}

	private static final boolean enforcePost = Nxt.getBooleanProperty("nxt.apiServerEnforcePOST");
	static final Map<String, APIRequestHandler> apiRequestHandlers;
	static final Map<String, APIRequestHandler> disabledRequestHandlers;

	static {

		final Map<String, APIRequestHandler> map = new HashMap<>();
		final Map<String, APIRequestHandler> disabledMap = new HashMap<>();

		for (final APIEnum api : APIEnum.values()) {
			if (!api.getName().isEmpty() && (api.getHandler() != null)) {
				map.put(api.getName(), api.getHandler());
			}
		}

		AddOns.registerAPIRequestHandlers(map);

		API.disabledAPIs.forEach(api -> {
			final APIRequestHandler handler = map.remove(api);
			if (handler == null) {
				throw new RuntimeException("Invalid API in nxt.disabledAPIs: " + api);
			}
			disabledMap.put(api, handler);
		});
		API.disabledAPITags.forEach(apiTag -> {
			final Iterator<Map.Entry<String, APIRequestHandler>> iterator = map.entrySet().iterator();
			while (iterator.hasNext()) {
				final Map.Entry<String, APIRequestHandler> entry = iterator.next();
				if (entry.getValue().getAPITags().contains(apiTag)) {
					disabledMap.put(entry.getKey(), entry.getValue());
					iterator.remove();
				}
			}
		});
		if (!API.disabledAPIs.isEmpty()) {
			Logger.logInfoMessage("Disabled APIs: " + API.disabledAPIs);
		}
		if (!API.disabledAPITags.isEmpty()) {
			Logger.logInfoMessage("Disabled APITags: " + API.disabledAPITags);
		}

		apiRequestHandlers = Collections.unmodifiableMap(map);
		disabledRequestHandlers = disabledMap.isEmpty() ? Collections.emptyMap()
				: Collections.unmodifiableMap(disabledMap);
	}

	public static APIRequestHandler getAPIRequestHandler(final String requestType) {
		return APIServlet.apiRequestHandlers.get(requestType);
	}

	static void initClass() {
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
		// Set response values now in case we create an asynchronous context
		resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
		resp.setHeader("Pragma", "no-cache");
		resp.setDateHeader("Expires", 0);
		resp.setContentType("text/plain; charset=UTF-8");

		JSONStreamAware response = JSON.emptyJSON;

		try {

			final long startTime = System.currentTimeMillis();

			if (!API.isAllowed(req.getRemoteHost())) {
				response = JSONResponses.ERROR_NOT_ALLOWED;
				return;
			}

			final String requestType = req.getParameter("requestType");
			if (requestType == null) {
				response = JSONResponses.ERROR_INCORRECT_REQUEST;
				return;
			}

			final APIRequestHandler apiRequestHandler = APIServlet.apiRequestHandlers.get(requestType);
			if (apiRequestHandler == null) {
				if (APIServlet.disabledRequestHandlers.containsKey(requestType)) {
					response = JSONResponses.ERROR_DISABLED;
				} else {
					response = JSONResponses.ERROR_INCORRECT_REQUEST;
				}
				return;
			}

			if (Constants.isLightClient && apiRequestHandler.requireFullClient()) {
				response = JSONResponses.LIGHT_CLIENT_DISABLED_API;
				return;
			}

			if (APIServlet.enforcePost && apiRequestHandler.requirePost() && !"POST".equals(req.getMethod())) {
				response = JSONResponses.POST_REQUIRED;
				return;
			}

			try {
				if (apiRequestHandler.requirePassword()) {
					API.verifyPassword(req);
				}
				final long requireBlockId = apiRequestHandler.allowRequiredBlockParameters()
						? ParameterParser.getUnsignedLong(req, "requireBlock", false) : 0;
				final long requireLastBlockId = apiRequestHandler.allowRequiredBlockParameters()
						? ParameterParser.getUnsignedLong(req, "requireLastBlock", false) : 0;
				if ((requireBlockId != 0) || (requireLastBlockId != 0)) {
					Nxt.getBlockchain().readLock();
				}
				try {
					try {
						if (apiRequestHandler.startDbTransaction()) {
							Db.db.beginTransaction();
						}
						if ((requireBlockId != 0) && !Nxt.getBlockchain().hasBlock(requireBlockId)) {
							response = JSONResponses.REQUIRED_BLOCK_NOT_FOUND;
							return;
						}
						if ((requireLastBlockId != 0)
								&& (requireLastBlockId != Nxt.getBlockchain().getLastBlock().getId())) {
							response = JSONResponses.REQUIRED_LAST_BLOCK_NOT_FOUND;
							return;
						}
						response = apiRequestHandler.processRequest(req, resp);
						if ((requireLastBlockId == 0) && (requireBlockId != 0) && (response instanceof JSONObject)) {
							((JSONObject) response).put("lastBlock", Nxt.getBlockchain().getLastBlock().getStringId());
						}
					} finally {
						if (apiRequestHandler.startDbTransaction()) {
							Db.db.endTransaction();
						}
					}
				} finally {
					if ((requireBlockId != 0) || (requireLastBlockId != 0)) {
						Nxt.getBlockchain().readUnlock();
					}
				}
			} catch (final ParameterException e) {
				response = e.getErrorResponse();
			} catch (NxtException | RuntimeException e) {
				Logger.logDebugMessage("Error processing API request", e);
				final JSONObject json = new JSONObject();
				JSONData.putException(json, e);
				response = JSON.prepare(json);
			} catch (final ExceptionInInitializerError err) {
				Logger.logErrorMessage("Initialization Error", err.getCause());
				response = JSONResponses.ERROR_INCORRECT_REQUEST;
			}
			if ((response != null) && (response instanceof JSONObject)) {
				((JSONObject) response).put("requestProcessingTime", System.currentTimeMillis() - startTime);
			}
		} catch (final Exception e) {
			Logger.logErrorMessage("Error processing request", e);
			response = JSONResponses.ERROR_INCORRECT_REQUEST;
		} finally {
			// The response will be null if we created an asynchronous context
			if (response != null) {
				try (Writer writer = resp.getWriter()) {
					JSON.writeJSONString(response, writer);
				}
			}
		}

	}

}

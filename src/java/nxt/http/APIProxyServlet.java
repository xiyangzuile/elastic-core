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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.proxy.AsyncMiddleManServlet;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.simple.JSONStreamAware;

import nxt.peer.Peer;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Logger;

public final class APIProxyServlet extends AsyncMiddleManServlet {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6674861463274583266L;

	private class APIProxyResponseListener extends AsyncMiddleManServlet.ProxyResponseListener {

		APIProxyResponseListener(final HttpServletRequest request, final HttpServletResponse response) {
			super(request, response);
		}

		@Override
		public void onFailure(final Response response, final Throwable failure) {
			super.onFailure(response, failure);
			Logger.logErrorMessage("proxy failed", failure);
			APIProxy.getInstance().blacklistHost(response.getRequest().getHost());
		}
	}
	private static class PasswordDetectedException extends RuntimeException {
		/**
		 * 
		 */
		private static final long serialVersionUID = -7121325972646380729L;
		private final JSONStreamAware errorResponse;

		private PasswordDetectedException(final JSONStreamAware errorResponse) {
			this.errorResponse = errorResponse;
		}
	}
	private static class PasswordFilteringContentTransformer implements AsyncMiddleManServlet.ContentTransformer {

		ByteArrayOutputStream os;

		@Override
		public void transform(final ByteBuffer input, final boolean finished, final List<ByteBuffer> output) throws IOException {
			if (finished) {
				ByteBuffer allInput;
				if (this.os == null) {
					allInput = input;
				} else {
					final byte[] b = new byte[input.remaining()];
					input.get(b);
					this.os.write(b);
					allInput = ByteBuffer.wrap(this.os.toByteArray());
				}
				final int tokenPos = PasswordFinder.process(allInput, new String[] { "secretPhrase=", "adminPassword=", "sharedKey=" });
				if (tokenPos >= 0) {
					final JSONStreamAware error = JSONResponses.PROXY_SECRET_DATA_DETECTED;
					throw new PasswordDetectedException(error);
				}
				output.add(allInput);
			} else {
				if (this.os == null) {
					this.os = new ByteArrayOutputStream();
				}
				final byte[] b = new byte[input.remaining()];
				input.get(b);
				this.os.write(b);
			}
		}
	}
	static class PasswordFinder {

		static int process(final ByteBuffer buffer, final String[] secrets) {
			try {
				final int[] pos = new int[secrets.length];
				final byte[][] tokens = new byte[secrets.length][];
				for (int i = 0; i < tokens.length; i++) {
					tokens[i] = secrets[i].getBytes();
				}
				while (buffer.hasRemaining()) {
					final byte current = buffer.get();
					for (int i = 0; i < tokens.length; i++) {
						if (current != tokens[i][pos[i]]) {
							pos[i] = 0;
							continue;
						}
						pos[i]++;
						if (pos[i] == tokens[i].length) {
							return buffer.position() - tokens[i].length;
						}
					}
				}
				return -1;
			} finally {
				buffer.rewind();
			}
		}
	}
	private static final Set<String> NOT_FORWARDED_REQUESTS;

	private static final Set<APITag> NOT_FORWARDED_TAGS;

	private static final String REMOTE_URL = APIProxyServlet.class.getName() + ".remoteUrl";

	private static final String REMOTE_SERVER_IDLE_TIMEOUT = APIProxyServlet.class.getName() + ".remoteServerIdleTimeout";

	static final int PROXY_IDLE_TIMEOUT_DELTA = 5000;

	static {
		final Set<String> requests = new HashSet<>();
		requests.add("getBlockchainStatus");
		requests.add("getState");
		NOT_FORWARDED_REQUESTS = Collections.unmodifiableSet(requests);

		final Set<APITag> tags = new HashSet<>();
		tags.add(APITag.UTILS);
		tags.add(APITag.DEBUG);
		tags.add(APITag.NETWORK);
		NOT_FORWARDED_TAGS = Collections.unmodifiableSet(tags);
	}

	static void initClass() {}

	@Override
	protected void addProxyHeaders(final HttpServletRequest clientRequest, final Request proxyRequest) {

	}

	private MultiMap<String> getRequestParameters(final HttpServletRequest request) {
		final MultiMap<String> parameters = new MultiMap<>();
		final String queryString = request.getQueryString();
		if (queryString != null) {
			UrlEncoded.decodeUtf8To(queryString, parameters);
		}
		return parameters;
	}

	private String getRequestType(final MultiMap<String> parameters) throws ParameterException {
		final String requestType = parameters.getString("requestType");
		if (Convert.emptyToNull(requestType) == null) {
			throw new ParameterException(JSONResponses.PROXY_MISSING_REQUEST_TYPE);
		}

		final APIServlet.APIRequestHandler apiRequestHandler = APIServlet.apiRequestHandlers.get(requestType);
		if (apiRequestHandler == null) {
			if (APIServlet.disabledRequestHandlers.containsKey(requestType)) {
				throw new ParameterException(JSONResponses.ERROR_DISABLED);
			} else {
				throw new ParameterException(JSONResponses.ERROR_INCORRECT_REQUEST);
			}
		}
		return requestType;
	}

	@Override
	public void init(final ServletConfig config) throws ServletException {
		super.init(config);
		config.getServletContext().setAttribute("apiServlet", new APIServlet());
	}

	private boolean initRemoteRequest(final HttpServletRequest clientRequest, final String requestType) {
		StringBuilder uri;
		if (!APIProxy.forcedServerURL.isEmpty()) {
			uri = new StringBuilder();
			uri.append(APIProxy.forcedServerURL);
		} else {
			final Peer servingPeer = APIProxy.getInstance().getServingPeer(requestType);
			if (servingPeer == null) {
				return false;
			}
			uri = servingPeer.getPeerApiUri();
			clientRequest.setAttribute(APIProxyServlet.REMOTE_SERVER_IDLE_TIMEOUT, servingPeer.getApiServerIdleTimeout());
		}
		uri.append("/nxt");
		final String query = clientRequest.getQueryString();
		if (query != null) {
			uri.append("?").append(query);
		}
		clientRequest.setAttribute(APIProxyServlet.REMOTE_URL, uri.toString());
		return true;
	}

	private boolean isForwardable(final String requestType) {
		final APIServlet.APIRequestHandler apiRequestHandler = APIServlet.apiRequestHandlers.get(requestType);
		if (!apiRequestHandler.requireBlockchain()) {
			return false;
		}
		if (apiRequestHandler.requireFullClient()) {
			return false;
		}
		if (APIProxyServlet.NOT_FORWARDED_REQUESTS.contains(requestType)) {
			return false;
		}
		//noinspection RedundantIfStatement
		if (!Collections.disjoint(apiRequestHandler.getAPITags(), APIProxyServlet.NOT_FORWARDED_TAGS)) {
			return false;
		}
		return true;
	}

	@Override
	protected ContentTransformer newClientRequestContentTransformer(final HttpServletRequest clientRequest, final Request proxyRequest) {
		final String contentType = clientRequest.getContentType();
		if ((contentType != null) && contentType.contains("multipart")) {
			return super.newClientRequestContentTransformer(clientRequest, proxyRequest);
		} else {
			if (APIProxy.isActivated() && this.isForwardable(clientRequest.getParameter("requestType"))) {
				return new PasswordFilteringContentTransformer();
			} else {
				return super.newClientRequestContentTransformer(clientRequest, proxyRequest);
			}
		}
	}

	@Override
	protected HttpClient newHttpClient() {
		final SslContextFactory sslContextFactory = new SslContextFactory();

		sslContextFactory.addExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA",
				"SSL_DHE_DSS_WITH_DES_CBC_SHA", "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
				"SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
		sslContextFactory.addExcludeProtocols("SSLv3");
		sslContextFactory.setTrustAll(true);

		return new HttpClient(sslContextFactory);
	}

	@Override
	protected Response.Listener newProxyResponseListener(final HttpServletRequest request, final HttpServletResponse response) {
		return new APIProxyResponseListener(request, response);
	}

	@Override
	protected void onClientRequestFailure(final HttpServletRequest clientRequest, final Request proxyRequest,
			final HttpServletResponse proxyResponse, final Throwable failure) {
		if (failure instanceof PasswordDetectedException) {
			final PasswordDetectedException passwordDetectedException = (PasswordDetectedException) failure;
			try (Writer writer = proxyResponse.getWriter()) {
				JSON.writeJSONString(passwordDetectedException.errorResponse, writer);
				this.sendProxyResponseError(clientRequest, proxyResponse, HttpStatus.OK_200);
			} catch (final IOException e) {
				e.addSuppressed(failure);
				super.onClientRequestFailure(clientRequest, proxyRequest, proxyResponse, e);
			}
		} else {
			super.onClientRequestFailure(clientRequest, proxyRequest, proxyResponse, failure);
		}
	}

	@Override
	protected String rewriteTarget(final HttpServletRequest clientRequest) {

		final Integer timeout = (Integer) clientRequest.getAttribute(APIProxyServlet.REMOTE_SERVER_IDLE_TIMEOUT);
		final HttpClient httpClient = this.getHttpClient();
		if ((timeout != null) && (httpClient != null)) {
			httpClient.setIdleTimeout(Math.max(timeout - APIProxyServlet.PROXY_IDLE_TIMEOUT_DELTA, 0));
		}

		final String remoteUrl = (String) clientRequest.getAttribute(APIProxyServlet.REMOTE_URL);
		final URI rewrittenURI = URI.create(remoteUrl).normalize();
		return rewrittenURI.toString();
	}

	@Override
	protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		JSONStreamAware responseJson = null;
		try {
			if (!API.isAllowed(request.getRemoteHost())) {
				responseJson = JSONResponses.ERROR_NOT_ALLOWED;
				return;
			}
			final MultiMap<String> parameters = this.getRequestParameters(request);
			final String requestType = this.getRequestType(parameters);
			if (APIProxy.isActivated() && this.isForwardable(requestType)) {
				if (parameters.containsKey("secretPhrase") || parameters.containsKey("adminPassword") || parameters.containsKey("sharedKey")) {
					throw new ParameterException(JSONResponses.PROXY_SECRET_DATA_DETECTED);
				}
				if (!this.initRemoteRequest(request, requestType)) {
					responseJson = JSONResponses.API_PROXY_NO_OPEN_API_PEERS;
				} else {
					super.service(request, response);
				}
			} else {
				final APIServlet apiServlet = (APIServlet)request.getServletContext().getAttribute("apiServlet");
				apiServlet.service(request, response);
			}
		} catch (final ParameterException e) {
			responseJson = e.getErrorResponse();
		} finally {
			if (responseJson != null) {
				try {
					try (Writer writer = response.getWriter()) {
						JSON.writeJSONString(responseJson, writer);
					}
				} catch(final IOException e) {
					Logger.logInfoMessage("Failed to write response to client", e);
				}
			}
		}
	}
}
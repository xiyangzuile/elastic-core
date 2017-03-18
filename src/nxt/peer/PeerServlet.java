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

package nxt.peer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import nxt.BlockchainProcessor;
import nxt.Constants;
import nxt.Nxt;
import nxt.util.CountingInputReader;
import nxt.util.CountingOutputWriter;
import nxt.util.JSON;
import nxt.util.Logger;

public final class PeerServlet extends WebSocketServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3383868920184510340L;

	abstract static class PeerRequestHandler {
		abstract JSONStreamAware processRequest(JSONObject request, Peer peer);

		abstract boolean rejectWhileDownloading();
	}

	/**
	 * WebSocket creator for peer connections
	 */
	private class PeerSocketCreator implements WebSocketCreator {
		/**
		 * Create a peer WebSocket
		 *
		 * @param req
		 *            WebSocket upgrade request
		 * @param resp
		 *            WebSocket upgrade response
		 * @return WebSocket
		 */
		@Override
		public Object createWebSocket(final ServletUpgradeRequest req, final ServletUpgradeResponse resp) {
			return Peers.useWebSockets ? new PeerWebSocket(PeerServlet.this) : null;
		}
	}

	private static final Map<String, PeerRequestHandler> peerRequestHandlers;

	static {
		final Map<String, PeerRequestHandler> map = new HashMap<>();
		map.put("addPeers", AddPeers.instance);
		map.put("getCumulativeDifficulty", GetCumulativeDifficulty.instance);
		map.put("getInfo", GetInfo.instance);
		map.put("getMilestoneBlockIds", GetMilestoneBlockIds.instance);
		map.put("getNextBlockIds", GetNextBlockIds.instance);
		map.put("getNextBlocks", GetNextBlocks.instance);
		map.put("getPeers", GetPeers.instance);
		map.put("getTransactions", GetTransactions.instance);
		map.put("getUnconfirmedTransactions", GetUnconfirmedTransactions.instance);
		map.put("processBlock", ProcessBlock.instance);
		map.put("processTransactions", ProcessTransactions.instance);

		// Only activate that servlet command, if we truly intend to become a supernode. Further checks are done deeper in the code
		if(Nxt.supernodePass.length()>0){
			map.put("processSupernodeTransactions", ProcessSupernodeTransactions.instance);
		}

		peerRequestHandlers = Collections.unmodifiableMap(map);
	}
	static final JSONStreamAware UNSUPPORTED_REQUEST_TYPE;

	static {
		final JSONObject response = new JSONObject();
		response.put("error", Errors.UNSUPPORTED_REQUEST_TYPE);
		UNSUPPORTED_REQUEST_TYPE = JSON.prepare(response);
	}
	private static final JSONStreamAware UNSUPPORTED_PROTOCOL;

	static {
		final JSONObject response = new JSONObject();
		response.put("error", Errors.UNSUPPORTED_PROTOCOL);
		UNSUPPORTED_PROTOCOL = JSON.prepare(response);
	}
	private static final JSONStreamAware UNKNOWN_PEER;

	static {
		final JSONObject response = new JSONObject();
		response.put("error", Errors.UNKNOWN_PEER);
		UNKNOWN_PEER = JSON.prepare(response);
	}
	private static final JSONStreamAware SEQUENCE_ERROR;

	static {
		final JSONObject response = new JSONObject();
		response.put("error", Errors.SEQUENCE_ERROR);
		SEQUENCE_ERROR = JSON.prepare(response);
	}
	private static final JSONStreamAware MAX_INBOUND_CONNECTIONS;

	static {
		final JSONObject response = new JSONObject();
		response.put("error", Errors.MAX_INBOUND_CONNECTIONS);
		MAX_INBOUND_CONNECTIONS = JSON.prepare(response);
	}
	private static final JSONStreamAware DOWNLOADING;

	static {
		final JSONObject response = new JSONObject();
		response.put("error", Errors.DOWNLOADING);
		DOWNLOADING = JSON.prepare(response);
	}
	private static final JSONStreamAware LIGHT_CLIENT;

	static {
		final JSONObject response = new JSONObject();
		response.put("error", Errors.LIGHT_CLIENT);
		LIGHT_CLIENT = JSON.prepare(response);
	}

	private static final BlockchainProcessor blockchainProcessor = Nxt.getBlockchainProcessor();

	static JSONStreamAware error(final Exception e) {
		final JSONObject response = new JSONObject();
		response.put("error", Peers.hideErrorDetails ? e.getClass().getName() : e.toString());
		return response;
	}

	/**
	 * Configure the WebSocket factory
	 *
	 * @param factory
	 *            WebSocket factory
	 */
	@Override
	public void configure(final WebSocketServletFactory factory) {
		factory.getPolicy().setIdleTimeout(Peers.webSocketIdleTimeout);
		factory.getPolicy().setMaxBinaryMessageSize(Peers.MAX_MESSAGE_SIZE);
		factory.setCreator(new PeerSocketCreator());
	}

	/**
	 * Process HTTP POST request
	 *
	 * @param req
	 *            HTTP request
	 * @param resp
	 *            HTTP response
	 * @throws ServletException
	 *             Servlet processing error
	 * @throws IOException
	 *             I/O error
	 */
	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
			throws ServletException, IOException {
		JSONStreamAware jsonResponse;
		//
		// Process the peer request
		//
		final PeerImpl peer = Peers.findOrCreatePeer(req.getRemoteAddr());
		if (peer == null) {
			jsonResponse = PeerServlet.UNKNOWN_PEER;
		} else {
			jsonResponse = this.process(peer, req.getReader());
		}
		//
		// Return the response
		//
		resp.setContentType("text/plain; charset=UTF-8");
		try (CountingOutputWriter writer = new CountingOutputWriter(resp.getWriter())) {
			JSON.writeJSONString(jsonResponse, writer);
			if (peer != null) {
				peer.updateUploadedVolume(writer.getCount());
			}
		} catch (RuntimeException | IOException e) {
			if (peer != null) {
				if ((Peers.communicationLoggingMask & Peers.LOGGING_MASK_EXCEPTIONS) != 0) {
					if (e instanceof RuntimeException) {
						Logger.logDebugMessage("Error sending response to peer " + peer.getHost(), e);
					} else {
						Logger.logDebugMessage(String.format("Error sending response to peer %s: %s", peer.getHost(),
								e.getMessage() != null ? e.getMessage() : e.toString()));
					}
				}
				peer.blacklist(e);
			}
			throw e;
		}
	}

	/**
	 * Process WebSocket POST request
	 *
	 * @param webSocket
	 *            WebSocket for the connection
	 * @param requestId
	 *            Request identifier
	 * @param request
	 *            Request message
	 */
	void doPost(final PeerWebSocket webSocket, final long requestId, final String request) {
		JSONStreamAware jsonResponse;
		//
		// Process the peer request
		//
		final InetSocketAddress socketAddress = webSocket.getRemoteAddress();
		if (socketAddress == null) {
			return;
		}
		final String remoteAddress = socketAddress.getHostString();
		final PeerImpl peer = Peers.findOrCreatePeer(remoteAddress);
		if (peer == null) {
			jsonResponse = PeerServlet.UNKNOWN_PEER;
		} else {
			peer.setInboundWebSocket(webSocket);
			jsonResponse = this.process(peer, new StringReader(request));
		}
		//
		// Return the response
		//
		try {
			final StringWriter writer = new StringWriter(1000);
			JSON.writeJSONString(jsonResponse, writer);
			final String response = writer.toString();
			webSocket.sendResponse(requestId, response);
			if (peer != null) {
				peer.updateUploadedVolume(response.length());
			}
		} catch (RuntimeException | IOException e) {
			if (peer != null) {
				if ((Peers.communicationLoggingMask & Peers.LOGGING_MASK_EXCEPTIONS) != 0) {
					if (e instanceof RuntimeException) {
						Logger.logDebugMessage("Error sending response to peer " + peer.getHost(), e);
					} else {
						Logger.logDebugMessage(String.format("Error sending response to peer %s: %s", peer.getHost(),
								e.getMessage() != null ? e.getMessage() : e.toString()));
					}
				}
				peer.blacklist(e);
			}
		}
	}

	/**
	 * Process the peer request
	 *
	 * @param peer
	 *            Peer
	 * @param inputReader
	 *            Input reader
	 * @return JSON response
	 */
	private JSONStreamAware process(final PeerImpl peer, final Reader inputReader) {
		//
		// Check for blacklisted peer
		//
		if (peer.isBlacklisted()) {
			final JSONObject jsonObject = new JSONObject();
			jsonObject.put("error", Errors.BLACKLISTED);
			jsonObject.put("cause", peer.getBlacklistingCause());
			return jsonObject;
		}
		Peers.addPeer(peer);
		//
		// Process the request
		//
		try (CountingInputReader cr = new CountingInputReader(inputReader, Peers.MAX_REQUEST_SIZE)) {
			final JSONObject request = (JSONObject) JSONValue.parseWithException(cr);
			peer.updateDownloadedVolume(cr.getCount());
			if ((request.get("protocol") == null) || (((Number) request.get("protocol")).intValue() != 1)) {
				Logger.logInfoMessage("Unsupported protocol " + request.get("protocol"));
				return PeerServlet.UNSUPPORTED_PROTOCOL;
			}
			final PeerRequestHandler peerRequestHandler = PeerServlet.peerRequestHandlers
					.get(request.get("requestType"));
			if (peerRequestHandler == null) {
				return PeerServlet.UNSUPPORTED_REQUEST_TYPE;
			}
			if (peer.getState() == Peer.State.DISCONNECTED) {
				peer.setState(Peer.State.CONNECTED);
			}
			if ((peer.getVersion() == null) && !"getInfo".equals(request.get("requestType"))) {
				return PeerServlet.SEQUENCE_ERROR;
			}
			if (!peer.isInbound()) {
				if (Peers.hasTooManyInboundPeers()) {
					return PeerServlet.MAX_INBOUND_CONNECTIONS;
				}
				Peers.notifyListeners(peer, Peers.Event.ADD_INBOUND);
			}
			peer.setLastInboundRequest(Nxt.getEpochTime());
			if (peerRequestHandler.rejectWhileDownloading()) {
				if (PeerServlet.blockchainProcessor.isDownloading()) {
					return PeerServlet.DOWNLOADING;
				}
				if (Constants.isLightClient) {
					return PeerServlet.LIGHT_CLIENT;
				}
			}
			return peerRequestHandler.processRequest(request, peer);
		} catch (RuntimeException | ParseException | IOException e) {
			Logger.logInfoMessage("Error processing POST request: " + e.toString());
			peer.blacklist(e);
			return PeerServlet.error(e);
		}
	}
}

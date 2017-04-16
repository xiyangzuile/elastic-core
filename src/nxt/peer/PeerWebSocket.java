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

package nxt.peer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeException;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import nxt.util.Logger;
import nxt.util.QueuedThreadPool;

/**
 * PeerWebSocket represents an HTTP/HTTPS upgraded connection
 */
@WebSocket
public class PeerWebSocket {

	/**
	 * POST request
	 */
	private class PostRequest {

		/** Request latch */
		private final CountDownLatch latch = new CountDownLatch(1);

		/** Response message */
		private volatile String response;

		/** Socket exception */
		private volatile IOException exception;

		/**
		 * Create a post request
		 */
		public PostRequest() {
		}

		/**
		 * Complete the request with an exception
		 *
		 * The caller must hold the lock for the request condition
		 *
		 * @param exception
		 *            I/O exception
		 */
		public void complete(final IOException exception) {
			this.exception = exception;
			this.latch.countDown();
		}

		/**
		 * Complete the request with a response message
		 *
		 * The caller must hold the lock for the request condition
		 *
		 * @param response
		 *            Response message
		 */
		public void complete(final String response) {
			this.response = response;
			this.latch.countDown();
		}

		/**
		 * Wait for the response
		 *
		 * The caller must hold the lock for the request condition
		 *
		 * @param timeout
		 *            Wait timeout
		 * @param unit
		 *            Time unit
		 * @return Response message
		 * @throws InterruptedException
		 *             Wait interrupted
		 * @throws IOException
		 *             I/O error occurred
		 */
		public String get(final long timeout, final TimeUnit unit) throws InterruptedException, IOException {
			if (!this.latch.await(timeout, unit)) throw new SocketTimeoutException("WebSocket read timeout exceeded");
			if (this.exception != null) throw this.exception;
			return this.response;
		}
	}

	/** Compressed message flag */
	private static final int FLAG_COMPRESSED = 1;

	/** Our WebSocket message version */
	private static final int VERSION = 1;
	/** Create the WebSocket client */
	private static WebSocketClient peerClient;

	static {
		try {
			PeerWebSocket.peerClient = new WebSocketClient();
			PeerWebSocket.peerClient.getPolicy().setIdleTimeout(Peers.webSocketIdleTimeout);
			PeerWebSocket.peerClient.getPolicy().setMaxBinaryMessageSize(Peers.MAX_MESSAGE_SIZE);
			PeerWebSocket.peerClient.setConnectTimeout(Peers.connectTimeout);
			PeerWebSocket.peerClient.start();
		} catch (final Exception exc) {
			Logger.logErrorMessage("Unable to start WebSocket client", exc);
			PeerWebSocket.peerClient = null;
		}
	}

	/** Thread pool for server request processing */
	private static final ExecutorService threadPool = new QueuedThreadPool(Runtime.getRuntime().availableProcessors(),
			Runtime.getRuntime().availableProcessors() * 4);

	/** Negotiated WebSocket message version */
	private int version = PeerWebSocket.VERSION;

	/** WebSocket session */
	private volatile Session session;

	/** WebSocket endpoint - set for an accepted connection */
	private final PeerServlet peerServlet;

	/** WebSocket lock */
	private final ReentrantLock lock = new ReentrantLock();

	/** Pending POST request map */
	private final ConcurrentHashMap<Long, PostRequest> requestMap = new ConcurrentHashMap<>();

	/** Next POST request identifier */
	private long nextRequestId = 0;

	/** WebSocket connection timestamp */
	private long connectTime = 0;

	/**
	 * Create a client socket
	 */
	public PeerWebSocket() {
		this.peerServlet = null;
	}

	/**
	 * Create a server socket
	 *
	 * @param peerServlet
	 *            Servlet for request processing
	 */
	public PeerWebSocket(final PeerServlet peerServlet) {
		this.peerServlet = peerServlet;
	}

	/**
	 * Close the WebSocket
	 */
	public void close() {
		this.lock.lock();
		try {
			if ((this.session != null) && this.session.isOpen()) this.session.close();
		} catch (final Exception exc) {
			Logger.logDebugMessage("Exception while closing WebSocket", exc);
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Process a POST request by sending the request message and then waiting
	 * for a response. This method is used by the connection originator.
	 *
	 * @param request
	 *            Request message
	 * @return Response message
	 * @throws IOException
	 *             I/O error occurred
	 */
	public String doPost(final String request) throws IOException {
		long requestId;
		//
		// Send the POST request
		//
		this.lock.lock();
		try {
			if ((this.session == null) || !this.session.isOpen())
                throw new IOException("WebSocket session is not open");
			requestId = this.nextRequestId++;
			byte[] requestBytes = request.getBytes("UTF-8");
			final int requestLength = requestBytes.length;
			int flags = 0;
			if (Peers.isGzipEnabled && (requestLength >= Peers.MIN_COMPRESS_SIZE)) {
				flags |= PeerWebSocket.FLAG_COMPRESSED;
				final ByteArrayOutputStream outStream = new ByteArrayOutputStream(requestLength);
				try (GZIPOutputStream gzipStream = new GZIPOutputStream(outStream)) {
					gzipStream.write(requestBytes);
				}
				requestBytes = outStream.toByteArray();
			}
			final ByteBuffer buf = ByteBuffer.allocate(requestBytes.length + 20);
			buf.putInt(this.version).putLong(requestId).putInt(flags).putInt(requestLength).put(requestBytes).flip();
			if (buf.limit() > Peers.MAX_MESSAGE_SIZE)
                throw new ProtocolException("POST request length exceeds max message size");
			this.session.getRemote().sendBytes(buf);
		} catch (final WebSocketException exc) {
			throw new SocketException(exc.getMessage());
		} finally {
			this.lock.unlock();
		}
		//
		// Get the response
		//
		String response;
		try {
			final PostRequest postRequest = new PostRequest();
			this.requestMap.put(requestId, postRequest);
			response = postRequest.get(Peers.readTimeout, TimeUnit.MILLISECONDS);
		} catch (final InterruptedException exc) {
			throw new SocketTimeoutException("WebSocket POST interrupted");
		}
		return response;
	}

	/**
	 * Return the remote address for this connection
	 *
	 * @return Remote address or null if the connection is closed
	 */
	public InetSocketAddress getRemoteAddress() {
		Session s;
		return (((s = this.session) != null) && s.isOpen() ? s.getRemoteAddress() : null);
	}

	/**
	 * Check if we have a WebSocket connection
	 *
	 * @return TRUE if we have a WebSocket connection
	 */
	public boolean isOpen() {
		Session s;
		return (((s = this.session) != null) && s.isOpen());
	}

	/**
	 * WebSocket session has been closed
	 *
	 * @param statusCode
	 *            Status code
	 * @param reason
	 *            Reason message
	 */
	@OnWebSocketClose
	public void onClose(final int statusCode, final String reason) {
		this.lock.lock();
		try {
			if (this.session != null) {
				if ((Peers.communicationLoggingMask & Peers.LOGGING_MASK_200_RESPONSES) != 0) {
					String shost = null;
					final InetSocketAddress addr = this.session.getRemoteAddress();
					if (addr != null) shost = addr.getHostString();
					if (shost == null) shost = "unknown address";

					Logger.logDebugMessage(String.format("%s WebSocket connection with %s closed",
							this.peerServlet != null ? "Inbound" : "Outbound", shost));
				}
				this.session = null;
			}
			final SocketException exc = new SocketException("WebSocket connection closed");
			final Set<Map.Entry<Long, PostRequest>> requests = this.requestMap.entrySet();
			requests.forEach((entry) -> entry.getValue().complete(exc));
			this.requestMap.clear();
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * WebSocket connection complete
	 *
	 * @param session
	 *            WebSocket session
	 */
	@OnWebSocketConnect
	public void onConnect(final Session session) {
		this.session = session;
		if ((Peers.communicationLoggingMask & Peers.LOGGING_MASK_200_RESPONSES) != 0) {
			String shost = null;
			final InetSocketAddress addr = session.getRemoteAddress();
			if (addr != null) shost = addr.getHostString();
			if (shost == null) shost = "unknown address";
			Logger.logDebugMessage(String.format("%s WebSocket connection with %s completed",
					this.peerServlet != null ? "Inbound" : "Outbound", shost));
		}
	}

	/**
	 * Process a socket message
	 *
	 * @param inbuf
	 *            Message buffer
	 * @param off
	 *            Starting offset
	 * @param len
	 *            Message length
	 */
	@OnWebSocketMessage
	public void onMessage(final byte[] inbuf, final int off, final int len) {
		this.lock.lock();
		try {
			final ByteBuffer buf = ByteBuffer.wrap(inbuf, off, len);
			this.version = Math.min(buf.getInt(), PeerWebSocket.VERSION);
			final Long requestId = buf.getLong();
			final int flags = buf.getInt();
			final int length = buf.getInt();
			byte[] msgBytes = new byte[buf.remaining()];
			buf.get(msgBytes);
			if ((flags & PeerWebSocket.FLAG_COMPRESSED) != 0) {
				final ByteArrayInputStream inStream = new ByteArrayInputStream(msgBytes);
				try (GZIPInputStream gzipStream = new GZIPInputStream(inStream, 1024)) {
					msgBytes = new byte[length];
					int offset = 0;
					while (offset < msgBytes.length) {
						final int count = gzipStream.read(msgBytes, offset, msgBytes.length - offset);
						if (count < 0) throw new EOFException("End-of-data reading compressed data");
						offset += count;
					}
				}
			}
			final String message = new String(msgBytes, "UTF-8");
			if (this.peerServlet != null)
                PeerWebSocket.threadPool.execute(() -> this.peerServlet.doPost(this, requestId, message));
            else {
				final PostRequest postRequest = this.requestMap.remove(requestId);
				if (postRequest != null) postRequest.complete(message);
			}
		} catch (final Exception exc) {
			Logger.logDebugMessage("Exception while processing WebSocket message", exc);
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Send POST response
	 *
	 * This method is used by the connection acceptor to return the POST
	 * response
	 *
	 * @param requestId
	 *            Request identifier
	 * @param response
	 *            Response message
	 * @throws IOException
	 *             I/O error occurred
	 */
	public void sendResponse(final long requestId, final String response) throws IOException {
		this.lock.lock();
		try {
			if ((this.session != null) && this.session.isOpen()) {
				byte[] responseBytes = response.getBytes("UTF-8");
				final int responseLength = responseBytes.length;
				int flags = 0;
				if (Peers.isGzipEnabled && (responseLength >= Peers.MIN_COMPRESS_SIZE)) {
					flags |= PeerWebSocket.FLAG_COMPRESSED;
					final ByteArrayOutputStream outStream = new ByteArrayOutputStream(responseLength);
					try (GZIPOutputStream gzipStream = new GZIPOutputStream(outStream)) {
						gzipStream.write(responseBytes);
					}
					responseBytes = outStream.toByteArray();
				}
				final ByteBuffer buf = ByteBuffer.allocate(responseBytes.length + 20);
				buf.putInt(this.version).putLong(requestId).putInt(flags).putInt(responseLength).put(responseBytes)
						.flip();
				if (buf.limit() > Peers.MAX_MESSAGE_SIZE)
                    throw new ProtocolException("POST response length exceeds max message size");
				this.session.getRemote().sendBytes(buf);
			}
		} catch (final WebSocketException exc) {
			throw new SocketException(exc.getMessage());
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Start a client session
	 *
	 * @param uri
	 *            Server URI
	 * @return TRUE if the WebSocket connection was completed
	 * @throws IOException
	 *             I/O error occurred
	 */
	public boolean startClient(final URI uri) throws IOException {
		if (PeerWebSocket.peerClient == null) return false;
		final String address = String.format("%s:%d", uri.getHost(), uri.getPort());
		boolean useWebSocket = false;
		//
		// Create a WebSocket connection. We need to serialize the connection
		// requests
		// since the NRS server will issue multiple concurrent requests to the
		// same peer.
		// After a successful connection, the subsequent connection requests
		// will return
		// immediately. After an unsuccessful connection, a new connect attempt
		// will not
		// be done until 60 seconds have passed.
		//
		this.lock.lock();
		try {
			if (this.session != null) useWebSocket = true;
            else if (System.currentTimeMillis() > (this.connectTime + (10 * 1000))) {
				this.connectTime = System.currentTimeMillis();
				final ClientUpgradeRequest req = new ClientUpgradeRequest();
				final Future<Session> conn = PeerWebSocket.peerClient.connect(this, uri, req);
				conn.get(Peers.connectTimeout + 100, TimeUnit.MILLISECONDS);
				useWebSocket = true;
			}
		} catch (final ExecutionException exc) {
			if (exc.getCause() instanceof UpgradeException) {
				// We will use HTTP
			} else // We will use HTTP
                // Report I/O exception
                if (exc.getCause() instanceof IOException) throw (IOException) exc.getCause();
                else Logger.logDebugMessage(String.format("WebSocket connection to %s failed", address), exc);
		} catch (final TimeoutException exc) {
			throw new SocketTimeoutException(String.format("WebSocket connection to %s timed out", address));
		} catch (final IllegalStateException exc) {
			if (!PeerWebSocket.peerClient.isStarted()) {
				Logger.logDebugMessage("WebSocket client not started or shutting down");
				throw exc;
			}
			Logger.logDebugMessage(String.format("WebSocket connection to %s failed", address), exc);
		} catch (final Exception exc) {
			Logger.logDebugMessage(String.format("WebSocket connection to %s failed", address), exc);
		} finally {
			if (!useWebSocket) this.close();
			this.lock.unlock();
		}
		return useWebSocket;
	}
}

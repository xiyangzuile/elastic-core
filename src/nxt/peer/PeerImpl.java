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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import nxt.Account;
import nxt.BlockchainProcessor;
import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.http.API;
import nxt.http.APIEnum;
import nxt.util.Convert;
import nxt.util.CountingInputReader;
import nxt.util.CountingInputStream;
import nxt.util.CountingOutputWriter;
import nxt.util.JSON;
import nxt.util.Logger;

final class PeerImpl implements Peer {

	private final String host;
	private final PeerWebSocket webSocket;
	private volatile PeerWebSocket inboundSocket;
	private volatile boolean useWebSocket;
	private volatile String announcedAddress;
	private volatile int port;
	private volatile boolean shareAddress;
	private volatile Hallmark hallmark;
	private volatile String platform;
	private volatile String application;
	private volatile int apiPort;
	private volatile int apiSSLPort;
	private volatile EnumSet<APIEnum> disabledAPIs;
	private volatile int apiServerIdleTimeout;
	private volatile String version;
	private volatile boolean isOldVersion;
	private volatile long adjustedWeight;
	private volatile int blacklistingTime;
	private volatile String blacklistingCause;
	private volatile State state;
	private volatile long downloadedVolume;
	private volatile long uploadedVolume;
	private volatile int lastUpdated;
	private volatile int lastConnectAttempt;
	private volatile int lastInboundRequest;
	private volatile long hallmarkBalance = -1;
	private volatile int hallmarkBalanceHeight;
	private volatile long services;
	private volatile BlockchainState blockchainState;


	PeerImpl(final String host, final String announcedAddress) {
		this.host = host;
		this.announcedAddress = announcedAddress;
		try {
			this.port = new URI("http://" + announcedAddress).getPort();
		} catch (final URISyntaxException ignore) {
		}
		this.state = State.NON_CONNECTED;
		this.shareAddress = true;
		this.webSocket = new PeerWebSocket();
		this.useWebSocket = Peers.useWebSockets && !Peers.useProxy;
		this.disabledAPIs = EnumSet.noneOf(APIEnum.class);
		this.apiServerIdleTimeout = API.apiServerIdleTimeout;
		this.blockchainState = BlockchainState.UP_TO_DATE;
	}

	private void addService(final Service service, final boolean doNotify) {
		boolean notifyListeners;
		synchronized (this) {
			notifyListeners = ((this.services & service.getCode()) == 0);
			this.services |= service.getCode();
		}
		if (notifyListeners && doNotify) {
			Peers.notifyListeners(this, Peers.Event.CHANGED_SERVICES);
		}
	}

	boolean analyzeHallmark(final String hallmarkString) {
		if (Constants.isLightClient) {
			return true;
		}

		if ((hallmarkString == null) && (this.hallmark == null)) {
			return true;
		}

		if ((this.hallmark != null) && this.hallmark.getHallmarkString().equals(hallmarkString)) {
			return true;
		}

		if (hallmarkString == null) {
			this.unsetHallmark();
			return true;
		}

		try {

			final Hallmark hallmark = Hallmark.parseHallmark(hallmarkString);
			if (!hallmark.isValid()) {
				Logger.logDebugMessage("Invalid hallmark " + hallmarkString + " for " + this.host);
				this.unsetHallmark();
				return false;
			}
			if (!hallmark.getHost().equals(this.host)) {
				final InetAddress hostAddress = InetAddress.getByName(this.host);
				boolean validHost = false;
				for (final InetAddress nextHallmark : InetAddress.getAllByName(hallmark.getHost())) {
					if (hostAddress.equals(nextHallmark)) {
						validHost = true;
						break;
					}
				}
				if (!validHost) {
					Logger.logDebugMessage("Hallmark host " + hallmark.getHost() + " doesn't match " + this.host);
					this.unsetHallmark();
					return false;
				}
			}
			this.setHallmark(hallmark);
			final long accountId = Account.getId(hallmark.getPublicKey());
			final List<PeerImpl> groupedPeers = new ArrayList<>();
			int mostRecentDate = 0;
			long totalWeight = 0;
			for (final PeerImpl peer : Peers.allPeers) {
				if (peer.hallmark == null) {
					continue;
				}
				if (accountId == peer.hallmark.getAccountId()) {
					groupedPeers.add(peer);
					if (peer.hallmark.getDate() > mostRecentDate) {
						mostRecentDate = peer.hallmark.getDate();
						totalWeight = peer.getHallmarkWeight(mostRecentDate);
					} else {
						totalWeight += peer.getHallmarkWeight(mostRecentDate);
					}
				}
			}

			for (final PeerImpl peer : groupedPeers) {
				if (totalWeight == 0) {
					peer.adjustedWeight = 0;
				} else {
					peer.adjustedWeight = (Constants.MAX_BALANCE_NXT * peer.getHallmarkWeight(mostRecentDate))
							/ totalWeight;
				}
				Peers.notifyListeners(peer, Peers.Event.WEIGHT);
			}

			return true;

		} catch (final UnknownHostException ignore) {
		} catch (final RuntimeException e) {
			Logger.logDebugMessage("Failed to analyze hallmark for peer " + this.host + ", " + e.toString(), e);
		}
		this.unsetHallmark();
		return false;

	}

	@Override
	public boolean isSupernode() {
		return Peers.getPotentialSNPeers().contains(this.getAnnouncedAddress()) || Peers.getPotentialSNPeers().contains(this.getHost());
	}


	@Override
	public void blacklist(final Exception cause) {
		if ((cause instanceof NxtException.NotCurrentlyValidException)
				|| (cause instanceof NxtException.LostValidityException)
				|| (cause instanceof BlockchainProcessor.BlockOutOfOrderException) || (cause instanceof SQLException)
				|| (cause.getCause() instanceof SQLException)) {
			// don't blacklist peers just because a feature is not yet enabled,
			// or because of database timeouts
			// prevents erroneous blacklisting during loading of blockchain from
			// scratch
			return;
		}
		if ((cause instanceof ParseException) && Errors.END_OF_FILE.equals(cause.toString())) {
			return;
		}
		if (!this.isBlacklisted()) {
			if ((cause instanceof IOException) || (cause instanceof ParseException)
					|| (cause instanceof IllegalArgumentException)) {
				Logger.logDebugMessage("Blacklisting " + this.host + " because of: " + cause.toString());
			} else {
				Logger.logDebugMessage("Blacklisting " + this.host + " because of: " + cause.toString(), cause);
			}
		}
		this.blacklist(
				(cause.toString() == null) || Peers.hideErrorDetails ? cause.getClass().getName() : cause.toString());
	}

	@Override
	public void blacklist(final String cause) {
		this.blacklistingTime = Nxt.getEpochTime();
		this.blacklistingCause = cause;
		this.setState(State.NON_CONNECTED);
		this.lastInboundRequest = 0;
		Peers.notifyListeners(this, Peers.Event.BLACKLIST);
	}

	@Override
	public int compareTo(final Peer o) {
		if (this.getWeight() > o.getWeight()) {
			return -1;
		} else if (this.getWeight() < o.getWeight()) {
			return 1;
		}
		return this.getHost().compareTo(o.getHost());
	}

	void connect() {
		this.lastConnectAttempt = Nxt.getEpochTime();
		try {
			if (!Peers.ignorePeerAnnouncedAddress && (this.announcedAddress != null)) {
				try {
					final URI uri = new URI("http://" + this.announcedAddress);
					final InetAddress inetAddress = InetAddress.getByName(uri.getHost());
					if (!inetAddress.equals(InetAddress.getByName(this.host))) {
						Logger.logDebugMessage("Connect: announced address " + this.announcedAddress + " now points to "
								+ inetAddress.getHostAddress() + ", replacing peer " + this.host);
						Peers.removePeer(this);
						final PeerImpl newPeer = Peers.findOrCreatePeer(inetAddress, this.announcedAddress, true);
						if (newPeer != null) {
							Peers.addPeer(newPeer);
							newPeer.connect();
						}
						return;
					}
				} catch (URISyntaxException | UnknownHostException e) {
					this.blacklist(e);
					return;
				}
			}
			final JSONObject response = this.send(Peers.getMyPeerInfoRequest());
			if (response != null) {
				if (response.get("error") != null) {
					this.setState(State.NON_CONNECTED);
					return;
				}
				final String servicesString = (String) response.get("services");
				final long origServices = this.services;
				this.services = (servicesString != null ? Long.parseUnsignedLong(servicesString) : 0);
				this.setApplication((String) response.get("application"));
				this.setApiPort(response.get("apiPort"));
				this.setApiSSLPort(response.get("apiSSLPort"));
				this.setDisabledAPIs(response.get("disabledAPIs"));
				this.setApiServerIdleTimeout(response.get("apiServerIdleTimeout"));
				this.setBlockchainState(response.get("blockchainState"));
				this.lastUpdated = this.lastConnectAttempt;
				this.setVersion((String) response.get("version"));
				this.setPlatform((String) response.get("platform"));
				this.shareAddress = Boolean.TRUE.equals(response.get("shareAddress"));
				this.analyzeHallmark((String) response.get("hallmark"));

				if (!Peers.ignorePeerAnnouncedAddress) {
					String newAnnouncedAddress = Convert.emptyToNull((String) response.get("announcedAddress"));
					if (newAnnouncedAddress != null) {
						newAnnouncedAddress = Peers.addressWithPort(newAnnouncedAddress.toLowerCase());
						if (newAnnouncedAddress != null) {
							if (!this.verifyAnnouncedAddress(newAnnouncedAddress)) {
								Logger.logDebugMessage(
										"Connect: new announced address for " + this.host + " not accepted");
								if (!this.verifyAnnouncedAddress(this.announcedAddress)) {
									Logger.logDebugMessage(
											"Connect: old announced address for " + this.host + " no longer valid");
									Peers.setAnnouncedAddress(this, this.host);
								}
								this.setState(State.NON_CONNECTED);
								return;
							}
							if (!newAnnouncedAddress.equals(this.announcedAddress)) {
								Logger.logDebugMessage("Connect: peer " + this.host + " has new announced address "
										+ newAnnouncedAddress + ", old is " + this.announcedAddress);
								final int oldPort = this.getPort();
								Peers.setAnnouncedAddress(this, newAnnouncedAddress);
								if (this.getPort() != oldPort) {
									// force checking connectivity to new
									// announced port
									this.setState(State.NON_CONNECTED);
									return;
								}
							}
						}
					} else {
						Peers.setAnnouncedAddress(this, this.host);
					}
				}

				if (this.announcedAddress == null) {
					if ((this.hallmark == null) || (this.hallmark.getPort() == Peers.getDefaultPeerPort())) {
						Peers.setAnnouncedAddress(this, this.host);
						Logger.logDebugMessage("Connected to peer without announced address, setting to " + this.host);
					} else {
						this.setState(State.NON_CONNECTED);
						return;
					}
				}

				if (!this.isOldVersion) {
					this.setState(State.CONNECTED);
					if (this.services != origServices) {
						Peers.notifyListeners(this, Peers.Event.CHANGED_SERVICES);
					}
				} else if (!this.isBlacklisted()) {
					this.blacklist("Old version: " + this.version);
				}
			} else {
				// Logger.logDebugMessage("Failed to connect to peer " +
				// peerAddress);
				this.setState(State.NON_CONNECTED);
			}
		} catch (final RuntimeException e) {
			this.blacklist(e);
		}
	}

	@Override
	public void deactivate() {
		if (this.state == State.CONNECTED) {
			this.setState(State.DISCONNECTED);
		} else {
			this.setState(State.NON_CONNECTED);
		}
		Peers.notifyListeners(this, Peers.Event.DEACTIVATE);
	}

	@Override
	public String getAnnouncedAddress() {
		return this.announcedAddress;
	}

	@Override
	public int getApiPort() {
		return this.apiPort;
	}

	@Override
	public int getApiServerIdleTimeout() {
		return this.apiServerIdleTimeout;
	}

	@Override
	public int getApiSSLPort() {
		return this.apiSSLPort;
	}

	@Override
	public String getApplication() {
		return this.application;
	}

	@Override
	public String getBlacklistingCause() {
		return this.blacklistingCause == null ? "unknown" : this.blacklistingCause;
	}

	@Override
	public BlockchainState getBlockchainState() {
		return this.blockchainState;
	}

	@Override
	public Set<APIEnum> getDisabledAPIs() {
		return Collections.unmodifiableSet(this.disabledAPIs);
	}

	@Override
	public long getDownloadedVolume() {
		return this.downloadedVolume;
	}

	@Override
	public Hallmark getHallmark() {
		return this.hallmark;
	}

	private int getHallmarkWeight(final int date) {
		if ((this.hallmark == null) || !this.hallmark.isValid() || (this.hallmark.getDate() != date)) {
			return 0;
		}
		return this.hallmark.getWeight();
	}

	@Override
	public String getHost() {
		return this.host;
	}

	@Override
	public int getLastConnectAttempt() {
		return this.lastConnectAttempt;
	}

	int getLastInboundRequest() {
		return this.lastInboundRequest;
	}

	@Override
	public int getLastUpdated() {
		return this.lastUpdated;
	}

	@Override
	public StringBuilder getPeerApiUri() {
		final StringBuilder uri = new StringBuilder();
		if (this.providesService(Peer.Service.API_SSL)) {
			uri.append("https://");
		} else {
			uri.append("http://");
		}
		uri.append(this.host).append(":");
		if (this.providesService(Peer.Service.API_SSL)) {
			uri.append(this.apiSSLPort);
		} else {
			uri.append(this.apiPort);
		}
		return uri;
	}

	@Override
	public String getPlatform() {
		return this.platform;
	}

	@Override
	public int getPort() {
		return this.port <= 0 ? Peers.getDefaultPeerPort() : this.port;
	}

	long getServices() {
		synchronized (this) {
			return this.services;
		}
	}

	@Override
	public String getSoftware() {
		return Convert.truncate(this.application, "?", 10, false) + " ("
				+ Convert.truncate(this.version, "?", 10, false) + ")" + " @ "
				+ Convert.truncate(this.platform, "?", 10, false);
	}

	@Override
	public State getState() {
		return this.state;
	}

	@Override
	public long getUploadedVolume() {
		return this.uploadedVolume;
	}

	@Override
	public String getVersion() {
		return this.version;
	}

	@Override
	public int getWeight() {
		if (this.hallmark == null) {
			return 0;
		}
		if ((this.hallmarkBalance == -1) || (this.hallmarkBalanceHeight < (Nxt.getBlockchain().getHeight() - 60))) {
			final long accountId = this.hallmark.getAccountId();
			final Account account = Account.getAccount(accountId);
			this.hallmarkBalance = account == null ? 0 : account.getBalanceNQT();
			this.hallmarkBalanceHeight = Nxt.getBlockchain().getHeight();
		}
		return (int) ((this.adjustedWeight * (this.hallmarkBalance / Constants.ONE_NXT)) / Constants.MAX_BALANCE_NXT);
	}

	@Override
	public boolean isApiConnectable() {
		return this.isOpenAPI() && (this.state == Peer.State.CONNECTED)
				&& !Peers.isOldVersion(this.version, Constants.MIN_PROXY_VERSION) && !Peers.isNewVersion(this.version)
				&& (this.blockchainState == Peer.BlockchainState.UP_TO_DATE);
	}

	@Override
	public boolean isBlacklisted() {
		return (this.blacklistingTime > 0) || this.isOldVersion || Peers.knownBlacklistedPeers.contains(this.host)
				|| ((this.announcedAddress != null) && Peers.knownBlacklistedPeers.contains(this.announcedAddress));
	}

	@Override
	public boolean isInbound() {
		return this.lastInboundRequest != 0;
	}

	@Override
	public boolean isInboundWebSocket() {
		PeerWebSocket s;
		return (((s = this.inboundSocket) != null) && s.isOpen());
	}

	@Override
	public boolean isOpenAPI() {
		return this.providesService(Peer.Service.API) || this.providesService(Peer.Service.API_SSL);
	}

	@Override
	public boolean isOutboundWebSocket() {
		return this.webSocket.isOpen();
	}

	@Override
	public boolean providesService(final Service service) {
		boolean isProvided;
		synchronized (this) {
			isProvided = ((this.services & service.getCode()) != 0);
		}
		return isProvided;
	}

	@Override
	public boolean providesServices(final long services) {
		boolean isProvided;
		synchronized (this) {
			isProvided = (services & this.services) == services;
		}
		return isProvided;
	}

	@Override
	public void remove() {
		this.webSocket.close();
		Peers.removePeer(this);
		Peers.notifyListeners(this, Peers.Event.REMOVE);
	}

	private void removeService(final Service service, final boolean doNotify) {
		boolean notifyListeners;
		synchronized (this) {
			notifyListeners = ((this.services & service.getCode()) != 0);
			this.services &= (~service.getCode());
		}
		if (notifyListeners && doNotify) {
			Peers.notifyListeners(this, Peers.Event.CHANGED_SERVICES);
		}
	}

	@Override
	public JSONObject send(final JSONStreamAware request) {
		return this.send(request, Peers.MAX_RESPONSE_SIZE);
	}

	@Override
	public JSONObject send(final JSONStreamAware request, final int maxResponseSize) {
		JSONObject response = null;
		String log = null;
		boolean showLog = false;
		HttpURLConnection connection = null;
		final int communicationLoggingMask = Peers.communicationLoggingMask;

		try {
			//
			// Create a new WebSocket session if we don't have one
			//
			if (this.useWebSocket && !this.webSocket.isOpen()) {
				this.useWebSocket = this.webSocket
						.startClient(URI.create("ws://" + this.host + ":" + this.getPort() + "/nxt"));
			}
			//
			// Send the request and process the response
			//
			if (this.useWebSocket) {
				//
				// Send the request using the WebSocket session
				//
				final StringWriter wsWriter = new StringWriter(1000);
				request.writeJSONString(wsWriter);
				final String wsRequest = wsWriter.toString();
				if (communicationLoggingMask != 0) {
					log = "WebSocket " + this.host + ": " + wsRequest;
				}
				final String wsResponse = this.webSocket.doPost(wsRequest);
				this.updateUploadedVolume(wsRequest.length());
				if (maxResponseSize > 0) {
					if ((communicationLoggingMask & Peers.LOGGING_MASK_200_RESPONSES) != 0) {
						log += " >>> " + wsResponse;
						showLog = true;
					}
					if (wsResponse.length() > maxResponseSize) {
						throw new NxtException.NxtIOException("Maximum size exceeded: " + wsResponse.length());
					}
					response = (JSONObject) JSONValue.parseWithException(wsResponse);
					this.updateDownloadedVolume(wsResponse.length());
				}
			} else {
				//
				// Send the request using HTTP
				//
				final URL url = new URL("http://" + this.host + ":" + this.getPort() + "/nxt");
				if (communicationLoggingMask != 0) {
					log = "\"" + url.toString() + "\": " + JSON.toString(request);
				}
				connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("POST");
				connection.setDoOutput(true);
				connection.setConnectTimeout(Peers.connectTimeout);
				connection.setReadTimeout(Peers.readTimeout);
				connection.setRequestProperty("Accept-Encoding", "gzip");
				connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
				try (Writer writer = new BufferedWriter(
						new OutputStreamWriter(connection.getOutputStream(), "UTF-8"))) {
					final CountingOutputWriter cow = new CountingOutputWriter(writer);
					request.writeJSONString(cow);
					this.updateUploadedVolume(cow.getCount());
				}
				if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
					if (maxResponseSize > 0) {
						if ((communicationLoggingMask & Peers.LOGGING_MASK_200_RESPONSES) != 0) {
							final CountingInputStream cis = new CountingInputStream(connection.getInputStream(),
									maxResponseSize);
							InputStream responseStream = cis;
							if ("gzip".equals(connection.getHeaderField("Content-Encoding"))) {
								responseStream = new GZIPInputStream(cis);
							}
							final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
							final byte[] buffer = new byte[1024];
							int numberOfBytes;
							try (InputStream inputStream = responseStream) {
								while ((numberOfBytes = inputStream.read(buffer, 0, buffer.length)) > 0) {
									byteArrayOutputStream.write(buffer, 0, numberOfBytes);
								}
							}
							final String responseValue = byteArrayOutputStream.toString("UTF-8");
							if ((responseValue.length() > 0) && (responseStream instanceof GZIPInputStream)) {
								log += String.format("[length: %d, compression ratio: %.2f]", cis.getCount(),
										(double) cis.getCount() / (double) responseValue.length());
							}
							log += " >>> " + responseValue;
							showLog = true;
							response = (JSONObject) JSONValue.parseWithException(responseValue);
							this.updateDownloadedVolume(responseValue.length());
						} else {
							InputStream responseStream = connection.getInputStream();
							if ("gzip".equals(connection.getHeaderField("Content-Encoding"))) {
								responseStream = new GZIPInputStream(responseStream);
							}
							try (Reader reader = new BufferedReader(new InputStreamReader(responseStream, "UTF-8"))) {
								final CountingInputReader cir = new CountingInputReader(reader, maxResponseSize);
								response = (JSONObject) JSONValue.parseWithException(cir);
								this.updateDownloadedVolume(cir.getCount());
							}
						}
					}
				} else {
					if ((communicationLoggingMask & Peers.LOGGING_MASK_NON200_RESPONSES) != 0) {
						log += " >>> Peer responded with HTTP " + connection.getResponseCode() + " code!";
						showLog = true;
					}
					Logger.logDebugMessage(
							"Peer " + this.host + " responded with HTTP " + connection.getResponseCode());
					this.deactivate();
					connection.disconnect();
				}
			}
			//
			// Check for an error response
			//
			if ((response != null) && (response.get("error") != null)) {
				this.deactivate();
				if (Errors.SEQUENCE_ERROR.equals(response.get("error")) && (request != Peers.getMyPeerInfoRequest())) {
					Logger.logDebugMessage("Sequence error, reconnecting to " + this.host);
					this.connect();
				} else {
					Logger.logDebugMessage("Peer " + this.host + " version " + this.version + " returned error: "
							+ response.toJSONString() + ", request was: " + JSON.toString(request) + ", disconnecting");
					if (connection != null) {
						connection.disconnect();
					}
				}
			}
		} catch (final NxtException.NxtIOException e) {
			this.blacklist(e);
			if (connection != null) {
				connection.disconnect();
			}
		} catch (RuntimeException | ParseException | IOException e) {
			if (!((e instanceof UnknownHostException) || (e instanceof SocketTimeoutException)
					|| (e instanceof SocketException) || Errors.END_OF_FILE.equals(e.getMessage()))) {
				Logger.logDebugMessage(String.format("Error sending request to peer %s: %s", this.host,
						e.getMessage() != null ? e.getMessage() : e.toString()));
			}
			if ((communicationLoggingMask & Peers.LOGGING_MASK_EXCEPTIONS) != 0) {
				log += " >>> " + e.toString();
				showLog = true;
			}
			this.deactivate();
			if (connection != null) {
				connection.disconnect();
			}
		}
		if (showLog) {
			Logger.logMessage(log + "\n");
		}

		return response;
	}

	void setAnnouncedAddress(final String announcedAddress) {
		if ((announcedAddress != null) && (announcedAddress.length() > Peers.MAX_ANNOUNCED_ADDRESS_LENGTH)) {
			throw new IllegalArgumentException("Announced address too long: " + announcedAddress.length());
		}
		this.announcedAddress = announcedAddress;
		if (announcedAddress != null) {
			try {
				this.port = new URI("http://" + announcedAddress).getPort();
			} catch (final URISyntaxException e) {
				this.port = -1;
			}
		} else {
			this.port = -1;
		}
	}

	void setApiPort(final Object apiPortValue) {
		if (apiPortValue != null) {
			try {
				this.apiPort = ((Long) apiPortValue).intValue();
			} catch (final RuntimeException e) {
				throw new IllegalArgumentException("Invalid peer apiPort " + apiPortValue);
			}
		}
	}

	void setApiServerIdleTimeout(final Object apiServerIdleTimeout) {
		if (apiServerIdleTimeout instanceof Integer) {
			this.apiServerIdleTimeout = (int) apiServerIdleTimeout;
		}
	}

	void setApiSSLPort(final Object apiSSLPortValue) {
		if (apiSSLPortValue != null) {
			try {
				this.apiSSLPort = ((Long) apiSSLPortValue).intValue();
			} catch (final RuntimeException e) {
				throw new IllegalArgumentException("Invalid peer apiSSLPort " + apiSSLPortValue);
			}
		}
	}

	void setApplication(final String application) {
		if ((application == null) || (application.length() > Peers.MAX_APPLICATION_LENGTH)) {
			throw new IllegalArgumentException("Invalid application");
		}
		this.application = application;
	}

	void setBlockchainState(final Object blockchainStateObj) {
		if (blockchainStateObj instanceof Integer) {
			final int blockchainStateInt = (int) blockchainStateObj;
			if ((blockchainStateInt >= 0) && (blockchainStateInt < BlockchainState.values().length)) {
				this.blockchainState = BlockchainState.values()[blockchainStateInt];
			}
		}
	}

	void setDisabledAPIs(final Object apiSetBase64) {
		if (apiSetBase64 instanceof String) {
			this.disabledAPIs = APIEnum.base64StringToEnumSet((String) apiSetBase64);
		}
	}

	private void setHallmark(final Hallmark hallmark) {
		this.hallmark = hallmark;
		this.addService(Service.HALLMARK, false);
	}

	void setInboundWebSocket(final PeerWebSocket inboundSocket) {
		this.inboundSocket = inboundSocket;
	}

	void setLastInboundRequest(final int now) {
		this.lastInboundRequest = now;
	}

	void setLastUpdated(final int lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	void setPlatform(final String platform) {
		if ((platform != null) && (platform.length() > Peers.MAX_PLATFORM_LENGTH)) {
			throw new IllegalArgumentException("Invalid platform length: " + platform.length());
		}
		this.platform = platform;
	}

	void setServices(final long services) {
		synchronized (this) {
			this.services = services;
		}
	}

	void setShareAddress(final boolean shareAddress) {
		this.shareAddress = shareAddress;
	}

	void setState(final State state) {
		if (state != State.CONNECTED) {
			this.webSocket.close();
		}
		if (this.state == state) {
			return;
		}
		if (this.state == State.NON_CONNECTED) {
			this.state = state;
			Peers.notifyListeners(this, Peers.Event.ADDED_ACTIVE_PEER);
		} else if (state != State.NON_CONNECTED) {
			this.state = state;
			Peers.notifyListeners(this, Peers.Event.CHANGED_ACTIVE_PEER);
		} else {
			this.state = state;
		}
	}

	void setVersion(final String version) {
		if ((version != null) && (version.length() > Peers.MAX_VERSION_LENGTH)) {
			throw new IllegalArgumentException("Invalid version length: " + version.length());
		}
		final boolean versionChanged = (version == null) || !version.equals(this.version);
		this.version = version;
		this.isOldVersion = false;
		if (Nxt.APPLICATION.equals(this.application)) {
			this.isOldVersion = Peers.isOldVersion(version, Constants.MIN_VERSION);
			if (this.isOldVersion) {
				if (versionChanged) {
					Logger.logDebugMessage(String.format("Blacklisting %s version %s", this.host, version));
				}
				this.blacklistingCause = "Old version: " + version;
				this.lastInboundRequest = 0;
				this.setState(State.NON_CONNECTED);
				Peers.notifyListeners(this, Peers.Event.BLACKLIST);
			}
		}
	}

	@Override
	public boolean shareAddress() {
		return this.shareAddress;
	}

	@Override
	public String toString() {
		return "Peer{" + "state=" + this.state + ", announcedAddress='" + this.announcedAddress + '\'' + ", services="
				+ this.services + ", host='" + this.host + '\'' + ", version='" + this.version + '\'' + '}';
	}

	@Override
	public void unBlacklist() {
		if (this.blacklistingTime == 0) {
			return;
		}
		Logger.logDebugMessage("Unblacklisting " + this.host);
		this.setState(State.NON_CONNECTED);
		this.blacklistingTime = 0;
		this.blacklistingCause = null;
		Peers.notifyListeners(this, Peers.Event.UNBLACKLIST);
	}

	private void unsetHallmark() {
		this.removeService(Service.HALLMARK, false);
		this.hallmark = null;
	}

	void updateBlacklistedStatus(final int curTime) {
		if ((this.blacklistingTime > 0) && ((this.blacklistingTime + Peers.blacklistingPeriod) <= curTime)) {
			this.unBlacklist();
		}
		if (this.isOldVersion && (this.lastUpdated < (curTime - 3600))) {
			this.isOldVersion = false;
		}
	}

	void updateDownloadedVolume(final long volume) {
		synchronized (this) {
			this.downloadedVolume += volume;
		}
		Peers.notifyListeners(this, Peers.Event.DOWNLOADED_VOLUME);
	}

	void updateUploadedVolume(final long volume) {
		synchronized (this) {
			this.uploadedVolume += volume;
		}
		Peers.notifyListeners(this, Peers.Event.UPLOADED_VOLUME);
	}

	boolean verifyAnnouncedAddress(final String newAnnouncedAddress) {
		if (newAnnouncedAddress == null) {
			return true;
		}
		try {
			final URI uri = new URI("http://" + newAnnouncedAddress);
			final int announcedPort = uri.getPort() == -1 ? Peers.getDefaultPeerPort() : uri.getPort();
			if ((this.hallmark != null) && (announcedPort != this.hallmark.getPort())) {
				Logger.logDebugMessage("Announced port " + announcedPort + " does not match hallmark "
						+ this.hallmark.getPort() + ", ignoring hallmark for " + this.host);
				this.unsetHallmark();
			}
			final InetAddress address = InetAddress.getByName(this.host);
			for (final InetAddress inetAddress : InetAddress.getAllByName(uri.getHost())) {
				if (inetAddress.equals(address)) {
					return true;
				}
			}
			Logger.logDebugMessage("Announced address " + newAnnouncedAddress + " does not resolve to " + this.host);
		} catch (UnknownHostException | URISyntaxException e) {
			Logger.logDebugMessage(e.toString());
			this.blacklist(e);
		}
		return false;
	}
}

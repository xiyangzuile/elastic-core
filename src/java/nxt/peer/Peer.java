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

import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.http.APIEnum;

public interface Peer extends Comparable<Peer> {

	enum BlockchainState {
		UP_TO_DATE,
		DOWNLOADING,
		LIGHT_CLIENT,
		FORK
	}

	enum Service {
		HALLMARK(1),                    // Hallmarked node
		PRUNABLE(2),                    // Stores expired prunable messages
		API(4),                         // Provides open API access over http
		API_SSL(8),                     // Provides open API access over https
		CORS(16);                       // API CORS enabled

		private final long code;        // Service code - must be a power of 2

		Service(final int code) {
			this.code = code;
		}

		public long getCode() {
			return this.code;
		}
	}

	enum State {
		NON_CONNECTED, CONNECTED, DISCONNECTED
	}

	void blacklist(Exception cause);

	void blacklist(String cause);

	void deactivate();

	String getAnnouncedAddress();

	int getApiPort();

	int getApiServerIdleTimeout();

	int getApiSSLPort();

	String getApplication();

	String getBlacklistingCause();

	BlockchainState getBlockchainState();

	Set<APIEnum> getDisabledAPIs();

	long getDownloadedVolume();

	Hallmark getHallmark();

	String getHost();

	int getLastConnectAttempt();

	int getLastUpdated();

	StringBuilder getPeerApiUri();

	String getPlatform();

	int getPort();

	String getSoftware();

	State getState();

	long getUploadedVolume();

	String getVersion();

	int getWeight();

	boolean isApiConnectable();

	boolean isBlacklisted();

	boolean isInbound();

	boolean isInboundWebSocket();

	boolean isOpenAPI();

	boolean isOutboundWebSocket();

	boolean providesService(Service service);

	boolean providesServices(long services);

	void remove();

	JSONObject send(JSONStreamAware request);

	JSONObject send(JSONStreamAware request, int maxResponseSize);

	boolean shareAddress();

	void unBlacklist();

}

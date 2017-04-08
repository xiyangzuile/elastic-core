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

package nxt.util;

import java.net.InetAddress;
import java.util.Map;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;

import nxt.Nxt;

/**
 * Forward ports using the UPnP protocol.
 */
public class UPnP {

	/** Initialization done */
	private static boolean initDone = false;

	/** UPnP gateway device */
	private static GatewayDevice gateway = null;

	/** Local address */
	private static InetAddress localAddress;

	/** External address */
	private static InetAddress externalAddress;

	/**
	 * Add a port to the UPnP mapping
	 *
	 * @param port
	 *            Port to add
	 */
	public static synchronized void addPort(final int port) {
		if (!UPnP.initDone) {
			UPnP.init();
		}
		//
		// Ignore the request if we didn't find a gateway device
		//
		if (UPnP.gateway == null) {
			return;
		}
		//
		// Forward the port
		//
		try {
			if (UPnP.gateway.addPortMapping(port, port, UPnP.localAddress.getHostAddress(), "TCP",
					Nxt.APPLICATION + " " + Nxt.VERSION)) {
				Logger.logDebugMessage("Mapped port [" + UPnP.externalAddress.getHostAddress() + "]:" + port);
			} else {
				Logger.logDebugMessage("Unable to map port " + port);
			}
		} catch (final Exception exc) {
			Logger.logErrorMessage("Unable to map port " + port + ": " + exc.toString());
		}
	}

	/**
	 * Delete a port from the UPnP mapping
	 *
	 * @param port
	 *            Port to delete
	 */
	public static synchronized void deletePort(final int port) {
		if (!UPnP.initDone || (UPnP.gateway == null)) {
			return;
		}
		//
		// Delete the port
		//
		try {
			if (UPnP.gateway.deletePortMapping(port, "TCP")) {
				Logger.logDebugMessage("Mapping deleted for port " + port);
			} else {
				Logger.logDebugMessage("Unable to delete mapping for port " + port);
			}
		} catch (final Exception exc) {
			Logger.logErrorMessage("Unable to delete mapping for port " + port + ": " + exc.toString());
		}
	}

	/**
	 * Return the external address
	 *
	 * @return External address or null if the address is not available
	 */
	public static synchronized InetAddress getExternalAddress() {
		if (!UPnP.initDone) {
			UPnP.init();
		}
		return UPnP.externalAddress;
	}

	/**
	 * Return the local address
	 *
	 * @return Local address or null if the address is not available
	 */
	public static synchronized InetAddress getLocalAddress() {
		if (!UPnP.initDone) {
			UPnP.init();
		}
		return UPnP.localAddress;
	}

	/**
	 * Initialize the UPnP support
	 */
	private static void init() {
		UPnP.initDone = true;
		//
		// Discover the gateway devices on the local network
		//
		try {
			Logger.logInfoMessage("Looking for UPnP gateway device...");
			GatewayDevice.setHttpReadTimeout(
					Nxt.getIntProperty("nxt.upnpGatewayTimeout", GatewayDevice.getHttpReadTimeout()));
			final GatewayDiscover discover = new GatewayDiscover();
			discover.setTimeout(Nxt.getIntProperty("nxt.upnpDiscoverTimeout", discover.getTimeout()));
			final Map<InetAddress, GatewayDevice> gatewayMap = discover.discover();
			if ((gatewayMap == null) || gatewayMap.isEmpty()) {
				Logger.logDebugMessage("There are no UPnP gateway devices");
			} else {
				gatewayMap.forEach((addr, device) -> Logger
						.logDebugMessage("UPnP gateway device found on " + addr.getHostAddress()));
				UPnP.gateway = discover.getValidGateway();
				if (UPnP.gateway == null) {
					Logger.logDebugMessage("There is no connected UPnP gateway device");
				} else {
					UPnP.localAddress = UPnP.gateway.getLocalAddress();
					UPnP.externalAddress = InetAddress.getByName(UPnP.gateway.getExternalIPAddress());
					Logger.logDebugMessage("Using UPnP gateway device on " + UPnP.localAddress.getHostAddress());
					Logger.logInfoMessage("External IP address is " + UPnP.externalAddress.getHostAddress());
				}
			}
		} catch (final Exception exc) {
			Logger.logErrorMessage("Unable to discover UPnP gateway devices: " + exc.toString());
		}
	}
}

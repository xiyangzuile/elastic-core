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

package nxt.env;

import java.io.File;
import java.net.URI;

import javax.swing.SwingUtilities;

import nxt.util.Logger;

public class DesktopMode implements RuntimeMode {

	private DesktopSystemTray desktopSystemTray;
	private Class<?> desktopApplication;

	@Override
	public void alert(final String message) {
		this.desktopSystemTray.alert(message);
	}

	@Override
	public void init() {
		LookAndFeel.init();
		this.desktopSystemTray = new DesktopSystemTray();
		SwingUtilities.invokeLater(this.desktopSystemTray::createAndShowGUI);
	}

	@Override
	public void launchDesktopApplication() {
		Logger.logInfoMessage("Launching desktop wallet");
		try {
			this.desktopApplication = Class.forName("nxtdesktop.DesktopApplication");
			this.desktopApplication.getMethod("launch").invoke(null);
		} catch (final ReflectiveOperationException e) {
			Logger.logInfoMessage("nxtdesktop.DesktopApplication failed to launch", e);
		}
	}

	@Override
	public void setServerStatus(final ServerStatus status, final URI wallet, final File logFileDir) {
		this.desktopSystemTray.setToolTip(new SystemTrayDataProvider(status.getMessage(), wallet, logFileDir));
	}

	@Override
	public void shutdown() {
		this.desktopSystemTray.shutdown();
		if (this.desktopApplication == null) {
			return;
		}
		try {
			this.desktopApplication.getMethod("shutdown").invoke(null);
		} catch (final ReflectiveOperationException e) {
			Logger.logInfoMessage("nxtdesktop.DesktopApplication failed to shutdown", e);
		}
	}
}

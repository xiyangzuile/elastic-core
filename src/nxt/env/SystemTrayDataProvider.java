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

public class SystemTrayDataProvider {

	private final String toolTip;
	private final URI wallet;
	private final File logFile;

	public SystemTrayDataProvider(final String toolTip, final URI wallet, final File logFile) {
		this.toolTip = toolTip;
		this.wallet = wallet;
		this.logFile = logFile;
	}

	public File getLogFile() {
		return this.logFile;
	}

	public String getToolTip() {
		return this.toolTip;
	}

	public URI getWallet() {
		return this.wallet;
	}
}

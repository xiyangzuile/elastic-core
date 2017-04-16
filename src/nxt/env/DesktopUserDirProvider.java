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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

abstract class DesktopUserDirProvider implements DirProvider {

	private static final String LOG_FILE_PATTERN = "java.util.logging.FileHandler.pattern";

	private File logFileDir;

	@Override
	public File getConfDir() {
		return new File(this.getUserHomeDir(), "conf");
	}

	@Override
	public String getDbDir(final String dbDir) {
		return Paths.get(this.getUserHomeDir()).resolve(Paths.get(dbDir)).toString();
	}

	@Override
	public File getLogFileDir() {
		return this.logFileDir;
	}

	@Override
	public abstract String getUserHomeDir();

	@Override
	public boolean isLoadPropertyFileFromUserDir() {
		return true;
	}

	@Override
	public void updateLogFileHandler(final Properties loggingProperties) {
		if (loggingProperties.getProperty(DesktopUserDirProvider.LOG_FILE_PATTERN) == null) {
			this.logFileDir = new File(this.getUserHomeDir(), "logs");
			return;
		}
		final Path logFilePattern = Paths.get(this.getUserHomeDir())
				.resolve(Paths.get(loggingProperties.getProperty(DesktopUserDirProvider.LOG_FILE_PATTERN)));
		loggingProperties.setProperty(DesktopUserDirProvider.LOG_FILE_PATTERN, logFilePattern.toString());

		final Path logDirPath = logFilePattern.getParent();
		System.out.printf("Logs dir %s\n", logDirPath.toString());
		this.logFileDir = new File(logDirPath.toString());
		if (!Files.isReadable(logDirPath)) {
			System.out.printf("Creating dir %s\n", logDirPath);
			try {
				Files.createDirectory(logDirPath);
			} catch (final IOException e) {
				throw new IllegalArgumentException("Cannot create " + logDirPath, e);
			}
		}
	}

}

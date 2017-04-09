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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class RuntimeEnvironment {

	public static final String RUNTIME_MODE_ARG = "nxt.runtime.mode";
	public static final String DIRPROVIDER_ARG = "nxt.runtime.dirProvider";

	private static final String osname = System.getProperty("os.name").toLowerCase();
	private static final boolean isHeadless;
	private static final boolean hasJavaFX;
	static {
		boolean b;
		try {
			// Load by reflection to prevent exception in case java.awt does not
			// exist
			final Class<?> graphicsEnvironmentClass = Class.forName("java.awt.GraphicsEnvironment");
			final Method isHeadlessMethod = graphicsEnvironmentClass.getMethod("isHeadless");
			b = (Boolean) isHeadlessMethod.invoke(null);
		} catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException
				| IllegalAccessException e) {
			b = true;
		}
		isHeadless = b;
		try {
			Class.forName("javafx.application.Application");
			b = true;
		} catch (final ClassNotFoundException e) {
			System.out.println("javafx not supported");
			b = false;
		}
		hasJavaFX = b;
	}

	public static DirProvider getDirProvider() {
		final String dirProvider = System.getProperty(RuntimeEnvironment.DIRPROVIDER_ARG);
		if (dirProvider != null) {
			try {
				return (DirProvider) Class.forName(dirProvider).newInstance();
			} catch (final ReflectiveOperationException e) {
				System.out.println("Failed to instantiate dirProvider " + dirProvider);
				throw new RuntimeException(e.getMessage(), e);
			}
		}
		if (RuntimeEnvironment.isDesktopEnabled()) {
			if (RuntimeEnvironment.isWindowsRuntime()) {
				return new WindowsUserDirProvider();
			}
			if (RuntimeEnvironment.isUnixRuntime()) {
				return new UnixUserDirProvider();
			}
			if (RuntimeEnvironment.isMacRuntime()) {
				return new MacUserDirProvider();
			}
		}
		return new DefaultDirProvider();
	}

	public static RuntimeMode getRuntimeMode() {
		System.out.println("isHeadless=" + RuntimeEnvironment.isHeadless());
		if (RuntimeEnvironment.isDesktopEnabled()) {
			return new DesktopMode();
		} else if (RuntimeEnvironment.isWindowsService()) {
			return new WindowsServiceMode();
		} else {
			return new CommandLineMode();
		}
	}

	public static boolean isDesktopApplicationEnabled() {
		return RuntimeEnvironment.isDesktopEnabled() && RuntimeEnvironment.hasJavaFX;
	}

	private static boolean isDesktopEnabled() {
		return "desktop".equalsIgnoreCase(System.getProperty(RuntimeEnvironment.RUNTIME_MODE_ARG))
				&& !RuntimeEnvironment.isHeadless();
	}

	private static boolean isHeadless() {
		return RuntimeEnvironment.isHeadless;
	}

	private static boolean isMacRuntime() {
		return RuntimeEnvironment.osname.contains("mac");
	}

	private static boolean isUnixRuntime() {
		return RuntimeEnvironment.osname.contains("nux") || RuntimeEnvironment.osname.contains("nix")
				|| RuntimeEnvironment.osname.contains("aix") || RuntimeEnvironment.osname.contains("bsd")
				|| RuntimeEnvironment.osname.contains("sunos");
	}

	private static boolean isWindowsRuntime() {
		return RuntimeEnvironment.osname.startsWith("windows");
	}

	private static boolean isWindowsService() {
		return "service".equalsIgnoreCase(System.getProperty(RuntimeEnvironment.RUNTIME_MODE_ARG))
				&& RuntimeEnvironment.isWindowsRuntime();
	}
}

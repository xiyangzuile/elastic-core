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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.LogManager;

import nxt.Constants;
import nxt.Nxt;

/**
 * Handle logging for the Nxt node server
 */
public final class Logger {

	/** Log event types */
	public enum Event {
		MESSAGE, EXCEPTION
	}

	/** Log levels */
	public enum Level {
		DEBUG, INFO, WARN, ERROR
	}

	/** Message listeners */
	private static final Listeners<String, Event> messageListeners = new Listeners<>();

	/** Exception listeners */
	private static final Listeners<Throwable, Event> exceptionListeners = new Listeners<>();

	/** Our logger instance */
	private static final org.slf4j.Logger log;

	/** Enable stack traces */
	private static final boolean enableStackTraces;

	/** Enable log traceback */
	private static final boolean enableLogTraceback;

	/**
	 * Logger initialization
	 *
	 * The existing Java logging configuration will be used if the Java logger
	 * has already been initialized. Otherwise, we will configure our own log
	 * manager and log handlers. The nxt/conf/logging-default.properties and
	 * nxt/conf/logging.properties configuration files will be used. Entries in
	 * logging.properties will override entries in logging-default.properties.
	 */
	static {
		final String oldManager = System.getProperty("java.util.logging.manager");
		System.setProperty("java.util.logging.manager", "nxt.util.NxtLogManager");
		if (!(LogManager.getLogManager() instanceof NxtLogManager)) {
			System.setProperty("java.util.logging.manager",
					(oldManager != null ? oldManager : "java.util.logging.LogManager"));
		}
		if (!Boolean.getBoolean("nxt.doNotConfigureLogging")) {
			try {
				final Properties loggingProperties = new Properties();
				Nxt.loadProperties(loggingProperties, "logging-default.properties", true);
				Nxt.loadProperties(loggingProperties, "logging.properties", false);
				Nxt.updateLogFileHandler(loggingProperties);
				if (loggingProperties.size() > 0) {
					final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
					loggingProperties.store(outStream, "logging properties");
					final ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
					java.util.logging.LogManager.getLogManager().readConfiguration(inStream);
					inStream.close();
					outStream.close();
				}
				BriefLogFormatter.init();
			} catch (final IOException e) {
				throw new RuntimeException("Error loading logging properties", e);
			}
		}
		log = org.slf4j.LoggerFactory.getLogger(nxt.Nxt.class);
		enableStackTraces = Nxt.getBooleanProperty("nxt.enableStackTraces");
		enableLogTraceback = Nxt.getBooleanProperty("nxt.enableLogTraceback");
		Logger.logInfoMessage("logging enabled");
	}

	/**
	 * Add an exception listener
	 *
	 * @param listener
	 *            Listener
	 * @param eventType
	 *            Notification event type
	 * @return TRUE if listener added
	 */
	public static boolean addExceptionListener(final Listener<Throwable> listener, final Event eventType) {
		return Logger.exceptionListeners.addListener(listener, eventType);
	}

	/**
	 * Add a message listener
	 *
	 * @param listener
	 *            Listener
	 * @param eventType
	 *            Notification event type
	 * @return TRUE if listener added
	 */
	public static boolean addMessageListener(final Listener<String> listener, final Event eventType) {
		return Logger.messageListeners.addListener(listener, eventType);
	}

	/**
	 * Log the event
	 *
	 * @param level
	 *            Level
	 * @param message
	 *            Message
	 * @param exc
	 *            Exception
	 */
	private static void doLog(final Level level, final String message, final Throwable exc) {
		String logMessage = message;
		Throwable e = exc;
		//
		// Add caller class and method if enabled
		//
		if (Logger.enableLogTraceback) {
			final StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
			String className = caller.getClassName();
			final int index = className.lastIndexOf('.');
			if (index != -1) {
				className = className.substring(index + 1);
			}
			logMessage = className + "." + caller.getMethodName() + ": " + logMessage;
		}
		//
		// Format the stack trace if enabled
		//
		if (e != null) {
			if (!Logger.enableStackTraces) {
				logMessage = logMessage + "\n" + exc.toString();
				e = null;
			}
		}
		//
		// Log the event
		//
		switch (level) {
		case DEBUG:
			Logger.log.debug(logMessage, e);
			break;
		case INFO:
			Logger.log.info(logMessage, e);
			break;
		case WARN:
			Logger.log.warn(logMessage, e);
			break;
		case ERROR:
			Logger.log.error(logMessage, e);
			break;
		}
		//
		// Notify listeners
		//
		if (exc != null) {
			Logger.exceptionListeners.notify(exc, Event.EXCEPTION);
		} else {
			Logger.messageListeners.notify(message, Event.MESSAGE);
		}
	}

	/**
	 * Logger initialization
	 */
	public static void init() {
	}

	public static boolean isDebugEnabled() {
		return Logger.log.isDebugEnabled();
	}

	public static boolean isErrorEnabled() {
		return Logger.log.isErrorEnabled();
	}

	public static boolean isInfoEnabled() {
		return Logger.log.isInfoEnabled();
	}

	public static boolean isWarningEnabled() {
		return Logger.log.isWarnEnabled();
	}

	public static void logSignMessage(final String message) {
		if(Constants.logSigningEvents)
			Logger.doLog(Level.INFO, "[SIGN] " + message, null);
	}


	/**
	 * Log a debug message
	 *
	 * @param message
	 *            Message
	 */
	public static void logDebugMessage(final String message) {
		Logger.doLog(Level.DEBUG, message, null);
	}

	/**
	 * Log a debug message
	 *
	 * @param format
	 *            Message format
	 * @param args
	 *            Message args
	 */
	public static void logDebugMessage(final String format, final Object... args) {
		Logger.doLog(Level.DEBUG, String.format(format, args), null);
	}

	/**
	 * Log a debug exception
	 *
	 * @param message
	 *            Message
	 * @param exc
	 *            Exception
	 */
	public static void logDebugMessage(final String message, final Throwable exc) {
		Logger.doLog(Level.DEBUG, message, exc);
	}

	/**
	 * Log an ERROR message
	 *
	 * @param message
	 *            Message
	 */
	public static void logErrorMessage(final String message) {
		Logger.doLog(Level.ERROR, message, null);
	}

	/**
	 * Log an ERROR exception
	 *
	 * @param message
	 *            Message
	 * @param exc
	 *            Exception
	 */
	public static void logErrorMessage(final String message, final Throwable exc) {
		Logger.doLog(Level.ERROR, message, exc);
	}

	/**
	 * Log an INFO message
	 *
	 * @param message
	 *            Message
	 */
	public static void logInfoMessage(final String message) {
		Logger.doLog(Level.INFO, message, null);
	}

	/**
	 * Log an INFO message
	 *
	 * @param format
	 *            Message format
	 * @param args
	 *            Message args
	 */
	public static void logInfoMessage(final String format, final Object... args) {
		Logger.doLog(Level.INFO, String.format(format, args), null);
	}

	/**
	 * Log an INFO exception
	 *
	 * @param message
	 *            Message
	 * @param exc
	 *            Exception
	 */
	public static void logInfoMessage(final String message, final Throwable exc) {
		Logger.doLog(Level.INFO, message, exc);
	}

	/**
	 * Log a message (map to INFO)
	 *
	 * @param message
	 *            Message
	 */
	public static void logMessage(final String message) {
		Logger.doLog(Level.INFO, message, null);
	}

	/**
	 * Log an exception (map to ERROR)
	 *
	 * @param message
	 *            Message
	 * @param exc
	 *            Exception
	 */
	public static void logMessage(final String message, final Exception exc) {
		Logger.doLog(Level.ERROR, message, exc);
	}

	public static void logShutdownMessage(final String message) {
		if (LogManager.getLogManager() instanceof NxtLogManager) {
			Logger.logMessage(message);
		} else {
			System.out.println(message);
		}
	}

	public static void logShutdownMessage(final String message, final Exception e) {
		if (LogManager.getLogManager() instanceof NxtLogManager) {
			Logger.logMessage(message, e);
		} else {
			System.out.println(message);
			System.out.println(e.toString());
		}
	}

	/**
	 * Log a WARNING message
	 *
	 * @param message
	 *            Message
	 */
	public static void logWarningMessage(final String message) {
		Logger.doLog(Level.WARN, message, null);
	}

	/**
	 * Log a WARNING exception
	 *
	 * @param message
	 *            Message
	 * @param exc
	 *            Exception
	 */
	public static void logWarningMessage(final String message, final Throwable exc) {
		Logger.doLog(Level.WARN, message, exc);
	}

	/**
	 * Remove an exception listener
	 *
	 * @param listener
	 *            Listener
	 * @param eventType
	 *            Notification event type
	 * @return TRUE if listener removed
	 */
	public static boolean removeExceptionListener(final Listener<Throwable> listener, final Event eventType) {
		return Logger.exceptionListeners.removeListener(listener, eventType);
	}

	/**
	 * Remove a message listener
	 *
	 * @param listener
	 *            Listener
	 * @param eventType
	 *            Notification event type
	 * @return TRUE if listener removed
	 */
	public static boolean removeMessageListener(final Listener<String> listener, final Event eventType) {
		return Logger.messageListeners.removeListener(listener, eventType);
	}

	/**
	 * Set the log level
	 *
	 * @param level
	 *            Desired log level
	 */
	public static void setLevel(final Level level) {
		final java.util.logging.Logger jdkLogger = java.util.logging.Logger.getLogger(Logger.log.getName());
		switch (level) {
		case DEBUG:
			jdkLogger.setLevel(java.util.logging.Level.FINE);
			break;
		case INFO:
			jdkLogger.setLevel(java.util.logging.Level.INFO);
			break;
		case WARN:
			jdkLogger.setLevel(java.util.logging.Level.WARNING);
			break;
		case ERROR:
			jdkLogger.setLevel(java.util.logging.Level.SEVERE);
			break;
		}
	}

	/**
	 * Logger shutdown
	 */
	public static void shutdown() {
		if (LogManager.getLogManager() instanceof NxtLogManager) {
			((NxtLogManager) LogManager.getLogManager()).nxtShutdown();
		}
	}

	/**
	 * No constructor
	 */
	private Logger() {
	}
}

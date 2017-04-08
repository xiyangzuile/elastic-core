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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * MemoryHandler maintains a ring buffer of log messages. The GetLog API is used
 * to retrieve these log messages.
 *
 * The following logging.properties entries are used:
 * <ul>
 * <li>nxt.util.MemoryHandler.level (default ALL)</li>
 * <li>nxt.util.MemoryHandler.size (default 100, minimum 10)</li>
 * </ul>
 */
public class MemoryHandler extends Handler {

	/** Default ring buffer size */
	private static final int DEFAULT_SIZE = 100;

	/** Level OFF value */
	private static final int OFF_VALUE = Level.OFF.intValue();

	/** Ring buffer */
	private final LogRecord[] buffer;

	/** Buffer start */
	private int start = 0;

	/** Number of buffer entries */
	private int count = 0;

	/** Publish level */
	private Level level;

	/**
	 * Create a MemoryHandler and configure it based on LogManager properties
	 */
	public MemoryHandler() {
		final LogManager manager = LogManager.getLogManager();
		final String cname = this.getClass().getName();
		String value;
		//
		// Allocate the ring buffer
		//
		int bufferSize;
		try {
			value = manager.getProperty(cname + ".size");
			if (value != null) {
				bufferSize = Math.max(Integer.valueOf(value.trim()), 10);
			} else {
				bufferSize = MemoryHandler.DEFAULT_SIZE;
			}
		} catch (final NumberFormatException exc) {
			bufferSize = MemoryHandler.DEFAULT_SIZE;
		}
		this.buffer = new LogRecord[bufferSize];
		//
		// Get publish level
		//
		try {
			value = manager.getProperty(cname + ".level");
			if (value != null) {
				this.level = Level.parse(value.trim());
			} else {
				this.level = Level.ALL;
			}
		} catch (final IllegalArgumentException exc) {
			this.level = Level.ALL;
		}
	}

	/**
	 * Close the handler
	 */
	@Override
	public void close() {
		this.level = Level.OFF;
	}

	/**
	 * Flush the ring buffer
	 */
	@Override
	public void flush() {
		synchronized (this.buffer) {
			this.start = 0;
			this.count = 0;
		}
	}

	/**
	 * Return the log messages from the ring buffer
	 *
	 * @param msgCount
	 *            Number of messages to return
	 * @return List of log messages
	 */
	public List<String> getMessages(final int msgCount) {
		final List<String> rtnList = new ArrayList<>(this.buffer.length);
		synchronized (this.buffer) {
			final int rtnSize = Math.min(msgCount, this.count);
			int pos = (this.start + (this.count - rtnSize)) % this.buffer.length;
			final Formatter formatter = this.getFormatter();
			for (int i = 0; i < rtnSize; i++) {
				rtnList.add(formatter.format(this.buffer[pos++]));
				if (pos == this.buffer.length) {
					pos = 0;
				}
			}
		}
		return rtnList;
	}

	/**
	 * Store a LogRecord in the ring buffer
	 *
	 * @param record
	 *            Description of the log event. A null record is silently
	 *            ignored and is not published
	 */
	@Override
	public void publish(final LogRecord record) {
		if ((record != null) && (record.getLevel().intValue() >= this.level.intValue())
				&& (this.level.intValue() != MemoryHandler.OFF_VALUE)) {
			synchronized (this.buffer) {
				final int ix = (this.start + this.count) % this.buffer.length;
				this.buffer[ix] = record;
				if (this.count < this.buffer.length) {
					this.count++;
				} else {
					this.start++;
					this.start %= this.buffer.length;
				}
			}
		}
	}
}

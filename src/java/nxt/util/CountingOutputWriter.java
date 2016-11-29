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

package nxt.util;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * CountingOutputWriter extends Writer to count the number of characters written
 */
public class CountingOutputWriter extends FilterWriter {

	/** Character count */
	private long count = 0;

	/**
	 * Create the CountingOutputWriter for the specified writer
	 *
	 * @param   writer              Output writer
	 */
	public CountingOutputWriter(final Writer writer) {
		super(writer);
	}

	/**
	 * Return the number of characters written
	 *
	 * @return                      Character count
	 */
	public long getCount() {
		return this.count;
	}

	/**
	 * Write an array of characters
	 *
	 * @param   cbuf                Characters to be written
	 * @throws  IOException         I/O error occurred
	 */
	@Override
	public void write(final char[] cbuf) throws IOException {
		super.write(cbuf);
		this.count += cbuf.length;
	}

	/**
	 * Write an array of characters starting at the specified offset
	 *
	 * @param   cbuf                Characters to be written
	 * @param   off                 Starting offset
	 * @param   len                 Number of characters to write
	 * @throws  IOException         I/O error occurred
	 */
	@Override
	public void write(final char[] cbuf, final int off, final int len) throws IOException {
		super.write(cbuf, off, len);
		this.count += len;
	}

	/**
	 * Write a single character
	 *
	 * @param   c                   Character to be written
	 * @throws  IOException         I/O error occurred
	 */
	@Override
	public void write(final int c) throws IOException {
		super.write(c);
		this.count++;
	}

	/**
	 * Write a string
	 *
	 * @param   s                   String to be written
	 * @throws  IOException         I/O error occurred
	 */
	@Override
	public void write(final String s) throws IOException {
		super.write(s);
		this.count += s.length();
	}

	/**
	 * Write a substring
	 *
	 * @param   s                   String to be written
	 * @param   off                 Starting offset
	 * @param   len                 Number of characters to write
	 * @throws  IOException         I/O error occurred
	 */
	@Override
	public void write(final String s, final int off, final int len) throws IOException {
		super.write(s, off, len);
		this.count += len;
	}
}

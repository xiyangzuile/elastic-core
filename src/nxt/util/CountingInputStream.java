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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import nxt.NxtException;

public class CountingInputStream extends FilterInputStream {

	private long count;
	private final long limit;

	public CountingInputStream(final InputStream in, final long limit) {
		super(in);
		this.limit = limit;
	}

	public long getCount() {
		return this.count;
	}

	private void incCount(final long n) throws NxtException.NxtIOException {
		this.count += n;
		if (this.count > this.limit) {
			throw new NxtException.NxtIOException("Maximum size exceeded: " + this.count);
		}
	}

	@Override
	public int read() throws IOException {
		final int read = super.read();
		if (read >= 0) {
			this.incCount(1);
		}
		return read;
	}

	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		final int read = super.read(b, off, len);
		if (read >= 0) {
			this.incCount(read);
		}
		return read;
	}

	@Override
	public long skip(final long n) throws IOException {
		final long skipped = super.skip(n);
		if (skipped >= 0) {
			this.incCount(skipped);
		}
		return skipped;
	}
}

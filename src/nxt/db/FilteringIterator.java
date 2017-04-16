/*
 * Copyright © 2013-2016 The XEL Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the XEL software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.db;

import java.util.Iterator;
import java.util.NoSuchElementException;

import nxt.util.Filter;

public final class FilteringIterator<T> implements Iterator<T>, Iterable<T>, AutoCloseable {

	private final DbIterator<T> dbIterator;
	private final Filter<T> filter;
	private final int from;
	private final int to;
	private T next;
	private boolean hasNext;
	private boolean iterated;
	private int count;

	public FilteringIterator(final DbIterator<T> dbIterator, final Filter<T> filter) {
		this(dbIterator, filter, 0, Integer.MAX_VALUE);
	}

	private FilteringIterator(final DbIterator<T> dbIterator, final Filter<T> filter, final int from, final int to) {
		this.dbIterator = dbIterator;
		this.filter = filter;
		this.from = from;
		this.to = to;
	}

	public FilteringIterator(final DbIterator<T> dbIterator, final int from, final int to) {
		this(dbIterator, t -> true, from, to);
	}

	@Override
	public void close() {
		this.dbIterator.close();
	}

	@Override
	public boolean hasNext() {
		if (this.hasNext) return true;
		while (this.dbIterator.hasNext() && (this.count <= this.to)) {
			this.next = this.dbIterator.next();
			if (this.filter.ok(this.next)) {
				if (this.count >= this.from) {
					this.count += 1;
					this.hasNext = true;
					return true;
				}
				this.count += 1;
			}
		}
		this.hasNext = false;
		return false;
	}

	@Override
	public Iterator<T> iterator() {
		if (this.iterated) throw new IllegalStateException("Already iterated");
		this.iterated = true;
		return this;
	}

	@Override
	public T next() {
		if (this.hasNext) {
			this.hasNext = false;
			return this.next;
		}
		while (this.dbIterator.hasNext() && (this.count <= this.to)) {
			this.next = this.dbIterator.next();
			if (this.filter.ok(this.next)) {
				if (this.count >= this.from) {
					this.count += 1;
					this.hasNext = false;
					return this.next;
				}
				this.count += 1;
			}
		}
		throw new NoSuchElementException();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}

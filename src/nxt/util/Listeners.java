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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Listeners<T, E extends Enum<E>> {

	private final ConcurrentHashMap<Enum<E>, List<Listener<T>>> listenersMap = new ConcurrentHashMap<>();

	public boolean addListener(final Listener<T> listener, final Enum<E> eventType) {
		synchronized (eventType) {
            List<Listener<T>> listeners = this.listenersMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
            return listeners.add(listener);
		}
	}

	public void notify(final T t, final Enum<E> eventType) {
		final List<Listener<T>> listeners = this.listenersMap.get(eventType);
		if (listeners != null) for (final Listener<T> listener : listeners) listener.notify(t);
	}

	public boolean removeListener(final Listener<T> listener, final Enum<E> eventType) {
		synchronized (eventType) {
			final List<Listener<T>> listeners = this.listenersMap.get(eventType);
			if (listeners != null) return listeners.remove(listener);
		}
		return false;
	}

}

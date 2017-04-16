package nxt;

import java.util.HashMap;

class DoubleLongLRUCache {
	private final int capacity;
	private final HashMap<Pair<Long, Long>, DoubleLongNode> map = new HashMap<>();
	private DoubleLongNode head = null;
	private DoubleLongNode end = null;

	public DoubleLongLRUCache(final int capacity) {
		this.capacity = capacity;
	}

	public long get(final long key1, final long key2) {
		final Pair<Long, Long> pairkey = new Pair<>(key1, key2);
		if (this.map.containsKey(pairkey)) {
			final DoubleLongNode n = this.map.get(pairkey);
			this.remove(n);
			this.setHead(n);
			return n.value;
		}

		return -1;
	}

	public void increment(final long key1, final long key2) {
		long res = this.get(key1, key2);
		res = res + 1;
		this.set(key1, key2, res);
	}

	private void remove(final DoubleLongNode n) {
		if (n.pre != null) n.pre.next = n.next;
        else this.head = n.next;

		if (n.next != null) n.next.pre = n.pre;
        else this.end = n.pre;

	}

	public void set(final long key1, final long key2, final long value) {
		final Pair<Long, Long> pairkey = new Pair<>(key1, key2);
		if (this.map.containsKey(pairkey)) {
			final DoubleLongNode old = this.map.get(pairkey);
			old.value = value;
			this.remove(old);
			this.setHead(old);
		} else {
			final DoubleLongNode created = new DoubleLongNode(key1, key2, value);
			if (this.map.size() >= this.capacity) {
				this.map.remove(pairkey);
				this.remove(this.end);
				this.setHead(created);

			} else this.setHead(created);
			this.map.put(pairkey, created);
		}
	}

	private void setHead(final DoubleLongNode n) {
		n.next = this.head;
		n.pre = null;

		if (this.head != null) this.head.pre = n;

		this.head = n;

		if (this.end == null) this.end = this.head;
	}
}

class DoubleLongNode {
	private final long key1;
	private final long key2;
	long value;
	DoubleLongNode pre;
	DoubleLongNode next;

	public DoubleLongNode(final long key1, final long key2, final long value) {
		this.key1 = key1;
		this.key2 = key2;
		this.value = value;
	}
}
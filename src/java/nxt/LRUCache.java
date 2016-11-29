package nxt;

import java.math.BigInteger;
import java.util.HashMap;

public class LRUCache {
	int capacity;
	HashMap<Long, Node> map = new HashMap<>();
	Node head = null;
	Node end = null;

	public LRUCache(final int capacity) {
		this.capacity = capacity;
	}

	public BigInteger get(final long key) {
		if (this.map.containsKey(key)) {
			final Node n = this.map.get(key);
			this.remove(n);
			this.setHead(n);
			return n.value;
		}

		return null;
	}

	public void remove(final Node n) {
		if (n.pre != null) {
			n.pre.next = n.next;
		} else {
			this.head = n.next;
		}

		if (n.next != null) {
			n.next.pre = n.pre;
		} else {
			this.end = n.pre;
		}

	}

	public void set(final long key, final BigInteger value) {
		if (this.map.containsKey(key)) {
			final Node old = this.map.get(key);
			old.value = value;
			this.remove(old);
			this.setHead(old);
		} else {
			final Node created = new Node(key, value);
			if (this.map.size() >= this.capacity) {
				this.map.remove(this.end.key);
				this.remove(this.end);
				this.setHead(created);

			} else {
				this.setHead(created);
			}

			this.map.put(key, created);
		}
	}

	public void setHead(final Node n) {
		n.next = this.head;
		n.pre = null;

		if (this.head != null) {
			this.head.pre = n;
		}

		this.head = n;

		if (this.end == null) {
			this.end = this.head;
		}
	}
}

class Node {
	long key;
	BigInteger value;
	Node pre;
	Node next;

	public Node(final long key, final BigInteger value) {
		this.key = key;
		this.value = value;
	}
}
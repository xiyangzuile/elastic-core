package nxt;

public class Triplet<T, U, V> {
	T a;
	U b;
	V c;

	Triplet(final T a, final U b, final V c) {
		this.a = a;
		this.b = b;
		this.c = c;
	}

	public T getA() {
		return this.a;
	}

	public U getB() {
		return this.b;
	}

	public V getC() {
		return this.c;
	}
}

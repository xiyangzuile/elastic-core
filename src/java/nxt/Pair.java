package nxt;

public class Pair<T, U> {
	T a;
	U b;

	Pair(final T a, final U b) {
		this.a = a;
		this.b = b;
	}

	public T getA() {
		return this.a;
	}

	public U getB() {
		return this.b;
	}
}

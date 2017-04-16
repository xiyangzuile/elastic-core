package nxt;

public class Quartett<T, U, V, W> {
	private final T a;
	private final U b;
	private final V c;
	private final W d;

	public Quartett(final T a, final U b, final V c, final W d) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.d = d;
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

	public W getD() {
		return this.d;
	}
}

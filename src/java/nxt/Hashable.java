package nxt;

public interface Hashable {
	public int[] personalizedIntStream(byte[] publicKey);
	public long getWorkId();
	byte[] getHash();
}

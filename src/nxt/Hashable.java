package nxt;

public interface Hashable {
	byte[] getHash();

	public long getWorkId();

	public int[] personalizedIntStream(byte[] publicKey, long blockId);
}

package nxt;

public interface Hashable {
	public int[] personalizedIntStream(byte[] publicKey, long blockId);
	public long getWorkId();
	byte[] getHash();
}

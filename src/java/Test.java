import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Test {
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(final byte[] bytes) {
		final char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length; j++ ) {
			final int v = bytes[j] & 0xFF;
			hexChars[j * 2] = Test.hexArray[v >>> 4];
			hexChars[(j * 2) + 1] = Test.hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
	public static void main(final String[] args) throws NoSuchAlgorithmException{
		final String text = "test";
		final MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(text.getBytes(StandardCharsets.UTF_8));
		final MessageDigest digest2 = MessageDigest.getInstance("SHA-256");
		digest2.update(text.getBytes(StandardCharsets.UTF_8));
		final byte[] hash = digest.digest();
		final byte[] hash2 = digest2.digest();
		System.out.println(Test.bytesToHex(hash)); System.out.println(Test.bytesToHex(hash2));

	}

}

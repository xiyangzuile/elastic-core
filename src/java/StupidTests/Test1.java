package StupidTests;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import nxt.util.Logger;

public class Test1 { 
	public static MessageDigest getMessageDigest(String algorithm) {
    try {
        return MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
        Logger.logMessage("Missing message digest algorithm: " + algorithm);
        throw new RuntimeException(e.getMessage(), e);
    }
}
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
public static MessageDigest sha256() {
    return getMessageDigest("SHA-256");
}
public static int toInt(byte[] bytes, int offset) {
	  int ret = 0;
	  for (int i=0; i<4 && i+offset<bytes.length; i++) {
	    ret <<= 8;
	    ret |= (int)bytes[i] & 0xFF;
	  }
	  return ret;
	}
	public static void main(String[] args) {
    	MessageDigest dig = sha256();
    	
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	DataOutputStream dos = new DataOutputStream(baos);
    	try{
        	dos.writeLong(575445L);
        	dos.close();
    	}catch(IOException e){
    		
    	}
    	byte[] longBytes = baos.toByteArray();
    	if(longBytes == null)
    		longBytes = new byte[0];
    	dig.update(longBytes);
    	byte[] digest = dig.digest();
    	System.out.println(bytesToHex(digest));
    	for(int i=0;i<12;++i){
    		int got = toInt(digest,0) ;
    		System.out.println(got);
    		dig.update(digest);
    		digest = dig.digest();
    		System.out.println(bytesToHex(digest));
    	}
    	
	}

}

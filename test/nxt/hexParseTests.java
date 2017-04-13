package nxt;

import nxt.util.Convert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by anonymous on 19.03.17.
 */
public class hexParseTests {

    @Test
    public void HEX() throws Exception {
        String test1="deadbeef";
        String test2="DEADBEEF";
        String test3="DeadBeef";
        Convert.parseHexString(test1);
        Convert.parseHexString(test2);
        Convert.parseHexString(test3);

    }
}

package nxt;

import nxt.http.JSONData;
import nxt.http.ParameterParser;
import nxt.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by anonymous on 19.03.17.
 */
public class txtests {

    @Test
    public void TXDecode() throws Exception {
        String hex1 = "03149c445b060300d9d5c57971eefb085e3abaf7a5a4a6cdb8185f30105583cdb09ad8f61886ec6500000000000000000000000000000000000000000000000000000000000000006ba66909eaf365ed000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000b6b206e51f4e71200f607adda50cf659ce4060ada5c8c450178ca4f26b113e0a3e75d62dadf0f7e2be54460c31df958902f41d5c53913815f8817ac0b343a564000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000025090000b950a2c762811bdf015857c08a82e09b46210000e504568a62d75d631276ed75a01b300843249b177c1caeea8cd35d7078d1bcb7";
        ByteBuffer bb = ByteBuffer.wrap(Convert.toBytes(hex1));
        bb.order( ByteOrder.nativeOrder());
        byte[] dd = bb.array();
        TransactionImpl t1 = TransactionImpl.newTransactionBuilder(dd).build();
    }
}

package nxt;

import nxt.util.Convert;
import org.json.simple.JSONObject;
import org.junit.Test;

/**
 * Created by anonymous on 19.03.17.
 */
public class hexParseTests {

    @Test
    public void HEX() throws Exception {
        String test1="{\"senderPublicKey\":\"d9d5c57971eefb085e3abaf7a5a4a6cdb8185f30105583cdb09ad8f61886ec65\",\"attachment\":{\"version.PiggybackedProofOfBounty\":1,\"id\":\"1634506993161957050\",\"storage\":\"202c302030202c302c30202c202c302030202c302c30202c202c302030202c302c30202c202c302030202c302c30202c202c302030202c302c30202c202c302030202c302c30202c202c302030202c302c30202c202c302030202c302c30202c202c302030202c302c30202c202c302030202c302c30202c202c302030202c30\",\"multiplicator\":\"000000000000000012a1f4000000000000000000000000006c947e7a38019f6c\"},\"subtype\":3,\"amountNQT\":0,\"signature\":\"3db9769e89b32ca69b92946dfe166c050994ee737c2eed05d72bcb39df64a60db4edc6da041094cd11d3718965bb04264a1bc7fcf3fd266d47cc7b0c298fa2e8\",\"feeNQT\":0,\"ecBlockHeight\":1915,\"type\":3,\"deadline\":3,\"version\":1,\"timestamp\":108245414,\"ecBlockId\":\"2780902213805297952\"}";
        org.json.simple.parser.JSONParser p = new org.json.simple.parser.JSONParser();
        TransactionImpl im = TransactionImpl.parseTransaction((JSONObject)p.parse(test1));
        System.out.println(im.getJSONObject().toJSONString());

    }
}

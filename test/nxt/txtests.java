package nxt;

import nxt.http.JSONData;
import nxt.http.ParameterParser;
import nxt.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

/**
 * Created by anonymous on 19.03.17.
 */
public class txtests {

    @Test
    public void TXDecode() throws Exception {
        String txraw = "{\"senderPublicKey\":\"d9d5c57971eefb085e3abaf7a5a4a6cdb8185f30105583cdb09ad8f61886ec65\",\"signature\":\"507b72d71dca7cec85869e259059e4244dbc4ea0341583660cd47e0d0173500d17d6a1edced0b8f7afbf1ebaeee0be3e8998ec303322875502445f6eea14f905\",\"feeNQT\":\"100000000\",\"type\":3,\"fullHash\":\"0ba962025f762b0a0d3e73755c4c9a39e6beef729ecd896788ccdfdcf6e7b28e\",\"version\":1,\"ecBlockId\":\"16374848887408420841\",\"signatureHash\":\"83e31a06ec9b8fcaac14579c088d45e439892c139522c3b2097633b34df4f30a\",\"attachment\":{\"xel_per_bounty\":\"100000000\",\"xel_per_pow\":\"100000000\",\"bountyLimit\":1,\"language\":\"1\",\"messageHash\":\"416f1a1b62ad7669ac386f1047cefde765fd100530d52873dfb85f2e5d4d8e3e\",\"source\":\"DUMMY WORK\",\"title\":\"Test\",\"deadline\":250,\"version.PrunableSourceCode\":1,\"version.WorkCreation\":1},\"senderRS\":\"XEL-E8JD-FHKJ-CQ9H-5KGMQ\",\"subtype\":0,\"amountNQT\":\"100100000000\",\"sender\":\"4273301882745002507\",\"ecBlockHeight\":481,\"deadline\":1440,\"transaction\":\"732809514811828491\",\"timestamp\":104615441,\"height\":2147483647}";

        JSONParser parser = new JSONParser();
        JSONObject txobj = (JSONObject)parser.parse(txraw);
        final TransactionImpl transaction2 = TransactionImpl.parseTransaction(txobj);
        transaction2.validateWithoutSn();


        final String transactionJSON = Convert.emptyToNull("");
        final String transactionBytes = Convert.emptyToNull("03102a713b06a005d9d5c57971eefb085e3abaf7a5a4a6cdb8185f30105583cdb09ad8f61886ec6500000000000000000000000000000000000000000000000000000000000000006ba66909eaf365ed00e876481700000000e1f505000000000000000000000000000000000000000000000000000000000000000000000000c05a4d10662d91711556380f1cc2ba3cb9199dc102821b20f32e4aa0860d0d0d270df53292931dec38da8c1e051c11842c33e6e4c644672c236dc66160ba2dc5000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000618e19af1ec34ffa01040074657374fa0000000a00000000ca9a3b0000000000e1f5050000000001416f1a1b62ad7669ac386f1047cefde765fd100530d52873dfb85f2e5d4d8e3e");
        final String prunableAttachmentJSON = Convert.emptyToNull("");

        final JSONObject response = new JSONObject();
        try {
            final Transaction.Builder builder = ParameterParser.parseTransaction(transactionJSON, transactionBytes,
                    prunableAttachmentJSON);
            final Transaction transaction = builder.build();


            response.put("transaction", transaction.getStringId());
            response.put("fullHash", transaction.getFullHash());
        } catch (NxtException.ValidationException | RuntimeException e) {
            JSONData.putException(response, e, "Failed to broadcast transaction");
        }
        System.out.println(response.toJSONString());
    }
}

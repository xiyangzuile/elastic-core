package nxt;

import nxt.http.JSONData;
import nxt.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;
import java.util.function.Consumer;

public class FailingTrasactionFromDBTest extends AbstractBlockchainTest {


    private static final Long failing_id = Long.parseUnsignedLong("9053406275210142716");

    @Before
    public void init() {
        Properties properties = AbstractBlockchainTest.newTestProperties();
        AbstractForgingTest.init(properties);
    }

    private JSONArray getTransactionPeerImpl(long txid){
        final JSONObject response = new JSONObject();
        final JSONArray transactionArray = new JSONArray();
        final JSONArray transactionIds = new JSONArray();
        transactionIds.add(Convert.toUnsignedLong(txid));
        final Blockchain blockchain = Nxt.getBlockchain();
        //
        // Return the transactions to the caller
        //
        transactionIds.forEach(new Consumer() {
            @Override
            public void accept(Object transactionId) {
                final long id = Long.parseUnsignedLong((String) transactionId);
                final Transaction transaction = blockchain.getTransaction(id);
                if (transaction != null) {
                    transaction.getAppendages(true);
                    final JSONObject transactionJSON = transaction.getJSONObject();
                    transactionArray.add(transactionJSON);
                }
            }
        });
        return transactionArray;
    }

    @Test
    public void fakeForgingTest() throws NxtException.NotValidException, ParseException {
        // Test for bad transactions
        System.out.println("Inspecting failing transaction: " + Convert.toUnsignedLong(failing_id));

        // Retrieve it
        Transaction tx = TransactionDb.findTransaction(failing_id);
        if(tx==null){
            System.err.println("Could not find transaction in Db. Please adjust the TxID in the code and run again.");
            return;
        }

        System.out.println("Java-Object transaction has id: " + Convert.toUnsignedLong(tx.getId()));

        // Check if java object validates
        try {
            tx.validate();

            System.out.println("Validates!!!! Is Signature correct? " + tx.verifySignature());
            System.out.println("Dumping raw hex data:\n" + Convert.toHexString(tx.getBytes()));

            // Reconstructing TX from raw hex data
            Transaction txvalid = TransactionImpl.newTransactionBuilder(tx.getBytes()).build();
            // Make sender ID appear
            txvalid.getSenderId();
            txvalid.validate();
            System.out.println("\nValidates!!!! Is Signature correct? " + txvalid.verifySignature());

            System.out.println("Reconstructed Byte-Object transaction has id: " + Convert.toUnsignedLong(txvalid.getId()));
            System.out.println("Dumping raw hex data:\n" + Convert.toHexString(txvalid.getBytes()));

            // Now retrieve from PEER IMPL
            JSONArray arr = getTransactionPeerImpl(failing_id);
            System.out.println("\nRetrieved from Peer IMPL:\n" + arr.toJSONString());
            JSONParser p = new JSONParser();
            Transaction txpeer = TransactionImpl.parseTransaction((JSONObject) p.parse(((JSONObject)arr.get(0)).toJSONString()));
            System.out.println("Dumping raw hex data:\n" + Convert.toHexString(txpeer.getBytes()));

            // Set breakpoint here to inspect different tx objects
            System.out.println("\nSet breakpoint here for TX inspection!");

        } catch (NxtException.ValidationException e) {
            e.printStackTrace();
            return;
        }


        // Create JSON Object
        JSONObject obj = tx.getJSONObject();
        System.out.println("JSON:\n" + obj.toJSONString());

        // Get other JSON Object
        JSONObject obj2 = JSONData.transaction(tx);
        System.out.println("JSON (With JSONData Interface):\n" + obj2.toJSONString());

        // Assert that JSON Objects match
        // Assert.assertSame(obj.toJSONString(),obj2.toJSONString());

        // Compare every item that is in the first TX to the second one
        for(Object key : obj.keySet()){
            String s1 = obj.get(key).toString();
            String s2 = obj2.get(key).toString();
            System.out.println(key+"\t"+s1 + "\t==\t" + s2);
            if(!s1.equalsIgnoreCase(s2)){
                System.err.println("We found a mismatch in the two transaction decoders. EXITING!");
                return;
            }

        }


        // Reconstruct TX from JSONObject
        // We need a trick here, so we lose the Byte signature - otherwise cast to Long fails
        {
            System.out.println("Testing Reconstruction on TX1");
            String h = obj.toJSONString();
            JSONParser p = new JSONParser();
            Transaction tx2 = TransactionImpl.parseTransaction((JSONObject) p.parse(h));
            System.out.println("TX1 is fine!");
        }
        {
            System.out.println("Testing Reconstruction on TX2");
            String h = obj2.toJSONString();
            JSONParser p = new JSONParser();
            Transaction tx2 = TransactionImpl.parseTransaction((JSONObject) p.parse(h));
            System.out.println("TX2 is fine!");
        }

        System.out.println("JSON (JSONData) Reconstructed transaction has id: " + Convert.toUnsignedLong(tx.getId()));



    }



}
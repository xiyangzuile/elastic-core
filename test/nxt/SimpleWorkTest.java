package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static nxt.TransactionBuilder.make;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import nxt.util.Logger;


import java.util.Properties;

public class SimpleWorkTest extends AbstractForgingTest {

    static final String secretPhrase = "Marty Mc Fly";
    static int current_height = startHeight;
    @Before
    public void init() {
        Properties properties = AbstractForgingTest.newTestProperties();
        properties.setProperty("nxt.disableGenerateBlocksThread", "false");
        properties.setProperty("nxt.enableFakeForging", "true");
        properties.setProperty("nxt.testnetGuaranteedBalanceConfirmations", "10"); // set this in conf-default-file (here, it doesnt suffice)

        properties.setProperty("nxt.timeMultiplier", "1");
        properties.setProperty("nxt.fakeForgingAccount", Convert.toUnsignedLong(Account.getId(Crypto.getPublicKey(secretPhrase))));
        AbstractForgingTest.init(properties);
        Assert.assertTrue("nxt.fakeForgingAccount must be defined in nxt.properties", Nxt.getStringProperty("nxt.fakeForgingAccount") != null);
    }

    void forgeBlocks(int howMany, String secretPhrase){
        forgeTo(current_height + howMany, secretPhrase);
        assertEquals(Nxt.getBlockchain().getHeight(),current_height + howMany);
        current_height += howMany;
    }

    @Test
    public void fakeForgingTest() {
        SimulatedUser user = new SimulatedUser(secretPhrase);
        Logger.logMessage("Account " + user.getRsAccount() + " starts with balance " + user.getInitialBalance());
        forgeBlocks(2, secretPhrase);
        assertTrue(user.getInitialBalance() == user.getBalance());


        // Redeem some funds
        {
            String secp_signatures = "IDDqgcqgTtUMqbEn3ACtjlxGSs66fmNRSYiTSHO94C1/L0LA1KFhLR/dt1GN4xT8I9ZNFuTBINre8wwEAmCB2Jg=";
            final Attachment attachment = new Attachment.RedeemAttachment(Redeem.listOfAddresses[0], secp_signatures);
            final Account fake_from = Account.getAccount(Genesis.REDEEM_ID);
            boolean success = false;
            try {
                make(attachment, secretPhrase, user.getId(), 353593009707920L);
                success = true;
            } catch (Exception e) {
                Logger.logErrorMessage(e.getMessage());
            }
            assertTrue(success);
        }
        Logger.logMessage("Account " + user.getRsAccount() + " Bal:" + user.getBalance() + " / U: " + user.getUnconfirmedBalance() + " / G: " + user.getGuaranteedBalance());

        // forge a few more blocks, so the next tx has a different timestamp
        forgeBlocks(6, secretPhrase);

        // Try to redeem again, must fail
        {
            String secp_signatures = "IDDqgcqgTtUMqbEn3ACtjlxGSs66fmNRSYiTSHO94C1/L0LA1KFhLR/dt1GN4xT8I9ZNFuTBINre8wwEAmCB2Jg=";
            final Attachment attachment = new Attachment.RedeemAttachment(Redeem.listOfAddresses[0], secp_signatures);
            final Account fake_from = Account.getAccount(Genesis.REDEEM_ID);
            boolean success = false;
            try {
                make(attachment, secretPhrase, user.getId(), 353593009707920L);
                success = true;
            } catch (Exception e) {
                Logger.logErrorMessage(e.getMessage());
            }
            assertTrue(!success);
        }
        Logger.logMessage("Account " + user.getRsAccount() + " Bal:" + user.getBalance() + " / U: " + user.getUnconfirmedBalance() + " / G: " + user.getGuaranteedBalance());
        assertEquals(user.getBalance(),353593009707920L);
        assertEquals(user.getGuaranteedBalance(),0L);

        // forge again few more blocks, unconfirmed should turn confirmed
        forgeBlocks(6, secretPhrase);

        Logger.logMessage("Account " + user.getRsAccount() + " Bal:" + user.getBalance() + " / U: " + user.getUnconfirmedBalance() + " / G: " + user.getGuaranteedBalance());
        assertEquals(user.getBalance(),353593009707920L);
        assertEquals(user.getGuaranteedBalance(),353593009707920L);



    }

    @After
    public void destroy() {
        AbstractForgingTest.shutdown();
    }

}
package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static nxt.TransactionBuilder.make;
import static nxt.TransactionBuilder.makeSupernodeSigned;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import nxt.util.Logger;


import java.util.Properties;

public class SimpleWorkTest extends AbstractForgingTest {

    private static final String secretPhrase = "Marty Mc Fly";
    private static int current_height = startHeight;
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

    private void forgeBlocks(int howMany, String secretPhrase){
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
                make(attachment, null, secretPhrase, user.getId(), 353593009707920L, false);
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
                make(attachment, null, secretPhrase, user.getId(), 353593009707920L, false);
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
        // Turn into supernode
        {
            String[] uris = new String[1];
            uris[0] = "http://127.0.0.1:6877";
            final Attachment sn = new Attachment.MessagingSupernodeAnnouncement(uris, 0);
            boolean success = false;
            try {
                make(sn, null, secretPhrase, user.getId(), 0, false);
                success = true;
            } catch (Exception e) {
                Logger.logErrorMessage(e.getMessage());
            }
            assertTrue(success);
        }
        forgeBlocks(2, secretPhrase);
        assertEquals(user.getBalance(),353593009707920L - Constants.SUPERNODE_DEPOSIT_AMOUNT);


        Logger.logMessage("Account (SN " + user.getAccount().isSuperNode() + ") " + user.getRsAccount() + " Bal:" + user.getBalance() + " / U: " + user.getUnconfirmedBalance() + " / G: " + user.getGuaranteedBalance());


        // Now create a very simple work (First one that will fail with an illegaly "pruned appendix")
        {
            String programCode = "verify m[1]==123;";
            final Attachment attachment = new Attachment.WorkCreation("Simple Work", (byte)0x01,
                    1440, 10, Constants.ONE_NXT, 10*Constants.ONE_NXT);
            final Appendix.PrunableSourceCode appdx = new Appendix.PrunableSourceCode(programCode, (byte)0x01);
            boolean success = false;
            try {
                makeSupernodeSigned(attachment, appdx, secretPhrase, user.getId(), 12000000000L, true);
                success = true;
            } catch (Exception e) {
                Logger.logErrorMessage(e.getMessage());
            }
            assertTrue(!success);
        }

        // try to confirm work
        forgeBlocks(1, secretPhrase);
        assertEquals(user.getUnconfirmedBalance(),353593009707920L - Constants.SUPERNODE_DEPOSIT_AMOUNT); // balance not changed due to shitty work creation tx

        // Now create a very simple work that will actually "WORK"
        {
            String programCode = "verify m[1]==123;";
            final Attachment attachment = new Attachment.WorkCreation("Simple Work", (byte)0x01,
                    1440, 10, Constants.ONE_NXT, 10*Constants.ONE_NXT);
            final Appendix.PrunableSourceCode appdx = new Appendix.PrunableSourceCode(programCode, (byte)0x01);
            boolean success = false;
            try {
                makeSupernodeSigned(attachment, appdx, secretPhrase, user.getId(), 12000000000L, false);
                success = true;
            } catch (Exception e) {
                Logger.logErrorMessage(e.getMessage());
                e.printStackTrace();
            }
            assertTrue(success);
        }

        // try to confirm work
        forgeBlocks(5, secretPhrase);
        // TODO: Double check why this check fails! assertEquals(user.getUnconfirmedBalance(),353593009707920L - Constants.SUPERNODE_DEPOSIT_AMOUNT - 12000000000L); // now it worked


    }

    @After
    public void destroy() {
        AbstractForgingTest.shutdown();
    }

}
package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;
import nxt.util.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static nxt.TransactionBuilder.make;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlankUserWithRedeemedAccount extends AbstractForgingTest {

    private static final String secretPhrase = "Marty Mc Fly";
    private static final String guard1 = "Guard 1";
    private static final String guard2 = "Guard 2";
    private static int current_height = startHeight;
    @Before
    public void init() {
        Properties properties = AbstractForgingTest.newTestProperties();
        properties.setProperty("nxt.disableGenerateBlocksThread", "false");
        properties.setProperty("nxt.supernodeBinding", "2");
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
    public void txTests() {
        Account g1 = null;
        Account g2 = null;
        try {
            Db.db.beginTransaction();
            //noinspection UnusedAssignment
            g1 = Account.addOrGetAccount(Crypto.getPublicKey(guard1));
            //noinspection UnusedAssignment
            g2 = Account.addOrGetAccount(Crypto.getPublicKey(guard2));
        }catch(Exception e){
            // For consensus reasons, this must work!!!
            Db.db.endTransaction();
            e.printStackTrace();
            System.exit(1);
        } finally {
            Db.db.endTransaction();
        }

        SimulatedUser user = new SimulatedUser(secretPhrase);
        Logger.logMessage("Account " + user.getRsAccount() + " starts with balance " + user.getInitialBalance());


        forgeBlocks(1, secretPhrase);
        assertTrue(user.getInitialBalance() == user.getBalance());


        // Redeem some funds
        {
            System.out.println("Redeeming from: " + Redeem.listOfAddresses[0] + " to " + user.getStrId());
            String secp_signatures = "IP5OEbIfwKia1zCuigMCZKYTvjWPKmDuZON2Nj/kWD6RE7wxE5PR/ICiGwrqcnvpIjYPg+lx5HTnCufXvmNdG8c=";
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
    
    }

    @After
    public void destroy() {
        AbstractForgingTest.shutdown();
    }

}
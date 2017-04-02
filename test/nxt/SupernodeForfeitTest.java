package nxt;

import nxt.crypto.Crypto;
import nxt.http.GetSupernodes;
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

public class SupernodeForfeitTest extends AbstractForgingTest {

    static final String secretPhrase = "Marty Mc Fly";
    static final String guard1 = "Guard 1";
    static final String guard2 = "Guard 2";
    static int current_height = startHeight;
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

    void forgeBlocks(int howMany, String secretPhrase){
        forgeTo(current_height + howMany, secretPhrase);
        assertEquals(Nxt.getBlockchain().getHeight(),current_height + howMany);
        current_height += howMany;
    }

    @Test
    public void superNodeTest() {
        Account g1 = null;
        Account g2 = null;

        g1 = Account.getAccount(Crypto.getPublicKey(guard1));
        g2 = Account.getAccount(Crypto.getPublicKey(guard2));

        System.out.println("G1: " + g1.getId() + " - is guard = " + g1.isGuardNode());
        System.out.println("G2: " + g2.getId() + " - is guard = " + g2.isGuardNode());

        System.out.println("BINDING PERIOD IS: " + Constants.SUPERNODE_DEPOSIT_BINDING_PERIOD);
        SimulatedUser user = new SimulatedUser(secretPhrase);
        Logger.logMessage("Account " + user.getRsAccount() + " starts with balance " + user.getInitialBalance());
        forgeBlocks(1, secretPhrase);
        assertTrue(user.getInitialBalance() == user.getBalance());


        // Extend supernode
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
            assertTrue(!success);
        }

        // Redeem some funds
        {
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

        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());
        forgeBlocks(1, secretPhrase);
        Logger.logMessage("Account " + user.getRsAccount() + " Bal:" + user.getBalance() + " / U: " + user.getUnconfirmedBalance() + " / G: " + user.getGuaranteedBalance());
        Logger.logMessage("Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");

        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());

        // Extend supernode
        {
            String[] uris = new String[1];
            uris[0] = "192.168.8.104";
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
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString() + " COUNTED = " + GetSupernodes.countSupernodes());
        Logger.logMessage("Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());
        Logger.logMessage("Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());
        Logger.logMessage("Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());

        // Extend
        {
            String[] uris = new String[1];
            uris[0] = "192.168.8.104";
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

        // Next block is number 8
        Logger.logMessage("8 Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());

        Logger.logMessage("9 Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        forgeBlocks(1, secretPhrase);

        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());        Logger.logMessage("Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());        Logger.logMessage("Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        forgeBlocks(1, secretPhrase);

        // NOW FORFEIT
        {
            String[] uris = new String[0];
            final Attachment sn = new Attachment.MessagingSupernodeAnnouncement(uris, user.getId());
            boolean success = false;
            try {
                make(sn, null, guard2, user.getId(), 0, false);
                success = true;
            } catch (Exception e) {
                Logger.logErrorMessage(e.getMessage());
            }
            assertTrue(success);
        }

        Logger.logMessage("Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());        Logger.logMessage("Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        forgeBlocks(1, secretPhrase);
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());        Logger.logMessage("Account " + user.getRsAccount() + " is supernode? " + user.getAccount().isSuperNode() + " [BAL: " + user.getUnconfirmedBalance() + "]");
        System.out.println("Supernode list: " + GetSupernodes.getSupernodes().toString());


        // Account forfeited test
        Account ff = Account.getAccount(Constants.FORFEITED_DEPOSITS_ACCOUNT);
        System.out.println("FF Account " + ff.getId() + " has balance of " + ff.getUnconfirmedBalanceNQT());
    }

    @After
    public void destroy() {
        AbstractForgingTest.shutdown();
    }

}
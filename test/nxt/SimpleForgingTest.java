package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

public class SimpleForgingTest extends AbstractForgingTest {

    static final String secretPhrase = "Marty Mc Fly";

    @Before
    public void init() {
        Properties properties = AbstractForgingTest.newTestProperties();
        properties.setProperty("nxt.disableGenerateBlocksThread", "false");
        properties.setProperty("nxt.enableFakeForging", "true");
        properties.setProperty("nxt.timeMultiplier", "1");
        properties.setProperty("nxt.fakeForgingAccount", Convert.toUnsignedLong(Account.getId(Crypto.getPublicKey(secretPhrase))));
        AbstractForgingTest.init(properties);
        Assert.assertTrue("nxt.fakeForgingAccount must be defined in nxt.properties", Nxt.getStringProperty("nxt.fakeForgingAccount") != null);
    }

    @Test
    public void fakeForgingTest() {
        SimulatedUser user = new SimulatedUser(secretPhrase);
        forgeTo(startHeight + 10, secretPhrase);
    }

    @After
    public void destroy() {
        AbstractForgingTest.shutdown();
    }

}
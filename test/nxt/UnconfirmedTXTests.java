package nxt;

import nxt.db.DbIterator;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

/**
 * Created by anonymous on 19.03.17.
 */
public class UnconfirmedTXTests {
    @Before
    public void init() {
        Properties properties = AbstractBlockchainTest.newTestProperties();
        AbstractForgingTest.init(properties);
    }
    @Test
    public void TXDecode() throws Exception {
        final DbIterator<UnconfirmedTransaction> it = TransactionProcessorImpl.getInstance().getAllUnconfirmedTransactions();
        while (it.hasNext()) {
            final UnconfirmedTransaction u = it.next();
            final TransactionImpl tImpl = u.getTransaction();
        }
    }
}

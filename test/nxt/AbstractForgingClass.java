/*
 * Copyright © 2013-2016 The XEL Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the XEL software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt;

import org.junit.Assert;

import java.util.Properties;

abstract class AbstractForgingTest extends AbstractBlockchainTest {

    private static final int minStartHeight = 0;
    static int startHeight;

    static Properties newTestProperties() {
        Properties properties = AbstractBlockchainTest.newTestProperties();
        properties.setProperty("nxt.isTestnet", "true");
        properties.setProperty("nxt.isOffline", "true");
        return properties;
    }

    static void init(Properties properties) {
        AbstractBlockchainTest.init(properties);
        startHeight = blockchain.getHeight();
        Assert.assertTrue(startHeight >= minStartHeight);
    }

    static void shutdown() {
        blockchainProcessor.popOffTo(startHeight);
        AbstractBlockchainTest.shutdown();
    }

}
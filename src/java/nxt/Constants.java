/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

import java.math.BigInteger;
import java.util.Calendar;
import java.util.TimeZone;

public final class Constants {

    public static final boolean isTestnet = Nxt.getBooleanProperty("nxt.isTestnet");
    public static final boolean isOffline = Nxt.getBooleanProperty("nxt.isOffline");
    public static final boolean isLightClient = Nxt.getBooleanProperty("nxt.isLightClient");
    public static BigInteger least_possible_target = new BigInteger("0000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",16);
    public static final int MAX_NUMBER_OF_TRANSACTIONS = 255;
    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_WORK_CODE_LENGTH = 1024*1024;
    public static final int POWRETARGET_N_BLOCKS = 3;
    public static final int POWRETARGET_POW_PER_BLOCK_SCALER = 10;
    public static final int MIN_TRANSACTION_SIZE = 176;
    public static final int MAX_DEADLINE_FOR_WORK = 1440;
    public static final int MIN_DEADLINE_FOR_WORK = 3;
    public static final int MAX_INTS_FOR_WORK = 12;
    public static final int MIN_INTS_FOR_WORK = 3;
    public static final long MAX_WORK_WCET_TIME = 200000L;
    public static final long MAX_WORK_POW_REWARD = 10000000000L;
    public static final long MIN_WORK_POW_REWARD = 0L;
    public static final int MIN_WORK_BOUNTY_LIMIT=1;
    public static final int PAY_FOR_AT_LEAST_X_POW=20;
    public static final long MIN_XEL_PER_POW=1000;
    public static final int MAX_WORK_BOUNTY_LIMIT=10;
    public static final int MAX_INTS_IN_VIRUAL_MACHINE_MEMORY = 262144;
    public static final int MAX_PAYLOAD_LENGTH = MAX_NUMBER_OF_TRANSACTIONS * MIN_TRANSACTION_SIZE;
    public static final long MAX_BALANCE_NXT = 100000000;
    public static final long ONE_NXT = 100000000;
    public static final long MAX_BALANCE_NQT = MAX_BALANCE_NXT * ONE_NXT;
    public static final long INITIAL_BASE_TARGET = 153722867;
    public static final long MAX_BASE_TARGET = MAX_BALANCE_NXT * INITIAL_BASE_TARGET;
    public static final long MAX_BASE_TARGET_2 = isTestnet ? MAX_BASE_TARGET : INITIAL_BASE_TARGET * 50;
    public static final long MIN_BASE_TARGET = INITIAL_BASE_TARGET * 9 / 10;
    public static final int MIN_BLOCKTIME_LIMIT = 53;
    public static final int MAX_BLOCKTIME_LIMIT = 67;
    public static final int BASE_TARGET_GAMMA = 64;
    public static final int MAX_ROLLBACK = Math.max(Nxt.getIntProperty("nxt.maxRollback"), 720);
    public static final int GUARANTEED_BALANCE_CONFIRMATIONS = isTestnet ? Nxt.getIntProperty("nxt.testnetGuaranteedBalanceConfirmations", 1440) : 1440;
    public static final int LEASING_DELAY = isTestnet ? Nxt.getIntProperty("nxt.testnetLeasingDelay", 1440) : 1440;
    public static final long MIN_FORGING_BALANCE_NQT = isTestnet ? Long.parseLong(Nxt.getStringProperty("nxt.testnetMinForgingBalance", String.valueOf(1000 * ONE_NXT))) : 1000 * ONE_NXT;;
    public static final int MAX_WORK_ROLLBACK_HEIGHT = 1441;
    public static final int MAX_TIMEDRIFT = 15; // allow up to 15 s clock difference
    public static final int FORGING_DELAY = Nxt.getIntProperty("nxt.forgingDelay");
    public static final int FORGING_SPEEDUP = Nxt.getIntProperty("nxt.forgingSpeedup");
    public static final int MAX_REFERENCED_TRANSACTION_TIMESPAN = 60 * 1440 * 60;
    public static final int MAX_ARBITRARY_MESSAGE_LENGTH = 160;
    public static final int MAX_ENCRYPTED_MESSAGE_LENGTH = 160 + 16;

    public static final int MAX_PRUNABLE_MESSAGE_LENGTH = 42 * 1024;
    public static final int MAX_PRUNABLE_ENCRYPTED_MESSAGE_LENGTH = 42 * 1024;
    
    public static final int MAX_HUB_ANNOUNCEMENT_URIS = 100;
    public static final int MAX_HUB_ANNOUNCEMENT_URI_LENGTH = 1000;
    public static final long MIN_HUB_EFFECTIVE_BALANCE = 100000;
    public static final int POW_VERIFICATION_UNBLOCK_WHEN_VALID_IN_LAST_BLOCKS = 5;
    public static final int MIN_PRUNABLE_LIFETIME = isTestnet ? 1440 * 60 : 14 * 1440 * 60;
    public static final int MAX_PRUNABLE_LIFETIME;
    public static final boolean ENABLE_PRUNING;
    static {
        int maxPrunableLifetime = Nxt.getIntProperty("nxt.maxPrunableLifetime");
        ENABLE_PRUNING = maxPrunableLifetime >= 0;
        MAX_PRUNABLE_LIFETIME = ENABLE_PRUNING ? Math.max(maxPrunableLifetime, MIN_PRUNABLE_LIFETIME) : Integer.MAX_VALUE;
    }
    public static final boolean INCLUDE_EXPIRED_PRUNABLE = Nxt.getBooleanProperty("nxt.includeExpiredPrunable");

    public static final int MAX_ACCOUNT_NAME_LENGTH = 100;
    public static final int MAX_ACCOUNT_DESCRIPTION_LENGTH = 1000;

    public static final int MAX_ACCOUNT_PROPERTY_NAME_LENGTH = 32;
    public static final int MAX_ACCOUNT_PROPERTY_VALUE_LENGTH = 160;

    public static final int MAX_TAGGED_DATA_NAME_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_TAGGED_DATA_TAGS_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_TYPE_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_CHANNEL_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_FILENAME_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_DATA_LENGTH = 42 * 1024;


    public static final int LAST_CHECKSUM_BLOCK = 0;
    public static final int LAST_KNOWN_BLOCK = isTestnet ? 0 : 0;

    public static final int[] MIN_VERSION = Constants.isTestnet ? new int[] {0, 3, 2} : new int[] {0, 3, 2};
    public static final int[] MIN_PROXY_VERSION = new int[] {0, 3, 0};

    static final long UNCONFIRMED_POOL_DEPOSIT_NQT = (isTestnet ? 50 : 100) * ONE_NXT;

    public static final boolean correctInvalidFees = Nxt.getBooleanProperty("nxt.correctInvalidFees");

    public static final long EPOCH_BEGINNING;
    static {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, 2013);
        calendar.set(Calendar.MONTH, Calendar.NOVEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 24);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        EPOCH_BEGINNING = calendar.getTimeInMillis();
    }

    public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    public static final String ALLOWED_CURRENCY_CODE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Constants() {} // never

}

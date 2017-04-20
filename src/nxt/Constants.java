/******************************************************************************
 * Copyright © 2013-2016 The XEL Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
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


	public static final long DEPOSITS_ACCOUNT = 123456789; // Here, this account holds all deposits
	public static final long FORFEITED_DEPOSITS_ACCOUNT = 123456788; // Here, all finally forfeited coins arrive

	public static final boolean isTestnet = Nxt.getBooleanProperty("nxt.isTestnet");
	public static final boolean isOffline = Nxt.getBooleanProperty("nxt.isOffline");
	public static final boolean isLightClient = Nxt.getBooleanProperty("nxt.isLightClient");
	public static final BigInteger least_possible_target = new BigInteger("000000ffffffffffffffffffffffffff", 16);

	public static final int BLOCKS_TO_LOCKIN_SOFT_FORK = (isTestnet)?15:1440;

	public static final int MAX_NUMBER_OF_TRANSACTIONS = 255;
	public static final int MAX_TITLE_LENGTH = 255;
	public static final int MAX_WORK_CODE_LENGTH = 1024 * 1024;
	private static final int MIN_TRANSACTION_SIZE = 176;
	public static final int MAX_DEADLINE_FOR_WORK = 1440;
	public static final int MIN_DEADLINE_FOR_WORK = 3;
	public static final int WORK_MULTIPLICATOR_BYTES = 32;
	public static final long MAX_WORK_WCET_TIME = 200000L;
	public static final int MIN_WORK_BOUNTY_LIMIT = 1;
	public static final int PAY_FOR_AT_LEAST_X_POW = 20;
	public static final long ONE_NXT = 100000000;
	public static final int EVAL_WORK_EXEC_TIME_AGE_SECONDS = 120;
	public static final long MIN_XEL_PER_POW = (long) (0.001 * Constants.ONE_NXT);
	public static final long MIN_XEL_PER_BOUNTY = Constants.ONE_NXT;
	public static final long DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION = 10 * Constants.ONE_NXT;
	public static final int MAX_HASH_ANNOUNCEMENT_SIZE_BYTES = 32;
	public static final int DEPOSIT_GRACE_PERIOD = 15 * 60; // 15 minutes
	public static final int MAX_WORK_BOUNTY_LIMIT = 10;
	public static final int MAX_PAYLOAD_LENGTH = Constants.MAX_NUMBER_OF_TRANSACTIONS * Constants.MIN_TRANSACTION_SIZE;
	public static final long MAX_BALANCE_NXT = 100000000;
	public static final long MAX_BALANCE_NQT = Constants.MAX_BALANCE_NXT * Constants.ONE_NXT;
	public static final long INITIAL_BASE_TARGET = 1537228670;
	public static final long MAX_BASE_TARGET = Constants.MAX_BALANCE_NXT * Constants.INITIAL_BASE_TARGET;
	public static final long MAX_BASE_TARGET_2 = Constants.isTestnet ? Constants.MAX_BASE_TARGET
			: Constants.INITIAL_BASE_TARGET * 50;
	public static final long MIN_BASE_TARGET = (Constants.INITIAL_BASE_TARGET * 9) / 10;
	public static final int MIN_BLOCKTIME_LIMIT = 53;
	public static final int MAX_BLOCKTIME_LIMIT = 67;
	public static final int BASE_TARGET_GAMMA = 64;
	public static final int MAX_ROLLBACK = Math.max(Nxt.getIntProperty("nxt.maxRollback"), 720);
	public static final int GUARANTEED_BALANCE_CONFIRMATIONS = Constants.isTestnet
			? Nxt.getIntProperty("nxt.testnetGuaranteedBalanceConfirmations", 1440) : 1440;
	public static final int LEASING_DELAY = Constants.isTestnet ? Nxt.getIntProperty("nxt.testnetLeasingDelay", 1440)
			: 1440;
	public static final long MIN_FORGING_BALANCE_NQT = Constants.isTestnet
			? Long.parseLong(
					Nxt.getStringProperty("nxt.testnetMinForgingBalance", String.valueOf(1000 * Constants.ONE_NXT)))
			: 1000 * Constants.ONE_NXT;;
	public static final int MAX_TIMEDRIFT = 15; // allow up to 15 s clock difference
	public static final int FIRST_X_BLOCKS_PSEUDO_EFFECTIVE_BALANCE = 5000;
	public static final int FORGING_DELAY = Nxt.getIntProperty("nxt.forgingDelay");
	public static final int FORGING_SPEEDUP = Nxt.getIntProperty("nxt.forgingSpeedup");
	public static final int MAX_REFERENCED_TRANSACTION_TIMESPAN = 60 * 1440 * 60;
	public static final int MAX_ARBITRARY_MESSAGE_LENGTH = 160;

	public static final int MAX_PRUNABLE_MESSAGE_LENGTH = 42 * 1024;
	public static final int MAX_SUPERNODE_ANNOUNCEMENT_URIS = 3;
	public static final int MAX_SUPERNODE_ANNOUNCEMENT_URI_LENGTH = 255;
	public static final int MIN_PRUNABLE_LIFETIME = Constants.isTestnet ? 1440 * 60 : 14 * 1440 * 60;
	public static final int MAX_PRUNABLE_LIFETIME;
	public static final boolean ENABLE_PRUNING;
	public static final int Supernode_Push_Limit = 2;
    public static final boolean logSigningEvents = Nxt.getBooleanProperty("nxt.logSigningEvents");
	public static final boolean logKimotoEvents = Nxt.getBooleanProperty("nxt.logKimotoEvents");

	static {
		final int maxPrunableLifetime = Nxt.getIntProperty("nxt.maxPrunableLifetime");
		ENABLE_PRUNING = maxPrunableLifetime >= 0;
		MAX_PRUNABLE_LIFETIME = Constants.ENABLE_PRUNING
				? Math.max(maxPrunableLifetime, Constants.MIN_PRUNABLE_LIFETIME) : Integer.MAX_VALUE;
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

	public static final int MAX_POWS_PER_BLOCK = 20;


	// Supernode stuff
	public static final int SUPERNODE_CONNECTED_NODES_ARE_ENOUGH = 5;
	public static final int SUPERNODE_DEPOSIT_BINDING_PERIOD = Constants.isTestnet ? Nxt.getIntProperty("nxt.supernodeBinding", 20)
			: 512;
	public static final long SUPERNODE_DEPOSIT_AMOUNT = 250000*ONE_NXT;
	public static final int SUPERNODE_PERCENTAGE_EARNINGS = 10; // Supernodes get this much in percent from what is paid out as a reward to the workers

	// Guard stuff (Hardcoded Guard Nodes)
	public static final long[] GUARD_NODES = new long[]{8473660669446786780L, -6336019433117180774L};


	public static final int LAST_CHECKSUM_BLOCK = 0;
	public static final int LAST_KNOWN_BLOCK = Constants.isTestnet ? 0 : 0;

	public static final int[] MIN_VERSION = Constants.isTestnet ? new int[] { 1, 0, 7 } : new int[] { 1, 0, 7};
	public static final int[] MIN_PROXY_VERSION = new int[] { 1, 0, 7 };

	static final long UNCONFIRMED_POOL_DEPOSIT_NQT = (Constants.isTestnet ? 50 : 100) * Constants.ONE_NXT;

	public static final boolean correctInvalidFees = Nxt.getBooleanProperty("nxt.correctInvalidFees");

	public static final long EPOCH_BEGINNING;
	static {
		final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
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

	private Constants() {
	} // never

}

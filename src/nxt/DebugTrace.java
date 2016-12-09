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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nxt.db.DbIterator;
import nxt.util.Convert;
import nxt.util.Logger;

public final class DebugTrace {

	static final String QUOTE = Nxt.getStringProperty("nxt.debugTraceQuote", "\"");
	static final String SEPARATOR = Nxt.getStringProperty("nxt.debugTraceSeparator", "\t");
	static final boolean LOG_UNCONFIRMED = Nxt.getBooleanProperty("nxt.debugLogUnconfirmed");

	// NOTE: first and last columns should not have a blank entry in any row,
	// otherwise VerifyTrace fails to parse the line
	private static final String[] columns = { "height", "event", "account", "asset", "currency", "balance",
			"unconfirmed balance", "asset balance", "unconfirmed asset balance", "currency balance",
			"unconfirmed currency balance", "transaction amount", "transaction fee", "generation fee",
			"effective balance", "dividend", "order", "order price", "order quantity", "order cost", "offer",
			"buy rate", "sell rate", "buy units", "sell units", "buy cost", "sell cost", "trade price",
			"trade quantity", "trade cost", "exchange rate", "exchange quantity", "exchange cost", "currency cost",
			"crowdfunding", "claim", "mint", "asset quantity", "currency units", "transaction", "lessee",
			"lessor guaranteed balance", "purchase", "purchase price", "purchase quantity", "purchase cost", "discount",
			"refund", "shuffling", "sender", "recipient", "block", "timestamp" };

	private static final Map<String, String> headers = new HashMap<>();

	static {
		for (final String entry : DebugTrace.columns) {
			DebugTrace.headers.put(entry, entry);
		}
	}

	public static DebugTrace addDebugTrace(final Set<Long> accountIds, final String logName) {
		final DebugTrace debugTrace = new DebugTrace(accountIds, logName);

		Account.addListener(account -> debugTrace.trace(account, false), Account.Event.BALANCE);
		if (DebugTrace.LOG_UNCONFIRMED) {
			Account.addListener(account -> debugTrace.trace(account, true), Account.Event.UNCONFIRMED_BALANCE);
		}

		Account.addLeaseListener(accountLease -> debugTrace.trace(accountLease, true), Account.Event.LEASE_STARTED);
		Account.addLeaseListener(accountLease -> debugTrace.trace(accountLease, false), Account.Event.LEASE_ENDED);
		Nxt.getBlockchainProcessor().addListener(debugTrace::traceBeforeAccept,
				BlockchainProcessor.Event.BEFORE_BLOCK_ACCEPT);
		Nxt.getBlockchainProcessor().addListener(debugTrace::trace, BlockchainProcessor.Event.BEFORE_BLOCK_APPLY);

		return debugTrace;
	}

	static void init() {
		final List<String> accountIdStrings = Nxt.getStringListProperty("nxt.debugTraceAccounts");
		final String logName = Nxt.getStringProperty("nxt.debugTraceLog");
		if (accountIdStrings.isEmpty() || (logName == null)) {
			return;
		}
		final Set<Long> accountIds = new HashSet<>();
		for (final String accountId : accountIdStrings) {
			if ("*".equals(accountId)) {
				accountIds.clear();
				break;
			}
			accountIds.add(Convert.parseAccountId(accountId));
		}
		final DebugTrace debugTrace = DebugTrace.addDebugTrace(accountIds, logName);
		Nxt.getBlockchainProcessor().addListener(block -> debugTrace.resetLog(),
				BlockchainProcessor.Event.RESCAN_BEGIN);
		Logger.logDebugMessage("Debug tracing of "
				+ (accountIdStrings.contains("*") ? "ALL" : String.valueOf(accountIds.size())) + " accounts enabled");
	}

	private final Set<Long> accountIds;
	private final String logName;
	private PrintWriter log;

	private DebugTrace(final Set<Long> accountIds, final String logName) {
		this.accountIds = accountIds;
		this.logName = logName;
		this.resetLog();
	}

	private Map<String, String> getValues(final long accountId, final Account.AccountLease accountLease,
			final boolean start) {
		final Map<String, String> map = new HashMap<>();
		map.put("account", Long.toUnsignedString(accountId));
		map.put("event", start ? "lease begin" : "lease end");
		map.put("timestamp", String.valueOf(Nxt.getBlockchain().getLastBlock().getTimestamp()));
		map.put("height", String.valueOf(Nxt.getBlockchain().getHeight()));
		map.put("lessee", Long.toUnsignedString(accountLease.getCurrentLesseeId()));
		return map;
	}

	private Map<String, String> getValues(final long accountId, final Block block) {
		final long fee = block.getTotalFeeNQT();
		if (fee == 0) {
			return Collections.emptyMap();
		}
		long totalBackFees = 0;
		final long[] backFees = new long[3];
		for (final Transaction transaction : block.getTransactions()) {
			final long[] fees = ((TransactionImpl) transaction).getBackFees();
			for (int i = 0; i < fees.length; i++) {
				backFees[i] += fees[i];
			}
		}
		for (int i = 0; i < backFees.length; i++) {
			if (backFees[i] == 0) {
				break;
			}
			totalBackFees += backFees[i];
			final long previousGeneratorId = BlockDb.findBlockAtHeight(block.getHeight() - i - 1).getGeneratorId();
			if (this.include(previousGeneratorId)) {
				final Map<String, String> map = this.getValues(previousGeneratorId, false);
				map.put("effective balance",
						String.valueOf(Account.getAccount(previousGeneratorId).getEffectiveBalanceNXT()));
				map.put("generation fee", String.valueOf(backFees[i]));
				map.put("block", block.getStringId());
				map.put("event", "block");
				map.put("timestamp", String.valueOf(block.getTimestamp()));
				map.put("height", String.valueOf(block.getHeight()));
				this.log(map);
			}
		}

		final Map<String, String> map = this.getValues(accountId, false);
		map.put("effective balance", String.valueOf(Account.getAccount(accountId).getEffectiveBalanceNXT()));
		map.put("generation fee", String.valueOf(fee - totalBackFees));
		map.put("block", block.getStringId());
		map.put("event", "block");
		map.put("timestamp", String.valueOf(block.getTimestamp()));
		map.put("height", String.valueOf(block.getHeight()));
		return map;
	}

	private Map<String, String> getValues(final long accountId, final boolean unconfirmed) {
		final Map<String, String> map = new HashMap<>();
		map.put("account", Long.toUnsignedString(accountId));
		final Account account = Account.getAccount(accountId);
		map.put("balance", String.valueOf(account != null ? account.getBalanceNQT() : 0));
		map.put("unconfirmed balance", String.valueOf(account != null ? account.getUnconfirmedBalanceNQT() : 0));
		map.put("timestamp", String.valueOf(Nxt.getBlockchain().getLastBlock().getTimestamp()));
		map.put("height", String.valueOf(Nxt.getBlockchain().getHeight()));
		map.put("event", unconfirmed ? "unconfirmed balance" : "balance");
		return map;
	}

	private Map<String, String> getValues(final long accountId, final Transaction transaction,
			final Attachment attachment, final boolean isRecipient) {
		return Collections.emptyMap();
	}

	private Map<String, String> getValues(final long accountId, final Transaction transaction,
			final boolean isRecipient, final boolean logFee, final boolean logAmount) {
		long amount = transaction.getAmountNQT();
		long fee = transaction.getFeeNQT();
		if (isRecipient) {
			fee = 0; // fee doesn't affect recipient account
		} else {
			// for sender the amounts are subtracted
			amount = -amount;
			fee = -fee;
		}
		if ((fee == 0) && (amount == 0)) {
			return Collections.emptyMap();
		}
		final Map<String, String> map = this.getValues(accountId, false);
		if (logAmount) {
			map.put("transaction amount", String.valueOf(amount));
		}
		if (logFee) {
			map.put("transaction fee", String.valueOf(fee));
		}
		map.put("transaction", transaction.getStringId());
		if (isRecipient) {
			map.put("sender", Long.toUnsignedString(transaction.getSenderId()));
		} else {
			map.put("recipient", Long.toUnsignedString(transaction.getRecipientId()));
		}
		map.put("event", "transaction");
		return map;
	}

	private boolean include(final long accountId) {
		return (accountId != 0) && (this.accountIds.isEmpty() || this.accountIds.contains(accountId));
	}

	private Map<String, String> lessorGuaranteedBalance(final Account account, final long lesseeId) {
		final Map<String, String> map = new HashMap<>();
		map.put("account", Long.toUnsignedString(account.getId()));
		map.put("lessor guaranteed balance", String.valueOf(account.getGuaranteedBalanceNQT()));
		map.put("lessee", Long.toUnsignedString(lesseeId));
		map.put("timestamp", String.valueOf(Nxt.getBlockchain().getLastBlock().getTimestamp()));
		map.put("height", String.valueOf(Nxt.getBlockchain().getHeight()));
		map.put("event", "lessor guaranteed balance");
		return map;
	}

	private void log(final Map<String, String> map) {
		if (map.isEmpty()) {
			return;
		}
		final StringBuilder buf = new StringBuilder();
		for (final String column : DebugTrace.columns) {
			if (!DebugTrace.LOG_UNCONFIRMED && column.startsWith("unconfirmed")) {
				continue;
			}
			final String value = map.get(column);
			if (value != null) {
				buf.append(DebugTrace.QUOTE).append(value).append(DebugTrace.QUOTE);
			}
			buf.append(DebugTrace.SEPARATOR);
		}
		this.log.println(buf.toString());
	}

	void resetLog() {
		if (this.log != null) {
			this.log.close();
		}
		try {
			this.log = new PrintWriter((new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.logName)))),
					true);
		} catch (final IOException e) {
			Logger.logDebugMessage("Debug tracing to " + this.logName + " not possible", e);
			throw new RuntimeException(e);
		}
		this.log(DebugTrace.headers);
	}

	private void trace(final Account account, final boolean unconfirmed) {
		if (this.include(account.getId())) {
			this.log(this.getValues(account.getId(), unconfirmed));
		}
	}

	private void trace(final Account.AccountLease accountLease, final boolean start) {
		if (!this.include(accountLease.getCurrentLesseeId()) && !this.include(accountLease.getLessorId())) {
			return;
		}
		this.log(this.getValues(accountLease.getLessorId(), accountLease, start));
	}

	private void trace(final Block block) {
		for (final Transaction transaction : block.getTransactions()) {
			final long senderId = transaction.getSenderId();

			if (this.include(senderId)) {
				this.log(this.getValues(senderId, transaction, false, true, true));
				this.log(this.getValues(senderId, transaction, transaction.getAttachment(), false));
			}
			long recipientId = transaction.getRecipientId();
			if ((transaction.getAmountNQT() > 0) && (recipientId == 0)) {
				recipientId = Genesis.CREATOR_ID;
			}
			if (this.include(recipientId)) {
				this.log(this.getValues(recipientId, transaction, true, true, true));
				this.log(this.getValues(recipientId, transaction, transaction.getAttachment(), true));
			}
		}
	}

	private void traceBeforeAccept(final Block block) {
		final long generatorId = block.getGeneratorId();
		if (this.include(generatorId)) {
			this.log(this.getValues(generatorId, block));
		}
		for (final long accountId : this.accountIds) {
			final Account account = Account.getAccount(accountId);
			if (account != null) {
				try (DbIterator<Account> lessors = account.getLessors()) {
					while (lessors.hasNext()) {
						this.log(this.lessorGuaranteedBalance(lessors.next(), accountId));
					}
				}
			}
		}
	}

}

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

package nxt.user;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Block;
import nxt.BlockImpl;
import nxt.Nxt;
import nxt.Transaction;
import nxt.db.DbIterator;

public final class UnlockAccount extends UserServlet.UserRequestHandler {

	static final UnlockAccount instance = new UnlockAccount();

	private static final Comparator<JSONObject> myTransactionsComparator = (o1, o2) -> {
		final int t1 = ((Number) o1.get("timestamp")).intValue();
		final int t2 = ((Number) o2.get("timestamp")).intValue();
		if (t1 < t2) {
			return 1;
		}
		if (t1 > t2) {
			return -1;
		}
		final String id1 = (String) o1.get("id");
		final String id2 = (String) o2.get("id");
		return id2.compareTo(id1);
	};

	private UnlockAccount() {
	}

	@Override
	JSONStreamAware processRequest(final HttpServletRequest req, final User user) throws IOException {
		final String secretPhrase = req.getParameter("secretPhrase");
		// lock all other instances of this account being unlocked
		Users.getAllUsers().forEach(u -> {
			if (secretPhrase.equals(u.getSecretPhrase())) {
				u.lockAccount();
				if (!u.isInactive()) {
					u.enqueue(JSONResponses.LOCK_ACCOUNT);
				}
			}
		});

		final long accountId = user.unlockAccount(secretPhrase);

		final JSONObject response = new JSONObject();
		response.put("response", "unlockAccount");
		response.put("account", Long.toUnsignedString(accountId));

		if (secretPhrase.length() < 30) {

			response.put("secretPhraseStrength", 1);

		} else {

			response.put("secretPhraseStrength", 5);

		}

		final Account account = Account.getAccount(accountId);
		if (account == null) {

			response.put("balanceNQT", 0);

		} else {

			response.put("balanceNQT", account.getUnconfirmedBalanceNQT());

			final JSONArray myTransactions = new JSONArray();
			final byte[] accountPublicKey = Account.getPublicKey(accountId);
			try (DbIterator<? extends Transaction> transactions = Nxt.getTransactionProcessor()
					.getAllUnconfirmedTransactions()) {
				while (transactions.hasNext()) {
					final Transaction transaction = transactions.next();
					if (Arrays.equals(transaction.getSenderPublicKey(), accountPublicKey)) {

						final JSONObject myTransaction = new JSONObject();
						myTransaction.put("index", Users.getIndex(transaction));
						myTransaction.put("transactionTimestamp", transaction.getTimestamp());
						myTransaction.put("deadline", transaction.getDeadline());
						myTransaction.put("account", Long.toUnsignedString(transaction.getRecipientId()));
						myTransaction.put("sentAmountNQT", transaction.getAmountNQT());
						if (accountId == transaction.getRecipientId()) {
							myTransaction.put("receivedAmountNQT", transaction.getAmountNQT());
						}
						myTransaction.put("feeNQT", transaction.getFeeNQT());
						myTransaction.put("numberOfConfirmations", -1);
						myTransaction.put("id", transaction.getStringId());

						myTransactions.add(myTransaction);

					} else if (accountId == transaction.getRecipientId()) {

						final JSONObject myTransaction = new JSONObject();
						myTransaction.put("index", Users.getIndex(transaction));
						myTransaction.put("transactionTimestamp", transaction.getTimestamp());
						myTransaction.put("deadline", transaction.getDeadline());
						myTransaction.put("account", Long.toUnsignedString(transaction.getSenderId()));
						myTransaction.put("receivedAmountNQT", transaction.getAmountNQT());
						myTransaction.put("feeNQT", transaction.getFeeNQT());
						myTransaction.put("numberOfConfirmations", -1);
						myTransaction.put("id", transaction.getStringId());

						myTransactions.add(myTransaction);

					}
				}
			}

			final SortedSet<JSONObject> myTransactionsSet = new TreeSet<>(UnlockAccount.myTransactionsComparator);

			final int blockchainHeight = Nxt.getBlockchain().getLastBlock().getHeight();
			final Iterator<BlockImpl> it = Nxt.getBlockchain().getBlocks(accountId, 0).iterator();
			while (it.hasNext()) {
				final Block block = it.next();
				if (block.getTotalFeeNQT() > 0) {
					final JSONObject myTransaction = new JSONObject();
					myTransaction.put("index", "block" + Users.getIndex(block));
					myTransaction.put("blockTimestamp", block.getTimestamp());
					myTransaction.put("block", block.getStringId());
					myTransaction.put("earnedAmountNQT", block.getTotalFeeNQT());
					myTransaction.put("numberOfConfirmations", blockchainHeight - block.getHeight());
					myTransaction.put("id", "-");
					myTransaction.put("timestamp", block.getTimestamp());
					myTransactionsSet.add(myTransaction);
				}

			}

			final Iterator<Transaction> transactionIterator = Nxt.getBlockchain()
					.getTransactions(accountId, (byte) -1, (byte) -1, 0, false).iterator();

			while (transactionIterator.hasNext()) {
				final Transaction transaction = transactionIterator.next();
				if (transaction.getSenderId() == accountId) {
					final JSONObject myTransaction = new JSONObject();
					myTransaction.put("index", Users.getIndex(transaction));
					myTransaction.put("blockTimestamp", transaction.getBlockTimestamp());
					myTransaction.put("transactionTimestamp", transaction.getTimestamp());
					myTransaction.put("account", Long.toUnsignedString(transaction.getRecipientId()));
					myTransaction.put("sentAmountNQT", transaction.getAmountNQT());
					if (accountId == transaction.getRecipientId()) {
						myTransaction.put("receivedAmountNQT", transaction.getAmountNQT());
					}
					myTransaction.put("feeNQT", transaction.getFeeNQT());
					myTransaction.put("numberOfConfirmations", blockchainHeight - transaction.getHeight());
					myTransaction.put("id", transaction.getStringId());
					myTransaction.put("timestamp", transaction.getTimestamp());
					myTransactionsSet.add(myTransaction);
				} else if (transaction.getRecipientId() == accountId) {
					final JSONObject myTransaction = new JSONObject();
					myTransaction.put("index", Users.getIndex(transaction));
					myTransaction.put("blockTimestamp", transaction.getBlockTimestamp());
					myTransaction.put("transactionTimestamp", transaction.getTimestamp());
					myTransaction.put("account", Long.toUnsignedString(transaction.getSenderId()));
					myTransaction.put("receivedAmountNQT", transaction.getAmountNQT());
					myTransaction.put("feeNQT", transaction.getFeeNQT());
					myTransaction.put("numberOfConfirmations", blockchainHeight - transaction.getHeight());
					myTransaction.put("id", transaction.getStringId());
					myTransaction.put("timestamp", transaction.getTimestamp());
					myTransactionsSet.add(myTransaction);
				}

			}

			final Iterator<JSONObject> iterator = myTransactionsSet.iterator();
			while ((myTransactions.size() < 1000) && iterator.hasNext()) {
				myTransactions.add(iterator.next());
			}

			if (myTransactions.size() > 0) {
				final JSONObject response2 = new JSONObject();
				response2.put("response", "processNewData");
				response2.put("addedMyTransactions", myTransactions);
				user.enqueue(response2);
			}
		}
		return response;
	}

	@Override
	boolean requirePost() {
		return true;
	}

}

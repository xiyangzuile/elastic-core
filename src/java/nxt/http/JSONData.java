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

package nxt.http;

import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import nxt.Account;
import nxt.AccountLedger;
import nxt.AccountLedger.LedgerEntry;
import nxt.Appendix;
import nxt.Block;
import nxt.Constants;
import nxt.Generator;
import nxt.Nxt;
import nxt.Token;
import nxt.Transaction;
import nxt.crypto.Crypto;
import nxt.crypto.EncryptedData;
import nxt.peer.Hallmark;
import nxt.peer.Peer;
import nxt.util.Convert;
import nxt.util.Filter;

public final class JSONData {

	static JSONObject accountBalance(final Account account, final boolean includeEffectiveBalance) {
		return JSONData.accountBalance(account, includeEffectiveBalance, Nxt.getBlockchain().getHeight());
	}

	static JSONObject accountBalance(final Account account, final boolean includeEffectiveBalance, final int height) {
		final JSONObject json = new JSONObject();
		if (account == null) {
			json.put("balanceNQT", "0");
			json.put("unconfirmedBalanceNQT", "0");
			json.put("forgedBalanceNQT", "0");
			if (includeEffectiveBalance) {
				json.put("effectiveBalanceNXT", "0");
				json.put("guaranteedBalanceNQT", "0");
			}
		} else {
			json.put("balanceNQT", String.valueOf(account.getBalanceNQT()));
			json.put("unconfirmedBalanceNQT", String.valueOf(account.getUnconfirmedBalanceNQT()));
			json.put("forgedBalanceNQT", String.valueOf(account.getForgedBalanceNQT()));
			if (includeEffectiveBalance) {
				json.put("effectiveBalanceNXT", account.getEffectiveBalanceNXT(height));
				json.put("guaranteedBalanceNQT", String
						.valueOf(account.getGuaranteedBalanceNQT(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, height)));
			}
		}
		return json;
	}

	static JSONObject apiRequestHandler(final APIServlet.APIRequestHandler handler) {
		final JSONObject json = new JSONObject();
		json.put("allowRequiredBlockParameters", handler.allowRequiredBlockParameters());
		if (handler.getFileParameter() != null) {
			json.put("fileParameter", handler.getFileParameter());
		}
		json.put("requireBlockchain", handler.requireBlockchain());
		json.put("requirePost", handler.requirePost());
		json.put("requirePassword", handler.requirePassword());
		json.put("requireFullClient", handler.requireFullClient());
		return json;
	}

	static JSONObject block(final Block block, final boolean includeTransactions, final boolean includeExecutedPhased) {
		final JSONObject json = new JSONObject();
		json.put("block", block.getStringId());
		json.put("height", block.getHeight());
		JSONData.putAccount(json, "generator", block.getGeneratorId());
		json.put("generatorPublicKey", Convert.toHexString(block.getGeneratorPublicKey()));
		json.put("timestamp", block.getTimestamp());
		json.put("numberOfTransactions", block.getTransactions().size());
		json.put("totalAmountNQT", String.valueOf(block.getTotalAmountNQT()));
		json.put("totalFeeNQT", String.valueOf(block.getTotalFeeNQT()));
		json.put("payloadLength", block.getPayloadLength());
		json.put("version", block.getVersion());
		json.put("baseTarget", Long.toUnsignedString(block.getBaseTarget()));
		json.put("cumulativeDifficulty", block.getCumulativeDifficulty().toString());
		if (block.getPreviousBlockId() != 0) {
			json.put("previousBlock", Long.toUnsignedString(block.getPreviousBlockId()));
		}
		if (block.getNextBlockId() != 0) {
			json.put("nextBlock", Long.toUnsignedString(block.getNextBlockId()));
		}
		json.put("payloadHash", Convert.toHexString(block.getPayloadHash()));
		json.put("generationSignature", Convert.toHexString(block.getGenerationSignature()));
		if (block.getVersion() > 1) {
			json.put("previousBlockHash", Convert.toHexString(block.getPreviousBlockHash()));
		}
		json.put("blockSignature", Convert.toHexString(block.getBlockSignature()));
		final JSONArray transactions = new JSONArray();
		if (includeTransactions) {
			block.getTransactions().forEach(transaction -> transactions.add(JSONData.transaction(transaction)));
		} else {
			block.getTransactions().forEach(transaction -> transactions.add(transaction.getStringId()));
		}
		json.put("transactions", transactions);

		return json;
	}

	static JSONObject encryptedData(final EncryptedData encryptedData) {
		final JSONObject json = new JSONObject();
		json.put("data", Convert.toHexString(encryptedData.getData()));
		json.put("nonce", Convert.toHexString(encryptedData.getNonce()));
		return json;
	}

	static JSONObject generator(final Generator generator, final int elapsedTime) {
		final JSONObject response = new JSONObject();
		final long deadline = generator.getDeadline();
		JSONData.putAccount(response, "account", generator.getAccountId());
		response.put("deadline", deadline);
		response.put("hitTime", generator.getHitTime());
		response.put("remaining", Math.max(deadline - elapsedTime, 0));
		return response;
	}

	static JSONObject hallmark(final Hallmark hallmark) {
		final JSONObject json = new JSONObject();
		JSONData.putAccount(json, "account", Account.getId(hallmark.getPublicKey()));
		json.put("host", hallmark.getHost());
		json.put("port", hallmark.getPort());
		json.put("weight", hallmark.getWeight());
		final String dateString = Hallmark.formatDate(hallmark.getDate());
		json.put("date", dateString);
		json.put("valid", hallmark.isValid());
		return json;
	}

	static void ledgerEntry(final JSONObject json, final LedgerEntry entry, final boolean includeTransactions,
			final boolean includeHoldingInfo) {
		JSONData.putAccount(json, "account", entry.getAccountId());
		json.put("ledgerId", Long.toUnsignedString(entry.getLedgerId()));
		json.put("block", Long.toUnsignedString(entry.getBlockId()));
		json.put("height", entry.getHeight());
		json.put("timestamp", entry.getTimestamp());
		json.put("eventType", entry.getEvent().name());
		json.put("event", Long.toUnsignedString(entry.getEventId()));
		json.put("isTransactionEvent", entry.getEvent().isTransaction());
		json.put("change", String.valueOf(entry.getChange()));
		json.put("balance", String.valueOf(entry.getBalance()));
		final AccountLedger.LedgerHolding ledgerHolding = entry.getHolding();
		if (ledgerHolding != null) {
			json.put("holdingType", ledgerHolding.name());
			if (entry.getHoldingId() != null) {
				json.put("holding", Long.toUnsignedString(entry.getHoldingId()));
			}
		}
		if (includeTransactions && entry.getEvent().isTransaction()) {
			final Transaction transaction = Nxt.getBlockchain().getTransaction(entry.getEventId());
			json.put("transaction", JSONData.transaction(transaction));
		}
	}

	static JSONObject lessor(final Account account, final boolean includeEffectiveBalance) {
		final JSONObject json = new JSONObject();
		final Account.AccountLease accountLease = account.getAccountLease();
		if (accountLease.getCurrentLesseeId() != 0) {
			JSONData.putAccount(json, "currentLessee", accountLease.getCurrentLesseeId());
			json.put("currentHeightFrom", String.valueOf(accountLease.getCurrentLeasingHeightFrom()));
			json.put("currentHeightTo", String.valueOf(accountLease.getCurrentLeasingHeightTo()));
			if (includeEffectiveBalance) {
				json.put("effectiveBalanceNXT", String.valueOf(account.getGuaranteedBalanceNQT() / Constants.ONE_NXT));
			}
		}
		if (accountLease.getNextLesseeId() != 0) {
			JSONData.putAccount(json, "nextLessee", accountLease.getNextLesseeId());
			json.put("nextHeightFrom", String.valueOf(accountLease.getNextLeasingHeightFrom()));
			json.put("nextHeightTo", String.valueOf(accountLease.getNextLeasingHeightTo()));
		}
		return json;
	}

	static JSONObject peer(final Peer peer) {
		final JSONObject json = new JSONObject();
		json.put("address", peer.getHost());
		json.put("port", peer.getPort());
		json.put("state", peer.getState().ordinal());
		json.put("announcedAddress", peer.getAnnouncedAddress());
		json.put("shareAddress", peer.shareAddress());
		if (peer.getHallmark() != null) {
			json.put("hallmark", peer.getHallmark().getHallmarkString());
		}
		json.put("weight", peer.getWeight());
		json.put("downloadedVolume", peer.getDownloadedVolume());
		json.put("uploadedVolume", peer.getUploadedVolume());
		json.put("application", peer.getApplication());
		json.put("version", peer.getVersion());
		json.put("platform", peer.getPlatform());
		if (peer.getApiPort() != 0) {
			json.put("apiPort", peer.getApiPort());
		}
		if (peer.getApiSSLPort() != 0) {
			json.put("apiSSLPort", peer.getApiSSLPort());
		}
		json.put("blacklisted", peer.isBlacklisted());
		json.put("lastUpdated", peer.getLastUpdated());
		json.put("lastConnectAttempt", peer.getLastConnectAttempt());
		json.put("inbound", peer.isInbound());
		json.put("inboundWebSocket", peer.isInboundWebSocket());
		json.put("outboundWebSocket", peer.isOutboundWebSocket());
		if (peer.isBlacklisted()) {
			json.put("blacklistingCause", peer.getBlacklistingCause());
		}
		final JSONArray servicesArray = new JSONArray();
		for (final Peer.Service service : Peer.Service.values()) {
			if (peer.providesService(service)) {
				servicesArray.add(service.name());
			}
		}
		json.put("services", servicesArray);
		return json;
	}

	static void putAccount(final JSONObject json, final String name, final long accountId) {
		json.put(name, Long.toUnsignedString(accountId));
		json.put(name + "RS", Convert.rsAccount(accountId));
	}

	static void putException(final JSONObject json, final Exception e) {
		JSONData.putException(json, e, "");
	}

	static void putException(final JSONObject json, final Exception e, String error) {
		json.put("errorCode", 4);
		if (error.length() > 0) {
			error += ": ";
		}
		json.put("error", e.toString());
		json.put("errorDescription", error + e.getMessage());
	}

	static void putPrunableAttachment(final JSONObject json, final Transaction transaction) {
		final JSONObject prunableAttachment = transaction.getPrunableAttachmentJSON();
		if (prunableAttachment != null) {
			json.put("prunableAttachmentJSON", prunableAttachment);
		}
	}

	static JSONObject token(final Token token) {
		final JSONObject json = new JSONObject();
		JSONData.putAccount(json, "account", Account.getId(token.getPublicKey()));
		json.put("timestamp", token.getTimestamp());
		json.put("valid", token.isValid());
		return json;
	}

	static JSONObject transaction(final Transaction transaction) {
		final JSONObject json = JSONData.transaction(transaction, null);

		return json;
	}

	static JSONObject transaction(final Transaction transaction, final Filter<Appendix> filter) {
		final JSONObject json = JSONData.unconfirmedTransaction(transaction, filter);
		json.put("block", Long.toUnsignedString(transaction.getBlockId()));
		json.put("confirmations", Nxt.getBlockchain().getHeight() - transaction.getHeight());
		json.put("blockTimestamp", transaction.getBlockTimestamp());
		json.put("transactionIndex", transaction.getIndex());
		return json;
	}

	static JSONObject unconfirmedTransaction(final Transaction transaction) {
		return JSONData.unconfirmedTransaction(transaction, null);
	}

	static JSONObject unconfirmedTransaction(final Transaction transaction, final Filter<Appendix> filter) {
		final JSONObject json = new JSONObject();
		json.put("type", transaction.getType().getType());
		json.put("subtype", transaction.getType().getSubtype());
		json.put("timestamp", transaction.getTimestamp());
		json.put("deadline", transaction.getDeadline());
		json.put("senderPublicKey", Convert.toHexString(transaction.getSenderPublicKey()));
		if (transaction.getRecipientId() != 0) {
			JSONData.putAccount(json, "recipient", transaction.getRecipientId());
		}
		json.put("amountNQT", String.valueOf(transaction.getAmountNQT()));
		json.put("feeNQT", String.valueOf(transaction.getFeeNQT()));
		final String referencedTransactionFullHash = transaction.getReferencedTransactionFullHash();
		if (referencedTransactionFullHash != null) {
			json.put("referencedTransactionFullHash", referencedTransactionFullHash);
		}
		final byte[] signature = Convert.emptyToNull(transaction.getSignature());
		if (signature != null) {
			json.put("signature", Convert.toHexString(signature));
			json.put("signatureHash", Convert.toHexString(Crypto.sha256().digest(signature)));
			json.put("fullHash", transaction.getFullHash());
			json.put("transaction", transaction.getStringId());
		}
		final JSONObject attachmentJSON = new JSONObject();
		if (filter == null) {
			for (final Appendix appendage : transaction.getAppendages(true)) {
				attachmentJSON.putAll(appendage.getJSONObject());
			}
		} else {
			for (final Appendix appendage : transaction.getAppendages(filter, true)) {
				attachmentJSON.putAll(appendage.getJSONObject());
			}
		}
		if (!attachmentJSON.isEmpty()) {
			for (final Map.Entry entry : (Iterable<Map.Entry>) attachmentJSON.entrySet()) {
				if (entry.getValue() instanceof Long) {
					entry.setValue(String.valueOf(entry.getValue()));
				}
			}
			json.put("attachment", attachmentJSON);
		}
		JSONData.putAccount(json, "sender", transaction.getSenderId());
		json.put("height", transaction.getHeight());
		json.put("version", transaction.getVersion());
		if (transaction.getVersion() > 0) {
			json.put("ecBlockId", Long.toUnsignedString(transaction.getECBlockId()));
			json.put("ecBlockHeight", transaction.getECBlockHeight());
		}

		return json;
	}

	private JSONData() {
	} // never

}

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

package nxt.http;

import java.util.Arrays;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Constants;
import nxt.HoldingType;
import nxt.util.Convert;
import nxt.util.JSON;

final class JSONResponses {
	public static final JSONStreamAware MISSING_LANGUAGE = JSONResponses.missing("program language");
	public static final JSONStreamAware MISSING_PROGAMCODE = JSONResponses.missing("program code");
	public static final JSONStreamAware MISSING_BOUNTYHOOK = JSONResponses.missing("bounty hook");
	public static final JSONStreamAware MISSING_NUMBER_INPUTVARS = JSONResponses.missing("number of input variables");
	public static final JSONStreamAware MISSING_NUMBER_OUTPUTVARS = JSONResponses.missing("number of output variables");
	public static final JSONStreamAware MISSING_BOUNTYLIMIT = JSONResponses.missing("bounty limit");
	public static final JSONStreamAware INCORRECT_WORK_NAME_LENGTH = JSONResponses.incorrect("work title");
	public static final JSONStreamAware INCORRECT_MULTIPLICATOR = JSONResponses.incorrect("multiplicator");
	public static final JSONStreamAware INCORRECT_STORAGE = JSONResponses.incorrect("storage");

	public static final JSONStreamAware INCORRECT_VARIABLES_NUM = JSONResponses
			.incorrect("number of input or output variables");
	public static final JSONStreamAware INCORRECT_WORK_LANGUAGE = JSONResponses.incorrect("work language");
	public static final JSONStreamAware INCORRECT_INPUT_NUMBER = JSONResponses.incorrect("number of inputs");
	public static final JSONStreamAware INCORRECT_AMOUNT = JSONResponses.incorrect("attached amount");
	public static final JSONStreamAware INCORRECT_AST_RECURSION = JSONResponses.incorrect("ast tree depth");

	public static final JSONStreamAware INCORRECT_SYNTAX = JSONResponses.incorrect("syntax");
	public static final JSONStreamAware INCORRECT_PROGRAM = JSONResponses.incorrect("program code");
	public static final JSONStreamAware INCORRECT_BOUNTYHOOK = JSONResponses.incorrect("bounty hook");
	public static final JSONStreamAware INCORRECT_WORKID = JSONResponses.incorrect("workId");
	public static final JSONStreamAware INCORRECT_BOOLEAN = JSONResponses.incorrect("boolean");
	public static final JSONStreamAware INCORRECT_HASH = JSONResponses.incorrect("hash");

	public static final JSONStreamAware INCORRECT_XEL_PER_POW = JSONResponses.incorrect("pow price in XEL");
	public static final JSONStreamAware INCORRECT_XEL_PER_BOUNTY = JSONResponses.incorrect("bounty price in XEL");
	public static final JSONStreamAware MISSING_XEL_PER_POW = JSONResponses.missing("pow price in XEL");
	public static final JSONStreamAware MISSING_FIELDS_REDEEM = JSONResponses.missing("address or secp_signatures");

	public static final JSONStreamAware MISSING_XEL_PER_BOUNTY = JSONResponses.missing("bounty price in XEL");
	public static final JSONStreamAware INCORRECT_EXECUTION_TIME = JSONResponses.incorrect("worst case execution time");
	public static final JSONStreamAware INCORRECT_PUBLIC_KEY = JSONResponses.incorrect("publicKey");
	public static final JSONStreamAware MISSING_ALIAS_NAME = JSONResponses.missing("aliasName");
	public static final JSONStreamAware MISSING_ALIAS_OR_ALIAS_NAME = JSONResponses.missing("alias", "aliasName");
	public static final JSONStreamAware MISSING_DEADLINE = JSONResponses.missing("deadline");
	public static final JSONStreamAware INCORRECT_DEADLINE = JSONResponses.incorrect("deadline");
	public static final JSONStreamAware MISSING_TRANSACTION_BYTES_OR_JSON = JSONResponses.missing("transactionBytes",
			"transactionJSON");
	public static final JSONStreamAware MISSING_WORKID = JSONResponses.missing("workId");
	public static final JSONStreamAware MISSING_INPUTS = JSONResponses.missing("program inputs");
	public static final JSONStreamAware MISSING_PASSPHRASE = JSONResponses.missing("miner passphrase");

	public static final JSONStreamAware INCORRECT_INPUTS = JSONResponses.incorrect("inputs array");
	public static final JSONStreamAware UNKNOWN_ORDER = JSONResponses.unknown("order");
	public static final JSONStreamAware MISSING_HALLMARK = JSONResponses.missing("hallmark");
	public static final JSONStreamAware INCORRECT_HALLMARK = JSONResponses.incorrect("hallmark");
	public static final JSONStreamAware MISSING_WEBSITE = JSONResponses.missing("website");
	public static final JSONStreamAware INCORRECT_WEBSITE = JSONResponses.incorrect("website");
	public static final JSONStreamAware MISSING_TOKEN = JSONResponses.missing("token");
	public static final JSONStreamAware INCORRECT_TOKEN = JSONResponses.incorrect("token");
	public static final JSONStreamAware MISSING_ACCOUNT = JSONResponses.missing("account");
	public static final JSONStreamAware INCORRECT_ACCOUNT = JSONResponses.incorrect("account");
	public static final JSONStreamAware INCORRECT_TIMESTAMP = JSONResponses.incorrect("timestamp");
	public static final JSONStreamAware UNKNOWN_ACCOUNT = JSONResponses.unknown("account");
	public static final JSONStreamAware UNKNOWN_BLOCK = JSONResponses.unknown("block");
	public static final JSONStreamAware INCORRECT_BLOCK = JSONResponses.incorrect("block");
	public static final JSONStreamAware UNKNOWN_ENTRY = JSONResponses.unknown("entry");
	public static final JSONStreamAware MISSING_PEER = JSONResponses.missing("peer");
	public static final JSONStreamAware UNKNOWN_PEER = JSONResponses.unknown("peer");
	public static final JSONStreamAware MISSING_TRANSACTION = JSONResponses.missing("transaction");
	public static final JSONStreamAware UNKNOWN_TRANSACTION = JSONResponses.unknown("transaction");
	public static final JSONStreamAware INCORRECT_TRANSACTION = JSONResponses.incorrect("transaction");
	public static final JSONStreamAware MISSING_NAME = JSONResponses.missing("name");
	public static final JSONStreamAware INCORRECT_DECIMALS = JSONResponses.incorrect("decimals");
	public static final JSONStreamAware MISSING_HOST = JSONResponses.missing("host");
	public static final JSONStreamAware MISSING_DATE = JSONResponses.missing("date");
	public static final JSONStreamAware MISSING_WEIGHT = JSONResponses.missing("weight");
	public static final JSONStreamAware INCORRECT_HOST = JSONResponses.incorrect("host",
			"(the length exceeds 100 chars limit)");
	public static final JSONStreamAware INCORRECT_WEIGHT = JSONResponses.incorrect("weight");
	public static final JSONStreamAware INCORRECT_DATE = JSONResponses.incorrect("date");
	public static final JSONStreamAware INCORRECT_RECIPIENT = JSONResponses.incorrect("recipient");
	public static final JSONStreamAware INCORRECT_ARBITRARY_MESSAGE = JSONResponses.incorrect("message");
	public static final JSONStreamAware MISSING_DESCRIPTION = JSONResponses.missing("description");

	public static final JSONStreamAware INCORRECT_WHITELIST = JSONResponses.incorrect("whitelist");
	public static final JSONStreamAware INCORRECT_ACCOUNT_NAME_LENGTH = JSONResponses.incorrect("name",
			"(length must be less than " + Constants.MAX_ACCOUNT_NAME_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_ACCOUNT_DESCRIPTION_LENGTH = JSONResponses.incorrect("description",
			"(length must be less than " + Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH + " characters)");
	public static final JSONStreamAware MISSING_UNSIGNED_BYTES = JSONResponses.missing("unsignedTransactionBytes");
	public static final JSONStreamAware MISSING_SIGNATURE_HASH = JSONResponses.missing("signatureHash");

	public static final JSONStreamAware INCORRECT_ENCRYPTED_MESSAGE = JSONResponses.incorrect("encryptedMessageData");
	public static final JSONStreamAware INCORRECT_HEIGHT = JSONResponses.incorrect("height");
	public static final JSONStreamAware MISSING_HEIGHT = JSONResponses.missing("height");
	public static final JSONStreamAware INCORRECT_MESSAGE_TO_ENCRYPT = JSONResponses.incorrect("messageToEncrypt");
	public static final JSONStreamAware MISSING_MESSAGE_TO_ENCRYPT = JSONResponses.missing("messageToEncrypt");
	public static final JSONStreamAware INCORRECT_ADMIN_PASSWORD = JSONResponses.incorrect("adminPassword",
			"(the specified password does not match nxt.adminPassword)");
	public static final JSONStreamAware LOCKED_ADMIN_PASSWORD = JSONResponses.incorrect("adminPassword",
			"(locked for 1 hour, too many incorrect password attempts)");
	public static final JSONStreamAware OVERFLOW = JSONResponses.error("overflow");
	public static final JSONStreamAware RESPONSE_STREAM_ERROR = JSONResponses.responseError("responseOutputStream");
	public static final JSONStreamAware RESPONSE_WRITE_ERROR = JSONResponses.responseError("responseWrite");
	public static final JSONStreamAware MISSING_TRANSACTION_FULL_HASH = JSONResponses.missing("transactionFullHash");
	public static final JSONStreamAware UNKNOWN_TRANSACTION_FULL_HASH = JSONResponses.unknown("transactionFullHash");
	public static final JSONStreamAware INCORRECT_LINKED_FULL_HASH = JSONResponses.incorrect("phasingLinkedFullHash");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_NAME = JSONResponses.incorrect("name",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_NAME_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_DESCRIPTION = JSONResponses.incorrect("description",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_DESCRIPTION_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_TAGS = JSONResponses.incorrect("tags",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_TAGS_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_FILENAME = JSONResponses.incorrect("filename",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_FILENAME_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_TYPE = JSONResponses.incorrect("type",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_TYPE_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_CHANNEL = JSONResponses.incorrect("channel",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_CHANNEL_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_FILE = JSONResponses.incorrect("data",
			"cannot read file data");
	public static final JSONStreamAware INCORRECT_FILE = JSONResponses.incorrect("file", "cannot read file data");
	public static final JSONStreamAware INCORRECT_DATA = JSONResponses.incorrect("data",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_DATA_LENGTH + " bytes)");
	public static final JSONStreamAware MISSING_MESSAGE_ENCRYPTED_MESSAGE = JSONResponses.missing("message",
			"encryptedMessageData");
	public static final JSONStreamAware EITHER_MESSAGE_ENCRYPTED_MESSAGE = JSONResponses.either("message",
			"encryptedMessageData");
	public static final JSONStreamAware INCORRECT_HASH_ALGORITHM = JSONResponses.incorrect("hashAlgorithm");
	public static final JSONStreamAware MISSING_SECRET = JSONResponses.missing("secret");
	public static final JSONStreamAware INCORRECT_SECRET = JSONResponses.incorrect("secret");
	public static final JSONStreamAware MISSING_RECIPIENT_PUBLIC_KEY = JSONResponses.missing("recipientPublicKey");
	public static final JSONStreamAware INCORRECT_ACCOUNT_PROPERTY_NAME_LENGTH = JSONResponses.incorrect("property",
			"(length must be > 0 but less than " + Constants.MAX_ACCOUNT_PROPERTY_NAME_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_ACCOUNT_PROPERTY_VALUE_LENGTH = JSONResponses.incorrect("value",
			"(length must be less than " + Constants.MAX_ACCOUNT_PROPERTY_VALUE_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_PROPERTY = JSONResponses.incorrect("property",
			"(cannot be deleted by this account)");
	public static final JSONStreamAware UNKNOWN_PROPERTY = JSONResponses.unknown("property");
	public static final JSONStreamAware MISSING_PROPERTY = JSONResponses.missing("property");
	public static final JSONStreamAware INCORRECT_EC_BLOCK = JSONResponses.incorrect("ecBlockId",
			"ecBlockId does not match the block id at ecBlockHeight");

	public static final JSONStreamAware NOT_ENOUGH_FUNDS;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 6);
		response.put("errorDescription", "Not enough funds");
		NOT_ENOUGH_FUNDS = JSON.prepare(response);
	}

	public static final JSONStreamAware ERROR_NOT_ALLOWED;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 7);
		response.put("errorDescription", "Not allowed");
		ERROR_NOT_ALLOWED = JSON.prepare(response);
	}

	public static final JSONStreamAware ERROR_DISABLED;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 16);
		response.put("errorDescription", "This API has been disabled");
		ERROR_DISABLED = JSON.prepare(response);
	}

	public static final JSONStreamAware ERROR_INCORRECT_REQUEST;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 1);
		response.put("errorDescription", "Incorrect request");
		ERROR_INCORRECT_REQUEST = JSON.prepare(response);
	}

	public static final JSONStreamAware NOT_FORGING;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Account is not forging");
		NOT_FORGING = JSON.prepare(response);
	}

	public static final JSONStreamAware POST_REQUIRED;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 1);
		response.put("errorDescription", "This request is only accepted using POST!");
		POST_REQUIRED = JSON.prepare(response);
	}

	public static final JSONStreamAware FEATURE_NOT_AVAILABLE;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 9);
		response.put("errorDescription", "Feature not available");
		FEATURE_NOT_AVAILABLE = JSON.prepare(response);
	}

	public static final JSONStreamAware DECRYPTION_FAILED;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Decryption failed");
		DECRYPTION_FAILED = JSON.prepare(response);
	}

	private static final JSONStreamAware DUPLICATE_REFUND;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Refund already sent");
		DUPLICATE_REFUND = JSON.prepare(response);
	}

	private static final JSONStreamAware NO_MESSAGE;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "No attached message found");
		NO_MESSAGE = JSON.prepare(response);
	}

	private static final JSONStreamAware HEIGHT_NOT_AVAILABLE;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Requested height not available");
		HEIGHT_NOT_AVAILABLE = JSON.prepare(response);
	}

	public static final JSONStreamAware NO_PASSWORD_IN_CONFIG;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Administrator's password is not configured. Please set nxt.adminPassword");
		NO_PASSWORD_IN_CONFIG = JSON.prepare(response);
	}

	private static final JSONStreamAware POLL_RESULTS_NOT_AVAILABLE;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Poll results no longer available, set nxt.processPolls=true and rescan");
		POLL_RESULTS_NOT_AVAILABLE = JSON.prepare(response);
	}

	private static final JSONStreamAware POLL_FINISHED;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Poll has already finished");
		POLL_FINISHED = JSON.prepare(response);
	}

	private static final JSONStreamAware HASHES_MISMATCH;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 10);
		response.put("errorDescription", "Hashes don't match. You should notify Jeff Garzik.");
		HASHES_MISMATCH = JSON.prepare(response);
	}

	public static final JSONStreamAware REQUIRED_BLOCK_NOT_FOUND;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 13);
		response.put("errorDescription", "Required block not found in the blockchain");
		REQUIRED_BLOCK_NOT_FOUND = JSON.prepare(response);
	}

	public static final JSONStreamAware REQUIRED_LAST_BLOCK_NOT_FOUND;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 14);
		response.put("errorDescription", "Current last block is different");
		REQUIRED_LAST_BLOCK_NOT_FOUND = JSON.prepare(response);
	}

	public static final JSONStreamAware MISSING_SECRET_PHRASE;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 3);
		response.put("errorDescription", "secretPhrase not specified or not submitted to the remote node");
		MISSING_SECRET_PHRASE = JSON.prepare(response);
	}

	public static final JSONStreamAware PRUNED_TRANSACTION;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 15);
		response.put("errorDescription", "Pruned transaction data not currently available from any peer");
		PRUNED_TRANSACTION = JSON.prepare(response);
	}

	public static final JSONStreamAware PROXY_MISSING_REQUEST_TYPE;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 17);
		response.put("errorDescription", "Proxy servlet needs requestType parameter in the URL query");
		PROXY_MISSING_REQUEST_TYPE = JSON.prepare(response);
	}

	public static final JSONStreamAware PROXY_SECRET_DATA_DETECTED;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 18);
		response.put("errorDescription", "Proxied requests contains secret parameters");
		PROXY_SECRET_DATA_DETECTED = JSON.prepare(response);
	}

	public static final JSONStreamAware API_PROXY_NO_OPEN_API_PEERS;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 19);
		response.put("errorDescription", "No openAPI peers found");
		API_PROXY_NO_OPEN_API_PEERS = JSON.prepare(response);
	}

	public static final JSONStreamAware LIGHT_CLIENT_DISABLED_API;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 20);
		response.put("errorDescription", "This API is disabled when running as light client");
		LIGHT_CLIENT_DISABLED_API = JSON.prepare(response);
	}

	public static final JSONStreamAware PEER_NOT_CONNECTED;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Peer not connected");
		PEER_NOT_CONNECTED = JSON.prepare(response);
	}

	public static final JSONStreamAware PEER_NOT_OPEN_API;
	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Peer is not providing open API");
		PEER_NOT_OPEN_API = JSON.prepare(response);
	}

	private static final JSONStreamAware MONITOR_ALREADY_STARTED;

	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Account monitor already started");
		MONITOR_ALREADY_STARTED = JSON.prepare(response);
	}

	private static final JSONStreamAware MONITOR_NOT_STARTED;

	static {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Account monitor not started");
		MONITOR_NOT_STARTED = JSON.prepare(response);
	}

	static JSONStreamAware either(final String... paramNames) {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 6);
		response.put("errorDescription", "Not more than one of " + Arrays.toString(paramNames) + " can be specified");
		return JSON.prepare(response);
	}

	private static JSONStreamAware error(final String error) {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 11);
		response.put("errorDescription", error);
		return JSON.prepare(response);
	}

	static JSONStreamAware fileNotFound(final String objectName) {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 10);
		response.put("errorDescription", "File not found " + objectName);
		return JSON.prepare(response);
	}

	static JSONStreamAware incorrect(final String paramName) {
		return JSONResponses.incorrect(paramName, null);
	}

	static JSONStreamAware incorrect(final String paramName, final String details) {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 4);
		response.put("errorDescription", "Incorrect \"" + paramName + (details != null ? "\" " + details : "\""));
		return JSON.prepare(response);
	}

	static JSONStreamAware missing(final String... paramNames) {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 3);
		if (paramNames.length == 1) response.put("errorDescription", "\"" + paramNames[0] + "\"" + " not specified");
		else
			response.put("errorDescription", "At least one of " + Arrays.toString(paramNames) + " must be specified");
		return JSON.prepare(response);
	}

	static JSONStreamAware notEnoughHolding(final HoldingType holdingType) {
		switch (holdingType) {
		case NXT:
			return JSONResponses.NOT_ENOUGH_FUNDS;
		default:
			throw new RuntimeException();
		}
	}

	private static JSONStreamAware responseError(final String error) {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 12);
		response.put("errorDescription", error);
		return JSON.prepare(response);
	}

	private static JSONStreamAware unknown(final String objectName) {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Unknown " + objectName);
		return JSON.prepare(response);
	}

	static JSONStreamAware unknownAccount(final long id) {
		final JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Unknown account");
		response.put("account", Long.toUnsignedString(id));
		response.put("accountRS", Convert.rsAccount(id));
		return JSON.prepare(response);
	}

	private JSONResponses() {
	} // never

}

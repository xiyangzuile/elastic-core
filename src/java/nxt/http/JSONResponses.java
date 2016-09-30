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

import java.util.Arrays;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Constants;
import nxt.HoldingType;
import nxt.util.Convert;
import nxt.util.JSON;

public final class JSONResponses {
	public static final JSONStreamAware MISSING_LANGUAGE = missing("program language");
	public static final JSONStreamAware MISSING_PROGAMCODE = missing("program code");
	public static final JSONStreamAware MISSING_BOUNTYHOOK = missing("bounty hook");
	public static final JSONStreamAware MISSING_NUMBER_INPUTVARS = missing("number of input variables");
	public static final JSONStreamAware MISSING_NUMBER_OUTPUTVARS = missing("number of output variables");
	public static final JSONStreamAware MISSING_BOUNTYLIMIT = missing("bounty limit");
	public static final JSONStreamAware INCORRECT_WORK_NAME_LENGTH = incorrect("work title");
	public static final JSONStreamAware INCORRECT_MULTIPLICATOR = incorrect("multiplicator");

	public static final JSONStreamAware INCORRECT_VARIABLES_NUM = incorrect("number of input or output variables");
	public static final JSONStreamAware INCORRECT_WORK_LANGUAGE = incorrect("work language");
	public static final JSONStreamAware INCORRECT_INPUT_NUMBER = incorrect("number of inputs");
	public static final JSONStreamAware INCORRECT_AMOUNT = incorrect("attached amount");
	public static final JSONStreamAware INCORRECT_AST_RECURSION = incorrect("ast tree depth");

	public static final JSONStreamAware INCORRECT_SYNTAX = incorrect("syntax");
	public static final JSONStreamAware INCORRECT_PROGRAM = incorrect("program code");
	public static final JSONStreamAware INCORRECT_BOUNTYHOOK = incorrect("bounty hook");
	public static final JSONStreamAware INCORRECT_WORKID = incorrect("workId");
	public static final JSONStreamAware INCORRECT_BOOLEAN = incorrect("boolean");
	public static final JSONStreamAware INCORRECT_HASH = incorrect("hash");

	public static final JSONStreamAware INCORRECT_XEL_PER_POW = incorrect("pow price in XEL");
	public static final JSONStreamAware INCORRECT_XEL_PER_BOUNTY = incorrect("bounty price in XEL");
	public static final JSONStreamAware MISSING_XEL_PER_POW = missing("pow price in XEL");
	public static final JSONStreamAware MISSING_XEL_PER_BOUNTY = missing("bounty price in XEL");
	public static final JSONStreamAware INCORRECT_EXECUTION_TIME = incorrect("worst case execution time");
	public static final JSONStreamAware INCORRECT_PUBLIC_KEY = incorrect("publicKey");
	public static final JSONStreamAware MISSING_ALIAS_NAME = missing("aliasName");
	public static final JSONStreamAware MISSING_ALIAS_OR_ALIAS_NAME = missing("alias", "aliasName");
	public static final JSONStreamAware MISSING_DEADLINE = missing("deadline");
	public static final JSONStreamAware INCORRECT_DEADLINE = incorrect("deadline");
	public static final JSONStreamAware MISSING_TRANSACTION_BYTES_OR_JSON = missing("transactionBytes",
			"transactionJSON");
	public static final JSONStreamAware MISSING_WORKID = missing("workId");
	public static final JSONStreamAware MISSING_INPUTS = missing("program inputs");
	public static final JSONStreamAware MISSING_PASSPHRASE = missing("miner passphrase");

    public static final JSONStreamAware INCORRECT_INPUTS = incorrect("inputs array");
	public static final JSONStreamAware UNKNOWN_ORDER = unknown("order");
	public static final JSONStreamAware MISSING_HALLMARK = missing("hallmark");
	public static final JSONStreamAware INCORRECT_HALLMARK = incorrect("hallmark");
	public static final JSONStreamAware MISSING_WEBSITE = missing("website");
	public static final JSONStreamAware INCORRECT_WEBSITE = incorrect("website");
	public static final JSONStreamAware MISSING_TOKEN = missing("token");
	public static final JSONStreamAware INCORRECT_TOKEN = incorrect("token");
	public static final JSONStreamAware MISSING_ACCOUNT = missing("account");
	public static final JSONStreamAware INCORRECT_ACCOUNT = incorrect("account");
	public static final JSONStreamAware INCORRECT_TIMESTAMP = incorrect("timestamp");
	public static final JSONStreamAware UNKNOWN_ACCOUNT = unknown("account");
	public static final JSONStreamAware UNKNOWN_BLOCK = unknown("block");
	public static final JSONStreamAware INCORRECT_BLOCK = incorrect("block");
	public static final JSONStreamAware UNKNOWN_ENTRY = unknown("entry");
	public static final JSONStreamAware MISSING_PEER = missing("peer");
	public static final JSONStreamAware UNKNOWN_PEER = unknown("peer");
	public static final JSONStreamAware MISSING_TRANSACTION = missing("transaction");
	public static final JSONStreamAware UNKNOWN_TRANSACTION = unknown("transaction");
	public static final JSONStreamAware INCORRECT_TRANSACTION = incorrect("transaction");
	public static final JSONStreamAware MISSING_NAME = missing("name");
	public static final JSONStreamAware INCORRECT_DECIMALS = incorrect("decimals");
	public static final JSONStreamAware MISSING_HOST = missing("host");
	public static final JSONStreamAware MISSING_DATE = missing("date");
	public static final JSONStreamAware MISSING_WEIGHT = missing("weight");
	public static final JSONStreamAware INCORRECT_HOST = incorrect("host", "(the length exceeds 100 chars limit)");
	public static final JSONStreamAware INCORRECT_WEIGHT = incorrect("weight");
	public static final JSONStreamAware INCORRECT_DATE = incorrect("date");
	public static final JSONStreamAware INCORRECT_RECIPIENT = incorrect("recipient");
	public static final JSONStreamAware INCORRECT_ARBITRARY_MESSAGE = incorrect("message");
	public static final JSONStreamAware MISSING_DESCRIPTION = missing("description");

	public static final JSONStreamAware INCORRECT_WHITELIST = incorrect("whitelist");
	public static final JSONStreamAware INCORRECT_ACCOUNT_NAME_LENGTH = incorrect("name",
			"(length must be less than " + Constants.MAX_ACCOUNT_NAME_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_ACCOUNT_DESCRIPTION_LENGTH = incorrect("description",
			"(length must be less than " + Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH + " characters)");
	public static final JSONStreamAware MISSING_UNSIGNED_BYTES = missing("unsignedTransactionBytes");
	public static final JSONStreamAware MISSING_SIGNATURE_HASH = missing("signatureHash");

	public static final JSONStreamAware INCORRECT_ENCRYPTED_MESSAGE = incorrect("encryptedMessageData");
	public static final JSONStreamAware INCORRECT_HEIGHT = incorrect("height");
	public static final JSONStreamAware MISSING_HEIGHT = missing("height");
	public static final JSONStreamAware INCORRECT_MESSAGE_TO_ENCRYPT = incorrect("messageToEncrypt");
	public static final JSONStreamAware MISSING_MESSAGE_TO_ENCRYPT = missing("messageToEncrypt");
	public static final JSONStreamAware INCORRECT_ADMIN_PASSWORD = incorrect("adminPassword",
			"(the specified password does not match nxt.adminPassword)");
	public static final JSONStreamAware LOCKED_ADMIN_PASSWORD = incorrect("adminPassword",
			"(locked for 1 hour, too many incorrect password attempts)");
	public static final JSONStreamAware OVERFLOW = error("overflow");
	public static final JSONStreamAware RESPONSE_STREAM_ERROR = responseError("responseOutputStream");
	public static final JSONStreamAware RESPONSE_WRITE_ERROR = responseError("responseWrite");
	public static final JSONStreamAware MISSING_TRANSACTION_FULL_HASH = missing("transactionFullHash");
	public static final JSONStreamAware UNKNOWN_TRANSACTION_FULL_HASH = unknown("transactionFullHash");
	public static final JSONStreamAware INCORRECT_LINKED_FULL_HASH = incorrect("phasingLinkedFullHash");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_NAME = incorrect("name",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_NAME_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_DESCRIPTION = incorrect("description",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_DESCRIPTION_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_TAGS = incorrect("tags",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_TAGS_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_FILENAME = incorrect("filename",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_FILENAME_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_TYPE = incorrect("type",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_TYPE_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_CHANNEL = incorrect("channel",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_CHANNEL_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_TAGGED_DATA_FILE = incorrect("data", "cannot read file data");
	public static final JSONStreamAware INCORRECT_FILE = incorrect("file", "cannot read file data");
	public static final JSONStreamAware INCORRECT_DATA = incorrect("data",
			"(length must be not longer than " + Constants.MAX_TAGGED_DATA_DATA_LENGTH + " bytes)");
	public static final JSONStreamAware MISSING_MESSAGE_ENCRYPTED_MESSAGE = missing("message", "encryptedMessageData");
	public static final JSONStreamAware EITHER_MESSAGE_ENCRYPTED_MESSAGE = either("message", "encryptedMessageData");
	public static final JSONStreamAware INCORRECT_HASH_ALGORITHM = incorrect("hashAlgorithm");
	public static final JSONStreamAware MISSING_SECRET = missing("secret");
	public static final JSONStreamAware INCORRECT_SECRET = incorrect("secret");
	public static final JSONStreamAware MISSING_RECIPIENT_PUBLIC_KEY = missing("recipientPublicKey");
	public static final JSONStreamAware INCORRECT_ACCOUNT_PROPERTY_NAME_LENGTH = incorrect("property",
			"(length must be > 0 but less than " + Constants.MAX_ACCOUNT_PROPERTY_NAME_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_ACCOUNT_PROPERTY_VALUE_LENGTH = incorrect("value",
			"(length must be less than " + Constants.MAX_ACCOUNT_PROPERTY_VALUE_LENGTH + " characters)");
	public static final JSONStreamAware INCORRECT_PROPERTY = incorrect("property",
			"(cannot be deleted by this account)");
	public static final JSONStreamAware UNKNOWN_PROPERTY = unknown("property");
	public static final JSONStreamAware MISSING_PROPERTY = missing("property");
	public static final JSONStreamAware INCORRECT_EC_BLOCK = incorrect("ecBlockId",
			"ecBlockId does not match the block id at ecBlockHeight");

	public static final JSONStreamAware NOT_ENOUGH_FUNDS;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 6);
		response.put("errorDescription", "Not enough funds");
		NOT_ENOUGH_FUNDS = JSON.prepare(response);
	}

	public static final JSONStreamAware ERROR_NOT_ALLOWED;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 7);
		response.put("errorDescription", "Not allowed");
		ERROR_NOT_ALLOWED = JSON.prepare(response);
	}

	public static final JSONStreamAware ERROR_DISABLED;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 16);
		response.put("errorDescription", "This API has been disabled");
		ERROR_DISABLED = JSON.prepare(response);
	}

	public static final JSONStreamAware ERROR_INCORRECT_REQUEST;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 1);
		response.put("errorDescription", "Incorrect request");
		ERROR_INCORRECT_REQUEST = JSON.prepare(response);
	}

	public static final JSONStreamAware NOT_FORGING;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Account is not forging");
		NOT_FORGING = JSON.prepare(response);
	}

	public static final JSONStreamAware POST_REQUIRED;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 1);
		response.put("errorDescription", "This request is only accepted using POST!");
		POST_REQUIRED = JSON.prepare(response);
	}

	public static final JSONStreamAware FEATURE_NOT_AVAILABLE;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 9);
		response.put("errorDescription", "Feature not available");
		FEATURE_NOT_AVAILABLE = JSON.prepare(response);
	}

	public static final JSONStreamAware DECRYPTION_FAILED;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Decryption failed");
		DECRYPTION_FAILED = JSON.prepare(response);
	}

	public static final JSONStreamAware DUPLICATE_REFUND;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Refund already sent");
		DUPLICATE_REFUND = JSON.prepare(response);
	}

	public static final JSONStreamAware NO_MESSAGE;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "No attached message found");
		NO_MESSAGE = JSON.prepare(response);
	}

	public static final JSONStreamAware HEIGHT_NOT_AVAILABLE;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Requested height not available");
		HEIGHT_NOT_AVAILABLE = JSON.prepare(response);
	}

	public static final JSONStreamAware NO_PASSWORD_IN_CONFIG;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Administrator's password is not configured. Please set nxt.adminPassword");
		NO_PASSWORD_IN_CONFIG = JSON.prepare(response);
	}

	public static final JSONStreamAware POLL_RESULTS_NOT_AVAILABLE;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Poll results no longer available, set nxt.processPolls=true and rescan");
		POLL_RESULTS_NOT_AVAILABLE = JSON.prepare(response);
	}

	public static final JSONStreamAware POLL_FINISHED;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 8);
		response.put("errorDescription", "Poll has already finished");
		POLL_FINISHED = JSON.prepare(response);
	}

	public static final JSONStreamAware HASHES_MISMATCH;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 10);
		response.put("errorDescription", "Hashes don't match. You should notify Jeff Garzik.");
		HASHES_MISMATCH = JSON.prepare(response);
	}

	public static final JSONStreamAware REQUIRED_BLOCK_NOT_FOUND;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 13);
		response.put("errorDescription", "Required block not found in the blockchain");
		REQUIRED_BLOCK_NOT_FOUND = JSON.prepare(response);
	}

	public static final JSONStreamAware REQUIRED_LAST_BLOCK_NOT_FOUND;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 14);
		response.put("errorDescription", "Current last block is different");
		REQUIRED_LAST_BLOCK_NOT_FOUND = JSON.prepare(response);
	}

	public static final JSONStreamAware MISSING_SECRET_PHRASE;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 3);
		response.put("errorDescription", "secretPhrase not specified or not submitted to the remote node");
		MISSING_SECRET_PHRASE = JSON.prepare(response);
	}

	public static final JSONStreamAware PRUNED_TRANSACTION;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 15);
		response.put("errorDescription", "Pruned transaction data not currently available from any peer");
		PRUNED_TRANSACTION = JSON.prepare(response);
	}

	public static final JSONStreamAware PROXY_MISSING_REQUEST_TYPE;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 17);
		response.put("errorDescription", "Proxy servlet needs requestType parameter in the URL query");
		PROXY_MISSING_REQUEST_TYPE = JSON.prepare(response);
	}

	public static final JSONStreamAware PROXY_SECRET_DATA_DETECTED;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 18);
		response.put("errorDescription", "Proxied requests contains secret parameters");
		PROXY_SECRET_DATA_DETECTED = JSON.prepare(response);
	}

	public static final JSONStreamAware API_PROXY_NO_OPEN_API_PEERS;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 19);
		response.put("errorDescription", "No openAPI peers found");
		API_PROXY_NO_OPEN_API_PEERS = JSON.prepare(response);
	}

	public static final JSONStreamAware LIGHT_CLIENT_DISABLED_API;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 20);
		response.put("errorDescription", "This API is disabled when running as light client");
		LIGHT_CLIENT_DISABLED_API = JSON.prepare(response);
	}

	public static final JSONStreamAware PEER_NOT_CONNECTED;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Peer not connected");
		PEER_NOT_CONNECTED = JSON.prepare(response);
	}

	public static final JSONStreamAware PEER_NOT_OPEN_API;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Peer is not providing open API");
		PEER_NOT_OPEN_API = JSON.prepare(response);
	}

	static JSONStreamAware missing(String... paramNames) {
		JSONObject response = new JSONObject();
		response.put("errorCode", 3);
		if (paramNames.length == 1) {
			response.put("errorDescription", "\"" + paramNames[0] + "\"" + " not specified");
		} else {
			response.put("errorDescription", "At least one of " + Arrays.toString(paramNames) + " must be specified");
		}
		return JSON.prepare(response);
	}

	static JSONStreamAware either(String... paramNames) {
		JSONObject response = new JSONObject();
		response.put("errorCode", 6);
		response.put("errorDescription", "Not more than one of " + Arrays.toString(paramNames) + " can be specified");
		return JSON.prepare(response);
	}

	static JSONStreamAware incorrect(String paramName) {
		return incorrect(paramName, null);
	}

	static JSONStreamAware incorrect(String paramName, String details) {
		JSONObject response = new JSONObject();
		response.put("errorCode", 4);
		response.put("errorDescription", "Incorrect \"" + paramName + (details != null ? "\" " + details : "\""));
		return JSON.prepare(response);
	}

	static JSONStreamAware unknown(String objectName) {
		JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Unknown " + objectName);
		return JSON.prepare(response);
	}

	static JSONStreamAware unknownAccount(long id) {
		JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Unknown account");
		response.put("account", Long.toUnsignedString(id));
		response.put("accountRS", Convert.rsAccount(id));
		return JSON.prepare(response);
	}

	static JSONStreamAware fileNotFound(String objectName) {
		JSONObject response = new JSONObject();
		response.put("errorCode", 10);
		response.put("errorDescription", "File not found " + objectName);
		return JSON.prepare(response);
	}

	static JSONStreamAware error(String error) {
		JSONObject response = new JSONObject();
		response.put("errorCode", 11);
		response.put("errorDescription", error);
		return JSON.prepare(response);
	}

	private static JSONStreamAware responseError(String error) {
		JSONObject response = new JSONObject();
		response.put("errorCode", 12);
		response.put("errorDescription", error);
		return JSON.prepare(response);
	}

	static JSONStreamAware notEnoughHolding(HoldingType holdingType) {
		switch (holdingType) {
		case NXT:
			return JSONResponses.NOT_ENOUGH_FUNDS;
		default:
			throw new RuntimeException();
		}
	}

	public static final JSONStreamAware MONITOR_ALREADY_STARTED;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Account monitor already started");
		MONITOR_ALREADY_STARTED = JSON.prepare(response);
	}

	public static final JSONStreamAware MONITOR_NOT_STARTED;
	static {
		JSONObject response = new JSONObject();
		response.put("errorCode", 5);
		response.put("errorDescription", "Account monitor not started");
		MONITOR_NOT_STARTED = JSON.prepare(response);
	}

	private JSONResponses() {
	} // never

}

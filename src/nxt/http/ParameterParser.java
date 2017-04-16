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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.eclipse.jetty.server.Request;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import nxt.Account;
import nxt.Appendix;
import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.Transaction;
import nxt.crypto.Crypto;
import nxt.crypto.EncryptedData;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.util.Search;

public final class ParameterParser {

	public static class FileData {
		private final Part part;
		private String filename;
		private byte[] data;

		public FileData(final Part part) {
			this.part = part;
		}

		public byte[] getData() {
			return this.data;
		}

		public String getFilename() {
			return this.filename;
		}

		public FileData invoke() throws IOException {
			try (InputStream is = this.part.getInputStream()) {
				int nRead;
				final byte[] bytes = new byte[1024];
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				while ((nRead = is.read(bytes, 0, bytes.length)) != -1) baos.write(bytes, 0, nRead);
				this.data = baos.toByteArray();
				this.filename = this.part.getSubmittedFileName();
			}
			return this;
		}
	}

	private static String convertStreamToString(final java.io.InputStream is) {
		final java.util.Scanner s = new java.util.Scanner(is);
		s.useDelimiter("\\A");
		final String res = s.hasNext() ? s.next() : "";
		s.close();
		return res;
	}

	public static Account getAccount(final HttpServletRequest req) throws ParameterException {
		return ParameterParser.getAccount(req, true);
	}

	private static Account getAccount(final HttpServletRequest req, final boolean isMandatory)
			throws ParameterException {
		final long accountId = ParameterParser.getAccountId(req, "account", isMandatory);
		if ((accountId == 0) && !isMandatory) return null;
		final Account account = Account.getAccount(accountId);
		if (account == null) throw new ParameterException(JSONResponses.unknownAccount(accountId));
		return account;
	}

	public static long getAccountId(final HttpServletRequest req, final boolean isMandatory) throws ParameterException {
		return ParameterParser.getAccountId(req, "account", isMandatory);
	}

	public static long getAccountId(final HttpServletRequest req, final String name, final boolean isMandatory)
			throws ParameterException {
		final String paramValue = Convert.emptyToNull(req.getParameter(name));
		if (paramValue == null) {
			if (isMandatory) throw new ParameterException(JSONResponses.missing(name));
			return 0;
		}
		try {
			final long value = Convert.parseAccountId(paramValue);
			if (value == 0) throw new ParameterException(JSONResponses.incorrect(name));
			return value;
		} catch (final RuntimeException e) {
			throw new ParameterException(JSONResponses.incorrect(name));
		}
	}

	public static Long[] getAccountIds(final HttpServletRequest req, final boolean isMandatory)
			throws ParameterException {
		final String[] paramValues = req.getParameterValues("account");
		if ((paramValues == null) || (paramValues.length == 0))
			if (isMandatory) throw new ParameterException(JSONResponses.MISSING_ACCOUNT);
			else return new Long[0];
		final Long[] values = new Long[paramValues.length];
		try {
			for (int i = 0; i < paramValues.length; i++) {
				if ((paramValues[i] == null) || paramValues[i].isEmpty())
					throw new ParameterException(JSONResponses.INCORRECT_ACCOUNT);
				values[i] = Convert.parseAccountId(paramValues[i]);
				if (values[i] == 0) throw new ParameterException(JSONResponses.INCORRECT_ACCOUNT);
			}
		} catch (final RuntimeException e) {
			throw new ParameterException(JSONResponses.INCORRECT_ACCOUNT);
		}
		return values;
	}

	public static List<Account> getAccounts(final HttpServletRequest req) throws ParameterException {
		final String[] accountValues = req.getParameterValues("account");
		if ((accountValues == null) || (accountValues.length == 0))
			throw new ParameterException(JSONResponses.MISSING_ACCOUNT);
		final List<Account> result = new ArrayList<>();
		for (final String accountValue : accountValues) {
			if ((accountValue == null) || accountValue.equals("")) continue;
			try {
				final Account account = Account.getAccount(Convert.parseAccountId(accountValue));
				if (account == null) throw new ParameterException(JSONResponses.UNKNOWN_ACCOUNT);
				result.add(account);
			} catch (final RuntimeException e) {
				throw new ParameterException(JSONResponses.INCORRECT_ACCOUNT);
			}
		}
		return result;
	}

	public static long getAmountNQT(final HttpServletRequest req) throws ParameterException {
		return ParameterParser.getLong(req, "amountNQT", 1L, Constants.MAX_BALANCE_NQT, true);
	}

	public static long getAmountNQTPerQNT(final HttpServletRequest req) throws ParameterException {
		return ParameterParser.getLong(req, "amountNQTPerQNT", 1L, Constants.MAX_BALANCE_NQT, true);
	}

	public static String getAnnouncement(final HttpServletRequest req, final boolean isMandatory)
			throws ParameterException {
		final String secretPhrase = Convert.emptyToNull(req.getParameter("hash_announcement"));
		if ((secretPhrase == null) && isMandatory) throw new ParameterException(JSONResponses.INCORRECT_HASH);
		return secretPhrase;
	}

	public static boolean getBoolean(final HttpServletRequest req, final String name, final boolean isMandatory)
			throws ParameterException {
		final String paramValue = Convert.emptyToNull(req.getParameter(name));
		if (paramValue == null) {
			if (isMandatory) throw new ParameterException(JSONResponses.missing(name));
			return false;
		}
		try {
			return Boolean.parseBoolean(paramValue);
		} catch (final RuntimeException e) {
			throw new ParameterException(
					JSONResponses.incorrect(name, String.format("value %s is not boolean", paramValue)));
		}
	}

	public static byte getByte(final HttpServletRequest req, final String name, final byte min, final byte max,
			final boolean isMandatory) throws ParameterException {
		final String paramValue = Convert.emptyToNull(req.getParameter(name));
		if (paramValue == null) {
			if (isMandatory) throw new ParameterException(JSONResponses.missing(name));
			return 0;
		}
		try {
			final byte value = Byte.parseByte(paramValue);
			if ((value < min) || (value > max)) throw new ParameterException(
					JSONResponses.incorrect(name, String.format("value %d not in range [%d-%d]", value, min, max)));
			return value;
		} catch (final RuntimeException e) {
			throw new ParameterException(
					JSONResponses.incorrect(name, String.format("value %s is not numeric", paramValue)));
		}
	}

	public static byte[] getBytes(final HttpServletRequest req, final String name, final boolean isMandatory)
			throws ParameterException {
		final String paramValue = Convert.emptyToNull(req.getParameter(name));
		if (paramValue == null) {
			if (isMandatory) throw new ParameterException(JSONResponses.missing(name));
			return Convert.EMPTY_BYTE;
		}
		return Convert.parseHexString(paramValue);
	}

	public static EncryptedData getEncryptedData(final HttpServletRequest req, final String messageType)
			throws ParameterException {
		final String dataString = Convert.emptyToNull(req.getParameter(messageType + "Data"));
		final String nonceString = Convert.emptyToNull(req.getParameter(messageType + "Nonce"));
		if (nonceString == null) return null;
		byte[] data;
		byte[] nonce;
		try {
			nonce = Convert.parseHexString(nonceString);
		} catch (final RuntimeException e) {
			throw new ParameterException(JSONResponses.incorrect(messageType + "Nonce"));
		}
		if (dataString != null) try {
			data = Convert.parseHexString(dataString);
		} catch (final RuntimeException e) {
			throw new ParameterException(JSONResponses.incorrect(messageType + "Data"));
		}
		else {
			if ((req.getContentType() == null) || !req.getContentType().startsWith("multipart/form-data")) return null;
			try {
				final Part part = req.getPart(messageType + "File");
				if (part == null) return null;
				final FileData fileData = new FileData(part).invoke();
				data = fileData.getData();
			} catch (IOException | ServletException e) {
				Logger.logDebugMessage("error in reading file data", e);
				throw new ParameterException(JSONResponses.incorrect(messageType + "File"));
			}
		}
		return new EncryptedData(data, nonce);
	}

	public static long getFeeNQT(final HttpServletRequest req) throws ParameterException {
		return ParameterParser.getLong(req, "feeNQT", 0L, Constants.MAX_BALANCE_NQT, true);
	}

	public static int getFirstIndex(final HttpServletRequest req) {
		try {
			final int firstIndex = Integer.parseInt(req.getParameter("firstIndex"));
			if (firstIndex < 0) return 0;
			return firstIndex;
		} catch (final NumberFormatException e) {
			return 0;
		}
	}

	public static int getHeight(final HttpServletRequest req) throws ParameterException {
		final String heightValue = Convert.emptyToNull(req.getParameter("height"));
		if (heightValue != null) try {
			final int height = Integer.parseInt(heightValue);
			if ((height < 0) || (height > Nxt.getBlockchain().getHeight()))
				throw new ParameterException(JSONResponses.INCORRECT_HEIGHT);
			return height;
		} catch (final NumberFormatException e) {
			throw new ParameterException(JSONResponses.INCORRECT_HEIGHT);
		}
		return -1;
	}

	public static int getInt(final HttpServletRequest req, final String name, final int min, final int max,
			final boolean isMandatory) throws ParameterException {
		final String paramValue = Convert.emptyToNull(req.getParameter(name));
		if (paramValue == null) {
			if (isMandatory) throw new ParameterException(JSONResponses.missing(name));
			return 0;
		}
		try {
			final int value = Integer.parseInt(paramValue);
			if ((value < min) || (value > max)) throw new ParameterException(
					JSONResponses.incorrect(name, String.format("value %d not in range [%d-%d]", value, min, max)));
			return value;
		} catch (final RuntimeException e) {
			throw new ParameterException(
					JSONResponses.incorrect(name, String.format("value %s is not numeric", paramValue)));
		}
	}

	public static int getLastIndex(final HttpServletRequest req) {
		int lastIndex = Integer.MAX_VALUE;
		try {
			lastIndex = Integer.parseInt(req.getParameter("lastIndex"));
			if (lastIndex < 0) lastIndex = Integer.MAX_VALUE;
		} catch (final NumberFormatException ignored) {
		}
		if (!API.checkPassword(req)) {
			final int firstIndex = Math.min(ParameterParser.getFirstIndex(req),
					(Integer.MAX_VALUE - API.maxRecords) + 1);
			lastIndex = Math.min(lastIndex, (firstIndex + API.maxRecords) - 1);
		}
		return lastIndex;
	}

	public static long getLong(final HttpServletRequest req, final String name, final boolean isMandatory)
			throws ParameterException {
		final String paramValue = Convert.emptyToNull(req.getParameter(name));
		if (paramValue == null) {
			if (isMandatory) throw new ParameterException(JSONResponses.missing(name));
			return 0;
		}
		try {
			return Long.parseLong(paramValue);
		} catch (final RuntimeException e) {
			throw new ParameterException(
					JSONResponses.incorrect(name, String.format("value %s is not numeric", paramValue)));
		}
	}

	private static long getLong(final HttpServletRequest req, final String name, final long min, final long max,
								final boolean isMandatory) throws ParameterException {
		final String paramValue = Convert.emptyToNull(req.getParameter(name));
		if (paramValue == null) {
			if (isMandatory) throw new ParameterException(JSONResponses.missing(name));
			return 0;
		}
		try {
			final long value = Long.parseLong(paramValue);
			if ((value < min) || (value > max)) throw new ParameterException(
					JSONResponses.incorrect(name, String.format("value %d not in range [%d-%d]", value, min, max)));
			return value;
		} catch (final RuntimeException e) {
			throw new ParameterException(
					JSONResponses.incorrect(name, String.format("value %s is not numeric", paramValue)));
		}
	}

	public static String getMultiplicator(final HttpServletRequest req, final boolean isMandatory)
			throws ParameterException {
		final String secretPhrase = Convert.emptyToNull(req.getParameter("multiplicator"));
		if ((secretPhrase == null) && isMandatory) throw new ParameterException(JSONResponses.INCORRECT_MULTIPLICATOR);
		return secretPhrase;
	}

	public static int getNumberOfConfirmations(final HttpServletRequest req) throws ParameterException {
		return ParameterParser.getInt(req, "numberOfConfirmations", 0, Nxt.getBlockchain().getHeight(), false);
	}

	public static long getOrCreateReceipientAccount(final HttpServletRequest req) throws ParameterException {
		return ParameterParser.getUnsignedLong(req, "receiver_id", true);
	}

	public static Account getOrCreateSenderAccount(final HttpServletRequest req) throws ParameterException {
		final byte[] publicKey = ParameterParser.getPublicKey(req);
		final Account account = Account.addOrGetAccount(publicKey);
		if (account == null) throw new ParameterException(JSONResponses.UNKNOWN_ACCOUNT);
		return account;
	}

	public static String getParameterMultipart(final HttpServletRequest req, final String arg0) {
		String result = req.getParameter(arg0);
		if ((result == null) && (Objects.equals(req.getMethod(), "POST"))
				&& req.getContentType().toLowerCase().contains("multipart")) try {
			final MultipartConfigElement multipartConfigElement = new MultipartConfigElement((String) null);
			req.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfigElement);
			result = ParameterParser.convertStreamToString(req.getPart(arg0).getInputStream());
		} catch (final Exception e) {
			// pass
		}
		return result;
	}

	public static long getPriceNQT(final HttpServletRequest req) throws ParameterException {
		return ParameterParser.getLong(req, "priceNQT", 1L, Constants.MAX_BALANCE_NQT, true);
	}

	public static byte[] getPublicKey(final HttpServletRequest req) throws ParameterException {
		return ParameterParser.getPublicKey(req, null);
	}

	private static byte[] getPublicKey(final HttpServletRequest req, final String prefix) throws ParameterException {
		final String secretPhraseParam = prefix == null ? "secretPhrase" : (prefix + "SecretPhrase");
		final String publicKeyParam = prefix == null ? "publicKey" : (prefix + "PublicKey");
		final String secretPhrase = Convert.emptyToNull(req.getParameter(secretPhraseParam));
		if (secretPhrase == null) try {
			final byte[] publicKey = Convert.parseHexString(Convert.emptyToNull(req.getParameter(publicKeyParam)));
			if (publicKey == null)
				throw new ParameterException(JSONResponses.missing(secretPhraseParam, publicKeyParam));
			if (!Crypto.isCanonicalPublicKey(publicKey))
				throw new ParameterException(JSONResponses.incorrect(publicKeyParam));
			return publicKey;
		} catch (final RuntimeException e) {
			throw new ParameterException(JSONResponses.incorrect(publicKeyParam));
		}
		else return Crypto.getPublicKey(secretPhrase);
	}

	public static String getSearchQuery(final HttpServletRequest req) throws ParameterException {
		String query = Convert.nullToEmpty(req.getParameter("query")).trim();
		final String tags = Convert.nullToEmpty(req.getParameter("tag")).trim();
		if (query.isEmpty() && tags.isEmpty()) throw new ParameterException(JSONResponses.missing("query", "tag"));
		if (!tags.isEmpty()) {
			final StringJoiner stringJoiner = new StringJoiner(" AND TAGS:", "TAGS:", "");
			for (final String tag : Search.parseTags(tags, 0, Integer.MAX_VALUE, Integer.MAX_VALUE))
				stringJoiner.add(tag);
			query = stringJoiner.toString() + (query.isEmpty() ? "" : (" AND (" + query + ")"));
		}
		return query;
	}

	public static String getSecretPhrase(final HttpServletRequest req, final boolean isMandatory)
			throws ParameterException {
		final String secretPhrase = Convert.emptyToNull(req.getParameter("secretPhrase"));
		if ((secretPhrase == null) && isMandatory) throw new ParameterException(JSONResponses.MISSING_SECRET_PHRASE);
		return secretPhrase;
	}

	public static Account getSenderAccount(final HttpServletRequest req) throws ParameterException {
		final byte[] publicKey = ParameterParser.getPublicKey(req);
		final Account account = Account.getAccount(publicKey);
		if (account == null) throw new ParameterException(JSONResponses.UNKNOWN_ACCOUNT);
		return account;
	}

	public static Appendix getSourceCode(final HttpServletRequest req) throws ParameterException {
		final String messageValue = Convert.emptyToNull(ParameterParser.getParameterMultipart(req, "source_code"));
		if (messageValue != null) try {
			return new Appendix.PrunableSourceCode(messageValue, (short) 1);
		} catch (final RuntimeException e) {
			throw new ParameterException(JSONResponses.INCORRECT_ARBITRARY_MESSAGE);
		}
		return null;
	}

	public static int getTimestamp(final HttpServletRequest req) throws ParameterException {
		return ParameterParser.getInt(req, "timestamp", 0, Integer.MAX_VALUE, false);
	}

	public static long getUnsignedLong(final HttpServletRequest req, final String name, final boolean isMandatory)
			throws ParameterException {
		final String paramValue = Convert.emptyToNull(req.getParameter(name));
		if (paramValue == null) {
			if (isMandatory) throw new ParameterException(JSONResponses.missing(name));
			return 0;
		}
		try {
			final long value = Convert.parseUnsignedLong(paramValue);
			// 0 is not allowed as an id
			if (value == 0) throw new ParameterException(JSONResponses.incorrect(name));
			return value;
		} catch (final RuntimeException e) {
			throw new ParameterException(JSONResponses.incorrect(name));
		}
	}

	public static long[] getUnsignedLongs(final HttpServletRequest req, final String name) throws ParameterException {
		final String[] paramValues = req.getParameterValues(name);
		if ((paramValues == null) || (paramValues.length == 0))
			throw new ParameterException(JSONResponses.missing(name));
		final long[] values = new long[paramValues.length];
		try {
			for (int i = 0; i < paramValues.length; i++) {
				if ((paramValues[i] == null) || paramValues[i].isEmpty())
					throw new ParameterException(JSONResponses.incorrect(name));
				values[i] = Long.parseUnsignedLong(paramValues[i]);
				if (values[i] == 0) throw new ParameterException(JSONResponses.incorrect(name));
			}
		} catch (final RuntimeException e) {
			throw new ParameterException(JSONResponses.incorrect(name));
		}
		return values;
	}

	public static Transaction.Builder parseTransaction(final String transactionJSON, final String transactionBytes,
			final String prunableAttachmentJSON) throws ParameterException {
		if ((transactionBytes == null) && (transactionJSON == null))
			throw new ParameterException(JSONResponses.MISSING_TRANSACTION_BYTES_OR_JSON);
		if ((transactionBytes != null) && (transactionJSON != null))
			throw new ParameterException(JSONResponses.either("transactionBytes", "transactionJSON"));
		if ((prunableAttachmentJSON != null) && (transactionBytes == null))
			throw new ParameterException(JSONResponses.missing("transactionBytes"));
		if (transactionJSON != null) try {
			final JSONObject json = (JSONObject) JSONValue.parseWithException(transactionJSON);
			return Nxt.newTransactionBuilder(json);
		} catch (NxtException.ValidationException | RuntimeException | ParseException e) {
			Logger.logDebugMessage(e.getMessage(), e);
			final JSONObject response = new JSONObject();
			JSONData.putException(response, e, "Incorrect transactionJSON");
			throw new ParameterException(response);
		}
		else try {
			final byte[] bytes = Convert.parseHexString(transactionBytes);
			final JSONObject prunableAttachments = prunableAttachmentJSON == null ? null
					: (JSONObject) JSONValue.parseWithException(prunableAttachmentJSON);
			return Nxt.newTransactionBuilder(bytes, prunableAttachments);
		} catch (NxtException.ValidationException | RuntimeException | ParseException e) {
			Logger.logDebugMessage(e.getMessage(), e);
			final JSONObject response = new JSONObject();
			JSONData.putException(response, e, "Incorrect transactionBytes");
			throw new ParameterException(response);
		}
	}

	private ParameterParser() {
	} // never
}

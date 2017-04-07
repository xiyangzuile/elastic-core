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

package nxt.peer;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;

import nxt.Account;
import nxt.Constants;
import nxt.crypto.Crypto;
import nxt.util.Convert;

public final class Hallmark {

	public static String formatDate(final int date) {
		final int year = date / 10000;
		final int month = (date % 10000) / 100;
		final int day = date % 100;
		return (year < 10 ? "000" : (year < 100 ? "00" : (year < 1000 ? "0" : ""))) + year + "-"
				+ (month < 10 ? "0" : "") + month + "-" + (day < 10 ? "0" : "") + day;
	}

	public static String generateHallmark(final String secretPhrase, final String host, final int weight,
			final int date) {

		if ((host.length() == 0) || (host.length() > 100)) {
			throw new IllegalArgumentException("Hostname length should be between 1 and 100");
		}
		if ((weight <= 0) || (weight > Constants.MAX_BALANCE_NXT)) {
			throw new IllegalArgumentException("Weight should be between 1 and " + Constants.MAX_BALANCE_NXT);
		}

		final byte[] publicKey = Crypto.getPublicKey(secretPhrase);
		final byte[] hostBytes = Convert.toBytes(host);

		final ByteBuffer buffer = ByteBuffer.allocate(32 + 2 + hostBytes.length + 4 + 4 + 1);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(publicKey);
		buffer.putShort((short) hostBytes.length);
		buffer.put(hostBytes);
		buffer.putInt(weight);
		buffer.putInt(date);

		final byte[] data = buffer.array();
		data[data.length - 1] = (byte) ThreadLocalRandom.current().nextInt();
		final byte[] signature = Crypto.sign(data, secretPhrase);

		return Convert.toHexString(data) + Convert.toHexString(signature);

	}

	public static int parseDate(final String dateValue) {
		return (Integer.parseInt(dateValue.substring(0, 4)) * 10000)
				+ (Integer.parseInt(dateValue.substring(5, 7)) * 100) + Integer.parseInt(dateValue.substring(8, 10));
	}

	public static Hallmark parseHallmark(String hallmarkString) {

		hallmarkString = hallmarkString.trim();
		if ((hallmarkString.length() % 2) != 0) {
			throw new IllegalArgumentException("Invalid hallmark string length " + hallmarkString.length());
		}

		final byte[] hallmarkBytes = Convert.parseHexString(hallmarkString);

		final ByteBuffer buffer = ByteBuffer.wrap(hallmarkBytes);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		final byte[] publicKey = new byte[32];
		buffer.get(publicKey);
		final int hostLength = buffer.getShort();
		if (hostLength > 300) {
			throw new IllegalArgumentException("Invalid host length");
		}
		final byte[] hostBytes = new byte[hostLength];
		buffer.get(hostBytes);
		final String host = Convert.toString(hostBytes);
		final int weight = buffer.getInt();
		final int date = buffer.getInt();
		buffer.get();
		final byte[] signature = new byte[64];
		buffer.get(signature);

		final byte[] data = new byte[hallmarkBytes.length - 64];
		System.arraycopy(hallmarkBytes, 0, data, 0, data.length);

		final boolean isValid = (host.length() < 100) && (weight > 0) && (weight <= Constants.MAX_BALANCE_NXT)
				&& Crypto.verify(signature, data, publicKey);
		try {
			return new Hallmark(hallmarkString, publicKey, signature, host, weight, date, isValid);
		} catch (final URISyntaxException e) {
			throw new RuntimeException(e.toString(), e);
		}

	}

	private final String hallmarkString;
	private final String host;
	private final int port;
	private final int weight;
	private final int date;
	private final byte[] publicKey;
	private final long accountId;
	private final byte[] signature;
	private final boolean isValid;

	private Hallmark(final String hallmarkString, final byte[] publicKey, final byte[] signature, final String host,
			final int weight, final int date, final boolean isValid) throws URISyntaxException {
		this.hallmarkString = hallmarkString;
		final URI uri = new URI("http://" + host);
		this.host = uri.getHost();
		this.port = uri.getPort() == -1 ? Peers.getDefaultPeerPort() : uri.getPort();
		this.publicKey = publicKey;
		this.accountId = Account.getId(publicKey);
		this.signature = signature;
		this.weight = weight;
		this.date = date;
		this.isValid = isValid;
	}

	public long getAccountId() {
		return this.accountId;
	}

	public int getDate() {
		return this.date;
	}

	public String getHallmarkString() {
		return this.hallmarkString;
	}

	public String getHost() {
		return this.host;
	}

	public int getPort() {
		return this.port;
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public int getWeight() {
		return this.weight;
	}

	public boolean isValid() {
		return this.isValid;
	}

}

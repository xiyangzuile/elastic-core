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

package nxt.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import nxt.Constants;
import nxt.NxtException;
import nxt.crypto.Crypto;

public final class Convert {

	private static final char[] hexChars = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
			'f'};
	private static final long[] multipliers = new long[]{1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000};

	public static final BigInteger two64 = new BigInteger("18446744073709551616");
	public static final long[] EMPTY_LONG = new long[0];
	public static final byte[] EMPTY_BYTE = new byte[0];
	private static final byte[][] EMPTY_BYTES = new byte[0][];
	public static final int[][] EMPTY_INT_BYTES = new int[0][];
	public static final String[] EMPTY_STRING = new String[0];

	public static final Comparator<byte[]> byteArrayComparator = (o1, o2) -> {
		final int minLength = Math.min(o1.length, o2.length);
		for (int i = 0; i < minLength; i++) {
			final int result = Byte.compare(o1[i], o2[i]);
			if (result != 0) return result;
		}
		return o1.length - o2.length;
	};

	public static byte[] compress(final byte[] bytes) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
			gzip.write(bytes);
			gzip.flush();
			gzip.close();
			return bos.toByteArray();
		} catch (final IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public static byte[] emptyToNull(final byte[] bytes) {
		if (bytes == null) return null;
		for (final byte b : bytes) if (b != 0) return bytes;
		return null;
	}

	public static String emptyToNull(final String s) {
		return (s == null) || (s.length() == 0) ? null : s;
	}

	public static long fromEpochTime(final int epochTime) {
		return ((epochTime * 1000L) + Constants.EPOCH_BEGINNING) - 500L;
	}

	public static long fullHashToId(final byte[] hash) {
		if ((hash == null) || (hash.length < 8))
			throw new IllegalArgumentException("Invalid hash: " + Arrays.toString(hash));
		final BigInteger bigInteger = new BigInteger(1,
				new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
		return bigInteger.longValue();
	}

	public static byte[][] nullToEmpty(final byte[][] bytes) {
		return bytes == null ? Convert.EMPTY_BYTES : bytes;
	}

	public static long[] nullToEmpty(final long[] array) {
		return array == null ? Convert.EMPTY_LONG : array;
	}

	public static String nullToEmpty(final String s) {
		return s == null ? "" : s;
	}

	public static long nullToZero(final Long l) {
		return l == null ? 0 : l;
	}

	public static long parseAccountId(String account) {
		if ((account == null) || (account = account.trim()).isEmpty()) return 0;
		account = account.toUpperCase();
		if (account.startsWith("XEL-")) return Crypto.rsDecode(account.substring(4));
		else return Long.parseUnsignedLong(account);
	}

	public static byte[] parseHexString(String hex) {
		if (hex == null) return null;
		hex=hex.toLowerCase();
		final byte[] bytes = new byte[hex.length() / 2];
		for (int i = 0; i < bytes.length; i++) {
			int char1 = hex.charAt(i * 2);
			char1 = char1 > 0x60 ? char1 - 0x57 : char1 - 0x30;
			int char2 = hex.charAt((i * 2) + 1);
			char2 = char2 > 0x60 ? char2 - 0x57 : char2 - 0x30;
			if ((char1 < 0) || (char2 < 0) || (char1 > 15) || (char2 > 15))
				throw new NumberFormatException("Invalid hex number: " + hex);
			bytes[i] = (byte) ((char1 << 4) + char2);
		}
		return bytes;
	}

	public static long parseLong(final Object o) {
		if (o == null) return 0;
		else if (o instanceof Long) return ((Long) o);
		else if (o instanceof String) return Long.parseLong((String) o);
		else throw new IllegalArgumentException("Not a long: " + o);
	}

	public static long parseNXT(final String nxt) {
		return Convert.parseStringFraction(nxt, 8, Constants.MAX_BALANCE_NXT);
	}

	private static long parseStringFraction(final String value, final int decimals, final long maxValue) {
		final String[] s = value.trim().split("\\.");
		if ((s.length == 0) || (s.length > 2)) throw new NumberFormatException("Invalid number: " + value);
		final long wholePart = Long.parseLong(s[0]);
		if (wholePart > maxValue) throw new IllegalArgumentException("Whole part of value exceeds maximum possible");
		if (s.length == 1) return wholePart * Convert.multipliers[decimals];
		long fractionalPart = Long.parseLong(s[1]);
		if ((fractionalPart >= Convert.multipliers[decimals]) || (s[1].length() > decimals))
			throw new IllegalArgumentException("Fractional part exceeds maximum allowed divisibility");
		for (int i = s[1].length(); i < decimals; i++) fractionalPart *= 10;
		return (wholePart * Convert.multipliers[decimals]) + fractionalPart;
	}

	public static long parseUnsignedLong(final String number) {
		if (number == null) return 0;
		return Long.parseUnsignedLong(number);
	}

	public static String readString(final ByteBuffer buffer, final int numBytes, final int maxLength)
			throws NxtException.NotValidException {
		if (numBytes > (3 * maxLength)) throw new NxtException.NotValidException("Max parameter length exceeded");
		final byte[] bytes = new byte[numBytes];
		buffer.get(bytes);
		return Convert.toString(bytes);
	}

	public static String rsAccount(final long accountId) {
		return "XEL-" + Crypto.rsEncode(accountId);
	}

	public static long safeAdd(final long left, final long right) throws ArithmeticException {
		if (right > 0 ? left > (Long.MAX_VALUE - right) : left < (Long.MIN_VALUE - right))
			throw new ArithmeticException("Integer overflow");
		return left + right;
	}

	public static long[] toArray(final List<Long> list) {
		final long[] result = new long[list.size()];
		for (int i = 0; i < list.size(); i++) result[i] = list.get(i);
		return result;
	}

	public static Long[] toArray(final long[] array) {
		final Long[] result = new Long[array.length];
		for (int i = 0; i < array.length; i++) result[i] = array[i];
		return result;
	}

	public static long[] toArray(final Long[] array) {
		final long[] result = new long[array.length];
		for (int i = 0; i < array.length; i++) result[i] = array[i];
		return result;
	}

	public static byte[] toBytes(final long n) {
		final byte[] bytes = new byte[8];
		for (int i = 0; i < 8; i++) bytes[i] = (byte) (n >> (8 * i));
		return bytes;
	}




	public static byte[] int2byte(int[]src) {
			int srcLength = src.length;
			byte[]dst = new byte[srcLength << 2];

			for (int i=0; i<srcLength; i++) {
				int x = src[i];
				int j = i << 2;
				dst[j++] = (byte) ((x >>> 0) & 0xff);
				dst[j++] = (byte) ((x >>> 8) & 0xff);
				dst[j++] = (byte) ((x >>> 16) & 0xff);
				dst[j++] = (byte) ((x >>> 24) & 0xff);
			}
			return dst;
	}

	public static int[] byte2int(byte buf[]) {
		int intArr[] = new int[buf.length / 4];
		int offset = 0;
		for(int i = 0; i < intArr.length; i++) {
			intArr[i] = (buf[3 + offset] & 0xFF) | ((buf[2 + offset] & 0xFF) << 8) |
					((buf[1 + offset] & 0xFF) << 16) | ((buf[0 + offset] & 0xFF) << 24);
			offset += 4;
		}
		return intArr;
	}



	public static byte[] toBytes(final String s) {
		try {
			return s.getBytes("UTF-8");
		} catch (final UnsupportedEncodingException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static byte[] toBytes(final String s, final boolean isText) {
		return isText ? Convert.toBytes(s) : Convert.parseHexString(s);
	}

	public static int toEpochTime(final long currentTime) {
		return (int) (((currentTime - Constants.EPOCH_BEGINNING) + 500) / 1000);
	}

	public static String toHexString(final byte[] bytes) {
		if (bytes == null) return null;
		final char[] chars = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			chars[i * 2] = Convert.hexChars[((bytes[i] >> 4) & 0xF)];
			chars[(i * 2) + 1] = Convert.hexChars[(bytes[i] & 0xF)];
		}
		return String.valueOf(chars);
	}

	public static List<Long> toList(final Long[] array) {
		final List<Long> result = new ArrayList<>(array.length);
		result.addAll(Arrays.asList(array));
		return result;
	}

	public static Set<Long> toSet(final Long[] array) {
		if ((array == null) || (array.length == 0)) return Collections.emptySet();
		final Set<Long> set = new HashSet<>(array.length);
		set.addAll(Arrays.asList(array));
		return set;
	}

	public static String toString(final byte[] bytes) {
		try {
			return new String(bytes, "UTF-8");
		} catch (final UnsupportedEncodingException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static String toString(final byte[] bytes, final boolean isText) {
		return isText ? Convert.toString(bytes) : Convert.toHexString(bytes);
	}

	public static String toUnsignedLong(final long objectId) {
		if (objectId >= 0) return String.valueOf(objectId);
		final BigInteger id = BigInteger.valueOf(objectId).add(Convert.two64);
		return id.toString();
	}

	public static String truncate(final String s, final String replaceNull, final int limit, final boolean dots) {
		return s == null ? replaceNull
				: s.length() > limit ? (s.substring(0, dots ? limit - 3 : limit) + (dots ? "..." : "")) : s;
	}

	public static byte[] uncompress(final byte[] bytes) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
				GZIPInputStream gzip = new GZIPInputStream(bis);
				ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			final byte[] buffer = new byte[1024];
			int nRead;
			while ((nRead = gzip.read(buffer, 0, buffer.length)) > 0) bos.write(buffer, 0, nRead);
			bos.flush();
			return bos.toByteArray();
		} catch (final IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public static byte[] toFixedBytesCutter(byte[] input, int len){
		byte[] res = new byte[len];
		if(input.length>=len) System.arraycopy(input, 0, res, 0, len);
		else if(input.length<len) System.arraycopy(input, 0, res, 0 + (len - input.length), input.length);
		return res;
	}

	private Convert() {
	} // never

}

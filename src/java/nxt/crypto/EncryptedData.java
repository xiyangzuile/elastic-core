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

package nxt.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nxt.NxtException;
import nxt.util.Convert;

public final class EncryptedData {

	public static final EncryptedData EMPTY_DATA = new EncryptedData(new byte[0], new byte[0]);

	public static EncryptedData encrypt(final byte[] plaintext, final String secretPhrase, final byte[] theirPublicKey) {
		if (plaintext.length == 0) {
			return EncryptedData.EMPTY_DATA;
		}
		final byte[] nonce = new byte[32];
		Crypto.getSecureRandom().nextBytes(nonce);
		final byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), theirPublicKey, nonce);
		final byte[] data = Crypto.aesEncrypt(plaintext, sharedKey);
		return new EncryptedData(data, nonce);
	}

	public static int getEncryptedDataLength(final byte[] plaintext) {
		if (plaintext.length == 0) {
			return 0;
		}
		return Crypto.aesEncrypt(plaintext, new byte[32]).length;
	}

	public static int getEncryptedSize(final byte[] plaintext) {
		if (plaintext.length == 0) {
			return 0;
		}
		return EncryptedData.getEncryptedDataLength(plaintext) + 32;
	}

	public static EncryptedData readEncryptedData(final byte[] bytes) {
		if (bytes.length == 0) {
			return EncryptedData.EMPTY_DATA;
		}
		final ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		try {
			return EncryptedData.readEncryptedData(buffer, bytes.length - 32, Integer.MAX_VALUE);
		} catch (final NxtException.NotValidException e) {
			throw new RuntimeException(e.toString(), e); // never
		}
	}

	public static EncryptedData readEncryptedData(final ByteBuffer buffer, final int length, final int maxLength)
			throws NxtException.NotValidException {
		if (length == 0) {
			return EncryptedData.EMPTY_DATA;
		}
		if (length > maxLength) {
			throw new NxtException.NotValidException("Max encrypted data length exceeded: " + length);
		}
		final byte[] data = new byte[length];
		buffer.get(data);
		final byte[] nonce = new byte[32];
		buffer.get(nonce);
		return new EncryptedData(data, nonce);
	}

	private final byte[] data;
	private final byte[] nonce;

	public EncryptedData(final byte[] data, final byte[] nonce) {
		this.data = data;
		this.nonce = nonce;
	}

	public byte[] decrypt(final String secretPhrase, final byte[] theirPublicKey) {
		if (this.data.length == 0) {
			return this.data;
		}
		final byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), theirPublicKey, this.nonce);
		return Crypto.aesDecrypt(this.data, sharedKey);
	}

	public byte[] getBytes() {
		final ByteBuffer buffer = ByteBuffer.allocate(this.nonce.length + this.data.length);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(this.data);
		buffer.put(this.nonce);
		return buffer.array();
	}

	public byte[] getData() {
		return this.data;
	}

	public byte[] getNonce() {
		return this.nonce;
	}

	public int getSize() {
		return this.data.length + this.nonce.length;
	}

	@Override
	public String toString() {
		return "data: " + Convert.toHexString(this.data) + " nonce: " + Convert.toHexString(this.nonce);
	}

}

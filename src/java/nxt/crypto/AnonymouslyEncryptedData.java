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
import java.util.Arrays;

import nxt.NxtException;
import nxt.util.Convert;

public final class AnonymouslyEncryptedData {

	public static AnonymouslyEncryptedData encrypt(final byte[] plaintext, final String secretPhrase, final byte[] theirPublicKey, final byte[] nonce) {
		final byte[] keySeed = Crypto.getKeySeed(secretPhrase, theirPublicKey, nonce);
		final byte[] myPrivateKey = Crypto.getPrivateKey(keySeed);
		final byte[] myPublicKey = Crypto.getPublicKey(keySeed);
		final byte[] sharedKey = Crypto.getSharedKey(myPrivateKey, theirPublicKey);
		final byte[] data = Crypto.aesGCMEncrypt(plaintext, sharedKey);
		return new AnonymouslyEncryptedData(data, myPublicKey);
	}

	public static AnonymouslyEncryptedData readEncryptedData(final byte[] bytes) {
		final ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		try {
			return AnonymouslyEncryptedData.readEncryptedData(buffer, bytes.length - 32, Integer.MAX_VALUE);
		} catch (final NxtException.NotValidException e) {
			throw new RuntimeException(e.toString(), e); // never
		}
	}

	public static AnonymouslyEncryptedData readEncryptedData(final ByteBuffer buffer, final int length, final int maxLength)
			throws NxtException.NotValidException {
		if (length > maxLength) {
			throw new NxtException.NotValidException("Max encrypted data length exceeded: " + length);
		}
		final byte[] data = new byte[length];
		buffer.get(data);
		final byte[] publicKey = new byte[32];
		buffer.get(publicKey);
		return new AnonymouslyEncryptedData(data, publicKey);
	}

	private final byte[] data;
	private final byte[] publicKey;

	public AnonymouslyEncryptedData(final byte[] data, final byte[] publicKey) {
		this.data = data;
		this.publicKey = publicKey;
	}

	public byte[] decrypt(final byte[] keySeed, final byte[] theirPublicKey) {
		if (!Arrays.equals(Crypto.getPublicKey(keySeed), this.publicKey)) {
			throw new RuntimeException("Data was not encrypted using this keySeed");
		}
		final byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(keySeed), theirPublicKey);
		return Crypto.aesGCMDecrypt(this.data, sharedKey);
	}

	public byte[] decrypt(final String secretPhrase) {
		final byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), this.publicKey);
		return Crypto.aesGCMDecrypt(this.data, sharedKey);
	}

	public byte[] getBytes() {
		final ByteBuffer buffer = ByteBuffer.allocate(this.data.length + 32);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(this.data);
		buffer.put(this.publicKey);
		return buffer.array();
	}

	public byte[] getData() {
		return this.data;
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	public int getSize() {
		return this.data.length + 32;
	}

	@Override
	public String toString() {
		return "data: " + Convert.toHexString(this.data) + " publicKey: " + Convert.toHexString(this.publicKey);
	}

}

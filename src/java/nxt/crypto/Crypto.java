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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.RIPEMD160;

import nxt.Nxt;
import nxt.util.Convert;
import nxt.util.Logger;

public final class Crypto {

	private static final boolean useStrongSecureRandom = Nxt.getBooleanProperty("nxt.useStrongSecureRandom");

	private static final ThreadLocal<SecureRandom> secureRandom = new ThreadLocal<SecureRandom>() {
		@Override
		protected SecureRandom initialValue() {
			try {
				final SecureRandom secureRandom = Crypto.useStrongSecureRandom ? SecureRandom.getInstanceStrong() : new SecureRandom();
				secureRandom.nextBoolean();
				return secureRandom;
			} catch (final NoSuchAlgorithmException e) {
				Logger.logErrorMessage("No secure random provider available");
				throw new RuntimeException(e.getMessage(), e);
			}
		}
	};

	public static byte[] aesDecrypt(final byte[] ivCiphertext, final byte[] key) {
		try {
			if ((ivCiphertext.length < 16) || ((ivCiphertext.length % 16) != 0)) {
				throw new InvalidCipherTextException("invalid ivCiphertext length");
			}
			final byte[] iv = Arrays.copyOfRange(ivCiphertext, 0, 16);
			final byte[] ciphertext = Arrays.copyOfRange(ivCiphertext, 16, ivCiphertext.length);
			final PaddedBufferedBlockCipher aes = new PaddedBufferedBlockCipher(new CBCBlockCipher(
					new AESEngine()));
			final CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
			aes.init(false, ivAndKey);
			final byte[] output = new byte[aes.getOutputSize(ciphertext.length)];
			int plaintextLength = aes.processBytes(ciphertext, 0, ciphertext.length, output, 0);
			plaintextLength += aes.doFinal(output, plaintextLength);
			final byte[] result = new byte[plaintextLength];
			System.arraycopy(output, 0, result, 0, result.length);
			return result;
		} catch (final InvalidCipherTextException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public static byte[] aesEncrypt(final byte[] plaintext, final byte[] key) {
		try {
			final byte[] iv = new byte[16];
			Crypto.secureRandom.get().nextBytes(iv);
			final PaddedBufferedBlockCipher aes = new PaddedBufferedBlockCipher(new CBCBlockCipher(
					new AESEngine()));
			final CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
			aes.init(true, ivAndKey);
			final byte[] output = new byte[aes.getOutputSize(plaintext.length)];
			int ciphertextLength = aes.processBytes(plaintext, 0, plaintext.length, output, 0);
			ciphertextLength += aes.doFinal(output, ciphertextLength);
			final byte[] result = new byte[iv.length + ciphertextLength];
			System.arraycopy(iv, 0, result, 0, iv.length);
			System.arraycopy(output, 0, result, iv.length, ciphertextLength);
			return result;
		} catch (final InvalidCipherTextException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public static byte[] aesGCMDecrypt(final byte[] ivCiphertext, final byte[] key) {
		try {
			if (ivCiphertext.length < 16) {
				throw new InvalidCipherTextException("invalid ivCiphertext length");
			}
			final byte[] iv = Arrays.copyOfRange(ivCiphertext, 0, 16);
			final byte[] ciphertext = Arrays.copyOfRange(ivCiphertext, 16, ivCiphertext.length);
			final GCMBlockCipher aes = new GCMBlockCipher(new AESEngine());
			final CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
			aes.init(false, ivAndKey);
			final byte[] output = new byte[aes.getOutputSize(ciphertext.length)];
			int plaintextLength = aes.processBytes(ciphertext, 0, ciphertext.length, output, 0);
			plaintextLength += aes.doFinal(output, plaintextLength);
			final byte[] result = new byte[plaintextLength];
			System.arraycopy(output, 0, result, 0, result.length);
			return result;
		} catch (final InvalidCipherTextException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public static byte[] aesGCMEncrypt(final byte[] plaintext, final byte[] key) {
		try {
			final byte[] iv = new byte[16];
			Crypto.secureRandom.get().nextBytes(iv);
			final GCMBlockCipher aes = new GCMBlockCipher(new AESEngine());
			final CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
			aes.init(true, ivAndKey);
			final byte[] output = new byte[aes.getOutputSize(plaintext.length)];
			int ciphertextLength = aes.processBytes(plaintext, 0, plaintext.length, output, 0);
			ciphertextLength += aes.doFinal(output, ciphertextLength);
			final byte[] result = new byte[iv.length + ciphertextLength];
			System.arraycopy(iv, 0, result, 0, iv.length);
			System.arraycopy(output, 0, result, iv.length, ciphertextLength);
			return result;
		} catch (final InvalidCipherTextException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}
	public static void curve(final byte[] Z, final byte[] k, final byte[] P) {
		Curve25519.curve(Z, k, P);
	}
	public static byte[] getKeySeed(final String secretPhrase, final byte[]... nonces) {
		final MessageDigest digest = Crypto.sha256();
		digest.update(Convert.toBytes(secretPhrase));
		for (final byte[] nonce : nonces) {
			digest.update(nonce);
		}
		return digest.digest();
	}

	public static MessageDigest getMessageDigest(final String algorithm) {
		try {
			return MessageDigest.getInstance(algorithm);
		} catch (final NoSuchAlgorithmException e) {
			Logger.logMessage("Missing message digest algorithm: " + algorithm);
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public static byte[] getPrivateKey(final byte[] keySeed) {
		final byte[] s = Arrays.copyOf(keySeed, keySeed.length);
		Curve25519.clamp(s);
		return s;
	}

	public static byte[] getPrivateKey(final String secretPhrase) {
		final byte[] s = Crypto.sha256().digest(Convert.toBytes(secretPhrase));
		Curve25519.clamp(s);
		return s;
	}

	public static byte[] getPublicKey(final byte[] keySeed) {
		final byte[] publicKey = new byte[32];
		Curve25519.keygen(publicKey, null, Arrays.copyOf(keySeed, keySeed.length));
		return publicKey;
	}

	public static byte[] getPublicKey(final String secretPhrase) {
		final byte[] publicKey = new byte[32];
		Curve25519.keygen(publicKey, null, Crypto.sha256().digest(Convert.toBytes(secretPhrase)));
		return publicKey;
	}

	public static SecureRandom getSecureRandom() {
		return Crypto.secureRandom.get();
	}

	public static byte[] getSharedKey(final byte[] myPrivateKey, final byte[] theirPublicKey) {
		return Crypto.sha256().digest(Crypto.getSharedSecret(myPrivateKey, theirPublicKey));
	}

	public static byte[] getSharedKey(final byte[] myPrivateKey, final byte[] theirPublicKey, final byte[] nonce) {
		final byte[] dhSharedSecret = Crypto.getSharedSecret(myPrivateKey, theirPublicKey);
		for (int i = 0; i < 32; i++) {
			dhSharedSecret[i] ^= nonce[i];
		}
		return Crypto.sha256().digest(dhSharedSecret);
	}

	private static byte[] getSharedSecret(final byte[] myPrivateKey, final byte[] theirPublicKey) {
		try {
			final byte[] sharedSecret = new byte[32];
			Curve25519.curve(sharedSecret, myPrivateKey, theirPublicKey);
			return sharedSecret;
		} catch (final RuntimeException e) {
			Logger.logMessage("Error getting shared secret", e);
			throw e;
		}
	}

	public static boolean isCanonicalPublicKey(final byte[] publicKey) {
		return Curve25519.isCanonicalPublicKey(publicKey);
	}

	public static boolean isCanonicalSignature(final byte[] signature) {
		return Curve25519.isCanonicalSignature(signature);
	}

	public static MessageDigest md5() {
		return Crypto.getMessageDigest("MD5");
	}

	public static MessageDigest ripemd160() {
		return new RIPEMD160.Digest();
	}

	public static long rsDecode(String rsString) {
		rsString = rsString.toUpperCase();
		try {
			final long id = ReedSolomon.decode(rsString);
			if (! rsString.equals(ReedSolomon.encode(id))) {
				throw new RuntimeException("ERROR: Reed-Solomon decoding of " + rsString
						+ " not reversible, decoded to " + id);
			}
			return id;
		} catch (final ReedSolomon.DecodeException e) {
			Logger.logDebugMessage("Reed-Solomon decoding failed for " + rsString + ": " + e.toString());
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static String rsEncode(final long id) {
		return ReedSolomon.encode(id);
	}

	public static MessageDigest sha256() {
		return Crypto.getMessageDigest("SHA-256");
	}

	public static MessageDigest sha3() {
		return new Keccak.Digest256();
	}

	public static byte[] sign(final byte[] message, final String secretPhrase) {
		final byte[] P = new byte[32];
		final byte[] s = new byte[32];
		final MessageDigest digest = Crypto.sha256();
		Curve25519.keygen(P, s, digest.digest(Convert.toBytes(secretPhrase)));

		final byte[] m = digest.digest(message);

		digest.update(m);
		final byte[] x = digest.digest(s);

		final byte[] Y = new byte[32];
		Curve25519.keygen(Y, null, x);

		digest.update(m);
		final byte[] h = digest.digest(Y);

		final byte[] v = new byte[32];
		Curve25519.sign(v, h, x, s);

		final byte[] signature = new byte[64];
		System.arraycopy(v, 0, signature, 0, 32);
		System.arraycopy(h, 0, signature, 32, 32);
		return signature;
	}

	public static boolean verify(final byte[] signature, final byte[] message, final byte[] publicKey, final boolean enforceCanonical) {
		try {
			if (signature.length != 64) {
				return false;
			}
			if (enforceCanonical && !Curve25519.isCanonicalSignature(signature)) {
				Logger.logDebugMessage("Rejecting non-canonical signature");
				return false;
			}

			if (enforceCanonical && !Curve25519.isCanonicalPublicKey(publicKey)) {
				Logger.logDebugMessage("Rejecting non-canonical public key");
				return false;
			}

			final byte[] Y = new byte[32];
			final byte[] v = new byte[32];
			System.arraycopy(signature, 0, v, 0, 32);
			final byte[] h = new byte[32];
			System.arraycopy(signature, 32, h, 0, 32);
			Curve25519.verify(Y, v, h, publicKey);

			final MessageDigest digest = Crypto.sha256();
			final byte[] m = digest.digest(message);
			digest.update(m);
			final byte[] h2 = digest.digest(Y);

			return Arrays.equals(h, h2);
		} catch (final RuntimeException e) {
			Logger.logErrorMessage("Error verifying signature", e);
			return false;
		}
	}

	private Crypto() {} //never

}

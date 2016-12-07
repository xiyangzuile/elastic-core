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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

public class Scrypt {

	private final Mac mac;
	{
		try {
			this.mac = Mac.getInstance("HmacSHA256");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
	private final byte[] H = new byte[32];
	private final byte[] B = new byte[128 + 4];
	private final int[] X = new int[32];
	private final int[] V = new int[32 * 1024];

	public byte[] hash(final byte input[]) {
		int i, j, k;
		System.arraycopy(input, 0, this.B, 0, input.length);
		try {
			this.mac.init(new SecretKeySpec(this.B, 0, 40, "HmacSHA256"));
		} catch (final InvalidKeyException e) {
			throw new IllegalStateException(e);
		}
		this.B[40] = 0;
		this.B[41] = 0;
		this.B[42] = 0;
		for (i = 0; i < 4; i++) {
			this.B[43] = (byte) (i + 1);
			this.mac.update(this.B, 0, 44);
			try {
				this.mac.doFinal(this.H, 0);
			} catch (final ShortBufferException e) {
				throw new IllegalStateException(e);
			}

			for (j = 0; j < 8; j++) {
				this.X[(i * 8) + j] = ((this.H[(j * 4) + 0] & 0xff) << 0) | ((this.H[(j * 4) + 1] & 0xff) << 8)
						| ((this.H[(j * 4) + 2] & 0xff) << 16) | ((this.H[(j * 4) + 3] & 0xff) << 24);
			}
		}

		for (i = 0; i < 1024; i++) {
			System.arraycopy(this.X, 0, this.V, i * 32, 32);
			this.xorSalsa8(0, 16);
			this.xorSalsa8(16, 0);
		}
		for (i = 0; i < 1024; i++) {
			k = (this.X[16] & 1023) * 32;
			for (j = 0; j < 32; j++) {
				this.X[j] ^= this.V[k + j];
			}
			this.xorSalsa8(0, 16);
			this.xorSalsa8(16, 0);
		}

		for (i = 0; i < 32; i++) {
			this.B[(i * 4) + 0] = (byte) (this.X[i] >> 0);
			this.B[(i * 4) + 1] = (byte) (this.X[i] >> 8);
			this.B[(i * 4) + 2] = (byte) (this.X[i] >> 16);
			this.B[(i * 4) + 3] = (byte) (this.X[i] >> 24);
		}

		this.B[128 + 3] = 1;
		this.mac.update(this.B, 0, 128 + 4);
		try {
			this.mac.doFinal(this.H, 0);
		} catch (final ShortBufferException e) {
			throw new IllegalStateException(e);
		}

		return this.H;
	}

	private void xorSalsa8(final int di, final int xi) {
		int x00 = (this.X[di + 0] ^= this.X[xi + 0]);
		int x01 = (this.X[di + 1] ^= this.X[xi + 1]);
		int x02 = (this.X[di + 2] ^= this.X[xi + 2]);
		int x03 = (this.X[di + 3] ^= this.X[xi + 3]);
		int x04 = (this.X[di + 4] ^= this.X[xi + 4]);
		int x05 = (this.X[di + 5] ^= this.X[xi + 5]);
		int x06 = (this.X[di + 6] ^= this.X[xi + 6]);
		int x07 = (this.X[di + 7] ^= this.X[xi + 7]);
		int x08 = (this.X[di + 8] ^= this.X[xi + 8]);
		int x09 = (this.X[di + 9] ^= this.X[xi + 9]);
		int x10 = (this.X[di + 10] ^= this.X[xi + 10]);
		int x11 = (this.X[di + 11] ^= this.X[xi + 11]);
		int x12 = (this.X[di + 12] ^= this.X[xi + 12]);
		int x13 = (this.X[di + 13] ^= this.X[xi + 13]);
		int x14 = (this.X[di + 14] ^= this.X[xi + 14]);
		int x15 = (this.X[di + 15] ^= this.X[xi + 15]);
		for (int i = 0; i < 8; i += 2) {
			x04 ^= Integer.rotateLeft(x00 + x12, 7);
			x08 ^= Integer.rotateLeft(x04 + x00, 9);
			x12 ^= Integer.rotateLeft(x08 + x04, 13);
			x00 ^= Integer.rotateLeft(x12 + x08, 18);
			x09 ^= Integer.rotateLeft(x05 + x01, 7);
			x13 ^= Integer.rotateLeft(x09 + x05, 9);
			x01 ^= Integer.rotateLeft(x13 + x09, 13);
			x05 ^= Integer.rotateLeft(x01 + x13, 18);
			x14 ^= Integer.rotateLeft(x10 + x06, 7);
			x02 ^= Integer.rotateLeft(x14 + x10, 9);
			x06 ^= Integer.rotateLeft(x02 + x14, 13);
			x10 ^= Integer.rotateLeft(x06 + x02, 18);
			x03 ^= Integer.rotateLeft(x15 + x11, 7);
			x07 ^= Integer.rotateLeft(x03 + x15, 9);
			x11 ^= Integer.rotateLeft(x07 + x03, 13);
			x15 ^= Integer.rotateLeft(x11 + x07, 18);
			x01 ^= Integer.rotateLeft(x00 + x03, 7);
			x02 ^= Integer.rotateLeft(x01 + x00, 9);
			x03 ^= Integer.rotateLeft(x02 + x01, 13);
			x00 ^= Integer.rotateLeft(x03 + x02, 18);
			x06 ^= Integer.rotateLeft(x05 + x04, 7);
			x07 ^= Integer.rotateLeft(x06 + x05, 9);
			x04 ^= Integer.rotateLeft(x07 + x06, 13);
			x05 ^= Integer.rotateLeft(x04 + x07, 18);
			x11 ^= Integer.rotateLeft(x10 + x09, 7);
			x08 ^= Integer.rotateLeft(x11 + x10, 9);
			x09 ^= Integer.rotateLeft(x08 + x11, 13);
			x10 ^= Integer.rotateLeft(x09 + x08, 18);
			x12 ^= Integer.rotateLeft(x15 + x14, 7);
			x13 ^= Integer.rotateLeft(x12 + x15, 9);
			x14 ^= Integer.rotateLeft(x13 + x12, 13);
			x15 ^= Integer.rotateLeft(x14 + x13, 18);
		}
		this.X[di + 0] += x00;
		this.X[di + 1] += x01;
		this.X[di + 2] += x02;
		this.X[di + 3] += x03;
		this.X[di + 4] += x04;
		this.X[di + 5] += x05;
		this.X[di + 6] += x06;
		this.X[di + 7] += x07;
		this.X[di + 8] += x08;
		this.X[di + 9] += x09;
		this.X[di + 10] += x10;
		this.X[di + 11] += x11;
		this.X[di + 12] += x12;
		this.X[di + 13] += x13;
		this.X[di + 14] += x14;
		this.X[di + 15] += x15;
	}

}

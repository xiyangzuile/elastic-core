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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

class TrustAllSSLProvider {

	// Verify-all name verifier
	private final static HostnameVerifier hostNameVerifier = (hostname, session) -> true;

	// Trust-all socket factory
	private static final SSLSocketFactory sslSocketFactory;
	static {
		final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			@Override
			public void checkClientTrusted(final X509Certificate[] certs, final String authType) {
			}

			@Override
			public void checkServerTrusted(final X509Certificate[] certs, final String authType) {
			}

			@Override
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		} };
		SSLContext sc;
		try {
			sc = SSLContext.getInstance("TLS");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		try {
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
		} catch (final KeyManagementException e) {
			throw new IllegalStateException(e);
		}
		sslSocketFactory = sc.getSocketFactory();
	}

	public static HostnameVerifier getHostNameVerifier() {
		return TrustAllSSLProvider.hostNameVerifier;
	}

	public static SSLSocketFactory getSslSocketFactory() {
		return TrustAllSSLProvider.sslSocketFactory;
	}
}

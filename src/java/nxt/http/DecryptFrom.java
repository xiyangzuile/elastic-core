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

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.NxtException;
import nxt.crypto.EncryptedData;
import nxt.util.Convert;
import nxt.util.Logger;

public final class DecryptFrom extends APIServlet.APIRequestHandler {

	static final DecryptFrom instance = new DecryptFrom();

	private DecryptFrom() {
		super(new APITag[] { APITag.MESSAGES }, "account", "data", "nonce", "decryptedMessageIsText",
				"uncompressDecryptedMessage", "secretPhrase");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final byte[] publicKey = Account.getPublicKey(ParameterParser.getAccountId(req, true));
		if (publicKey == null) {
			return JSONResponses.INCORRECT_ACCOUNT;
		}
		final String secretPhrase = ParameterParser.getSecretPhrase(req, true);
		final byte[] data = Convert.parseHexString(Convert.nullToEmpty(req.getParameter("data")));
		final byte[] nonce = Convert.parseHexString(Convert.nullToEmpty(req.getParameter("nonce")));
		final EncryptedData encryptedData = new EncryptedData(data, nonce);
		final boolean isText = !"false".equalsIgnoreCase(req.getParameter("decryptedMessageIsText"));
		final boolean uncompress = !"false".equalsIgnoreCase(req.getParameter("uncompressDecryptedMessage"));
		try {
			final byte[] decrypted = Account.decryptFrom(publicKey, encryptedData, secretPhrase, uncompress);
			final JSONObject response = new JSONObject();
			response.put("decryptedMessage", isText ? Convert.toString(decrypted) : Convert.toHexString(decrypted));
			return response;
		} catch (final RuntimeException e) {
			Logger.logDebugMessage(e.toString());
			return JSONResponses.DECRYPTION_FAILED;
		}
	}

}

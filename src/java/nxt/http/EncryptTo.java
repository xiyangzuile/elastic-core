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

import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.NxtException;
import nxt.crypto.EncryptedData;
import nxt.util.Convert;

public final class EncryptTo extends APIServlet.APIRequestHandler {

	static final EncryptTo instance = new EncryptTo();

	private EncryptTo() {
		super(new APITag[] {APITag.MESSAGES}, "recipient", "messageToEncrypt", "messageToEncryptIsText", "compressMessageToEncrypt", "secretPhrase");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final long recipientId = ParameterParser.getAccountId(req, "recipient", true);
		final byte[] recipientPublicKey = Account.getPublicKey(recipientId);
		if (recipientPublicKey == null) {
			return JSONResponses.INCORRECT_RECIPIENT;
		}
		final boolean isText = !"false".equalsIgnoreCase(req.getParameter("messageToEncryptIsText"));
		final boolean compress = !"false".equalsIgnoreCase(req.getParameter("compressMessageToEncrypt"));
		final String plainMessage = Convert.emptyToNull(req.getParameter("messageToEncrypt"));
		if (plainMessage == null) {
			return JSONResponses.MISSING_MESSAGE_TO_ENCRYPT;
		}
		byte[] plainMessageBytes;
		try {
			plainMessageBytes = isText ? Convert.toBytes(plainMessage) : Convert.parseHexString(plainMessage);
		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_MESSAGE_TO_ENCRYPT;
		}
		final String secretPhrase = ParameterParser.getSecretPhrase(req, true);
		final EncryptedData encryptedData = Account.encryptTo(recipientPublicKey, plainMessageBytes, secretPhrase, compress);
		return JSONData.encryptedData(encryptedData);

	}

}

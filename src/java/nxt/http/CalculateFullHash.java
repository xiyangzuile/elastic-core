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

import java.security.MessageDigest;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
import nxt.Transaction;
import nxt.crypto.Crypto;
import nxt.util.Convert;

public final class CalculateFullHash extends APIServlet.APIRequestHandler {

	static final CalculateFullHash instance = new CalculateFullHash();

	private CalculateFullHash() {
		super(new APITag[] { APITag.TRANSACTIONS }, "unsignedTransactionBytes", "unsignedTransactionJSON",
				"signatureHash");
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final String unsignedBytesString = Convert.emptyToNull(req.getParameter("unsignedTransactionBytes"));
		final String signatureHashString = Convert.emptyToNull(req.getParameter("signatureHash"));
		final String unsignedTransactionJSONString = Convert.emptyToNull(req.getParameter("unsignedTransactionJSON"));

		if (signatureHashString == null) {
			return JSONResponses.MISSING_SIGNATURE_HASH;
		}
		final JSONObject response = new JSONObject();
		try {
			final Transaction transaction = ParameterParser
					.parseTransaction(unsignedTransactionJSONString, unsignedBytesString, null).build();
			final MessageDigest digest = Crypto.sha256();
			digest.update(transaction.getUnsignedBytes());
			final byte[] fullHash = digest.digest(Convert.parseHexString(signatureHashString));
			response.put("fullHash", Convert.toHexString(fullHash));
		} catch (final NxtException.NotValidException e) {
			JSONData.putException(response, e, "Incorrect unsigned transaction json or bytes");
		}
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

}

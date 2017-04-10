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

package nxt.http;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
import nxt.Transaction;
import nxt.util.Convert;

public final class SignTransaction extends APIServlet.APIRequestHandler {

	static final SignTransaction instance = new SignTransaction();

	private SignTransaction() {
		super(new APITag[] { APITag.TRANSACTIONS }, "unsignedTransactionJSON", "unsignedTransactionBytes",
				"prunableAttachmentJSON", "secretPhrase", "validate");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final String transactionJSON = Convert.emptyToNull(req.getParameter("unsignedTransactionJSON"));
		final String transactionBytes = Convert.emptyToNull(req.getParameter("unsignedTransactionBytes"));
		final String prunableAttachmentJSON = Convert.emptyToNull(req.getParameter("prunableAttachmentJSON"));

		final Transaction.Builder builder = ParameterParser.parseTransaction(transactionJSON, transactionBytes,
				prunableAttachmentJSON);

		final String secretPhrase = ParameterParser.getSecretPhrase(req, true);
		final boolean validate = !"false".equalsIgnoreCase(req.getParameter("validate"));

		final JSONObject response = new JSONObject();
		try {
			final Transaction transaction = builder.build(secretPhrase);
			final JSONObject signedTransactionJSON = JSONData.unconfirmedTransaction(transaction);
			if (validate) {
				transaction.validate();
				response.put("verify", transaction.verifySignature());
			}
			response.put("transactionJSON", signedTransactionJSON);
			response.put("fullHash", signedTransactionJSON.get("fullHash"));
			response.put("signatureHash", signedTransactionJSON.get("signatureHash"));
			response.put("transaction", transaction.getStringId());
			response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
			JSONData.putPrunableAttachment(response, transaction);
		} catch (NxtException.ValidationException | RuntimeException e) {
			JSONData.putException(response, e, "Incorrect unsigned transaction json or bytes");
		}
		return response;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

}

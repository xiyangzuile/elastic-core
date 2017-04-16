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

import nxt.Nxt;
import nxt.Transaction;
import nxt.util.Convert;

public final class GetTransactionBytes extends APIServlet.APIRequestHandler {

	static final GetTransactionBytes instance = new GetTransactionBytes();

	private GetTransactionBytes() {
		super(new APITag[] { APITag.TRANSACTIONS }, "transaction");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		final String transactionValue = req.getParameter("transaction");
		if (transactionValue == null) return JSONResponses.MISSING_TRANSACTION;

		long transactionId;
		Transaction transaction;
		try {
			transactionId = Convert.parseUnsignedLong(transactionValue);
		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_TRANSACTION;
		}

		transaction = Nxt.getBlockchain().getTransaction(transactionId);
		final JSONObject response = new JSONObject();
		if (transaction == null) {
			transaction = Nxt.getTransactionProcessor().getUnconfirmedTransaction(transactionId);
			if (transaction == null) return JSONResponses.UNKNOWN_TRANSACTION;
		} else response.put("confirmations", Nxt.getBlockchain().getHeight() - transaction.getHeight());
		response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
		response.put("unsignedTransactionBytes", Convert.toHexString(transaction.getUnsignedBytes()));
		JSONData.putPrunableAttachment(response, transaction);
		return response;

	}

}

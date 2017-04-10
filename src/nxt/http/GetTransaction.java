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

import org.json.simple.JSONStreamAware;

import nxt.Nxt;
import nxt.Transaction;
import nxt.util.Convert;

public final class GetTransaction extends APIServlet.APIRequestHandler {

	static final GetTransaction instance = new GetTransaction();

	private GetTransaction() {
		super(new APITag[] { APITag.TRANSACTIONS }, "transaction", "fullHash", "includePhasingResult");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		final String transactionIdString = Convert.emptyToNull(req.getParameter("transaction"));
		final String transactionFullHash = Convert.emptyToNull(req.getParameter("fullHash"));
		if ((transactionIdString == null) && (transactionFullHash == null)) {
			return JSONResponses.MISSING_TRANSACTION;
		}

		long transactionId = 0;
		Transaction transaction;
		try {
			if (transactionIdString != null) {
				transactionId = Convert.parseUnsignedLong(transactionIdString);
				transaction = Nxt.getBlockchain().getTransaction(transactionId);
			} else {
				transaction = Nxt.getBlockchain().getTransactionByFullHash(transactionFullHash);
				if (transaction == null) {
					return JSONResponses.UNKNOWN_TRANSACTION;
				}
			}
		} catch (final RuntimeException e) {
			return JSONResponses.INCORRECT_TRANSACTION;
		}

		if (transaction == null) {
			transaction = Nxt.getTransactionProcessor().getUnconfirmedTransaction(transactionId);
			if (transaction == null) {
				return JSONResponses.UNKNOWN_TRANSACTION;
			}
			return JSONData.unconfirmedTransaction(transaction);
		} else {
			return JSONData.transaction(transaction);
		}

	}

}

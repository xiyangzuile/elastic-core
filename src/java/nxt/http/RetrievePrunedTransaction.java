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

import nxt.Nxt;
import nxt.Transaction;

public class RetrievePrunedTransaction extends APIServlet.APIRequestHandler {

	static final RetrievePrunedTransaction instance = new RetrievePrunedTransaction();

	private RetrievePrunedTransaction() {
		super(new APITag[] {APITag.TRANSACTIONS}, "transaction");
	}

	@Override
	protected final boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {
		final long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
		Transaction transaction = Nxt.getBlockchain().getTransaction(transactionId);
		if (transaction == null) {
			return JSONResponses.UNKNOWN_TRANSACTION;
		}
		transaction = Nxt.getBlockchainProcessor().restorePrunedTransaction(transactionId);
		if (transaction == null) {
			return JSONResponses.PRUNED_TRANSACTION;
		}
		return JSONData.transaction(transaction);
	}

	@Override
	protected final boolean requirePost() {
		return true;
	}

}

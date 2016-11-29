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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Nxt;
import nxt.NxtException;
import nxt.Transaction;
import nxt.db.DbIterator;

public final class GetBlockchainTransactions extends APIServlet.APIRequestHandler {

	static final GetBlockchainTransactions instance = new GetBlockchainTransactions();

	private GetBlockchainTransactions() {
		super(new APITag[] {APITag.ACCOUNTS, APITag.TRANSACTIONS}, "account", "timestamp", "type", "subtype",
				"firstIndex", "lastIndex", "numberOfConfirmations", "withMessage", "phasedOnly", "nonPhasedOnly",
				"includeExpiredPrunable", "includePhasingResult", "executedOnly");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final long accountId = ParameterParser.getAccountId(req, true);
		final int timestamp = ParameterParser.getTimestamp(req);
		final int numberOfConfirmations = ParameterParser.getNumberOfConfirmations(req);
		final boolean withMessage = "true".equalsIgnoreCase(req.getParameter("withMessage"));
		final boolean phasedOnly = "true".equalsIgnoreCase(req.getParameter("phasedOnly"));
		final boolean nonPhasedOnly = "true".equalsIgnoreCase(req.getParameter("nonPhasedOnly"));
		final boolean includeExpiredPrunable = "true".equalsIgnoreCase(req.getParameter("includeExpiredPrunable"));
		final boolean executedOnly = "true".equalsIgnoreCase(req.getParameter("executedOnly"));

		byte type;
		byte subtype;
		try {
			type = Byte.parseByte(req.getParameter("type"));
		} catch (final NumberFormatException e) {
			type = -1;
		}
		try {
			subtype = Byte.parseByte(req.getParameter("subtype"));
		} catch (final NumberFormatException e) {
			subtype = -1;
		}

		final int firstIndex = ParameterParser.getFirstIndex(req);
		final int lastIndex = ParameterParser.getLastIndex(req);

		final JSONArray transactions = new JSONArray();
		try (DbIterator<? extends Transaction> iterator = Nxt.getBlockchain().getTransactions(accountId, numberOfConfirmations,
				type, subtype, timestamp, withMessage, phasedOnly, nonPhasedOnly, firstIndex, lastIndex,
				includeExpiredPrunable, executedOnly)) {
			while (iterator.hasNext()) {
				final Transaction transaction = iterator.next();
				transactions.add(JSONData.transaction(transaction));
			}
		}

		final JSONObject response = new JSONObject();
		response.put("transactions", transactions);
		return response;

	}

}

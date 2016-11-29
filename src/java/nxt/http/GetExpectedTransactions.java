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

import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Nxt;
import nxt.NxtException;
import nxt.Transaction;
import nxt.util.Convert;
import nxt.util.Filter;

public final class GetExpectedTransactions extends APIServlet.APIRequestHandler {

	static final GetExpectedTransactions instance = new GetExpectedTransactions();

	private GetExpectedTransactions() {
		super(new APITag[] { APITag.TRANSACTIONS }, "account", "account", "account");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final Set<Long> accountIds = Convert.toSet(ParameterParser.getAccountIds(req, false));
		final Filter<Transaction> filter = accountIds.isEmpty() ? transaction -> true
				: transaction -> accountIds.contains(transaction.getSenderId())
						|| accountIds.contains(transaction.getRecipientId());

		final List<? extends Transaction> transactions = Nxt.getBlockchain().getExpectedTransactions(filter);

		final JSONObject response = new JSONObject();
		final JSONArray jsonArray = new JSONArray();
		transactions.forEach(transaction -> jsonArray.add(JSONData.unconfirmedTransaction(transaction)));
		response.put("expectedTransactions", jsonArray);

		return response;
	}

}

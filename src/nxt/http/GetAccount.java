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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.NxtException;
import nxt.db.DbIterator;
import nxt.util.Convert;

public final class GetAccount extends APIServlet.APIRequestHandler {

	static final GetAccount instance = new GetAccount();

	private GetAccount() {
		super(new APITag[] { APITag.ACCOUNTS }, "account", "includeLessors", "includeAssets", "includeCurrencies",
				"includeEffectiveBalance");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final Account account = ParameterParser.getAccount(req);
		final boolean includeLessors = "true".equalsIgnoreCase(req.getParameter("includeLessors"));
		final boolean includeEffectiveBalance = "true".equalsIgnoreCase(req.getParameter("includeEffectiveBalance"));

		final JSONObject response = JSONData.accountBalance(account, includeEffectiveBalance);
		JSONData.putAccount(response, "account", account.getId());

		final byte[] publicKey = Account.getPublicKey(account.getId());
		if (publicKey != null) {
			response.put("publicKey", Convert.toHexString(publicKey));
		}
		final Account.AccountInfo accountInfo = account.getAccountInfo();
		if (accountInfo != null) {
			response.put("name", Convert.nullToEmpty(accountInfo.getName()));
			response.put("description", Convert.nullToEmpty(accountInfo.getDescription()));
		}
		final Account.AccountLease accountLease = account.getAccountLease();
		if (accountLease != null) {
			JSONData.putAccount(response, "currentLessee", accountLease.getCurrentLesseeId());
			response.put("currentLeasingHeightFrom", accountLease.getCurrentLeasingHeightFrom());
			response.put("currentLeasingHeightTo", accountLease.getCurrentLeasingHeightTo());
			if (accountLease.getNextLesseeId() != 0) {
				JSONData.putAccount(response, "nextLessee", accountLease.getNextLesseeId());
				response.put("nextLeasingHeightFrom", accountLease.getNextLeasingHeightFrom());
				response.put("nextLeasingHeightTo", accountLease.getNextLeasingHeightTo());
			}
		}

		if (!account.getControls().isEmpty()) {
			final JSONArray accountControlsJson = new JSONArray();
			account.getControls().forEach(accountControl -> accountControlsJson.add(accountControl.toString()));
			response.put("accountControls", accountControlsJson);
		}

		if (includeLessors) {
			try (DbIterator<Account> lessors = account.getLessors()) {
				if (lessors.hasNext()) {
					final JSONArray lessorIds = new JSONArray();
					final JSONArray lessorIdsRS = new JSONArray();
					final JSONArray lessorInfo = new JSONArray();
					while (lessors.hasNext()) {
						final Account lessor = lessors.next();
						lessorIds.add(Long.toUnsignedString(lessor.getId()));
						lessorIdsRS.add(Convert.rsAccount(lessor.getId()));
						lessorInfo.add(JSONData.lessor(lessor, includeEffectiveBalance));
					}
					response.put("lessors", lessorIds);
					response.put("lessorsRS", lessorIdsRS);
					response.put("lessorsInfo", lessorInfo);
				}
			}
		}

		return response;

	}

}

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
import nxt.Attachment;
import nxt.Constants;
import nxt.NxtException;

public final class LeaseBalance extends CreateTransaction {

	static final LeaseBalance instance = new LeaseBalance();

	private LeaseBalance() {
		super(new APITag[] { APITag.FORGING, APITag.ACCOUNT_CONTROL, APITag.CREATE_TRANSACTION }, "period",
				"recipient");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final int period = ParameterParser.getInt(req, "period", Constants.LEASING_DELAY, 65535, true);
		final Account account = ParameterParser.getSenderAccount(req);
		final long recipient = ParameterParser.getAccountId(req, "recipient", true);
		final Account recipientAccount = Account.getAccount(recipient);
		if ((recipientAccount == null) || (Account.getPublicKey(recipientAccount.getId()) == null)) {
			final JSONObject response = new JSONObject();
			response.put("errorCode", 8);
			response.put("errorDescription", "recipient account does not have public key");
			return response;
		}
		final Attachment attachment = new Attachment.AccountControlEffectiveBalanceLeasing(period);
		return this.createTransaction(req, account, recipient, 0, attachment);

	}

}

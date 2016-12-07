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

package nxt.user;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.Transaction;
import nxt.util.Convert;

public final class SendMoney extends UserServlet.UserRequestHandler {

	static final SendMoney instance = new SendMoney();

	private SendMoney() {
	}

	@Override
	JSONStreamAware processRequest(final HttpServletRequest req, final User user)
			throws NxtException.ValidationException, IOException {
		if (user.getSecretPhrase() == null) {
			return null;
		}

		final String recipientValue = req.getParameter("recipient");
		final String amountValue = req.getParameter("amountNXT");
		final String feeValue = req.getParameter("feeNXT");
		final String deadlineValue = req.getParameter("deadline");
		final String secretPhrase = req.getParameter("secretPhrase");

		long recipient;
		long amountNQT = 0;
		long feeNQT = 0;
		short deadline = 0;

		try {

			recipient = Convert.parseUnsignedLong(recipientValue);
			if (recipient == 0) {
				throw new IllegalArgumentException("invalid recipient");
			}
			amountNQT = Convert.parseNXT(amountValue.trim());
			feeNQT = Convert.parseNXT(feeValue.trim());
			deadline = (short) (Double.parseDouble(deadlineValue) * 60);

		} catch (final RuntimeException e) {

			final JSONObject response = new JSONObject();
			response.put("response", "notifyOfIncorrectTransaction");
			response.put("message", "One of the fields is filled incorrectly!");
			response.put("recipient", recipientValue);
			response.put("amountNXT", amountValue);
			response.put("feeNXT", feeValue);
			response.put("deadline", deadlineValue);

			return response;
		}

		if (!user.getSecretPhrase().equals(secretPhrase)) {

			final JSONObject response = new JSONObject();
			response.put("response", "notifyOfIncorrectTransaction");
			response.put("message", "Wrong secret phrase!");
			response.put("recipient", recipientValue);
			response.put("amountNXT", amountValue);
			response.put("feeNXT", feeValue);
			response.put("deadline", deadlineValue);

			return response;

		} else if ((amountNQT <= 0) || (amountNQT > Constants.MAX_BALANCE_NQT)) {

			final JSONObject response = new JSONObject();
			response.put("response", "notifyOfIncorrectTransaction");
			response.put("message", "\"Amount\" must be greater than 0!");
			response.put("recipient", recipientValue);
			response.put("amountNXT", amountValue);
			response.put("feeNXT", feeValue);
			response.put("deadline", deadlineValue);

			return response;

		} else if ((feeNQT < Constants.ONE_NXT) || (feeNQT > Constants.MAX_BALANCE_NQT)) {

			final JSONObject response = new JSONObject();
			response.put("response", "notifyOfIncorrectTransaction");
			response.put("message", "\"Fee\" must be at least 1 NXT!");
			response.put("recipient", recipientValue);
			response.put("amountNXT", amountValue);
			response.put("feeNXT", feeValue);
			response.put("deadline", deadlineValue);

			return response;

		} else if ((deadline < 1) || (deadline > 1440)) {

			final JSONObject response = new JSONObject();
			response.put("response", "notifyOfIncorrectTransaction");
			response.put("message", "\"Deadline\" must be greater or equal to 1 minute and less than 24 hours!");
			response.put("recipient", recipientValue);
			response.put("amountNXT", amountValue);
			response.put("feeNXT", feeValue);
			response.put("deadline", deadlineValue);

			return response;

		}

		final Account account = Account.getAccount(user.getPublicKey());
		if ((account == null) || (Math.addExact(amountNQT, feeNQT) > account.getUnconfirmedBalanceNQT())) {

			final JSONObject response = new JSONObject();
			response.put("response", "notifyOfIncorrectTransaction");
			response.put("message", "Not enough funds!");
			response.put("recipient", recipientValue);
			response.put("amountNXT", amountValue);
			response.put("feeNXT", feeValue);
			response.put("deadline", deadlineValue);

			return response;

		} else {

			final Transaction transaction = Nxt.newTransactionBuilder(user.getPublicKey(), amountNQT, feeNQT, deadline,
					Attachment.ORDINARY_PAYMENT).recipientId(recipient).build(secretPhrase);

			Nxt.getTransactionProcessor().broadcast(transaction);

			return JSONResponses.NOTIFY_OF_ACCEPTED_TRANSACTION;

		}
	}

	@Override
	boolean requirePost() {
		return true;
	}

}

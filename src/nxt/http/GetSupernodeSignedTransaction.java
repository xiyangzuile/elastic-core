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

import nxt.Account;
import nxt.Nxt;
import nxt.NxtException;
import nxt.Transaction;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

/**
 * The purpose of broadcast transaction is to support client side signing of
 * transactions. Clients first submit their transaction using
 * {@link CreateTransaction} without providing the secret phrase.<br>
 * In response the client receives the unsigned transaction JSON and transaction
 * bytes.
 * <p>
 * The client then signs and submits the signed transaction using
 * {@link GetSupernodeSignedTransaction}
 * <p>
 * The default wallet implements this procedure in nrs.server.js which you can
 * use as reference.
 * <p>
 * {@link GetSupernodeSignedTransaction} accepts the following parameters:<br>
 * transactionJSON - JSON representation of the signed transaction<br>
 * transactionBytes - row bytes composing the signed transaction bytes excluding
 * the prunable appendages<br>
 * prunableAttachmentJSON - JSON representation of the prunable appendages<br>
 * <p>
 * Clients can submit either the signed transactionJSON or the signed
 * transactionBytes but not both.<br>
 * In case the client submits transactionBytes for a transaction containing
 * prunable appendages, the client also needs to submit the
 * prunableAttachmentJSON parameter which includes the attachment JSON for the
 * prunable appendages.<br>
 * <p>
 * Prunable appendages are classes implementing the
 * {@link nxt.Appendix.Prunable} interface.
 */
public final class GetSupernodeSignedTransaction extends APIServlet.APIRequestHandler {

	static final GetSupernodeSignedTransaction instance = new GetSupernodeSignedTransaction();

	private GetSupernodeSignedTransaction() {
		super(new APITag[] { APITag.TRANSACTIONS }, "transactionJSON", "transactionBytes", "prunableAttachmentJSON");
	}

	@Override
	protected final boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws ParameterException {

		final String transactionJSON = Convert.emptyToNull(req.getParameter("transactionJSON"));
		final String transactionBytes = Convert.emptyToNull(req.getParameter("transactionBytes"));
		final String prunableAttachmentJSON = Convert.emptyToNull(req.getParameter("prunableAttachmentJSON"));
		final String secretPhrase = ParameterParser.getSecretPhrase(req, true);

		Account snAccount = Account.getAccount(Crypto.getPrivateKey(secretPhrase));
		final JSONObject response = new JSONObject();
		if(snAccount == null || snAccount.isSuperNode() == false){
			Exception e = new Exception("You are not a super node");
			JSONData.putException(response, e, e.getMessage());
		}else {
			try {
				final Transaction.Builder builder = ParameterParser.parseTransaction(transactionJSON, transactionBytes,
						prunableAttachmentJSON);
				final Transaction transaction = builder.build();

				// super node sign
				transaction.signSuperNode(secretPhrase);

				response.put("transaction", transaction.getStringId());
				response.put("fullHash", transaction.getFullHash());
				response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
			} catch (NxtException.ValidationException | RuntimeException e) {
				JSONData.putException(response, e, "Failed to sign transaction");
			}
		}
		return response;

	}

	@Override
	protected boolean requirePost() {
		return true;
	}

}

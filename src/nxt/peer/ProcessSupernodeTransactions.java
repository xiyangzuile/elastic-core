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

package nxt.peer;

import nxt.Nxt;
import nxt.NxtException;
import nxt.TransactionImpl;
import nxt.util.JSON;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.util.ArrayList;
import java.util.List;

final class ProcessSupernodeTransactions extends PeerServlet.PeerRequestHandler {

	static final ProcessSupernodeTransactions instance = new ProcessSupernodeTransactions();

	private ProcessSupernodeTransactions() {
	}

	public void doEverything(final JSONArray transactionsData) throws NxtException.NotValidException {

		if ((transactionsData == null) || transactionsData.isEmpty()) {
			return;
		}
		final long arrivalTimestamp = System.currentTimeMillis();
		final List<TransactionImpl> receivedTransactions = new ArrayList<>();
		final List<Exception> exceptions = new ArrayList<>();
		for (final Object transactionData : transactionsData) {
			try {
				final TransactionImpl transaction = TransactionImpl.parseTransaction((JSONObject) transactionData);
				if (transaction.getType().mustHaveSupernodeSignature() == false || transaction.getSupernodeSig() != null)
					throw new NxtException.NotValidException("Not designated for a super node");
				receivedTransactions.add(transaction);
			} catch (Exception e) {
				exceptions.add(e);
			}

			if (!exceptions.isEmpty()) {
				throw new NxtException.NotValidException("Peer sends invalid transactions: " + exceptions.toString());
			}
		}


		// Process everything
		// TODO: This is pseudo SN code, do the real stuff later on
			for(TransactionImpl t : receivedTransactions){
				t.signSuperNode(Nxt.supernodePass);
				try {
					Nxt.getTransactionProcessor().broadcast(t);
				} catch (NxtException.ValidationException e) {
						// TODO: Check some deeper stuff here
					e.printStackTrace();
				}
			}
	}

	@Override
	JSONStreamAware processRequest(final JSONObject request, final Peer peer) {

		if(Nxt.supernodePass.length()==0) return JSON.emptyJSON;

		try {
			final JSONArray transactionsData = (JSONArray) request.get("transactions");
			Logger.logInfoMessage("SN received " + transactionsData.size() + " TX to process.");
			doEverything(transactionsData);
			return JSON.emptyJSON;
		} catch (RuntimeException | NxtException.ValidationException e) {
			// Logger.logDebugMessage("Failed to parse peer transactions: " +
			// request.toJSONString());
			peer.blacklist(e);
			return PeerServlet.error(e);
		}

	}

	@Override
	boolean rejectWhileDownloading() {
		return true;
	}

}

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
import java.math.BigInteger;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.BlockImpl;
import nxt.Constants;
import nxt.Nxt;
import nxt.Transaction;
import nxt.db.DbIterator;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;

public final class GetInitialData extends UserServlet.UserRequestHandler {

	static final GetInitialData instance = new GetInitialData();

	private GetInitialData() {
	}

	@Override
	JSONStreamAware processRequest(final HttpServletRequest req, final User user) throws IOException {

		final JSONArray unconfirmedTransactions = new JSONArray();
		final JSONArray activePeers = new JSONArray(), knownPeers = new JSONArray(), blacklistedPeers = new JSONArray();
		final JSONArray recentBlocks = new JSONArray();

		try (DbIterator<? extends Transaction> transactions = Nxt.getTransactionProcessor()
				.getAllUnconfirmedTransactions()) {
			while (transactions.hasNext()) {
				final Transaction transaction = transactions.next();

				final JSONObject unconfirmedTransaction = new JSONObject();
				unconfirmedTransaction.put("index", Users.getIndex(transaction));
				unconfirmedTransaction.put("timestamp", transaction.getTimestamp());
				unconfirmedTransaction.put("deadline", transaction.getDeadline());
				unconfirmedTransaction.put("recipient", Long.toUnsignedString(transaction.getRecipientId()));
				unconfirmedTransaction.put("amountNQT", transaction.getAmountNQT());
				unconfirmedTransaction.put("feeNQT", transaction.getFeeNQT());
				unconfirmedTransaction.put("sender", Long.toUnsignedString(transaction.getSenderId()));
				unconfirmedTransaction.put("id", transaction.getStringId());

				unconfirmedTransactions.add(unconfirmedTransaction);
			}
		}

		for (final Peer peer : Peers.getAllPeers()) {

			if (peer.isBlacklisted()) {

				final JSONObject blacklistedPeer = new JSONObject();
				blacklistedPeer.put("index", Users.getIndex(peer));
				blacklistedPeer.put("address", peer.getHost());
				blacklistedPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
				blacklistedPeer.put("software", peer.getSoftware());
				blacklistedPeers.add(blacklistedPeer);

			} else if (peer.getState() == Peer.State.NON_CONNECTED) {

				final JSONObject knownPeer = new JSONObject();
				knownPeer.put("index", Users.getIndex(peer));
				knownPeer.put("address", peer.getHost());
				knownPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
				knownPeer.put("software", peer.getSoftware());
				knownPeers.add(knownPeer);

			} else {

				final JSONObject activePeer = new JSONObject();
				activePeer.put("index", Users.getIndex(peer));
				if (peer.getState() == Peer.State.DISCONNECTED) {
					activePeer.put("disconnected", true);
				}
				activePeer.put("address", peer.getHost());
				activePeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
				activePeer.put("weight", peer.getWeight());
				activePeer.put("downloaded", peer.getDownloadedVolume());
				activePeer.put("uploaded", peer.getUploadedVolume());
				activePeer.put("software", peer.getSoftware());
				activePeers.add(activePeer);
			}
		}

		final Iterator<BlockImpl> it = Nxt.getBlockchain().getBlocks(0, 59).iterator();
		while (it.hasNext()) {
			final BlockImpl block = it.next();
			final JSONObject recentBlock = new JSONObject();
			recentBlock.put("index", Users.getIndex(block));
			recentBlock.put("timestamp", block.getTimestamp());
			recentBlock.put("numberOfTransactions", block.getTransactions().size());
			recentBlock.put("totalAmountNQT", block.getTotalAmountNQT());
			recentBlock.put("totalFeeNQT", block.getTotalFeeNQT());
			recentBlock.put("payloadLength", block.getPayloadLength());
			recentBlock.put("generator", Long.toUnsignedString(block.getGeneratorId()));
			recentBlock.put("height", block.getHeight());
			recentBlock.put("version", block.getVersion());
			recentBlock.put("block", block.getStringId());
			recentBlock.put("baseTarget", BigInteger.valueOf(block.getBaseTarget()).multiply(BigInteger.valueOf(100000))
					.divide(BigInteger.valueOf(Constants.INITIAL_BASE_TARGET)));

			recentBlocks.add(recentBlock);

		}

		final JSONObject response = new JSONObject();
		response.put("response", "processInitialData");
		response.put("version", Nxt.VERSION);
		if (unconfirmedTransactions.size() > 0) {
			response.put("unconfirmedTransactions", unconfirmedTransactions);
		}
		if (activePeers.size() > 0) {
			response.put("activePeers", activePeers);
		}
		if (knownPeers.size() > 0) {
			response.put("knownPeers", knownPeers);
		}
		if (blacklistedPeers.size() > 0) {
			response.put("blacklistedPeers", blacklistedPeers);
		}
		if (recentBlocks.size() > 0) {
			response.put("recentBlocks", recentBlocks);
		}

		return response;
	}
}

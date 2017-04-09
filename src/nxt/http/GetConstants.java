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

import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Constants;
import nxt.Genesis;
import nxt.TransactionType;
import nxt.crypto.HashFunction;
import nxt.peer.Peer;
import nxt.util.JSON;
import nxt.util.Logger;

public final class GetConstants extends APIServlet.APIRequestHandler {

	private static final class Holder {

		private static final JSONStreamAware CONSTANTS;

		static {
			try {
				final JSONObject response = new JSONObject();
				response.put("genesisBlockId", Long.toUnsignedString(Genesis.GENESIS_BLOCK_ID));
				response.put("genesisAccountId", Long.toUnsignedString(Genesis.CREATOR_ID));
				response.put("redeemAccountId", Long.toUnsignedString(Genesis.REDEEM_ID));
				response.put("epochBeginning", Constants.EPOCH_BEGINNING);
				response.put("maxBlockPayloadLength", Constants.MAX_PAYLOAD_LENGTH);
				response.put("maxArbitraryMessageLength", Constants.MAX_ARBITRARY_MESSAGE_LENGTH);
				response.put("maxPrunableMessageLength", Constants.MAX_PRUNABLE_MESSAGE_LENGTH);

				final JSONObject transactionJSON = new JSONObject();
				final JSONObject transactionSubTypesJSON = new JSONObject();
				outer: for (int type = 0;; type++) {
					final JSONObject typeJSON = new JSONObject();
					final JSONObject subtypesJSON = new JSONObject();
					for (int subtype = 0;; subtype++) {
						final TransactionType transactionType = TransactionType.findTransactionType((byte) type,
								(byte) subtype);
						if (transactionType == null) {
							if (subtype == 0) {
								break outer;
							} else {
								break;
							}
						}
						final JSONObject subtypeJSON = new JSONObject();
						subtypeJSON.put("name", transactionType.getName());
						subtypeJSON.put("canHaveRecipient", transactionType.canHaveRecipient());
						subtypeJSON.put("mustHaveRecipient", transactionType.mustHaveRecipient());
						subtypeJSON.put("type", type);
						subtypeJSON.put("subtype", subtype);
						subtypesJSON.put(subtype, subtypeJSON);
						transactionSubTypesJSON.put(transactionType.getName(), subtypeJSON);
					}
					typeJSON.put("subtypes", subtypesJSON);
					transactionJSON.put(type, typeJSON);
				}
				response.put("transactionTypes", transactionJSON);
				response.put("transactionSubTypes", transactionSubTypesJSON);

				final JSONObject hashFunctions = new JSONObject();
				for (final HashFunction hashFunction : HashFunction.values()) {
					hashFunctions.put(hashFunction.toString(), hashFunction.getId());
				}
				response.put("hashAlgorithms", hashFunctions);

				final JSONObject peerStates = new JSONObject();
				for (final Peer.State peerState : Peer.State.values()) {
					peerStates.put(peerState.toString(), peerState.ordinal());
				}
				response.put("peerStates", peerStates);
				response.put("maxTaggedDataDataLength", Constants.MAX_TAGGED_DATA_DATA_LENGTH);

				final JSONObject requestTypes = new JSONObject();
				for (final Map.Entry<String, APIServlet.APIRequestHandler> handlerEntry : APIServlet.apiRequestHandlers
						.entrySet()) {
					final JSONObject handlerJSON = JSONData.apiRequestHandler(handlerEntry.getValue());
					handlerJSON.put("enabled", true);
					requestTypes.put(handlerEntry.getKey(), handlerJSON);
				}
				for (final Map.Entry<String, APIServlet.APIRequestHandler> handlerEntry : APIServlet.disabledRequestHandlers
						.entrySet()) {
					final JSONObject handlerJSON = JSONData.apiRequestHandler(handlerEntry.getValue());
					handlerJSON.put("enabled", false);
					requestTypes.put(handlerEntry.getKey(), handlerJSON);
				}
				response.put("requestTypes", requestTypes);

				final JSONObject apiTags = new JSONObject();
				for (final APITag apiTag : APITag.values()) {
					final JSONObject tagJSON = new JSONObject();
					tagJSON.put("name", apiTag.getDisplayName());
					tagJSON.put("enabled", !API.disabledAPITags.contains(apiTag));
					apiTags.put(apiTag.name(), tagJSON);
				}
				response.put("apiTags", apiTags);

				final JSONArray disabledAPIs = new JSONArray();
				Collections.addAll(disabledAPIs, API.disabledAPIs);
				response.put("disabledAPIs", disabledAPIs);

				final JSONArray disabledAPITags = new JSONArray();
				API.disabledAPITags.forEach(apiTag -> disabledAPITags.add(apiTag.getDisplayName()));
				response.put("disabledAPITags", disabledAPITags);

				CONSTANTS = JSON.prepare(response);
			} catch (final Exception e) {
				Logger.logErrorMessage(e.toString(), e);
				throw e;
			}
		}
	}

	static final GetConstants instance = new GetConstants();

	private GetConstants() {
		super(new APITag[] { APITag.INFO });
	}

	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {
		return Holder.CONSTANTS;
	}

	@Override
	protected boolean requireBlockchain() {
		return false;
	}

}

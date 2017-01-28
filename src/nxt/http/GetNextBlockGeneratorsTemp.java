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

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.Blockchain;
import nxt.Generator;
import nxt.Nxt;
import nxt.NxtException;

/**
 * <p>
 * The GetNextBlockGenerators API will return the next block generators ordered
 * by the hit time. The list of active forgers is initialized using the block
 * generators with at least 2 blocks generated within the previous 10,000
 * blocks. Accounts without a public key will not be included. The list is
 * updated as new blocks are processed. This means the results will not be 100%
 * correct since previously active generators may no longer be running and new
 * generators won't be known until they generate a block. This API will be
 * replaced when transparent forging is activated.
 * <p>
 * Request parameters:
 * <ul>
 * <li>limit - The number of forgers to return and defaults to 1.
 * </ul>
 * <p>
 * Return fields:
 * <ul>
 * <li>activeCount - The number of active generators
 * <li>height - The last block height
 * <li>lastBlock - The last block identifier
 * <li>timestamp - The last block timestamp
 * <li>generators - The next block generators
 * <ul>
 * <li>account - The account identifier
 * <li>accountRS - The account RS identifier
 * <li>deadline - The difference between the generation time and the last block
 * timestamp
 * <li>effectiveBalanceNXT - The account effective balance
 * <li>hitTime - The generation time for the account
 * </ul>
 * </ul>
 */
public final class GetNextBlockGeneratorsTemp extends APIServlet.APIRequestHandler {

	static final GetNextBlockGeneratorsTemp instance = new GetNextBlockGeneratorsTemp();

	private GetNextBlockGeneratorsTemp() {
		super(new APITag[] { APITag.FORGING }, "limit");
	}

	/**
	 * No required block parameters
	 *
	 * @return FALSE to disable the required block parameters
	 */
	@Override
	protected boolean allowRequiredBlockParameters() {
		return false;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {
		final JSONObject response = new JSONObject();
		final int limit = Math.max(1, ParameterParser.getInt(req, "limit", 1, Integer.MAX_VALUE, false));
		final Blockchain blockchain = Nxt.getBlockchain();
		blockchain.readLock();
		try {
			final Block lastBlock = blockchain.getLastBlock();
			response.put("timestamp", lastBlock.getTimestamp());
			response.put("height", lastBlock.getHeight());
			response.put("lastBlock", Long.toUnsignedString(lastBlock.getId()));
			final List<Generator.ActiveGenerator> activeGenerators = Generator.getNextGenerators();
			response.put("activeCount", activeGenerators.size());
			final JSONArray generators = new JSONArray();
			for (final Generator.ActiveGenerator generator : activeGenerators) {
				if (generator.getHitTime() > Integer.MAX_VALUE) {
					break;
				}
				final JSONObject resp = new JSONObject();
				JSONData.putAccount(resp, "account", generator.getAccountId());
				resp.put("effectiveBalanceNXT", generator.getEffectiveBalance());
				resp.put("hitTime", generator.getHitTime());
				resp.put("deadline", (int) generator.getHitTime() - lastBlock.getTimestamp());
				generators.add(resp);
				if (generators.size() == limit) {
					break;
				}
			}
			response.put("generators", generators);
		} finally {
			blockchain.readUnlock();
		}
		return response;
	}
}
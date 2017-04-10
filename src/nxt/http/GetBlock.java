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

import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.Nxt;
import nxt.util.Convert;

public final class GetBlock extends APIServlet.APIRequestHandler {

	static final GetBlock instance = new GetBlock();

	private GetBlock() {
		super(new APITag[] { APITag.BLOCKS }, "block", "height", "timestamp", "includeTransactions",
				"includeExecutedPhased");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) {

		Block blockData;
		final String blockValue = Convert.emptyToNull(req.getParameter("block"));
		final String heightValue = Convert.emptyToNull(req.getParameter("height"));
		final String timestampValue = Convert.emptyToNull(req.getParameter("timestamp"));
		if (blockValue != null) {
			try {
				blockData = Nxt.getBlockchain().getBlock(Convert.parseUnsignedLong(blockValue));
			} catch (final RuntimeException e) {
				return JSONResponses.INCORRECT_BLOCK;
			}
		} else if (heightValue != null) {
			try {
				final int height = Integer.parseInt(heightValue);
				if ((height < 0) || (height > Nxt.getBlockchain().getHeight())) {
					return JSONResponses.INCORRECT_HEIGHT;
				}
				blockData = Nxt.getBlockchain().getBlockAtHeight(height);
			} catch (final RuntimeException e) {
				return JSONResponses.INCORRECT_HEIGHT;
			}
		} else if (timestampValue != null) {
			try {
				final int timestamp = Integer.parseInt(timestampValue);
				if (timestamp < 0) {
					return JSONResponses.INCORRECT_TIMESTAMP;
				}
				blockData = Nxt.getBlockchain().getLastBlock(timestamp);
			} catch (final RuntimeException e) {
				return JSONResponses.INCORRECT_TIMESTAMP;
			}
		} else {
			blockData = Nxt.getBlockchain().getLastBlock();
		}

		if (blockData == null) {
			return JSONResponses.UNKNOWN_BLOCK;
		}

		final boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));
		final boolean includeExecutedPhased = "true".equalsIgnoreCase(req.getParameter("includeExecutedPhased"));

		return JSONData.block(blockData, includeTransactions, includeExecutedPhased);

	}

}
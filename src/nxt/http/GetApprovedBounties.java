/* // LEAVE THIS OUT FOR NOW
package nxt.http;


import javax.servlet.http.HttpServletRequest;

import nxt.Constants;
import nxt.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
// LEAVE THIS OUT FOR NOW  import nxt.PowAndBountyAnnouncements;
import nxt.Work;

public final class GetApprovedBounties extends APIServlet.APIRequestHandler {

	static final GetApprovedBounties instance = new GetApprovedBounties();

	private GetApprovedBounties() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account", "timestamp", "type", "subtype", "firstIndex",
				"lastIndex", "numberOfConfirmations", "withMessage");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final long wid = ParameterParser.getUnsignedLong(req, "work_id", true);

		byte[] hash;
		try {
			final String readParam = ParameterParser.getAnnouncement(req, true);
			hash = Convert.parseHexString(readParam);
			if(hash.length> Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES)
                throw new NxtException.NotValidException("One of your requested hashes exceeds the maximum allowed number of bytes: " + Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES);
			hash = Convert.toFixedBytesCutter(hash, Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES);
		} catch (final Exception e) {
			hash = null;
		}

		final Work w = Work.getWork(wid);
		if ((w == null) || w.isClosed()) {
			final JSONObject response = new JSONObject();
			response.put("approved", "deprecated");
			return response;
		}

		final JSONObject response = new JSONObject();

		final boolean hasIt = PowAndBountyAnnouncements.hasValidHash(wid, hash);
		final boolean hasItFailed = PowAndBountyAnnouncements.hasHash(wid, hash);
		if (hasIt) response.put("approved", "true");
        else if (hasItFailed) response.put("approved", "deprecated");
        else response.put("approved", "false");

		return response;

	}

}
*/
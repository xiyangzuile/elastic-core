package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
import nxt.PowAndBountyAnnouncements;
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

		byte hash[] = null;
		try {
			final String readParam = ParameterParser.getAnnouncement(req, true);

			final BigInteger b = new BigInteger(readParam, 16);
			hash = b.toByteArray();
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
		if (hasIt) {
			response.put("approved", "true");
		} else if (hasItFailed) {
			response.put("approved", "deprecated");
		} else {
			response.put("approved", "false");
		}

		return response;

	}

}

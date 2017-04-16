package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
import nxt.PowAndBounty;
import nxt.db.DbIterator;

public final class GetWorkBounties extends APIServlet.APIRequestHandler {

	static final GetWorkBounties instance = new GetWorkBounties();

	private GetWorkBounties() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account", "timestamp", "type", "subtype", "firstIndex",
				"lastIndex", "numberOfConfirmations", "withMessage");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		long wid;
		try {
			final String readParam = ParameterParser.getParameterMultipart(req, "work_id");
			final BigInteger b = new BigInteger(readParam);
			wid = b.longValue();
		} catch (final Exception e) {
			wid = 0;
		}

		final JSONArray bounties = new JSONArray();

		ParameterParser.getFirstIndex(req);
		ParameterParser.getLastIndex(req);
		try (DbIterator<? extends PowAndBounty> iterator = PowAndBounty.getBounties(wid)) {
			while (iterator.hasNext()) {
				final PowAndBounty b = iterator.next();
				bounties.add(b.toJsonObjectWithIntegers());
			}
		}

		final JSONObject response = new JSONObject();
		response.put("bounties", bounties);
		return response;

	}

}

package nxt.http;

import nxt.NxtException;
import nxt.Work;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;

public final class GetWork extends APIServlet.APIRequestHandler {

	static final GetWork instance = new GetWork();

	private GetWork() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account", "timestamp", "type", "subtype", "firstIndex",
				"lastIndex", "numberOfConfirmations", "withMessage");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final JSONObject response = new JSONObject();
		long onlyOneId;
		try {
			final String readParam = ParameterParser.getParameterMultipart(req, "onlyOneId");
			final BigInteger b = new BigInteger(readParam);
			onlyOneId = b.longValue();
		} catch (final Exception e) {
			onlyOneId = 0;
		}

		Work work = Work.getWorkByWorkId(onlyOneId);
		if(work==null) response.put("work", null);
		else response.put("work", work.toJsonObject());

		return response;

	}

}

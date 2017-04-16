package nxt.http;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.NxtException;
import nxt.Work;

public final class GetAccountWork extends APIServlet.APIRequestHandler {

	static final GetAccountWork instance = new GetAccountWork();

	private GetAccountWork() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account", "timestamp", "type", "subtype", "firstIndex",
				"lastIndex", "numberOfConfirmations", "withMessage");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final Account account = ParameterParser.getAccount(req);

		long onlyOneId;
		try {
			final String readParam = ParameterParser.getParameterMultipart(req, "onlyOneId");
			final BigInteger b = new BigInteger(readParam);
			onlyOneId = b.longValue();
		} catch (final Exception e) {
			onlyOneId = 0;
		}

		final JSONArray work_packages;

		boolean only_counts = false;
		try {
			final String readParam = ParameterParser.getParameterMultipart(req, "count");
			only_counts = Boolean.parseBoolean(readParam);
		} catch (final Exception ignored) {
		}

		if (only_counts) {
			final JSONObject response = new JSONObject();
			response.put("open", Work.countAccountWork(account.getId(), true));
			response.put("total", Work.countAccountWork(account.getId(), false));
			return response;
		} else {
			final int firstIndex = ParameterParser.getFirstIndex(req);
			final int lastIndex = ParameterParser.getLastIndex(req);

			final List<Work> work = Work.getAccountWork(account.getId(), true, firstIndex, lastIndex, onlyOneId);
			work_packages = work.stream().map(Work::toJsonObject).collect(Collectors.toCollection(JSONArray::new));

			final JSONObject response = new JSONObject();
			response.put("work_packages", work_packages);
			return response;
		}

	}

}

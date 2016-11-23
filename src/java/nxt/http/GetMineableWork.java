package nxt.http;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Nxt;
import nxt.NxtException;
import nxt.Work;
import nxt.db.DbIterator;



public final class GetMineableWork extends APIServlet.APIRequestHandler {

	static final GetMineableWork instance = new GetMineableWork();

	

	private GetMineableWork() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC });
	}

	@Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

		int n = 5;
		try {
			String readParam = ParameterParser.getParameterMultipart(req, "n");
			n = Integer.parseInt(readParam);
		} catch (Exception e) {
		}
		
		JSONArray work_packages = new JSONArray();

        try (DbIterator<? extends Work> iterator = Work.getActiveWorks(0, -1)) {
		  while (iterator.hasNext()) { Work transaction = iterator.next(); work_packages.add(transaction.toJsonObjectWithSource());
		} }
		 
		JSONObject response = new JSONObject();
		response.put("work_packages", work_packages);
	
		return response;

	}

}

package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.NxtException;
import nxt.Work;
import nxt.db.DbIterator;



public final class GetAccountWork extends APIServlet.APIRequestHandler {

	static final GetAccountWork instance = new GetAccountWork();

	

	private GetAccountWork() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account",
				"timestamp", "type", "subtype", "firstIndex", "lastIndex",
				"numberOfConfirmations", "withMessage");
	}

	@Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

		Account account = ParameterParser.getAccount(req);
	
		long onlyOneId = 0;
		try {
			String readParam = ParameterParser.getParameterMultipart(req, "onlyOneId");
			BigInteger b = new BigInteger(readParam);
			onlyOneId = b.longValue();
		} catch (Exception e) {
			onlyOneId = 0;
		}
		
		JSONArray work_packages = new JSONArray();

		boolean only_counts = false;
		try {
			String readParam = ParameterParser.getParameterMultipart(req, "count");
			only_counts = Boolean.parseBoolean(readParam);
		} catch (Exception e) {
		}

		if (only_counts ){
			JSONObject response = new JSONObject();
			response.put("open", Work.countAccountWork(account.getId(), true));
			response.put("total", Work.countAccountWork(account.getId(), false));
			return response;
		}else{
	        int firstIndex = ParameterParser.getFirstIndex(req);
	        int lastIndex = ParameterParser.getLastIndex(req);
			
	        try (DbIterator<? extends Work> iterator = Work.getAccountWork(account.getId(), true, firstIndex, lastIndex, onlyOneId)) {
			  while (iterator.hasNext()) { Work transaction = iterator.next(); work_packages.add(transaction.toJsonObject());
			} }
			 
	        
			JSONObject response = new JSONObject();
			response.put("work_packages", work_packages);
			return response;
		}

	}

}

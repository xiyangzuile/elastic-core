package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.NxtException;
import nxt.Work;
import nxt.WorkLogicManager;
import nxt.db.DbIterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;



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
			System.out.println(b);
			onlyOneId = b.longValue();
			System.out.println(onlyOneId);
		} catch (Exception e) {
			onlyOneId = 0;
		}
		
		JSONArray work_packages = new JSONArray();


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

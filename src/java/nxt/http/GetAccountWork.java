package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.NxtException;
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
		int timestamp = ParameterParser.getTimestamp(req);
		int numberOfConfirmations = ParameterParser
				.getNumberOfConfirmations(req);

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
		
		byte type;
		try {
			type = Byte.parseByte(ParameterParser.getParameterMultipart(req, "type"));
		} catch (NumberFormatException e) {
			type = -1;
		}

		JSONArray work_packages = new JSONArray();


        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
		
        try (DbIterator<? extends JSONObject> iterator = WorkLogicManager.getInstance().getWorkList(account, firstIndex, lastIndex, onlyOneId)) {
		  while (iterator.hasNext()) { JSONObject transaction = iterator.next(); work_packages.add(transaction);
		} }
		 


		
		JSONObject response = new JSONObject();
		response.put("work_packages", work_packages);
		return response;

	}

}

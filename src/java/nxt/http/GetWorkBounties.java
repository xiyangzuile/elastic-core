package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.NxtException;
import nxt.PowAndBounty;
import nxt.Work;
import nxt.db.DbIterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;



public final class GetWorkBounties extends APIServlet.APIRequestHandler {

	static final GetWorkBounties instance = new GetWorkBounties();

	

	private GetWorkBounties() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account",
				"timestamp", "type", "subtype", "firstIndex", "lastIndex",
				"numberOfConfirmations", "withMessage");
	}

	@Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

	
		long wid = 0;
		try {
			String readParam = ParameterParser.getParameterMultipart(req, "work_id");
			BigInteger b = new BigInteger(readParam);
			wid = b.longValue();
		} catch (Exception e) {
			wid = 0;
		}
		

		JSONArray bounties = new JSONArray();

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        try (DbIterator<? extends PowAndBounty> iterator = PowAndBounty.getBounties(wid)) {
		  while (iterator.hasNext()) { PowAndBounty b = iterator.next(); bounties.add(b.toJsonObject());
		} }
		 
		JSONObject response = new JSONObject();
		response.put("bounties", bounties);
		return response;

	}

}

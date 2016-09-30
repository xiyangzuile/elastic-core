package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
import nxt.PowAndBounty;
import nxt.PowAndBountyAnnouncements;
import nxt.db.DbIterator;



public final class GetApprovedBounties extends APIServlet.APIRequestHandler {

	static final GetApprovedBounties instance = new GetApprovedBounties();

	

	private GetApprovedBounties() {
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
		
		byte hash[] = null;
		try {
			String readParam = ParameterParser.getParameterMultipart(req, "hash_announced");
			BigInteger b = new BigInteger(readParam);
			hash = b.toByteArray();
		} catch (Exception e) {
			hash = null;
		}

      
		 
		JSONObject response = new JSONObject();
		
		boolean hasIt = PowAndBountyAnnouncements.hasValidHash(wid, hash);
		boolean hasItFailed = PowAndBountyAnnouncements.hasHash(wid, hash);
		if(hasIt){
			response.put("approved", "true");
		}else if (hasItFailed){
			response.put("approved", "deprecated");
		}else{
			response.put("approved", "false");
		}
		
		
		return response;

	}

}

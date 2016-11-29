package nxt.http;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;



public final class GetUnclaimedRedeems extends APIServlet.APIRequestHandler {

	static final GetUnclaimedRedeems instance = new GetUnclaimedRedeems();



	private GetUnclaimedRedeems() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account",
				"timestamp", "type", "subtype", "firstIndex", "lastIndex",
				"numberOfConfirmations", "withMessage");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {


		final JSONArray redeems = new JSONArray();
		for(int i=0;i<nxt.Redeem.listOfAddresses.length;++i){
			if(!nxt.Redeem.isAlreadyRedeemed(nxt.Redeem.listOfAddresses[i] )) {
				redeems.add(new String(String.valueOf(i) + "," + nxt.Redeem.listOfAddresses[i] + "," + String.valueOf(nxt.Redeem.amounts[i]).replace("L", "")));
			}
		}


		final JSONObject response = new JSONObject();
		response.put("redeems", redeems);
		return response;

	}

}

package nxt.http;

import javax.servlet.http.HttpServletRequest;

import nxt.*;
import nxt.Redeem;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class GetUnclaimedRedeems extends APIServlet.APIRequestHandler {

	static final GetUnclaimedRedeems instance = new GetUnclaimedRedeems();

	private GetUnclaimedRedeems() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account", "timestamp", "type", "subtype", "firstIndex",
				"lastIndex", "numberOfConfirmations", "withMessage");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

        final JSONArray redeems = IntStream.range(0, nxt.Redeem.listOfAddresses.length).filter(i -> !Redeem.isAlreadyRedeemed(Redeem.listOfAddresses[i])).mapToObj(i -> new String(String.valueOf(i) + "," + Redeem.listOfAddresses[i] + ","
                + String.valueOf(Redeem.amounts[i]).replace("L", ""))).collect(Collectors.toCollection(JSONArray::new));

        final JSONObject response = new JSONObject();
		response.put("redeems", redeems);
		return response;

	}

}

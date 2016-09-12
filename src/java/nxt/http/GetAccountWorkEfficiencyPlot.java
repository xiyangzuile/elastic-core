package nxt.http;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import nxt.NxtException;
import nxt.Quartett;
import nxt.Triplet;
import nxt.WorkLogicManager;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;



public final class GetAccountWorkEfficiencyPlot extends APIServlet.APIRequestHandler {

	static final GetAccountWorkEfficiencyPlot instance = new GetAccountWorkEfficiencyPlot();

	private GetAccountWorkEfficiencyPlot() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account",
				"timestamp", "type", "subtype", "firstIndex", "lastIndex",
				"numberOfConfirmations", "withMessage");
	}

	@SuppressWarnings("unchecked")
	JSONArray dateValuePair(long timestamp, double d){
		JSONArray nullValue = new JSONArray();
		nullValue.add(timestamp);
		nullValue.add(d);
		return nullValue;
	}
	
	@Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
		JSONObject response = new JSONObject();

		

		long workId = 0;
		try {
			String readParam = ParameterParser.getParameterMultipart(req, "workId");
			BigInteger b = new BigInteger(readParam);
			workId = b.longValue();
		} catch (Exception e) {
			e.printStackTrace();
			workId = 0;
		}
		
		int last_num = 20;
		try {
			String readParam = ParameterParser.getParameterMultipart(req, "last_num");
			BigInteger b = new BigInteger(readParam);
			last_num = b.intValue();
		} catch (Exception e) {
			
		}
		
		JSONArray computation_power = getComputationPlot(workId, last_num);
		
		response.put("computation_power", computation_power);
		
		
		
		return response;

	}

	private JSONArray getComputationPlot(long workId, int last_num) {
		JSONArray computation_power = new JSONArray();
		
		ArrayList<Quartett<Integer,Long,String, Long>> ret_pre = WorkLogicManager.getInstance().getDataForPlot(workId, last_num);
		for(Quartett<Integer,Long,String, Long> t : ret_pre){
			JSONArray inner = new JSONArray();
			inner.add(t.getA());
			inner.add(t.getB());
			inner.add(t.getC());
			inner.add(t.getD());
			computation_power.add(inner);
		}
				
		return computation_power;
	}

}

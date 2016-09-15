package nxt.http;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import nxt.Constants;
import nxt.Db;
import nxt.Nxt;
import nxt.NxtException;
import nxt.Quartett;
import nxt.Triplet;

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
	
	public ArrayList<Quartett<Integer,Long,String,Long>> getDataForPlot(long id, int limit_minutes) {

		ArrayList<Quartett<Integer,Long,String,Long>> ret = new ArrayList<Quartett<Integer,Long,String,Long>>();

		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT count(pow_and_bounty.id), block.min_pow_target, transaction.block_timestamp FROM pow_and_bounty INNER JOIN transaction ON transaction.id = pow_and_bounty.id INNER JOIN block ON block.id = transaction.block_id WHERE work_id=? AND transaction.block_timestamp > ? GROUP BY transaction.block_id ORDER BY transaction.block_timestamp DESC")) {
			int i = 0;
			pstmt.setLong(++i, id);
			pstmt.setInt(++i, Nxt.getEpochTime()-limit_minutes*60);
			ResultSet check = pstmt.executeQuery();
			while (check.next()) {
				long stime = (long) check.getInt(3);
				stime = stime + (Constants.EPOCH_BEGINNING/1000);		
				Quartett<Integer,Long,String,Long> d = new Quartett<Integer,Long,String,Long>((int) check.getInt(1),stime,(String) check.getString(2), 0L); 
				ret.add(d);
				
			}

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

		Collections.reverse(ret);
		return ret;
	}

	private JSONArray getComputationPlot(long workId, int last_num) {
		JSONArray computation_power = new JSONArray();
		
		ArrayList<Quartett<Integer,Long,String, Long>> ret_pre = getDataForPlot(workId, last_num);
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

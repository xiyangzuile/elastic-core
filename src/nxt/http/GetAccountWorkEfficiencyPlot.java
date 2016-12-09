package nxt.http;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Constants;
import nxt.Db;
import nxt.Nxt;
import nxt.NxtException;
import nxt.Quartett;

public final class GetAccountWorkEfficiencyPlot extends APIServlet.APIRequestHandler {

	static final GetAccountWorkEfficiencyPlot instance = new GetAccountWorkEfficiencyPlot();

	private GetAccountWorkEfficiencyPlot() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account", "timestamp", "type", "subtype", "firstIndex",
				"lastIndex", "numberOfConfirmations", "withMessage");
	}

	@SuppressWarnings("unchecked")
	JSONArray dateValuePair(final long timestamp, final double d) {
		final JSONArray nullValue = new JSONArray();
		nullValue.add(timestamp);
		nullValue.add(d);
		return nullValue;
	}

	private JSONArray getComputationPlot(final long workId, final int last_num) {
		final JSONArray computation_power = new JSONArray();

		final ArrayList<Quartett<Integer, Long, String, Long>> ret_pre = this.getDataForPlot(workId, last_num);
		for (final Quartett<Integer, Long, String, Long> t : ret_pre) {
			final JSONArray inner = new JSONArray();
			inner.add(t.getA());
			inner.add(t.getB());
			inner.add(t.getC());
			inner.add(t.getD());
			computation_power.add(inner);
		}

		return computation_power;
	}

	public ArrayList<Quartett<Integer, Long, String, Long>> getDataForPlot(final long id, final int limit_minutes) {

		final ArrayList<Quartett<Integer, Long, String, Long>> ret = new ArrayList<>();

		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con.prepareStatement(
						"SELECT count(pow_and_bounty.id), block.min_pow_target, transaction.block_timestamp FROM pow_and_bounty INNER JOIN transaction ON transaction.id = pow_and_bounty.id INNER JOIN block ON block.id = transaction.block_id WHERE work_id=? AND transaction.block_timestamp > ? GROUP BY transaction.block_id ORDER BY transaction.block_timestamp DESC")) {
			int i = 0;
			pstmt.setLong(++i, id);
			pstmt.setInt(++i, Nxt.getEpochTime() - (limit_minutes * 60));
			final ResultSet check = pstmt.executeQuery();
			while (check.next()) {
				long stime = check.getInt(3);
				stime = stime + (Constants.EPOCH_BEGINNING / 1000);
				final Quartett<Integer, Long, String, Long> d = new Quartett<>(check.getInt(1), stime,
						check.getString(2), 0L);
				ret.add(d);

			}

		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

		Collections.reverse(ret);
		return ret;
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {
		final JSONObject response = new JSONObject();

		long workId = 0;
		try {
			final String readParam = ParameterParser.getParameterMultipart(req, "workId");
			final BigInteger b = new BigInteger(readParam);
			workId = b.longValue();
		} catch (final Exception e) {
			e.printStackTrace();
			workId = 0;
		}

		int last_num = 20;
		try {
			final String readParam = ParameterParser.getParameterMultipart(req, "last_num");
			final BigInteger b = new BigInteger(readParam);
			last_num = b.intValue();
		} catch (final Exception e) {

		}

		final JSONArray computation_power = this.getComputationPlot(workId, last_num);

		response.put("computation_power", computation_power);

		return response;

	}

}

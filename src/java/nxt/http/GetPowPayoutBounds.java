package nxt.http;

import static java.lang.Integer.parseInt;
import static nxt.http.JSONResponses.INCORRECT_AMOUNT;
import static nxt.http.JSONResponses.INCORRECT_DEADLINE;
import static nxt.http.JSONResponses.INCORRECT_WORK_LANGUAGE;
import static nxt.http.JSONResponses.INCORRECT_WORK_NAME_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_XEL_PER_POW;
import static nxt.http.JSONResponses.MISSING_BOUNTYLIMIT;
import static nxt.http.JSONResponses.MISSING_DEADLINE;
import static nxt.http.JSONResponses.MISSING_LANGUAGE;
import static nxt.http.JSONResponses.MISSING_NAME;
import static nxt.http.JSONResponses.MISSING_NUMBER_INPUTVARS;
import static nxt.http.JSONResponses.MISSING_NUMBER_OUTPUTVARS;
import static nxt.http.JSONResponses.MISSING_PROGAMCODE;

import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.Constants;
import nxt.Triplet;
import nxt.WorkLogicManager;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;



public final class GetPowPayoutBounds extends APIServlet.APIRequestHandler {

    static final GetPowPayoutBounds instance = new GetPowPayoutBounds();

    private GetPowPayoutBounds() {
        super(new APITag[] {APITag.INFO});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        JSONObject response = new JSONObject();
        response.put("min", WorkLogicManager.getInstance().GetMinPossiblePowPrice());
        response.put("max", WorkLogicManager.getInstance().GetMaxPossiblePowPrice());
        
        Triplet<Long,Long,Long> averages = WorkLogicManager.getInstance().GetMinMaxAvgActualPowPrice();
        response.put("min_used", averages.getA().longValue());
        response.put("max_used", averages.getB().longValue());
        response.put("avg_used", averages.getC().longValue());

        String xelPerPow = ParameterParser.getParameterMultipart(req, "xel_per_pow");
        
        if(xelPerPow!=null && xelPerPow.length()>0){
        long xelPerPowInt;
	        try {
	        	xelPerPowInt = parseInt(xelPerPow);
	        	if(WorkLogicManager.getInstance().isPowPriceCorrect(xelPerPowInt) == false){
	        		return INCORRECT_XEL_PER_POW;
	        	}
	        	response.put("relative_position", WorkLogicManager.getInstance().GetHowManyPowAboveMe(xelPerPowInt));
	        } catch (NumberFormatException e) {
	            return INCORRECT_XEL_PER_POW;
	        }
        }
        return response;
    }

}

package nxt.http;

import static nxt.http.JSONResponses.INCORRECT_WORKID;
import static nxt.http.JSONResponses.MISSING_WORKID;


import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.Attachment;
import nxt.NxtException;
import nxt.WorkLogicManager;

import org.json.simple.JSONStreamAware;


public final class CancelWork extends CreateTransaction {

    static final CancelWork instance = new CancelWork();

    private CancelWork() {
        super(new APITag[] {APITag.WC, APITag.CREATE_TRANSACTION, APITag.CANCEL_TRANSACTION}, "name", "description", "minNumberOfOptions", "maxNumberOfOptions", "optionsAreBinary", "option1", "option2", "option3"); // hardcoded to 3 options for testing
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

        String workIdString = ParameterParser.getParameterMultipart(req, "workId");
        Account account = ParameterParser.getSenderAccount(req);
        
        if (workIdString == null) {
            return MISSING_WORKID;
        }
        
        long workId = 0;
        try {
        	workId = Long.parseUnsignedLong(workIdString);
        } catch (NumberFormatException e) {
            return INCORRECT_WORKID;
        }
                
        Attachment attachment = new Attachment.WorkIdentifierCancellationRequest(workId);
        return createTransaction(req, account, attachment);
    }
    
    

}

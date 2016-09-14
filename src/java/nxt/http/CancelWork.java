package nxt.http;

import static nxt.http.JSONResponses.INCORRECT_WORKID;
import static nxt.http.JSONResponses.MISSING_WORKID;


import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.Attachment;
import nxt.NxtException;

import org.json.simple.JSONStreamAware;


public final class CancelWork extends CreateTransaction {

    static final CancelWork instance = new CancelWork();

    private CancelWork() {
        super(new APITag[] {APITag.WC, APITag.CREATE_TRANSACTION, APITag.CANCEL_TRANSACTION}, "name", "description", "minNumberOfOptions", "maxNumberOfOptions", "optionsAreBinary", "option1", "option2", "option3"); // hardcoded to 3 options for testing
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

        long workId = ParameterParser.getUnsignedLong(req, "workId",true);
        Account account = ParameterParser.getSenderAccount(req);
        
      
                
        Attachment attachment = new Attachment.WorkIdentifierCancellationRequest(workId);
        return createTransaction(req, account, attachment);
    }
    
    

}

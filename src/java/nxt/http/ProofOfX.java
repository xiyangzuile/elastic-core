package nxt.http;

import static nxt.http.JSONResponses.INCORRECT_BOOLEAN;
import static nxt.http.JSONResponses.INCORRECT_INPUTS;
import static nxt.http.JSONResponses.INCORRECT_WORKID;
import static nxt.http.JSONResponses.INCORRECT_ACCOUNT;

import static nxt.http.JSONResponses.MISSING_INPUTS;
import static nxt.http.JSONResponses.MISSING_PASSPHRASE;
import static nxt.http.JSONResponses.INCORRECT_PUBLIC_KEY;
import static nxt.http.JSONResponses.MISSING_SECRET_PHRASE;

import static nxt.http.JSONResponses.MISSING_WORKID;
import static nxt.http.JSONResponses.UNKNOWN_ACCOUNT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.Attachment;
import nxt.Db;
import nxt.NxtException;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import nxt.util.Logger;

import org.json.simple.JSONStreamAware;

public final class ProofOfX extends CreateTransaction {
	
    
	
	
	static final ProofOfX instance = new ProofOfX();

	private ProofOfX() {
		super(new APITag[] { APITag.POX, APITag.CREATE_TRANSACTION }, "name", "description", "minNumberOfOptions",
				"maxNumberOfOptions", "optionsAreBinary", "option1", "option2", "option3"); // hardcoded
																							// to
																							// 3
																							// options
																							// for
																							// testing
	}


	@Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

		long workId = ParameterParser.getUnsignedLong(req, "work_id",true);
	    Account account = null;
	    try {
            Db.db.beginTransaction();
            account = ParameterParser.getOrCreateSenderAccount(req);
            Db.db.commitTransaction();
        } catch (Exception e) {
            Logger.logMessage(e.toString(), e);
            Db.db.rollbackTransaction();
            throw e;
        } finally {
            Db.db.endTransaction();
        }
	    
	    if(account == null){
	    	return INCORRECT_ACCOUNT;
	    }
	    
	    boolean is_pow = ParameterParser.getBoolean(req, "is_pow", true);

		String inputs = ParameterParser.getParameterMultipart(req, "inputs");

		List<Integer> inputRaw = new ArrayList<Integer>();
		try {
			if (inputs.contains(",")) {
				List<String> elephantList = Arrays.asList(inputs.split(","));
				for (String s : elephantList) {
					inputRaw.add(Integer.parseInt(s));
				}
			} else {
				inputRaw.add(Integer.parseInt(inputs));
			}

		} catch (NumberFormatException e) {
			return INCORRECT_INPUTS;
		}
		int[] inputUltraRaw = new int[inputRaw.size()];
		for (int i = 0; i < inputUltraRaw.length; i++) {
			inputUltraRaw[i] = inputRaw.get(i);
		}
		

		if (is_pow) {
			Attachment.PiggybackedProofOfWork attachment = new Attachment.PiggybackedProofOfWork(
					workId, inputUltraRaw);
			return createTransaction(req, account, attachment);
		} else {
			Attachment.PiggybackedProofOfBounty attachment = new Attachment.PiggybackedProofOfBounty(
					workId, inputUltraRaw);
			return createTransaction(req, account, attachment);
		}

	}

}

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
import nxt.WorkLogicManager;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import nxt.util.Logger;

import org.json.simple.JSONStreamAware;

public final class ProofOfX extends CreateTransaction {
	
    static long getRecipientId(HttpServletRequest req) throws ParameterException {
        String recipientValue = Convert.emptyToNull(ParameterParser.getParameterMultipart(req, "recipient"));
        if (recipientValue == null || "0".equals(recipientValue)) {
            throw new ParameterException(UNKNOWN_ACCOUNT);
        }
        long recipientId;
        try {
            recipientId = Convert.parseAccountId(recipientValue);
        } catch (RuntimeException e) {
            throw new ParameterException(UNKNOWN_ACCOUNT);
        }
        if (recipientId == 0) {
            throw new ParameterException(UNKNOWN_ACCOUNT);
        }
        return recipientId;
    }
    
	static Account getSenderAccount(HttpServletRequest req) throws ParameterException {
        Account account;
        String secretPhrase = Convert.emptyToNull(ParameterParser.getParameterMultipart(req, "secretPhrase"));
        String publicKeyString = Convert.emptyToNull(ParameterParser.getParameterMultipart(req, "publicKey"));
        if (secretPhrase != null) {
            account = Account.getAccount(Crypto.getPublicKey(secretPhrase));
        } else if (publicKeyString != null) {
            try {
                account = Account.getAccount(Convert.parseHexString(publicKeyString));
            } catch (RuntimeException e) {
                throw new ParameterException(INCORRECT_PUBLIC_KEY);
            }
        } else {
            throw new ParameterException(MISSING_SECRET_PHRASE);
        }
        if (account == null) {
            throw new ParameterException(UNKNOWN_ACCOUNT);
        }
        return account;
    }
	
	static Account getOrCreateSenderAccount(HttpServletRequest req) throws ParameterException {
		String accountValue = Convert.emptyToNull(ParameterParser.getParameterMultipart(req, "account"));
        if (accountValue == null) {
            throw new ParameterException(UNKNOWN_ACCOUNT);
        }
        try {
        	long accId = Convert.parseAccountId(accountValue);
            Account account = Account.addOrGetAccount(accId);
            if (account == null) {
                throw new ParameterException(UNKNOWN_ACCOUNT);
            }
            return account;
        } catch (RuntimeException e) {
            throw new ParameterException(INCORRECT_ACCOUNT);
        }
	}

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

		String workId = ParameterParser.getParameterMultipart(req, "workId");
		String inputs = ParameterParser.getParameterMultipart(req, "inputs");
		String ProofOfWork = ParameterParser.getParameterMultipart(req, "pow");
		String passphrase = ParameterParser.getParameterMultipart(req, "secretPhrase");

		if (workId == null) {
			return MISSING_WORKID;
		} else if (inputs == null) {
			return MISSING_INPUTS;
		}

		else if (passphrase == null) {
			return MISSING_PASSPHRASE;
		}

		try {
			if (WorkLogicManager.getInstance().haveWork(Long.parseUnsignedLong(workId)) == false) {
				System.out.println("haveWork() Returned FALSE");
				return INCORRECT_WORKID;
			}
		} catch (NumberFormatException e) {
			System.out.println("haveWork() preprocessing crashed");
			return INCORRECT_WORKID;
		}

		boolean proofOfWork = true;
		try {
			proofOfWork = Boolean.parseBoolean(ProofOfWork);
		} catch (NumberFormatException e) {
			return INCORRECT_BOOLEAN;
		}

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
		Account account = null;
		try {
			Db.db.beginTransaction();
			account = getOrCreateSenderAccount(req);
			Db.db.commitTransaction();

		} catch (Exception e) {
			Logger.logErrorMessage(e.toString(), e);
			Db.db.rollbackTransaction();
			throw e;
		} finally {
			Db.db.endTransaction();
		}
		
		if(account==null){
			return INCORRECT_ACCOUNT;
		}

		if (proofOfWork) {
			Attachment.PiggybackedProofOfWork attachment = new Attachment.PiggybackedProofOfWork(
					Long.parseUnsignedLong(workId), inputUltraRaw);
			long recipient = getRecipientId(req);
			long amountNQT = ParameterParser.getAmountNQT(req);
			System.out.println("API has created POW submission.");
			return createTransaction(req, account, recipient, amountNQT, attachment);
		} else {
			Attachment.PiggybackedProofOfBounty attachment = new Attachment.PiggybackedProofOfBounty(
					Long.parseUnsignedLong(workId), inputUltraRaw);
			long recipient = getRecipientId(req); // this one is
																	// only a
																	// dummy
																	// (bad
																	// programming)
			System.out.println("API has created Bounty submission.");
			return createTransaction(req, account, recipient, 0, attachment);
		}

	}

}

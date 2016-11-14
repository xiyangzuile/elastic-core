package nxt.http;

import static nxt.http.JSONResponses.MISSING_FIELDS_REDEEM;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Attachment;
import nxt.Db;
import nxt.Genesis;
import nxt.NxtException;
import nxt.util.Logger;




public final class Redeem extends CreateTransaction {

	static final Redeem instance = new Redeem();

	private Redeem() {
		super(new APITag[] { APITag.WC, APITag.CREATE_TRANSACTION }, "name", "description", "minNumberOfOptions",
				"maxNumberOfOptions", "optionsAreBinary", "option1", "option2", "option3"); // hardcoded
																							// to
																							// 3
																							// options
																							// for
																							// testing
	}

	@Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

		String address = ParameterParser.getParameterMultipart(req, "redeem_address");
		String secp_signatures = ParameterParser.getParameterMultipart(req, "secp_signatures");
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
		
		long account_to = ParameterParser.getOrCreateReceipientAccount(req);
		
		if (address == null) {
			return MISSING_FIELDS_REDEEM;
		} else if (secp_signatures == null) {
			return MISSING_FIELDS_REDEEM;
		} else if (account == null) {
			return MISSING_FIELDS_REDEEM;
		}
		
		String[] parts = address.split(",");
		if(parts.length==3){
			address = parts[1];
		}
		
		if(nxt.Redeem.hasAddress(address) == false){
			return MISSING_FIELDS_REDEEM;
		}
		// More boundary checks
		long amountlong = ParameterParser.getAmountNQT(req);

		Attachment attachment = new Attachment.RedeemAttachment(address, secp_signatures);
		Account fake_from = Account.getAccount(Genesis.REDEEM_ID);
		return createTransaction(req, fake_from, account_to, amountlong, attachment);

	}

}

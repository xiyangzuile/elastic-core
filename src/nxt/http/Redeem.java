package nxt.http;

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
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		String address = ParameterParser.getParameterMultipart(req, "redeem_address");
		final String secp_signatures = ParameterParser.getParameterMultipart(req, "secp_signatures");
		Account account;
		try {
			Db.db.beginTransaction();
			account = ParameterParser.getOrCreateSenderAccount(req);
			Db.db.commitTransaction();
		} catch (final Exception e) {
			Logger.logMessage(e.toString(), e);
			Db.db.rollbackTransaction();
			throw e;
		} finally {
			Db.db.endTransaction();
		}

		final long account_to = ParameterParser.getOrCreateReceipientAccount(req);

		if (address == null) return JSONResponses.MISSING_FIELDS_REDEEM;
        else if (secp_signatures == null) return JSONResponses.MISSING_FIELDS_REDEEM;
        else if (account == null) return JSONResponses.MISSING_FIELDS_REDEEM;

		final String[] parts = address.split(",");
		if (parts.length == 3) address = parts[1];

		if (!nxt.Redeem.hasAddress(address)) return JSONResponses.MISSING_FIELDS_REDEEM;
		// More boundary checks
		final long amountlong = ParameterParser.getAmountNQT(req);

		final Attachment attachment = new Attachment.RedeemAttachment(address, secp_signatures);
		final Account fake_from = Account.getAccount(Genesis.REDEEM_ID);
		return createTransaction(req, fake_from, account_to, amountlong, attachment);

	}

}

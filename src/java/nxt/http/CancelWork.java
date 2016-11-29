package nxt.http;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Attachment;
import nxt.NxtException;

public final class CancelWork extends CreateTransaction {

	static final CancelWork instance = new CancelWork();

	private CancelWork() {
		super(new APITag[] { APITag.WC, APITag.CREATE_TRANSACTION, APITag.CANCEL_TRANSACTION }, "name", "description",
				"minNumberOfOptions", "maxNumberOfOptions", "optionsAreBinary", "option1", "option2", "option3"); // hardcoded
																													// to
																													// 3
																													// options
																													// for
																													// testing
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final long workId = ParameterParser.getUnsignedLong(req, "workId", true);
		final Account account = ParameterParser.getSenderAccount(req);

		final Attachment attachment = new Attachment.WorkIdentifierCancellationRequest(workId);
		return this.createTransaction(req, account, attachment);
	}

}

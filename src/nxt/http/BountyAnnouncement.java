package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Attachment;
import nxt.Db;
import nxt.NxtException;
import nxt.util.Logger;

public final class BountyAnnouncement extends CreateTransaction {

	static final BountyAnnouncement instance = new BountyAnnouncement();

	private BountyAnnouncement() {
		super(new APITag[] { APITag.POX, APITag.CREATE_TRANSACTION }, "name", "description", "minNumberOfOptions",
				"maxNumberOfOptions", "optionsAreBinary", "option1", "option2", "option3"); // hardcoded
		// to
		// 3
		// options
		// for
		// testing
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final long workId = ParameterParser.getUnsignedLong(req, "work_id", true);
		Account account = null;
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

		if (account == null) {
			return JSONResponses.INCORRECT_ACCOUNT;
		}

		final String multiplicator_multipart = ParameterParser.getAnnouncement(req, true);
		byte[] hash = null;
		if (multiplicator_multipart != null) {
			final BigInteger multiplicator_bigint = new BigInteger(multiplicator_multipart, 16);
			// restore fixed sized multiplicator array
			hash = multiplicator_bigint.toByteArray();
		}

		final Attachment.PiggybackedProofOfBountyAnnouncement attachment = new Attachment.PiggybackedProofOfBountyAnnouncement(
				workId, hash);
		return this.createTransaction(req, account, attachment);

	}

}

// LEAVE THIS OUT FOR NOW
/*
package nxt.http;


import javax.servlet.http.HttpServletRequest;

import nxt.*;
import nxt.util.Convert;
import org.json.simple.JSONStreamAware;

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

		if (account == null) return JSONResponses.INCORRECT_ACCOUNT;
		byte[] hash = new byte[Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES];
		final String multiplicator_multipart = ParameterParser.getAnnouncement(req, true);
		if (multiplicator_multipart != null) {
			// restore fixed sized multiplicator array
			hash = Convert.parseHexString(multiplicator_multipart);
			if(hash.length> Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES)
                throw new NxtException.NotValidException("Your announced hash exceeds the maximum allowed number of bytes: " + Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES);
			hash = Convert.toFixedBytesCutter(hash, Constants.MAX_HASH_ANNOUNCEMENT_SIZE_BYTES);
		}

		final Attachment.PiggybackedProofOfBountyAnnouncement attachment = new Attachment.PiggybackedProofOfBountyAnnouncement(
				workId, hash);
		return this.createTransaction(req, account, attachment);

	}

}
*/
package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONStreamAware;

import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.Db;
import nxt.NxtException;
import nxt.util.Logger;

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

		final boolean is_pow = ParameterParser.getBoolean(req, "is_pow", true);

		final String multiplicator_multipart = ParameterParser.getMultiplicator(req, true);
		if ((multiplicator_multipart == null) || (multiplicator_multipart.length() > 65)) {
			return JSONResponses.INCORRECT_MULTIPLICATOR;
		}
		final byte[] multiplicator = new byte[Constants.WORK_MULTIPLICATOR_BYTES];
		// null it first (just to be safe)
		for (int i = 0; i < Constants.WORK_MULTIPLICATOR_BYTES; ++i) {
			multiplicator[i] = 0;
		}
		if (multiplicator_multipart != null) {
			final BigInteger multiplicator_bigint = new BigInteger(multiplicator_multipart, 16);
			// restore fixed sized multiplicator array
			final byte[] multiplicator_byte_representation = multiplicator_bigint.toByteArray();
			int back_position = Constants.WORK_MULTIPLICATOR_BYTES - 1;
			for (int i = Math.min(multiplicator_byte_representation.length, 32); i > 0; --i) {
				multiplicator[back_position] = multiplicator_byte_representation[i - 1];
				back_position--;
			}
		}

		if (is_pow) {
			final Attachment.PiggybackedProofOfWork attachment = new Attachment.PiggybackedProofOfWork(workId,
					multiplicator);
			return this.createTransaction(req, account, attachment);
		} else {
			final Attachment.PiggybackedProofOfBounty attachment = new Attachment.PiggybackedProofOfBounty(workId,
					multiplicator);
			return this.createTransaction(req, account, attachment);
		}

	}

}

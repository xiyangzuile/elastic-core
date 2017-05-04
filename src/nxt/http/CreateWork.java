package nxt.http;

import javax.servlet.http.HttpServletRequest;
import org.json.simple.JSONStreamAware;
import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.NxtException;

public final class CreateWork extends CreateTransaction {

	static final CreateWork instance = new CreateWork();

	private CreateWork() {
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

		final String workTitle = ParameterParser.getParameterMultipart(req, "work_title");
		final String workLanguage = ParameterParser.getParameterMultipart(req, "work_language");
		final String programCode = ParameterParser.getParameterMultipart(req, "source_code");
		final String deadline = ParameterParser.getParameterMultipart(req, "work_deadline");
		final String xelPerBounty = ParameterParser.getParameterMultipart(req, "xel_per_bounty");
		final String xelPerPow = ParameterParser.getParameterMultipart(req, "xel_per_pow");
		final String bountyLimit = ParameterParser.getParameterMultipart(req, "bounty_limit");
		final String repetitionsStr = ParameterParser.getParameterMultipart(req, "repetitions");
		final Account account = ParameterParser.getSenderAccount(req);

		if (workTitle == null) return JSONResponses.MISSING_NAME;
        else if (workLanguage == null) return JSONResponses.MISSING_LANGUAGE;
        else if (programCode == null) return JSONResponses.MISSING_PROGAMCODE;
        else if (deadline == null) return JSONResponses.MISSING_DEADLINE;
        else if (xelPerBounty == null) return JSONResponses.MISSING_XEL_PER_BOUNTY;
        else if (xelPerPow == null) return JSONResponses.MISSING_XEL_PER_POW;
        else if (bountyLimit == null) return JSONResponses.MISSING_BOUNTYLIMIT;

		// Do some boundary checks
		final byte workLanguageByte = 0x01;

		int deadlineInt;
		try {
			deadlineInt = Integer.parseInt(deadline);
			if (deadlineInt > Constants.MAX_DEADLINE_FOR_WORK || deadlineInt < Constants.MIN_DEADLINE_FOR_WORK)
                return JSONResponses.INCORRECT_DEADLINE;

		} catch (final NumberFormatException e) {
			return JSONResponses.INCORRECT_DEADLINE;
		}

		int bountyLimitInt;
		try {
			bountyLimitInt = Integer.parseInt(bountyLimit);
			if ((bountyLimitInt > Constants.MAX_WORK_BOUNTY_LIMIT)
					|| (bountyLimitInt < Constants.MIN_WORK_BOUNTY_LIMIT)) return JSONResponses.MISSING_BOUNTYLIMIT;
		} catch (final NumberFormatException e) {
			return JSONResponses.MISSING_BOUNTYLIMIT;
		}

		int repetitions;
		try {
			repetitions = Integer.parseInt(repetitionsStr);
			if ((repetitions < 1)) return JSONResponses.MISSING_REPETITIONS;
		} catch (final NumberFormatException e) {
			return JSONResponses.MISSING_REPETITIONS;
		}

		long xelPerPowInt;
		try {
			xelPerPowInt = Long.parseLong(xelPerPow);
			if (xelPerPowInt < 0) return JSONResponses.INCORRECT_XEL_PER_POW;
		} catch (final NumberFormatException e) {
			return JSONResponses.INCORRECT_XEL_PER_POW;
		}

		long xelPerBountyInt;
		try {
			xelPerBountyInt = Long.parseLong(xelPerBounty);
			if (xelPerBountyInt < Constants.MIN_XEL_PER_BOUNTY) return JSONResponses.INCORRECT_XEL_PER_BOUNTY;
		} catch (final NumberFormatException e) {
			return JSONResponses.INCORRECT_XEL_PER_BOUNTY;
		}

		// More boundary checks
		final long amountlong = ParameterParser.getAmountNQT(req);

		if ((workTitle.length() > Constants.MAX_TITLE_LENGTH) || (workTitle.length() < 1))
            return JSONResponses.INCORRECT_WORK_NAME_LENGTH;

		final Attachment attachment = new Attachment.WorkCreation(workTitle, workLanguageByte,
				deadlineInt, bountyLimitInt, xelPerPowInt, repetitions, xelPerBountyInt);
		return this.createTransaction(req, account, 0, amountlong, attachment);
	}

}

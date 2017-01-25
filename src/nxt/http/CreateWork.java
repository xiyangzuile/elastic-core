package nxt.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import com.elastic.grammar.*;

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

		final Account account = ParameterParser.getSenderAccount(req);

		if (workTitle == null) {
			return JSONResponses.MISSING_NAME;
		} else if (workLanguage == null) {
			return JSONResponses.MISSING_LANGUAGE;
		} else if (programCode == null) {
			return JSONResponses.MISSING_PROGAMCODE;
		} else if (deadline == null) {
			return JSONResponses.MISSING_DEADLINE;
		} else if (xelPerBounty == null) {
			return JSONResponses.MISSING_XEL_PER_BOUNTY;
		} else if (xelPerPow == null) {
			return JSONResponses.MISSING_XEL_PER_POW;
		} else if (bountyLimit == null) {
			return JSONResponses.MISSING_BOUNTYLIMIT;
		}

		// Do some boundary checks
		final byte workLanguageByte = 0x01;

		int deadlineInt;
		try {
			deadlineInt = Integer.parseInt(deadline);
			if ((deadlineInt > Constants.MAX_DEADLINE_FOR_WORK) || (deadlineInt < Constants.MIN_DEADLINE_FOR_WORK)) {
				return JSONResponses.INCORRECT_DEADLINE;
			}

		} catch (final NumberFormatException e) {
			return JSONResponses.INCORRECT_DEADLINE;
		}

		int bountyLimitInt;
		try {
			bountyLimitInt = Integer.parseInt(bountyLimit);
			if ((bountyLimitInt > Constants.MAX_WORK_BOUNTY_LIMIT)
					|| (bountyLimitInt < Constants.MIN_WORK_BOUNTY_LIMIT)) {
				return JSONResponses.MISSING_BOUNTYLIMIT;
			}
		} catch (final NumberFormatException e) {
			return JSONResponses.MISSING_BOUNTYLIMIT;
		}

		long xelPerPowInt;
		try {
			xelPerPowInt = Long.parseLong(xelPerPow);
			if (xelPerPowInt < Constants.MIN_XEL_PER_POW) {
				return JSONResponses.INCORRECT_XEL_PER_POW;
			}
		} catch (final NumberFormatException e) {
			return JSONResponses.INCORRECT_XEL_PER_POW;
		}

		long xelPerBountyInt;
		try {
			xelPerBountyInt = Long.parseLong(xelPerBounty);
			if (xelPerBountyInt < Constants.MIN_XEL_PER_BOUNTY) {
				return JSONResponses.INCORRECT_XEL_PER_BOUNTY;
			}
		} catch (final NumberFormatException e) {
			return JSONResponses.INCORRECT_XEL_PER_BOUNTY;
		}

		// Now we parse the given sourcecode once and check for syntax errors
		// and for the number of input vars
		final byte[] byteCode = programCode.getBytes(StandardCharsets.UTF_8);
		final InputStream stream = new ByteArrayInputStream(byteCode);
		final ElasticPLParser parser = new ElasticPLParser(stream);
		long WCET = 0L;
		boolean stackExceeded = false;
		// Differentiate between different languages
		// if (workLanguageByte == 0x01) {
		try {
			parser.CompilationUnit();

			// Check worst case execution time
			final ASTCompilationUnit rootNode = ((ASTCompilationUnit) parser.rootNode());
			stackExceeded = RuntimeEstimator.exceedsStackUsage(rootNode);

			WCET = RuntimeEstimator.worstWeight(rootNode);
			if (stackExceeded) {
				return JSONResponses.INCORRECT_AST_RECURSION;
			} else {
				// all went well
			}
			if (WCET > Constants.MAX_WORK_WCET_TIME) {
				return JSONResponses.INCORRECT_EXECUTION_TIME;
			} else {
				// all went well
			}
			rootNode.reset();
		} catch (final Exception e) {
			e.printStackTrace(System.out);
			return JSONResponses.INCORRECT_SYNTAX;
		}
		// }

		// More boundary checks
		final long amountlong = ParameterParser.getAmountNQT(req);

		if ((workTitle.length() > Constants.MAX_TITLE_LENGTH) || (workTitle.length() < 1)) {
			return JSONResponses.INCORRECT_WORK_NAME_LENGTH;
		}

		final Attachment attachment = new Attachment.WorkCreation(workTitle, workLanguageByte,
				deadlineInt, bountyLimitInt, xelPerPowInt, xelPerBountyInt);
		return this.createTransaction(req, account, 0, amountlong, attachment);

	}

}

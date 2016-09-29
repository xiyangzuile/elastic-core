package nxt.http;

import static java.lang.Integer.parseInt;
import static nxt.http.JSONResponses.INCORRECT_DEADLINE;
import static nxt.http.JSONResponses.INCORRECT_EXECUTION_TIME;
import static nxt.http.JSONResponses.INCORRECT_SYNTAX;
import static nxt.http.JSONResponses.INCORRECT_WORK_NAME_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_XEL_PER_POW;
import static nxt.http.JSONResponses.INCORRECT_XEL_PER_BOUNTY;
import static nxt.http.JSONResponses.MISSING_XEL_PER_POW;
import static nxt.http.JSONResponses.MISSING_XEL_PER_BOUNTY;
import static nxt.http.JSONResponses.MISSING_BOUNTYLIMIT;
import static nxt.http.JSONResponses.MISSING_DEADLINE;
import static nxt.http.JSONResponses.MISSING_LANGUAGE;
import static nxt.http.JSONResponses.MISSING_NAME;
import static nxt.http.JSONResponses.MISSING_PROGAMCODE;
import static nxt.http.JSONResponses.INCORRECT_AST_RECURSION;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONStreamAware;

import elastic.pl.interpreter.ASTCompilationUnit;
import elastic.pl.interpreter.ElasticPLParser;
import elastic.pl.interpreter.RuntimeEstimator;
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
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

		String workTitle = ParameterParser.getParameterMultipart(req, "work_title");
		String workLanguage = ParameterParser.getParameterMultipart(req, "work_language"); 
		String programCode = ParameterParser.getParameterMultipart(req, "source_code");
		String deadline = ParameterParser.getParameterMultipart(req, "work_deadline");
		String xelPerBounty = ParameterParser.getParameterMultipart(req, "xel_per_bounty");
		String xelPerPow = ParameterParser.getParameterMultipart(req, "xel_per_pow");
		String bountyLimit = ParameterParser.getParameterMultipart(req, "bounty_limit");
		
		Account account = ParameterParser.getSenderAccount(req);

		if (workTitle == null) {
			return MISSING_NAME;
		} else if (workLanguage == null) {
			return MISSING_LANGUAGE;
		} else if (programCode == null) {
			return MISSING_PROGAMCODE;
		} else if (deadline == null) {
			return MISSING_DEADLINE;
		} else if (xelPerBounty == null) {
			return MISSING_XEL_PER_BOUNTY;
		} else if (xelPerPow == null) {
			return MISSING_XEL_PER_POW;
		} else if (bountyLimit == null) {
				return MISSING_BOUNTYLIMIT;
		}

		// Do some boundary checks
		byte workLanguageByte = 0x01;

		int deadlineInt;
		try {
			deadlineInt = parseInt(deadline);
			if(deadlineInt > Constants.MAX_DEADLINE_FOR_WORK || deadlineInt < Constants.MIN_DEADLINE_FOR_WORK){
				return INCORRECT_DEADLINE;
        	}
			
		} catch (NumberFormatException e) {
			return INCORRECT_DEADLINE;
		}

		int bountyLimitInt;
		try {
			bountyLimitInt = parseInt(bountyLimit);
			if (bountyLimitInt > Constants.MAX_WORK_BOUNTY_LIMIT || bountyLimitInt < Constants.MIN_WORK_BOUNTY_LIMIT ) {
				return MISSING_BOUNTYLIMIT;
			}
		} catch (NumberFormatException e) {
			return MISSING_BOUNTYLIMIT;
		}

		long xelPerPowInt;
		try {
			xelPerPowInt = Long.parseLong(xelPerPow);
			if (xelPerPowInt < Constants.MIN_XEL_PER_POW) {
				return INCORRECT_XEL_PER_POW;
			}
		} catch (NumberFormatException e) {
			return INCORRECT_XEL_PER_POW;
		}
		
		long xelPerBountyInt;
		try {
			xelPerBountyInt = Long.parseLong(xelPerBounty);
			if (xelPerBountyInt < Constants.MIN_XEL_PER_BOUNTY) {
				return INCORRECT_XEL_PER_BOUNTY;
			}
		} catch (NumberFormatException e) {
			return INCORRECT_XEL_PER_BOUNTY;
		}

		// Now we parse the given sourcecode once and check for syntax errors
		// and for the number of input vars
		byte[] byteCode = programCode.getBytes(StandardCharsets.UTF_8);
		InputStream stream = new ByteArrayInputStream(byteCode);
		ElasticPLParser parser = new ElasticPLParser(stream);
		long WCET = 0L;
		boolean stackExceeded = false;
		// Differentiate between different languages
		if (workLanguageByte == 0x01) {
			try {
				parser.CompilationUnit();

				// Check worst case execution time
				ASTCompilationUnit rootNode = ((ASTCompilationUnit) parser.rootNode());
				stackExceeded = RuntimeEstimator.exceedsStackUsage(rootNode);
				
				WCET = RuntimeEstimator.worstWeight(rootNode);
				if (stackExceeded) {
					return INCORRECT_AST_RECURSION;
				}else{
					// all went well
				}
				if (WCET >  Constants.MAX_WORK_WCET_TIME) {
					return INCORRECT_EXECUTION_TIME;
				}else{
					// all went well
				}
				rootNode.reset();
			} catch (Exception e) {
				e.printStackTrace(System.out);
				return INCORRECT_SYNTAX;
			}
		}

		

		// More boundary checks
		long amountlong = ParameterParser.getAmountNQT(req);

		if (workTitle.length() > Constants.MAX_TITLE_LENGTH || workTitle.length() < 1) {
			return INCORRECT_WORK_NAME_LENGTH;
		}

		Attachment attachment = new Attachment.WorkCreation(workTitle, workLanguageByte, programCode.getBytes(),
				deadlineInt, bountyLimitInt, xelPerPowInt, xelPerBountyInt);
		return createTransaction(req, account, 0, amountlong, attachment);

	}

}

package nxt.http;

import static java.lang.Integer.parseInt;
import static nxt.http.JSONResponses.INCORRECT_DEADLINE;
import static nxt.http.JSONResponses.INCORRECT_WORK_LANGUAGE;
import static nxt.http.JSONResponses.INCORRECT_WORK_NAME_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_AMOUNT;
import static nxt.http.JSONResponses.INCORRECT_INPUT_NUMBER;
import static nxt.http.JSONResponses.INCORRECT_XEL_PER_POW;
import static nxt.http.JSONResponses.INCORRECT_SYNTAX;
import static nxt.http.JSONResponses.INCORRECT_EXECUTION_TIME;
import static nxt.http.JSONResponses.MISSING_DEADLINE;
import static nxt.http.JSONResponses.MISSING_LANGUAGE;
import static nxt.http.JSONResponses.MISSING_NAME;
import static nxt.http.JSONResponses.MISSING_BOUNTYLIMIT;
import static nxt.http.JSONResponses.MISSING_NUMBER_INPUTVARS;
import static nxt.http.JSONResponses.MISSING_NUMBER_OUTPUTVARS;
import static nxt.http.JSONResponses.MISSING_PROGAMCODE;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;

import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.NxtException;
import nxt.WorkLogicManager;

import org.json.simple.JSONStreamAware;

import ElasticPL.ASTCompilationUnit;
import ElasticPL.ElasticPLParser;
import ElasticPL.RuntimeEstimator;


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
		String amount_spent = ParameterParser.getParameterMultipart(req, "amountNQT");
		String bountyLimit = ParameterParser.getParameterMultipart(req, "bountyLimit");
		String xelPerPow = ParameterParser.getParameterMultipart(req, "xel_per_pow");

		Account account = ParameterParser.getSenderAccount(req);

		if (workTitle == null) {
			return MISSING_NAME;
		} else if (workLanguage == null) {
			return MISSING_LANGUAGE;
		} else if (programCode == null) {
			return MISSING_PROGAMCODE;
		} else if (deadline == null) {
			return MISSING_DEADLINE;
		} else if (bountyLimit == null) {
			return MISSING_BOUNTYLIMIT;
		}

		// Do some boundary checks
		byte workLanguageByte;
		try {
			workLanguageByte = WorkLogicManager.getInstance().getLanguageByte(workLanguage);
			if (WorkLogicManager.getInstance().checkWorkLanguage(workLanguageByte) == false) {
				return INCORRECT_WORK_LANGUAGE;
			}
		} catch (NumberFormatException e) {
			return INCORRECT_WORK_LANGUAGE;
		}

		int deadlineInt;
		try {
			deadlineInt = parseInt(deadline);
			if (WorkLogicManager.getInstance().checkDeadline(deadlineInt) == false) {
				return INCORRECT_DEADLINE;
			}
		} catch (NumberFormatException e) {
			return INCORRECT_DEADLINE;
		}

		int bountyLimitInt;
		try {
			bountyLimitInt = parseInt(bountyLimit);
			if (WorkLogicManager.getInstance().checkDeadline(deadlineInt) == false) {
				return MISSING_BOUNTYLIMIT;
			}
		} catch (NumberFormatException e) {
			return MISSING_BOUNTYLIMIT;
		}

		long xelPerPowInt;
		try {
			xelPerPowInt = Long.parseLong(xelPerPow);
			if (WorkLogicManager.getInstance().isPowPriceCorrect(xelPerPowInt) == false) {
				return INCORRECT_XEL_PER_POW;
			}
		} catch (NumberFormatException e) {
			return INCORRECT_XEL_PER_POW;
		}

		// Now we parse the given sourcecode once and check for syntax errors
		// and for the number of input vars
		byte[] byteCode = programCode.getBytes(StandardCharsets.UTF_8);
		InputStream stream = new ByteArrayInputStream(byteCode);
		ElasticPLParser parser = new ElasticPLParser(stream);
		long WCET = 0L;
		Byte numberInputVars = 0;

		// Differentiate between different languages
		if (workLanguageByte == 0x01) {
			try {
				parser.CompilationUnit();

				// Check worst case execution time
				ASTCompilationUnit rootNode = ((ASTCompilationUnit) parser.jjtree.rootNode());
				WCET = RuntimeEstimator.worstWeight(rootNode);
				if (WCET > WorkLogicManager.getInstance().maxWorstCaseExecutionTime()) {
					return INCORRECT_EXECUTION_TIME;
				}else{
					// all went well
				}

				rootNode.reset();
				numberInputVars = (byte) ((ASTCompilationUnit) parser.jjtree.rootNode()).getRandomIntNumber();
			} catch (Exception e) {
				e.printStackTrace(System.out);
				return INCORRECT_SYNTAX;
			}
		}

		// Differentiate between different languages
		if (workLanguageByte == 0x01) {
			if (numberInputVars < WorkLogicManager.getInstance().getMinNumberInputInts()
					|| numberInputVars > WorkLogicManager.getInstance().getMaxNumberInputInts()) {
				return INCORRECT_INPUT_NUMBER;
			}
		}

		// More boundary checks
		long amount;
		try {
			amount = Long.parseLong(amount_spent);
		} catch (NumberFormatException e) {
			return INCORRECT_AMOUNT;
		}

		if (workTitle.length() > Constants.MAX_TITLE_LENGTH || workTitle.length() < 1) {
			return INCORRECT_WORK_NAME_LENGTH;
		}

		Attachment attachment = new Attachment.WorkCreation(workTitle, workLanguageByte, programCode.getBytes(),
				numberInputVars, deadlineInt, bountyLimitInt, xelPerPowInt, 60);
		return createTransaction(req, account, 0, amount, attachment);

	}

}

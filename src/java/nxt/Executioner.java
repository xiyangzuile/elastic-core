package nxt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;

import elastic.pl.interpreter.ASTCompilationUnit;
import elastic.pl.interpreter.ElasticPLParser;
import elastic.pl.interpreter.ParseException;
import elastic.pl.interpreter.RuntimeEstimator;

public class Executioner {

	private static final Object LOCK = new Object();

	public static void checkSyntax(final byte[] code) throws ParseException {
		synchronized (Executioner.LOCK) {
			final InputStream stream = new ByteArrayInputStream(code);
			final ElasticPLParser parser = new ElasticPLParser(stream);
			parser.CompilationUnit();
			final ASTCompilationUnit rootNode = ((ASTCompilationUnit) parser.rootNode());
			rootNode.reset();
			final boolean stackExceeded = RuntimeEstimator.exceedsStackUsage(rootNode);
			if (stackExceeded) {
				throw new ParseException("AST tree secursion too high");
			}

			final long WCET = RuntimeEstimator.worstWeight(rootNode);

			if (WCET > Constants.MAX_WORK_WCET_TIME) {
				throw new ParseException("WCET too high");
			}

			rootNode.reset();
		}
	}

	public static boolean executeBountyHooks(final byte[] code, final int inputs[]) throws ParseException {
		synchronized (Executioner.LOCK) {
			final InputStream stream = new ByteArrayInputStream(code);
			final ElasticPLParser parser = new ElasticPLParser(stream);
			parser.CompilationUnit();

			((ASTCompilationUnit) parser.rootNode()).reset();
			((ASTCompilationUnit) parser.rootNode()).fillGivenIntNumber(inputs);
			((ASTCompilationUnit) parser.rootNode()).interpret();

			final boolean verifyB = ((ASTCompilationUnit) parser.rootNode()).verifyBounty();

			((ASTCompilationUnit) parser.rootNode()).reset();
			return verifyB;
		}
	}

	public static ASTCompilationUnit.POW_CHECK_RESULT executeProofOfWork(final byte[] code, final int inputs[],
			final BigInteger target_pow, final BigInteger soft_unblock_pow) throws ParseException {
		synchronized (Executioner.LOCK) {
			final InputStream stream = new ByteArrayInputStream(code);
			final ElasticPLParser parser = new ElasticPLParser(stream);
			parser.CompilationUnit();

			((ASTCompilationUnit) parser.rootNode()).reset();
			((ASTCompilationUnit) parser.rootNode()).fillGivenIntNumber(inputs);
			((ASTCompilationUnit) parser.rootNode()).interpret();

			final ASTCompilationUnit.POW_CHECK_RESULT verifyPow = ((ASTCompilationUnit) parser.rootNode())
					.verifyPOW(target_pow, soft_unblock_pow);

			((ASTCompilationUnit) parser.rootNode()).reset();
			return verifyPow;
		}
	}

}
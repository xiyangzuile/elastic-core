package nxt;



import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;

import elastic.pl.interpreter.ASTCompilationUnit;
import elastic.pl.interpreter.ElasticPLParser;
import elastic.pl.interpreter.ParseException;
import elastic.pl.interpreter.RuntimeEstimator;



public class Executioner{
	
	private static final Object LOCK = new Object();
	


	public static void checkSyntax(byte[] code) throws ParseException {
		synchronized(LOCK){
			InputStream stream = new ByteArrayInputStream(code);
			ElasticPLParser parser = new ElasticPLParser(stream);
			parser.CompilationUnit();
			ASTCompilationUnit rootNode = ((ASTCompilationUnit) parser.rootNode());
			rootNode.reset();
			
			long WCET = RuntimeEstimator.worstWeight(rootNode);
			boolean stackExceeded = RuntimeEstimator.exceedsStackUsage(rootNode);
			if(WCET>Constants.MAX_WORK_WCET_TIME){
				throw new ParseException("WCET too high");
			}
			if(stackExceeded){
				throw new ParseException("AST tree secursion too high");
			}			
			rootNode.reset();
		}
	}

	public static boolean executeBountyHooks(byte[] code, int inputs[]) throws ParseException {
		synchronized(LOCK){
			InputStream stream = new ByteArrayInputStream(code);
			ElasticPLParser parser = new ElasticPLParser(stream);
			parser.CompilationUnit();
			
			((ASTCompilationUnit) parser.rootNode()).reset();
			((ASTCompilationUnit) parser.rootNode()).fillGivenIntNumber(inputs);
			((ASTCompilationUnit) parser.rootNode()).interpret();
			
			
			boolean verifyB = ((ASTCompilationUnit) parser.rootNode()).verifyBounty();
			
			((ASTCompilationUnit) parser.rootNode()).reset();
			return verifyB;
		}
	}

	public static ASTCompilationUnit.POW_CHECK_RESULT executeProofOfWork(byte[] code, int inputs[], BigInteger target_pow, BigInteger soft_unblock_pow) throws ParseException{
		synchronized(LOCK){
			InputStream stream = new ByteArrayInputStream(code);
			ElasticPLParser parser = new ElasticPLParser(stream);
			parser.CompilationUnit();
			
			((ASTCompilationUnit) parser.rootNode()).reset();
			((ASTCompilationUnit) parser.rootNode()).fillGivenIntNumber(inputs);
			((ASTCompilationUnit) parser.rootNode()).interpret();
			
			ASTCompilationUnit.POW_CHECK_RESULT verifyPow = ((ASTCompilationUnit) parser.rootNode()).verifyPOW(target_pow, soft_unblock_pow);
			
			((ASTCompilationUnit) parser.rootNode()).reset();
			return verifyPow;
		}
	}

	
}
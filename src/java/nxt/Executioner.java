package nxt;



import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;


import ElasticPL.ASTCompilationUnit;
import ElasticPL.ElasticPLParser;
import ElasticPL.ParseException;
import ElasticPL.RuntimeEstimator;



public class Executioner{
	
	private static final Object LOCK = new Object();
	
	public enum POW_CHECK_RESULT
	{
		OK,
		SOFT_UNBLOCKED,
		ERROR
	};

	public static void checkSyntax(byte[] code) throws ParseException {
		synchronized(LOCK){
			InputStream stream = new ByteArrayInputStream(code);
			ElasticPLParser parser = new ElasticPLParser(stream);
			parser.CompilationUnit();
			ASTCompilationUnit rootNode = ((ASTCompilationUnit) parser.jjtree.rootNode());
			rootNode.reset();
			
			long WCET = RuntimeEstimator.worstWeight(rootNode);
			
			if(WCET>Constants.MAX_WORK_WCET_TIME){
				throw new ParseException("WCET too high");
			}
						
			rootNode.reset();
		}
	}

	public static boolean executeBountyHooks(byte[] code, int inputs[]) throws ParseException {
		synchronized(LOCK){
			InputStream stream = new ByteArrayInputStream(code);
			ElasticPLParser parser = new ElasticPLParser(stream);
			parser.CompilationUnit();
			
			((ASTCompilationUnit) parser.jjtree.rootNode()).reset();
			((ASTCompilationUnit) parser.jjtree.rootNode()).fillGivenIntNumber(inputs);
			((ASTCompilationUnit) parser.jjtree.rootNode()).interpret();
			
			
			boolean verifyB = ((ASTCompilationUnit) parser.jjtree.rootNode()).verifyBounty();
			
			((ASTCompilationUnit) parser.jjtree.rootNode()).reset();
			return verifyB;
		}
	}

	public static POW_CHECK_RESULT executeProofOfWork(byte[] code, int inputs[], BigInteger target_pow, BigInteger soft_unblock_pow) throws ParseException{
		synchronized(LOCK){
			InputStream stream = new ByteArrayInputStream(code);
			ElasticPLParser parser = new ElasticPLParser(stream);
			parser.CompilationUnit();
			
			((ASTCompilationUnit) parser.jjtree.rootNode()).reset();
			((ASTCompilationUnit) parser.jjtree.rootNode()).fillGivenIntNumber(inputs);
			((ASTCompilationUnit) parser.jjtree.rootNode()).interpret();
			
			POW_CHECK_RESULT verifyPow = ((ASTCompilationUnit) parser.jjtree.rootNode()).verifyPOW(target_pow, soft_unblock_pow);
			
			((ASTCompilationUnit) parser.jjtree.rootNode()).reset();
			return verifyPow;
		}
	}

	
}
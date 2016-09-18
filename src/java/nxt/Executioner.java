package nxt;

import static nxt.http.JSONResponses.INCORRECT_EXECUTION_TIME;
import static nxt.http.JSONResponses.INCORRECT_SYNTAX;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import ElasticPL.ASTCompilationUnit;
import ElasticPL.ElasticPLParser;
import ElasticPL.ParseException;
import ElasticPL.RuntimeEstimator;



public class Executioner{
	private int numberOfInputs;
	private String script;
	private long identifier;
	
	public enum POW_CHECK_RESULT
	{
		OK,
		SOFT_UNBLOCKED,
		ERROR
	};
	
	
	InputStream stream = null;
	ElasticPLParser parser = null;

	public byte[] byteHash(int randomInput[],int output[]) throws NoSuchAlgorithmException {
		
		

		MessageDigest m = MessageDigest.getInstance("SHA-256");
		m.reset();
		ByteBuffer byteBuffer = ByteBuffer.allocate(output.length * 4);
		IntBuffer intBuffer = byteBuffer.asIntBuffer();
		intBuffer.put(output);
		ByteBuffer byteBufferIn = ByteBuffer.allocate(randomInput.length * 4);
		IntBuffer intInBuffer = byteBufferIn.asIntBuffer();
		intBuffer.put(output);

		byte[] array = byteBuffer.array();
		byte[] array2 = byteBufferIn.array();
		m.update(array);
		m.update(array2);
		byte[] digest = m.digest();
		return digest;
	}

	
	public Executioner(String code, long identifier) throws ParseException {
		// id
		this.identifier = identifier;
		this.script = code;
		
		stream = new ByteArrayInputStream(code.getBytes());
		parser = new ElasticPLParser(stream);
		// Compile program, should work as syntax and semantic tests went through in TX validation
		// TODO FIXME, Compilation Unit does not crash properly when code is wrong
		parser.CompilationUnit();
	}
	

	public boolean executeBountyHooks(int inputs[]) {
		
		
		((ASTCompilationUnit) parser.jjtree.rootNode()).reset();
		((ASTCompilationUnit) parser.jjtree.rootNode()).fillGivenIntNumber(inputs);
		((ASTCompilationUnit) parser.jjtree.rootNode()).interpret();
		
		boolean verifyB = ((ASTCompilationUnit) parser.jjtree.rootNode()).verifyBounty();
		return verifyB;
	}

	public POW_CHECK_RESULT executeProofOfWork(int inputs[], BigInteger target_pow, BigInteger soft_unblock_pow){
		((ASTCompilationUnit) parser.jjtree.rootNode()).reset();
		((ASTCompilationUnit) parser.jjtree.rootNode()).fillGivenIntNumber(inputs);
		((ASTCompilationUnit) parser.jjtree.rootNode()).interpret();
		
		POW_CHECK_RESULT verifyPow = ((ASTCompilationUnit) parser.jjtree.rootNode()).verifyPOW(target_pow, soft_unblock_pow);
		return verifyPow;
	}

	
	public long getIdentifier() {
		return this.identifier;
	}
}
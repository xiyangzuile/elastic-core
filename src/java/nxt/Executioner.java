package nxt;

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



public class Executioner{
	private int numberOfInputs;
	private String script;
	private long identifier;
	
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

	
	public Executioner(String code, int numberOfInputs, long identifier) throws ParseException {
		// id
		this.identifier = identifier;

		// remember number of inputs and outputs
		this.numberOfInputs = numberOfInputs;
		this.script = code;
		
		stream = new ByteArrayInputStream(Ascii85.decode(code));
		parser = new ElasticPLParser(stream);
		
		// Compile program, should work as syntax and semantic tests went through in TX validation
		parser.CompilationUnit();

	}
	

	public boolean executeBountyHooks(int inputs[]) {
		
		
		((ASTCompilationUnit) parser.jjtree.rootNode()).reset();
		((ASTCompilationUnit) parser.jjtree.rootNode()).fillGivenIntNumber(inputs);
		((ASTCompilationUnit) parser.jjtree.rootNode()).interpret();
		
		boolean verifyB = ((ASTCompilationUnit) parser.jjtree.rootNode()).verifyBounty();
		return verifyB;
	}

	public boolean executeProofOfWork(int inputs[], BigInteger target_pow){
		((ASTCompilationUnit) parser.jjtree.rootNode()).reset();
		((ASTCompilationUnit) parser.jjtree.rootNode()).fillGivenIntNumber(inputs);
		((ASTCompilationUnit) parser.jjtree.rootNode()).interpret();
		
		boolean verifyPow = ((ASTCompilationUnit) parser.jjtree.rootNode()).verifyPOW(target_pow);
		return verifyPow;
	}

	
	public long getIdentifier() {
		return this.identifier;
	}
}
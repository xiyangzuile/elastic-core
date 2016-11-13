package StupidTests;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.nevec.rjm.*;

public class Retarget {

	
	
	public static void main(String[] args) {
		
		// x^(y^3)=z
		// y^3 = log(z)/log(x)
		// y = (y^3)^(1/3) 
		
		// Testing the retarget
		BigInteger targetBigint = BigInteger.valueOf(9);
		BigDecimal target = new BigDecimal(targetBigint);
		
		BigInteger least_possible_targetBigint = new BigInteger("000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
		BigDecimal least_possible_target = new BigDecimal(least_possible_targetBigint);
		
		BigDecimal quotient = least_possible_target.divide(target,RoundingMode.FLOOR);
		quotient = BigDecimalMath.root(3, quotient);

		System.out.println("Quotient: " + quotient.toString());

		System.out.println("Start Target: " +  target.toString());

		target=target.multiply(quotient);
		System.out.println("Intermediate Target 1: " + target.toString());
		
		target=target.multiply(quotient);
		
		System.out.println("Intermediate Target 2: " +  target.toString());
		target=target.multiply(quotient);
		System.out.println("Final Target: " +  target.toBigInteger().toString(16));
		BigDecimal diff = least_possible_target.subtract(target);
		System.out.println("Diff (must be positive and small): " + diff.toString());

	}
}

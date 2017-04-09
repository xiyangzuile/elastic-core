/******************************************************************************
 * Copyright © 2013-2016 The XEL Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;

import nxt.Nxt;
import nxt.Transaction;
import nxt.util.Convert;

public final class SignTransactions {

	public static void main(final String[] args) {
		try {
			if (args.length != 2) {
				System.out.println(
						"Usage: SignTransactions <unsigned transaction bytes file> <signed transaction bytes file>");
				System.exit(1);
			}
			final File unsigned = new File(args[0]);
			if (!unsigned.exists()) {
				System.out.println("File not found: " + unsigned.getAbsolutePath());
				System.exit(1);
			}
			final File signed = new File(args[1]);
			if (signed.exists()) {
				System.out.println("File already exists: " + signed.getAbsolutePath());
				System.exit(1);
			}
			String secretPhrase = null;
			final Console console = System.console();
			if (console == null) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
					secretPhrase = reader.readLine();
				}
			} else {
				char[] chararr = null;
				chararr = console.readPassword("Secret phrase: ");
				if (chararr != null) {
					secretPhrase = new String(chararr);
				}
			}
			if (secretPhrase == null) {
				System.err.println("No secret phrase given!");
				return;
			}
			int n = 0;
			try (BufferedReader reader = new BufferedReader(new FileReader(unsigned));
					BufferedWriter writer = new BufferedWriter(new FileWriter(signed))) {
				String line;
				while ((line = reader.readLine()) != null) {
					final byte[] transactionBytes = Convert.parseHexString(line);
					final Transaction.Builder builder = Nxt.newTransactionBuilder(transactionBytes);
					final Transaction transaction = builder.build(secretPhrase);
					writer.write(Convert.toHexString(transaction.getBytes()));
					writer.newLine();
					n += 1;
				}
			}
			System.out.println("Signed " + n + " transactions");
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

}

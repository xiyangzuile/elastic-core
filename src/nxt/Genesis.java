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

package nxt;

import nxt.crypto.Crypto;
import nxt.util.Logger;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class Genesis {



	public static final long FUCKED_TX_ID = 4823535488705771609L;
	public static final long REDEEM_ID = Long.parseUnsignedLong("10687454573350862535");
	public static final String REDEEM_ID_PUBKEY = "15d039ed644401856cf294e475cc9b9ed1d8abbf8393435f89f4ab484faa0e2e";


	public static final long[] GENESIS_RECIPIENTS = new long[]{Genesis.REDEEM_ID};

	public static final int[] GENESIS_AMOUNTS = new int[]{100000000};




	/* VOLATILE PART BEGIN */
    public static final byte[] CREATOR_PUBLIC_KEY = new byte[]{(byte) 0xad, (byte) 0x32, (byte) 0x35, (byte) 0x04, (byte) 0x55, (byte) 0xad, (byte) 0xe2, (byte) 0x6b, (byte) 0x9d, (byte) 0xc5, (byte) 0xc8, (byte) 0xa2, (byte) 0xf9, (byte) 0x13, (byte) 0x13, (byte) 0xa4, (byte) 0x72, (byte) 0x4a, (byte) 0x18, (byte) 0x53, (byte) 0xa9, (byte) 0x9e, (byte) 0x39, (byte) 0xaa, (byte) 0x07, (byte) 0x0b, (byte) 0xaf, (byte) 0x4a, (byte) 0x9e, (byte) 0xc9, (byte) 0xa5, (byte) 0x3f,};
    public static final long CREATOR_ID = -1340397127567038869L;
    public static final byte[][] GENESIS_SIGNATURES = new byte[][] {
            new byte[]{-43, 75, 34, -29, -72, -4, 7, -74, -101, -122, 63, 104, -8, -104, 105, -77, -81, -99, -49, 112, 35, 32, -40, -77, -56, 78, -59, -88, -64, -38, -54, 9, -115, 91, -59, 55, -119, 42, 126, -73, -6, -91, 125, 28, 102, -33, 3, 13, 68, -3, 57, 10, 72, -85, -128, -117, 17, -101, 48, -72, 30, -123, -75, -118},
    }
            ;
    public static final long GENESIS_BLOCK_ID = -409894504513302943L;
    public static final byte[] GENESIS_GENERATION_SIGNATURE = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,};
    public static final byte[] GENESIS_BLOCK_SIGNATURE = new byte[]{(byte) 0x75, (byte) 0xc5, (byte) 0xad, (byte) 0xcf, (byte) 0x5f, (byte) 0xcc, (byte) 0x52, (byte) 0xab, (byte) 0x66, (byte) 0x74, (byte) 0xa7, (byte) 0x9f, (byte) 0xb6, (byte) 0x68, (byte) 0x16, (byte) 0x81, (byte) 0x40, (byte) 0x0d, (byte) 0x80, (byte) 0x90, (byte) 0xca, (byte) 0x88, (byte) 0xc1, (byte) 0x27, (byte) 0xd7, (byte) 0x99, (byte) 0xfe, (byte) 0x79, (byte) 0x27, (byte) 0x20, (byte) 0x7d, (byte) 0x08, (byte) 0xb5, (byte) 0x32, (byte) 0x12, (byte) 0x76, (byte) 0x73, (byte) 0x11, (byte) 0x3a, (byte) 0x08, (byte) 0xc7, (byte) 0xd8, (byte) 0xb6, (byte) 0x1c, (byte) 0x54, (byte) 0x1d, (byte) 0x5c, (byte) 0x02, (byte) 0x6a, (byte) 0xec, (byte) 0x75, (byte) 0x0e, (byte) 0x26, (byte) 0xa5, (byte) 0x9a, (byte) 0x8d, (byte) 0x73, (byte) 0x70, (byte) 0xde, (byte) 0xea, (byte) 0x9c, (byte) 0x3a, (byte) 0x76, (byte) 0x3c,};

	/* VOLATILE PART END */

	private Genesis() {
	} // never

    private static BlockImpl getGenesis(String genesisSecretKey) throws Exception {
        byte[] genesisAccount = Crypto.getPublicKey(genesisSecretKey);
        long id = Account.getId(genesisAccount);
        System.out.print("public static final byte[] CREATOR_PUBLIC_KEY = { ");
        for (byte c : genesisAccount) System.out.format("(byte)0x%02x, ", c);
        System.out.println("};");

        System.out.println("public static final long CREATOR_ID = " + Long.toString(id) + "L;");


        final List<TransactionImpl> transactions = new ArrayList<>();
        for (int i = 0; i < Genesis.GENESIS_RECIPIENTS.length; i++) {
            final TransactionImpl transaction = new TransactionImpl.BuilderImpl((byte) 0,
                    Genesis.CREATOR_PUBLIC_KEY, Genesis.GENESIS_AMOUNTS[i] * Constants.ONE_NXT, 0, (short) 0,
                    Attachment.ORDINARY_PAYMENT).timestamp(0).recipientId(Genesis.GENESIS_RECIPIENTS[i])
                    .height(0).ecBlockHeight(0).ecBlockId(0)
                    .build(genesisSecretKey);
            transactions.add(transaction);
        }
        transactions.sort(Comparator.comparingLong(Transaction::getId));

            String signatures = "{\n";
            for (int i = 0; i < Genesis.GENESIS_RECIPIENTS.length; i++) {

                signatures += "{";
                for (int s = 0; s < transactions.get(i).getSignature().length; ++s) {
                    signatures += String.valueOf((int) transactions.get(i)
                            .getSignature()[s]);
                    if (s < transactions.get(i).getSignature().length - 1) signatures += " ,";
                }
                signatures += "},\n";

            }
            signatures += "}\n";
            System.out.println("public static final byte[][] GENESIS_SIGNATURES = new byte[][] " + signatures + ";");

            transactions.sort(Comparator.comparingLong((transaction1) -> transaction1.getId()));
            MessageDigest digest = Crypto.sha256();
            for (Transaction transaction : transactions) digest.update(transaction.getBytes());


        return new BlockImpl(0, 0, 0,
                Constants.MAX_BALANCE_NQT, 0, transactions.size() * 128,
                digest.digest(), genesisAccount, new byte[32],
                new byte[32], transactions, genesisSecretKey, Constants.least_possible_target);

    }
	public static void mineGenesis() throws Exception {
	    try{
		    String genesisSecretKey = "Test123";

		    BlockImpl genesisBlock = getGenesis(genesisSecretKey);

            System.out.println("public static final long GENESIS_BLOCK_ID = " + Long.toString(genesisBlock.getId()) + "L;");


            System.out.print("public static final byte[] GENESIS_GENERATION_SIGNATURE = { ");
            for(byte c :  genesisBlock.getGenerationSignature()) System.out.format("(byte)0x%02x, ", c);
            System.out.println("};");

            System.out.print("public static final byte[] GENESIS_BLOCK_SIGNATURE = { ");
            for(byte c :  genesisBlock.getBlockSignature()) System.out.format("(byte)0x%02x, ", c);
            System.out.println("};");



			Logger.logMessage("VERIFYBLOCKSIGNATURE: " + String.valueOf(genesisBlock.verifyBlockSignature()));
            Logger.logMessage("VERIFYGENERATIONSIGNATURE: " + String.valueOf(genesisBlock.verifyGenerationSignature()));

		} catch (NxtException.ValidationException e) {
			Logger.logMessage(e.getMessage());
			throw new RuntimeException(e.toString(), e);
		}
	}

}

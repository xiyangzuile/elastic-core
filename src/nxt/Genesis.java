/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
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


	public static final long[] GENESIS_RECIPIENTS = { Genesis.REDEEM_ID };

	public static final int[] GENESIS_AMOUNTS = { 100000000 };




	/* VOLATILE PART BEGIN */
    public static final byte[] CREATOR_PUBLIC_KEY = { (byte)0xad, (byte)0x32, (byte)0x35, (byte)0x04, (byte)0x55, (byte)0xad, (byte)0xe2, (byte)0x6b, (byte)0x9d, (byte)0xc5, (byte)0xc8, (byte)0xa2, (byte)0xf9, (byte)0x13, (byte)0x13, (byte)0xa4, (byte)0x72, (byte)0x4a, (byte)0x18, (byte)0x53, (byte)0xa9, (byte)0x9e, (byte)0x39, (byte)0xaa, (byte)0x07, (byte)0x0b, (byte)0xaf, (byte)0x4a, (byte)0x9e, (byte)0xc9, (byte)0xa5, (byte)0x3f, };
    public static final long CREATOR_ID = -1340397127567038869L;
    public static final byte[][] GENESIS_SIGNATURES = new byte[][] {
            {110 ,-60 ,111 ,50 ,-58 ,-7 ,-6 ,-85 ,-11 ,111 ,-64 ,-76 ,-25 ,81 ,127 ,98 ,42 ,-74 ,86 ,-44 ,-81 ,2 ,-58 ,-70 ,-44 ,-77 ,-21 ,108 ,114 ,94 ,-11 ,5 ,30 ,-91 ,-62 ,94 ,-35 ,-123 ,1 ,-66 ,-112 ,104 ,91 ,-47 ,17 ,-91 ,-59 ,78 ,62 ,-87 ,-104 ,24 ,-35 ,-17 ,-9 ,-59 ,68 ,-28 ,29 ,-21 ,-79 ,-54 ,11 ,94},
    }
            ;
    public static final long GENESIS_BLOCK_ID = 6438387248299498698L;
    public static final byte[] GENESIS_GENERATION_SIGNATURE = { (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, };
    public static final byte[] GENESIS_BLOCK_SIGNATURE = { (byte)0x92, (byte)0x9a, (byte)0x3a, (byte)0x85, (byte)0xb2, (byte)0x46, (byte)0xb8, (byte)0x7a, (byte)0x35, (byte)0x1e, (byte)0x12, (byte)0x7b, (byte)0xb2, (byte)0x15, (byte)0xd4, (byte)0xab, (byte)0xe9, (byte)0x43, (byte)0x94, (byte)0x09, (byte)0x2c, (byte)0xeb, (byte)0x98, (byte)0x37, (byte)0x2f, (byte)0xa0, (byte)0x47, (byte)0x2a, (byte)0x09, (byte)0x43, (byte)0x07, (byte)0x00, (byte)0xcd, (byte)0x93, (byte)0xb4, (byte)0x35, (byte)0xc3, (byte)0xd7, (byte)0x94, (byte)0x84, (byte)0x4a, (byte)0x73, (byte)0xa4, (byte)0x10, (byte)0x7d, (byte)0xf0, (byte)0x0a, (byte)0x5a, (byte)0xae, (byte)0x6c, (byte)0x57, (byte)0xbc, (byte)0xb0, (byte)0x32, (byte)0x61, (byte)0x5b, (byte)0xb1, (byte)0xac, (byte)0x3c, (byte)0x21, (byte)0xd6, (byte)0x36, (byte)0x82, (byte)0x8c, };

	/* VOLATILE PART END */

	private Genesis() {
	} // never

    public static BlockImpl getGenesis(String genesisSecretKey) throws Exception {
        byte[] genesisAccount = Crypto.getPublicKey(genesisSecretKey);
        long id = Account.getId(genesisAccount);
        System.out.print("public static final byte[] CREATOR_PUBLIC_KEY = { ");
        for (byte c : genesisAccount) {
            System.out.format("(byte)0x%02x, ", c);
        }
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
        Collections.sort(transactions, Comparator.comparingLong(Transaction::getId));

            String signatures = "{\n";
            for (int i = 0; i < Genesis.GENESIS_RECIPIENTS.length; i++) {

                signatures += "{";
                for (int s = 0; s < transactions.get(i).getSignature().length; ++s) {
                    signatures += String.valueOf((int) transactions.get(i)
                            .getSignature()[s]);
                    if (s < transactions.get(i).getSignature().length - 1) {
                        signatures += " ,";
                    }
                }
                signatures += "},\n";

            }
            signatures += "}\n";
            System.out.println("public static final byte[][] GENESIS_SIGNATURES = new byte[][] " + signatures + ";");

            Collections.sort(transactions, new Comparator<TransactionImpl>() {
                @Override
                public int compare(TransactionImpl o1, TransactionImpl o2) {
                    return Long.compare(o1.getId(), o2.getId());
                }
            });
            MessageDigest digest = Crypto.sha256();
            for (Transaction transaction : transactions) {
                digest.update(transaction.getBytes());
            }


            BlockImpl genesisBlock = new BlockImpl(0, 0, 0,
                    Constants.MAX_BALANCE_NQT, 0, transactions.size() * 128,
                    digest.digest(), genesisAccount, new byte[32],
                    new byte[32], transactions, genesisSecretKey, Constants.least_possible_target);
            return genesisBlock;

    }
	public static void mineGenesis() throws Exception {
	    try{
		    String genesisSecretKey = "Test123";

		    BlockImpl genesisBlock = getGenesis(genesisSecretKey);

            System.out.println("public static final long GENESIS_BLOCK_ID = " + Long.toString(genesisBlock.getId()) + "L;");


            System.out.print("public static final byte[] GENESIS_GENERATION_SIGNATURE = { ");
            for(byte c :  genesisBlock.getGenerationSignature()) {
                System.out.format("(byte)0x%02x, ", c);
            }
            System.out.println("};");

            System.out.print("public static final byte[] GENESIS_BLOCK_SIGNATURE = { ");
            for(byte c :  genesisBlock.getBlockSignature()) {
                System.out.format("(byte)0x%02x, ", c);
            }
            System.out.println("};");



			Logger.logMessage("VERIFYBLOCKSIGNATURE: " + String.valueOf(genesisBlock.verifyBlockSignature()));
            Logger.logMessage("VERIFYGENERATIONSIGNATURE: " + String.valueOf(genesisBlock.verifyGenerationSignature()));

		} catch (NxtException.ValidationException e) {
			Logger.logMessage(e.getMessage());
			throw new RuntimeException(e.toString(), e);
		}
	}

}

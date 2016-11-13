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

public final class Genesis {

    public static final long GENESIS_BLOCK_ID = 445516706271790606L;
    public static final long CREATOR_ID = 4823535488705771609L;
    public static final long FUCKED_TX_ID = 4823535488705771609L;
    public static final byte[] CREATOR_PUBLIC_KEY = {
    		(byte)0xf1, (byte)0xb3, (byte)0x03, (byte)0xe3, (byte)0x8c, (byte)0x3c, (byte)0x29, (byte)0x34, (byte)0x7a, (byte)0x89, (byte)0x6c, (byte)0x85, (byte)0x7e, (byte)0x29, (byte)0x88, (byte)0x32, (byte)0x3f, (byte)0x40, (byte)0x66, (byte)0x75, (byte)0x22, (byte)0xa1, (byte)0x0b, (byte)0x6f, (byte)0xeb, (byte)0x46, (byte)0xc0, (byte)0xaf, (byte)0x2f, (byte)0x2c, (byte)0x13, (byte)0x59};


    public static final long[] GENESIS_RECIPIENTS = { (Long.parseUnsignedLong("10687454573350862535")) };

    public static final int[] GENESIS_AMOUNTS = { 100000000 };


    public static final byte[][] GENESIS_SIGNATURES = {
    		{77 ,89 ,-11 ,88 ,-121 ,-99 ,83 ,-3 ,56 ,23 ,93 ,79 ,115 ,-122 ,6 ,107 ,-55 ,-113 ,43 ,77 ,-60 ,113 ,77 ,112 ,36 ,-81 ,-43 ,39 ,9 ,47 ,28 ,13 ,-98 ,-32 ,-69 ,-120 ,-43 ,-17 ,-58 ,12 ,77 ,86 ,80 ,4 ,72 ,-117 ,126 ,42 ,20 ,29 ,-78 ,-101 ,71 ,104 ,-30 ,125 ,89 ,124 ,-58 ,118 ,-51 ,-97 ,-38 ,61}
    };

    public static final byte[] GENESIS_GENERATION_SIGNATURE = new byte[]{
    		(byte)0x59, (byte)0x38, (byte)0xcc, (byte)0x85, (byte)0xf8, (byte)0xa3, (byte)0xf0, (byte)0x42, (byte)0x3c, (byte)0x21, (byte)0x1e, (byte)0x27, (byte)0x41, (byte)0x36, (byte)0xb2, (byte)0x2e, (byte)0x74, (byte)0xbe, (byte)0x51, (byte)0x77, (byte)0x1f, (byte)0xab, (byte)0xb7, (byte)0xa2, (byte)0x8a, (byte)0x09, (byte)0x5b, (byte)0x07, (byte)0x2f, (byte)0xae, (byte)0x8b, (byte)0xdc };
    
    public static final byte[] GENESIS_BLOCK_SIGNATURE = new byte[]{
    		(byte)0x65, (byte)0xf2, (byte)0xed, (byte)0xd7, (byte)0x32, (byte)0x09, (byte)0xcc, (byte)0x68, (byte)0x3f, (byte)0x86, (byte)0xf0, (byte)0x14, (byte)0x50, (byte)0x43, (byte)0xd3, (byte)0x53, (byte)0xd6, (byte)0xad, (byte)0x7d, (byte)0x97, (byte)0xf0, (byte)0x1f, (byte)0x3b, (byte)0xe8, (byte)0x2d, (byte)0x7c, (byte)0x7c, (byte)0x9a, (byte)0xd6, (byte)0xb6, (byte)0xe1, (byte)0x0a, (byte)0x7e, (byte)0x75, (byte)0xbf, (byte)0x42, (byte)0x1b, (byte)0x04, (byte)0x1d, (byte)0xf8, (byte)0xe4, (byte)0x1a, (byte)0x87, (byte)0x11, (byte)0x0b, (byte)0xef, (byte)0xeb, (byte)0x0a, (byte)0xa3, (byte)0x67, (byte)0x75, (byte)0x8d, (byte)0xe8, (byte)0xdf, (byte)0x5f, (byte)0x08, (byte)0x91, (byte)0x4a, (byte)0x1b, (byte)0x25, (byte)0x5a, (byte)0x08, (byte)0x12, (byte)0x4a };
    

    private Genesis() {} // never
    

}

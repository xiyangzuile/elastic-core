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

    public static final long GENESIS_BLOCK_ID = 2680262203532249785L;
    public static final long CREATOR_ID = 1739068987193023818L;
    public static final byte[] CREATOR_PUBLIC_KEY = {
            18, 89, -20, 33, -45, 26, 48, -119, -115, 124, -47, 96, -97, -128, -39, 102,
            -117, 71, 120, -29, -39, 126, -108, 16, 68, -77, -97, 12, 68, -46, -27, 27
    };

    public static final long[] GENESIS_RECIPIENTS = { (Long.parseUnsignedLong("10687454573350862535")) };

    public static final int[] GENESIS_AMOUNTS = { 100000000 };


    public static final byte[][] GENESIS_SIGNATURES = {
			{77 ,89 ,-11 ,88 ,-121 ,-99 ,83 ,-3 ,56 ,23 ,93 ,79 ,115 ,-122 ,6 ,107 ,-55 ,-113 ,43 ,77 ,-60 ,113 ,77 ,112 ,36 ,-81 ,-43 ,39 ,9 ,47 ,28 ,13 ,-98 ,-32 ,-69 ,-120 ,-43 ,-17 ,-58 ,12 ,77 ,86 ,80 ,4 ,72 ,-117 ,126 ,42 ,20 ,29 ,-78 ,-101 ,71 ,104 ,-30 ,125 ,89 ,124 ,-58 ,118 ,-51 ,-97 ,-38 ,61},
	};

    public static final byte[] GENESIS_BLOCK_SIGNATURE = new byte[]{
			17 ,90 ,62 ,1 ,20 ,58 ,23 ,-20 ,-65 ,-28 ,-94 ,124 ,-96 ,-60 ,59 ,-58 ,-125 ,31 ,107 ,-44 ,68 ,-10 ,51 ,61 ,-11 ,-49 ,-116 ,-54 ,90 ,4 ,-117 ,9 ,-2 ,67 ,-25 ,17 ,108 ,-112 ,61 ,-18 ,-68 ,4 ,3 ,36 ,67 ,-117 ,93 ,58 ,53 ,-69 ,-77 ,-123 ,126 ,-117 ,-32 ,-63 ,-9 ,-74 ,-23 ,-10 ,-119 ,-103 ,-98 ,45};


    private Genesis() {} // never

}

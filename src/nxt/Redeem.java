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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.VersionedEntityDbTable;

public final class Redeem {

	/**
	 * tests: 1AktLQnreNm585z1r1QbAjgrrJCuYuXtZm -
	 * L1SmPPpHtqR8na6ZvCC2Yu59fEQuavTkU72fQNte6bP4PYrKfYwX
	 * 1L6R3tirR3uEBwNe27p7cC6f1CErXrJFTm -
	 * Kwn2i9nxfDTDDvPW6Nn6rujDT63A2jWwTeTQfsraEzqoASvN6XRM
	 * 1P2N2BWqZQef7pn6LLet2Ftwo57fhiNuC7 -
	 * L4M8W8V59oamWUU5pJ73KDuGuNGQq1sZSY2iRVRkvxquHPukrYCk
	 */

	public static String[] listOfAddresses = null;
	public static String[] listOfAddressesMainNet = {
			"1FuMDoQh7fj7NTm1p6zqhgfpTXBRVsXm54", "1Naira51gy4pc6hnSeokQ2mCm7tQ5eZyZU",
			"14WMdsXApZjz1ybye79nA3JJf6h2R5T2SH", "1EranfPkcFFAwbXDjzTix1m3AxkXjr7ZDD",
			"1MhLm4VaZ7XWPPsFrZnqUfUUehNhG84Ww2", "1AA6KycbJ32bTphqFKZTkSCYhaeh348jkE",
			"1JJuVWjefhYk7VfFi5D7UovVdA19X7hCv5", "13aKeNqzEzNrdFV1SfbZ4Spg8x8QqM1VpW",
			"1BH2KAuUpiuhkszMA9fenviQRbn6pHgGE", "19MxTmoksJEj9CgzifZKcy145Q6sDpMo7t",
			"1GHkwabM8z66PiTfcNCTTYkNnvdF22SQwY", "1D7w471ZDkbtp8p8QZoMGduzJ26V9u2Jdm",
			"1EwBpn9NNPGE174NvkSjhtdAsFdUCiHdk7", "18LLinoGvUvSSQXLovt3fDWXwMSfmS1UDG",
			"1B7jF7NDfFSdToSiPvYWnHAxEUmNaMfYe", "13bgUr8JugfD2rb5PjYe9CzTPTGPNdBaTT",
			"1PACs4zsCX3CLeybt1ijj74Jj4q71wrBeL", "1FvqVxaCtsxtNTBTYndhsS9Xa5LxqjZGoo",
			"1NTQfZFBN61ke2Dxb7dQ4d4oNEMsf5fq1u",
			"2-1P7JAqMWndrh7r96mUtgtHJpi3DDjB6W6Q-1M9sfm4KVEBQtCBFvesq2GuCJGeBTfNWYP-1JwefaNfiTd4WzKnXirdMTmzp3eaoo341A",
			"1DLbmALgy7saTfpEsbweP1es9CmVVzHpts", "19ToAspzr9VoRW5VgiSEGwSP5xHgdM64Z",
			"18Z18VmxKiXRz3Zd45ejRv8vtkFUa7e9GS", "19Zd1HCUXyveUvj7QwHEqWiF3To7wuv5PQ",
			"1CbhHTFFaUjmDwwFYucSLKRmD6pUtKbVNN", "1PXbfNGNGB65kTTd7bxeD7RBkPiL1AJDiR",
			"1FTqLnbjCUiF84cbQMxaguQ8sU9rikKhwW", "13jRQ6xKNiRq7uvMFVmV9kQnPVbxRe385W",
			"14CpY8dRsjfLBzussdADgAQwFKbeWuz8j3", "17gvkFpYV9842qZriAtN9S2L28sZnJx4oE",
			"17SnP5CDEUKPZBa8yXTUqrvtnM3VHB2VkV", "1LJudB5t6sq8NTsDj2Cg7SvUq23z6Q1qju",
			"19m3QXzPX9gwZ3iD3gq2RS9EuqwkP6GkdV", "1NMTFJ278TJHFoFPZwxV9ZyseD6inn2Zj9",
			"12LDkCjLBJJSXpfrTcR8dMFVMtrvmF2Prr", "1MneNPGDvscZdn1aGcVKcRvLoauCBTRfaB",
			"1NXscUA1e7EDDaPEDwNgqP53BF3a3yMQEh", "1QEEd5GjiWaeE6ZdgB7UPteyDQpMs6PRSp",
			"196PUcEJfiNYMdDaXhnqgqzzA3cjHpLmSt", "1JPVXfTmC6xwbYW2RgSeM6iKZP3NsMBByT",
			"12S34uZ2HDiVgdsY2sV8nZpQZHwsENiqoH", "1BEbASKFwGuJfwLHFh1jSTWSr6RMviN9hs",
			"1pr62K6d6MYddi6LfY5oPoaY5AEfn7Utx", "1NU9xzCWnrZCXHa3rRZtdi1QqvNpRMRhvH",
			"1BemsM9i3gjshwmJ2txfjXXnFHfqWs8Huq", "14erK2S3EDZ1YnKnkRJqiTopfkvvCQRXPD",
			"1CiYTFjgVWJG3GFAXehmyp38Z96uHPTqut", "1BeyK8UPBAuvKahaeLbdDBwuqu1WJRdnca",
			"186MNGTmgdoesFNhCvQAts43K9ys13DjRK", "18u8n8GjQJavb6Cs9WywR2ZhfJxUKP3YJU",
			"12py9NUvkLQBqbBS4De8miMREhjpUrcKZp", "1JoGVcZdSXHKKH2W5YjxwqectqsTT2QAj5",
			"1friNTBnsATv3Voyujd5Fn2eDAYXcNE9S", "18jsbdMm2AyiAv54rrFC5ncnQiLTh8FzoP",
			"17cHZHiKHpUk8jmSYCKqpzT3YcaPom16Ev", "149pNpMNVQCCzgXHwckkkd1u34efahQicQ",
			"14MKronX9pQJui5vVENveDqwKFE6bxWVk4", "1J23z9TRWiZRDHyE6xQTsTnkA2g8cPp3UY",
			"1HVPu1KhFCrjvzZLHvmU1zvmmCx35rCFbk", "12VZ3shGNn8wkt6tWYSDSr1ehiz2NDGBhP",
			"19NL1hXyD6omKMMbomui9z1o9mgYENXKva", "1MrEo9uD8tAgHML5JLzo9biUkKH7ggcsp",
			"1J8kXzvDLHuQQTTNt7bNmMKUz25RPJC8wz", "13Qnwu6UUHrPfa2bo89v4kqKkUrDW8Z2Cf",
			"128XumH2QsZ4BGo6jEuTDjShcretUxDqLi", "1GL6C71jDeDsszFnkdYdQsYLxtJL2arGWm",
			"1PuntP7sziEBVfDHWsPXiDEN2ZQo1YibwD", "18szmRivgPcsPVes3QNyR1Xne6ND4xj8DK",
			"1Le5NkD6aYnzZPKCiadVAPzcbWsH6CjbYJ", "1Fd6r9JrvB1W2MgU4n9vkjzG2NJ5Rx8b2A",
			"14f4hDX2z9cwkpZuSZqiK2uj3aakNFnoHx", "179ConMuWNepwGCkqaiJuFfqEhCvGANGwH",
			"1Fm6b2qFJvp5HiTtBJn8vwsgynm18yD2Ru", "1DyHXmfFqfyX2HHt6RhqXdePDwGELwUbJx",
			"1B8vxvHfc9whEkhKyUoE9fMVNUbAaKehfU", "1GYvGYrUqLGzQoErLhsegPw3nygpNFwZR9",
			"16LUs7scDuJvGk4A4LaR24DwHiXuwsZuVf", "17MDYNKvWD7JzB1aoxX7pnCiPYUrC8LQgn",
			"1LtDqWvBoFDF4aoAYzBMmsgGQ4g4HsmYJo", "1PhcxuPuhGnDH7GeAcYTDkky1cdcZt6mZT",
			"13iCYHRpivtTNhYKc7sgC2AxzS3SzNTuVD", "18jbntdbaQLEkc8sknUed5QyJ2AgAy3a5V",
			"1KeBxECUCqQ53MoJSbYvuKCe8idDGUHoxR", "1Mmm4c8PZvNttyzZLJJwyMR1QkFMA5GF5P",
			"1JainHFBinv3cCELLJW7scdxucu3hU9mgk", "13L6Qb9VJXS4PvGPbtWwyz8roHwC7DEHZX",
			"1Gx4nmEt8VyNeSjn675o3bHZ7VSaZjS5yk", "17o4Dyfc7GMbwMgaeXhQQ74nqDcdmP7RVM",
			"1PpUpmpGN848rmiVg29CHj6StP9GSFvCMe", "19fLMHcFPX3CiHXTDUnNJg3ES23yXQTYbv",
			"1K44nMoCmDEf5Wa1v9Y2UFUiM8Qai99RQj", "12B571aTWKsrq6fohnQXjT1nhHcC2617rx",
			"1Gc1Zz8VTrnTrq5DLa4wZ8CmD2AaSaUJ4t", "12bycbUapdamVGhrnvUJiooqVQQ95Mei4S",
			"14UwzH27d7YGKvkzQLbp2EbnAQErkSHTKB", "19yegjdDMdx3ArzASkxkpkpyycSpmag6sW",
			"1LmJ7z3WezMRWyKx1ZHYdTgg9cX23bHNHJ", "1ATMQmcUszWtFpbLs8M8iZsZhS6oZCk7bN",
			"1GK62XAceZtLQmikjNUVCQrrLRgkAN8GfJ", "1KKV9aNR1TnWnHNzqpX17Jio8NMoXLycCt",
			"1JFAUpkrjwU6cn8Af2npwaQ3c8b4eMsbSw",
			"2-13vY9JGHRae2z4s3wKWtCBdbS7q3qwhguJ-1FSto1iU7P4rbWFejPHzUT7Fkyr2YNSQFS-1PdNdK4YuAcPsz49iFhyMuWmW76qtSzkkZ",
			"1KKVprQg2zYLceAYbx8kJxbzGFUn6bUX1C", "1BgLudnKgbYGuJkvyDLzS3qgNaeJ51ngoQ",
			"12cNxny1B7mmMMNTZgrsXGiqk5hCZCJnGu",
			"2-18sKBw7LDtVDCF9qW6fgL5Cix6t8EJbazL-1ZUK8tvVP5MgYxA9K81ryLwSG57xpbsdh-1PywAgQx2PrywqaWqEwDZ4ZyE4akqsEG77",
			"12XRgca2NLFWTEsmm9gg3FqfDSYhbnCXcD", "1D8AJUBvkFLiLi7qqzLNe1SnD7sA3duVNP",
			"2-1HTTuVyKtcDcQJtFUykU3tWSJUu3Z89RVy-16XsaUzxBqXRyEScspkvNhgtmKZgohSjWf-128iuf7pgWu1y4RUj7VttEVHpU5uoASPmW",
			"1MVqoPXUTJ2BgFetBFojvtauotz7TGvnka", "1LzCmnXqvNApPN8BLcyUWnddc7x4QQ5caR",
			"1BrKwsVmHvHxnbgGnnQ4U2KC4rV1a2nUS3", "18brPBR9c2eNuHMtzEhr9ayTxCBmipzrk7",
			"14LEPr49hNgfM9EaDU67UFTwWey8rZc3wU", "1M7rCmfjBGxwGRtZXv5K8Nzdy3A7v4QTPm",
			"13NeNTpwFW2EMeh8B6q75t6Vxj4pGBMAKW", "1HW2VtmAuomDjBPAqVVWPWfRuDx8S3qzMK",
			"1154rigDAHKtLQKRZgS3cz9PUxouck1c9k", "1Ha2sMvSMgow6Dz6bqhXLfx1d73JNPHn8y",
			"1H1sAkEMqJ6w2yms3V26nzSKcjtkVdzkkv", "18iYdBTcAY2hPZiKHqDz5D61YjrKgJ7gyY",
			"2-1F8kmFiJkFqtbAhkUEDdwipitGNZULGEec-1L6N25HHcUupvb2d89xcrAnsWeuFbBS3k7-14xVGvBQWgDDE8Lr4HBAepA7VHZWsXzJWh",
			"1Pw6qbzQivyb7kBpaBrQedcXJt7d4uQu9G", "1AZMaW5vswDpboeGfNVA1ueoNab1nfHfCe",
			"18fjqZuCQ3ZM5XoGjAKfus5PsRfoZmf4EH", "1BP1Ai1CurMqtpvLx7jU1NB38oe2Vrm1b6",
			"16Z2uhUBhg6VWk3eXEpgbbXVTm5Phxove9", "14W27o3HBYxVRkeJj36Qj6RHHDj8ga4fPY",
			"19n1xVC4JWsx8GhtfoxdDfoN4Ensz3mg1N", "1KeFywuPFoZVUkmzZC2qgNco8qU9rPVTvR",
			"12v4d15AuopsVzudct7J8LJMm7qH95SxA1", "1J75pvyVUyRt7JpW6RQHzYi99QzSwkfjAS",
			"1GmY61vTsBjvY9cqvQY6LS9BBy6JSqjBYm", "17y4rTjnqLe6AST5Ygv4DixagaRj4o75Wf",
			"1NUtkYVqjF6rmUkSrZEdmmRjcq3fgWAag3", "14ndafdPdqB2wLQddXT1N4kaVzvgdgi9eY",
			"2-1DNxCpKHDBHYzmmasV1gC3166ndbLR5tUX-16bvdPbFynNXWWyKXuFnm9KrcziKVXxFhd-1Fi14hM1XP4noNwjHHjJF4FgWF7r1kNdau",
			"1G2pGEM88fqnMtZdJNCLF8wh7T6YQSkWog", "1F1NVgJe6KTNRucyZTZvWoEqBUFnNWRSMY",
			"1DMHGapZs43By6siPrzZUgWxCFt95V65nk", "1HLEZL9LxSmCeZMr3XkMsWYSUCC2EXUSaL",
			"1KF6tLemSoVjgtQ7WiFNseqKdnf1HZh27V", "1CcYJtqKDTMPtsPfiuYJjsJoL9nj5f3b3s",
			"135uZKXDhYYhiLisdCvyx3fLhKS1mbp8nE", "1K5pv63rag715mN2REYcfMbQs7RyukPuSK",
			"129yMeagKaaEEyQQz88zAfuaeY8YYz26wL",
			"2-1MgJG1Lan1f5gz9S1MZFfL7AmkKjCyDjcP-1KjUMwz1GAXRzzQMEjUEJEbqsHR5TkQjDV-1PFZvwoszumZi6cYqYjJxAJbY7nRhP6q8H",
			"1nMrSrMAiEDWopZKPUFQC6fBx5EGAsEdH", "13ujSkcNMmzmNHfqr7p8ddnH9KHZc5ngx7",
			"1EePAYbyhidFDhKsu3KsfnY8BtmcTqttrb", "12iDQNAbfVduDhfWhFevyLCJBGCJHd7NmH",
			"1HLFG8EuA6vsfUi5tw37PcUCnASqA4x1w3", "1G82zewBq8NzpC1gFHVsPRC6K67mFNon6a",
			"13sUuLiyLU2kdxRpfsNUJXgLR1xiNdadBG", "1M5xPWSfQw4qAhSibZuaSWrtukso1eMmBq",
			"1P21GkkUFt8ME2U65SbGwutP9EZcuQoFs", "1Eq8bqMRBxADqFVW1YUP1zYyufYBp4kfs1",
			"1Gd4niapnmcn6UJpByhBJ2c6uxkpy1k1MJ",
			"2-1J6YrmvRZMvw7idk4JhLChhxs4aBSFX3ni-1L4EoMA6BPeVAvedykpDZNUmQLK6JQGefs-14HnMuS3tqXBtoejZg9pusiMzrZR2Jnezv",
			"2-16n4kKvWVqJhwQY4dMdt9M4uVcxdajpLFr-1PFU1NvhqSXwgw3RMBWHgvFyqgPgkZbnkP-15q28uDQk7NNKn7hLWW39w8UHryqPt1gM7",
			"1KazZQVFXT1LdTAKPh4b36N1ETwJSJv4Rq",
			"2-149YJRmvksFwxhVsdBkWM4yhgrwzTSzkhD-17S81r4HEYhxx6jCYwbUj72F5SwhdUpvSA-1M8nxGnxwkurzkfq6GTLb1cPf8PPasrpzm",
			"1BZCTgfergVvwwUx5kMDFPHYKjrk3fnf5W", "1FndprLxopAaZFMkWDmACEhmS2W9s5CVfH",
			"1LhuczTy4Ep4VVXQHraL2jAN2AZS95z1nE", "1odajeaZaCpHrxMPKq4b37KbsKnGRkxwt",
			"1zuDxdhaLrEyzmSbKrBRnJputgccSY5wD", "17GiWvV6Sb4damGNKUFzNdesBxYXG7cknm",
			"16XNyCf9rEnn7qxPeaQPDXz4JY1FhzDbDA", "15MvcgPMpWLZuRtTFHhE8LBTGP45CE7Cos",
			"17Awa5AbqkFiUmW9PxdBhYFGs9n4BWBTgf", "1EDTRFD6JFb2N6EYzAcB2RwESLLfUbT9a5",
			"1P37KAuSLGUmoRTQYWqeXtTCRuLsvszuZp", "14d1PAGLCdLA4t5KHzPhCAf5V6ekYhDCME",
			"1LZNS7UNiL3Q74RuGTzY593jMP8t2hKfDb", "1B84kRYre9YHXoASmAAVvaTRuEHNQ1tQCQ",
			"1NJJs32wnaA6NAjhTGPg1KyKv5R3PrEw1Z", "19bCRByV1WE51vfRqEKtweJknfzKevm7wM",
			"1EmrdgNEfsqDwfGF4LygEFyowTTZTPV5Uh", "1LcEW8ycTnjcSdQRZ1o5fz1c3GEFSwZtxQ",
			"1B7R2yexLNxESAhTS6K8fY9epLXtzP2QN", "1Q4P6QW5TmtB8u5QsSoo8FihD7AphHML1w",
			"18YLC2tD1yGEdaNitXuaueyFPJ9yeJRwgL", "1BVap5sg6gTNg9VkU5eu5t4JNjbLAxEpRM",
			"155VUG4hd9pGYsQeuyMvrbGgzpP7nSzJSn", "1EY7UYP49CdEwVWExmekAKGUwGPppepV1R",
			"1HoYtFhQbYnx9UY1dfWfoM3gksuEP8397m", "17qB4S8KJhovDj2ag8iFtQfcW2isy3ZJj",
			"12NZvfXZ7VpuxCfWJ1GJ3A927An1vW1Uh2", "15QmJV44C8Zs2AqATqBpvgp8poyCcABY53",
			"1614UqptoSZd5SznRsvoK8tzYZuR7TUfpE", "19FH6UgAHbRSwNr7sGJxNYW7p8NA8ZxwYp",
			"19AKeSTDAWT4VB9WUAK3aiw5KSfLKooTKy", "1M6pdtoTPe36pXtChNZvH4c8mSB6vzSJHJ",
			"1KW1eim7pRcduC2EaTBCPQHy9jCBp4BsX2", "1HJn55Y6wbtwpSh2rauTy8Un5Cduk12gaT",
			"2-1HPRacvTUSPXzFB1xuYkAftZ9R9kiLz3y9-1PxGBYYjjc5aKVe38s9JEwqHPinGVntyn2-1Mh1bm4MJA9LFreL5fLynKR4GEaEns4xGU",
			"1DHKTB55ZPEQ4Ng1rxwe39i5jiF5ebmYHf", "195zUtZLEHYvwDTbPNkzHBBF1SoZThDZyy",
			"1GR4CaxvHwBgDVyBXroPDPkjNTj4HRkKkH", "122R8pEGWT4ABeh9BWsz4DPhJ8xyYMq9TQ",
			"1LM4KFSY6gT9HAyQFzKhkXK3A2LafLax1y", "1DsTZJvGo4ztdsBMdEpeqHHMdaTcZXMd2N",
			"1L6KsD32NSo5kcDeDAwNiYuUdpozS7mMPs", "1LXzyxj848i1d8QTnoVegaYziSYz7z6DNi",
			"18RQH8yB7qMapzqRQ4Um8qdHvs1EcoKhsY", "1Ahauxg3uxhcnxnSdmqKVhomz3MF61ynxA",
			"14HADDEQz5KY6V4VyP96War1UqQdq66W1e", "1DksYUW6J9wGuArxM6n1eMhjG86mqxnmA8",
			"1GzikhiugNF4uHxsWaLHgLBsD4GUeMQiDy", "1ELZkisGYyocYdSsd6MTsbXAyHWwwPBuGz",
			"1CiBuR4aYoAfhKt1AhMKiJMGwkSCaQuWxm", "1KHsvBy9B7vxMgoNL1n64ChfTAczbp1ASh",
			"19Ua5ieT7V4opixMv6aSc2pCK3Rp4sJz9T", "1GQjLN8jEVWLEPxVXgYszbwhhDurmxar9r",
			"1NArvaEWFrt4R6G6Uxar6XNM4s3h4sBqkw", "1GxENbATk1HfUx2ijYaSvRDENXBymF91mw",
			"2-1FbmCy3vduzEYjyBmYsLyUtVG9YUiuhbvB-1aq8giKn7Fx6DvU1wr3egymmnr6u2CKWW-1374o987v7F52Rvxtu64xZRnLFmfmNK4dY",
			"18FCNW11Bv5emmMRj7P1pek9hxXXCZqda4", "19HrkEdm6sbwdm5cGoPAaY2ccxpUYAA5mm",
			"13tLKf7nhd7t1rgnT5mVMNpEDyQbbbiABJ", "1Nqf2fnLHEt1qLxGHFDmVspy2c4b36FeFo",
			"1ABpYSgv6tcUpF4ryiQarGy5Rqfy1qwC9v", "1AMbK69tF4rpjotkAekQoEiFiA9xZaRZ1J",
			"1JEhp9E4aLfAw1HCaXTAQMUGRLGmbP7Xh4", "1HhRfRPDLcUk8J1zpURFug1xSpacaeXCtL",
			"2-1E9TZGqarDJgFDrj3amLhTcgEqSJuYGjfh-12mA33A8uh5HfKKrQtVGMDMwRapcmR5XpG-1DC5JMZzh8KNSjgQgGEwRCjN6FbVe8x27o",
			"169R7xxSRxZXmxNX3YoKNTtaYQwHLNZNao", "17736rfhafvjFk3usfo9zQFR3PfHsvtiYQ",
			"15imAyVyDtsuwUdLccY52epJt7Kakg7ZnP", "1HsFYGTJddaCspLWCmxydg8vWpeRPMrEmF",
			"1B5WmWZi8twk83iaN3YbjmZEYgvQRU5D2P", "1CQ6koPvnna4d5ENNhaEzRHmLEDegMaP2S",
			"1LvVTc9TyxYVYdPQcqKxpNoJfbJNdGNs1b", "16bekfRKhSKpUkz6brFCyKXwqJobzmM6mi",
			"13ckSm9CJL2SPqiQVrDC2C1UxLBUV2Y1Xe", "14Tc6PU1kzLnmooPHXdsy4ePtPxMgoXEYj",
			"1MaL859XbZ6AKrzCj8Q73QTGA5fVZ3mhYm", "1HNPGJmb1bSUk6tsbJgsFjVc8iDJ2uNdgN",
			"16E4kUAf739pK7c48J5uJpcdB3fUN39Fcb", "1ELCSHBK1JQQwDC96MFRAY2MtYYuVF3bwG",
			"1GC2SoDHDUzctaKGGn9mhhBu6j4qaWCHFN", "1CkH38kWDAf6d9EwVU38s6fjvAShu6tfvg",
			"14FPA9qD3hFDtLjZHDy2JUDkixFtEXDiwx", "1CVUSDGjd3jWrmGU7Vb2VzN3z6eMaxZ5dn",
			"1LovVqqwnLYzVUjFsycPDtx334BmCtcha7", "1Q7aBEendYyjtrA4wLPY2aHMYfD3kuPBXX",
			"1FT3ko6cZwTsNWxgroujBpsB3wrpu4NQ2a", "1B9f9HChAAonTUxJPNU9PkUu2zRLVA1sLv",
			"14yZJxhDKQkgaK19PEUpguaPtBrv2hbJxe", "1KDm91GSEjob7PyVF5GUPmSiMg4KMSAXsY",
			"18VD8S7Tc9u9B87u5DZAbbshzxLhmqtE9R",
			"2-1EahcFcCVHF8N48Gv3oejk5xhQLbcBmfW4-14JM5jNjgGZYCAiV9Nvk457HRPboT182FR-1MVCzDaTgDXPjzUna3DJoPG9o7QcbR6o9N",
			"2-13pLi61HGaK13ToqR4nfBDBSq8PuCg4XiX-1MyVGJUTdfE6S2na5ZAmWAbimFWzmMBu1X-1KScv7hKYeBT6FsUaAy2mtsUFrf163vz6x",
			"1CEagPKG8WL6oX5baJ16uWacD5kQRCCQvk", "12knCup7U5V8oQ3FWPD6Be9L4HfFYD4MDZ",
			"15vUxBo9S8Seob94aAhdsGps7MEzBoViPa", "16HLVBKgSmTWq7igbtVmxKU2YujuckpEnA",
			"1PCmmJu8c7BgUXjnM8QqTQ1Yu5YM7MeBDa", "1NKbkA4KxwGayoK5cBeQzPMVXCXAbcLtdp",
			"1JuW2mEzofGwKrYfVx5wW67hrPvpBAJd43", "1DxKXSnBxYcfrMqRtzrX9WbFYPktSRa5E3",
			"16tQCYcfJMJ2n36T9chCbNUQiB4hZeCB6j", "1NWg1Mga4n5CWLwQPrhkQdLJ9fJdJy8zbV",
			"1LBKdycznxbx5egJBqz19ijugwHcHFY1jm", "1PCNXireXa6mZGub7vJ8jRCMnKj5D3sVtt",
			"17hdJnw7J5jvWx75nPenQP8hfstQSnw7uG", "14skxeEv8cniqDm2TxjPumM5sJpghqZo1b",
			"1MNStNrkraqYaawKSNaZ5d6GqzJm4EGrJY", "1PBpVC2P5YrMyabyFD4QiS5jvhGCxCKANa",
			"2-19QKxdx5aq9L8Z7nBPrBhQCQLP8tD4SyQQ-1LFmQMM76N7N2GvDqmCLAMCfH7GvgLP8dL-12NStY7wC9cFzUurSYd7ffShCY4stzecwr",
			"1815FyZMLvjTKiTSmxVhnw81imJZeScdtm", "19tCadLqSh3hD7v9W2AomqKj7WpoQFjJYr",
			"16SxRuDSeUQp1mDnaLTHBMjWsUtPXup1dZ", "1EdmcmNLjtzxyrGcZ7TsMB7Up7aQ4qSFcr",
			"172aiWHKukWFGE11vUFniZVU7kp6ZRUZyU", "17KWUsihUPxeiVYxFZqTJxFtno5rxvV89g",
			"1GVdqzA4ULCctJJZRwKX1mRpYdHfEL1WwY", "1Voidf1WWAwqMd2uMLr5uXisRyjPnWRA7",
			"18ZjiPMDuMdMqvknMdhbpDKBRbBXywCNKC", "1HgVAQfFqSycVMxRUtmWUc772RS1BQU2XF",
			"1JyuxSxPsd2A9HfF2X7EbFFZfSAPZPs4M5", "16K2qfVXgwzAJkXun933zPp1CxpqjeGLbR",
			"1HcpjzYFoonrhFYDs3AdUxbS7Qu6fS1XYN", "176bEJsG2tHEo4bsA17AguZN9jMU6LmYBX",
			"1HqxgWn4yc76auxd5GTAfP1ShzHmYHg6RA", "1Mo2U2did2AFKX45sFg1tbK2z8K8LuHWyu",
			"13UCGZ4qnGwMiP4PqqUx4Qi6tqQfvcqt2m", "1FBSjuR2vFqCuSW5QbfSG6uz3T7RVEwhGB",
			"13UtpxQJuoTigEeeuq4HrkUvp1uyPxYXEu", "1NkATnRy31UTDpkuzEru2o3nGzE5JYm3p8",
			"1DE6puFK3MFZxMtnFiAWcLewQNqpn7GxDV", "1EAesPzaxxiffDVHAvHJZWxqNNyH1yq2YZ",
			"1LE1hYNegqX2CQaPsuw3ovQrbMKuAacbFd", "1GVDe4y5VwZ8jHNuF8HxXuKzKSXPfjF223",
			"1FzxRgEPRcsjzQ3X2tbZPUkfa5ktH9vG1Q", "1uCjf49oZSLaduGNF8nPgCAeEQDzYqEgC",
			"17gJqvqZ26i22p3sKaKRqAb12sZF64meFY", "1AZ52RzySPbdbTQZwxkUnAuYCnrQgQ1UHD",
			"1CmpnV341LScP1iakDSJqsf6EGinJjuP6G", "115docnYkRJopTHTeEgTAii7Jv7N5wBhSz",
			"1KyDFiDR21fo9XL4Dp76UJV8ThfdzYuqFf", "16y4wASazPdKEGDcoxDAL3CWrfBse3g5B6",
			"144EvecNyPVS3wS4uuVgcQrnK6UreKFk4v", "1GV6FRbvNuunDxRdJ5UfT7oHefwB6ugcZd",
			"1FDg5uY5SVEQexpwmvZcnLGxFRuy4q7NQp", "1B5nabx9sJs6somo3PhN2zC65iVG8uFTiQ",
			"1ACajk8H6fknesYhJVSVw62faDQsUfxzSC", "1Kf6ggJHdRS8oeyjDSx4MouG9Bdzr61EUf",
			"1FtdAZvrTYNNkgUR6WHmM8q9r6mt2g7tT3", "17aVroAkjg5dS7h3fdfKav72kDgivavc4d",
			"1KrJ6e325yXVrNpd6NgYcuiRfvTcbZbBAR", "1JHyxrRhvhpKmDvkcVL6m6v4pZs7iE2N87",
			"1K1eaDW9TvBeVxo5xafQRoY5BGhKMXsRnn", "1C9Hg66TbbQtoaWP3Y31MCQp2JC2TmVPd5",
			"1JMGMnkv4skseqfkcSizUFdT1JuN9wVUke", "1MqE1mkEX5Jj1VBsHuEkGDiaFFpHA1E5LF",
			"1D3miCgxVoV3yuJDPG4RAtXu8qnLdRE6j5", "12ucKWqPxXBxctauuPcJY154rTm1bnPNDM",
			"1J15wnNEE44Nn7yVH6hc5gVc4EZn6ejghM", "1MzNRG1WUtLgKTkpxMnUAh3F257Wkg1JQw",
			"1STmcsewju8ykyuUGkmjVEuPiVyjXc9Pj", "1L4kRvtiQvgBj4J79GhLyop1VNoYkFaN1J",
			"17MLkiXDu4iFoaUoMPhaUrvjVSSMuBkJhH", "12wspBHpbn3UvZvmNUFQ4tWephVcx9Nt6Y",
			"18q3cUrw6g7CGs1mPYuxcQ5XaBFqU4sR27", "1D3rqNoj9CjYFqSv7nzL54BFaZMUGkQ29A",
			"1HP81SXjHkhYoEAxuVWmJXVpxffwZWMZs5", "1MqjkKzZjTTvXHvuNZzEqSSPhidhu8H44Y",
			"1EPwygdMJXY7wAQUMhQuBZGkXA5h47R3dn", "1McnyFoBSC1eeBaXPA876MTP7PEQGrNAWc",
			"12GTLir6rEfwJpShcSqYxybjoZYVhF9t8d", "16ayhut16RXFeKYTnWaBhCj7DNocRL3u8Q",
			"1BexuDc3UUpMRuxE9YV6TqVWTv9dhEvucF", "14T3ub4q64EFdLsKuvumRabscLUddh1aKc",
			"1Kv6jbP9JWHagfrQjjfVzMcnKEGynZ7pf3", "1Lu7yvwyfsPno1rdA4mu2reGsC6FKHnmso",
			"17ASKQx5PxCM42Ym8MEvaeAPjRwCP3asXK", "19ZFUNJ2Jdwm4yR2Y2uQ4MRNdE6L3fQFZH",
			"1BDt2KN7GcBAvzKgTLUDTogg1zZiWKmhaW", "1JuTYh6yGiSgdD5HebFYAgDrhzXN2vioR1",
			"13StFxJgNBiHMatJqnkBGt1damZPZV4qeM", "13Qr22PY9862o2AMwGiJjELbPFg1EVqjvY",
			"1594FrR5ZbS2npVC3irmtNfKV3n92nQ8Zq", "1DuQF15bDFp64qsxEDJXYabshnkudvfWar",
			"1KcXadtYBJgavHKNfE3VfmTJyVZdXUpCmN", "196fbmwTBkFm3xjUisMBSggmV4xyR5642W",
			"1EQx6f1zYK8LpUa7mWBPTEJ9Ny34jTvio", "1ArturoQZAbmeYtZgJFyRnK5GsRLitcBfH",
			"1Ptc1krYh1NAidPiqovQRjy8YtpcYH4kdL", "1F5C89a5s9tfMYdUxcM7JfaBdZB6i9BKDP",
			"1JRuH2BFgtFgiMaVtCPzvnTY1PYAmEJEG", "1DnXbLXgAoVxwKVmXtETaEPcDLxhb8HHox",
			"1HXC9ra6x5VZy4GRSCNSaDSWw3aNjzidwi", "16d9C5nBjh78JwasrPg9CimwUBBoGArfSr",
			"1EAhDVNZK4kPAoBswQeDWeHkV4taVkqfsb", "1ZcgfRp7sEt3XYr6QMc4x4AkYKHfrkLAE",
			"1K4iZ9FFDx5CyAWKgfTH3hSCnVRtrmm1eS", "1Annm6pLezYsCDR8q32zFGoWqWkERDwaGu",
			"2-17CXevWLZVTooW5H1kCLJkv7tiXZFfkbom-1DFhbHytfbWUpKiy3hoaCatD59oRg7djn5-1J442e3ijRbKEH4VZWxVm4QstYBVBCr1va",
			"17hz5ebzoig5jqvNbQbLeQ5y23TNW5i8sn", "1DEDnBdU8FCjkacRf6Ttc2CQAS7FNFrfwS",
			"1hRHm9hxrvFuyUugH64iQxCdyxeFThWUf", "19oo6gsF6qf6C4CX1yFR7fv5wHPLm78Hjo",
			"17dfDBiqCmFbUvPaXDACNk37TbkYTQ7bQB", "1D7E61eVfnbZJdk4cPWFaDYyQ9usqY8CxL",
			"16oCBjNuxStiQq5RbvdgkCgCnkSpewCr8N", "1Fwpiz9CpFJvtca1aaJP7oQKq51FSF4R1X",
			"1DsBd2BSefpZ7NWKJYgjCWHem6VXSip2PL",
			"2-1EdBSiNBmP6TkJt4EoCi4vPf9MMoWQACXN-15LicSLzBPAAeUznRwLXRNdyGGidAqbENJ-18NmmFBVDF28meU9MFtLP4D3GVjThYH8HA",
			"1BxVCHGP9FZA2BcvLtLma1V3pDJAuo44DK", "1Kk1uqZqZiWWGFzZ8onYGWWG3mRVR6P94H",
			"2-12MBcoBnjxdVBpceCaPCdnq1Y3p2U4kY2L-17KTsyEggmpMUjHJBNfjE7WraHaMBCrmVd-1MQyNFQCDo8wm9WksryTmWc41GW1xHppUG",
			"1C1ZXeQUJQGi8GL7iyjoi6zfGd4EC25981", "1JAnM7Xy1xJ7Ufzv46CNiVwrRfV2esVotL",
			"16wNqcVCy57GdtTKcPfQaQmtmkyGZtMcBA", "1FecDmcz9uLbDFrFQz7jbxCi9mFNHnTisW",
			"1GtTeRLhsvyFjS65Jcs3fL1R3Myhxi6S2o", "1AdWkjLpaCteeKznVbCXLXFoxymJeKz2Xz",
			"1EccR9M3dfsVTPxvrEygFWoxyLRt6r4tnZ", "12oipr5BLUhanfsPy9xG5X4Foai4Gh9FNv",
			"1MsaU3qFkz84Lid6RfiACSf5qqLbDgdDwz", "18GfHvyRd3UWRB8iVQpKJg1trVAbL8vq5G",
			"1JW3wHLApAgwpFTYg5KTMc8MJe4b2E1vtd", "1Cy2FFLrqDTFzeScfieawPafAXj2i8ybK3",
			"18dz62bLpfMepyg59nCnkBrufrQAz2H5gt", "18eiw3eMbW7iLDvwrpyJoVRA9drL2DQeHa",
			"1Jgdk1NcMVck9nXPjJSE4LxBtG9SsEEWRy", "1KFVdnGMVPQ2gGJJZNKWQq5pJP1yVjUcLb",
			"1Cw65HfhdehZfu3MEPwpUhdcooBSpy2rjo", "1CDz6WgBbNNXBhbtsRCLBXHvnzZdF4YMeJ",
			"1JmFmpbKq7S36J7w2zpXPhJHFFj9Zc4Gbr", "1Kr5JqiaCGo5SugDPdFSd8KJ5GofVhzhqW",
			"1G1tyHcpFQzQyxMFxna8mB99kPfaQjdLPW",
			"2-1CPjYDawNh8TDmWi3be5fz1oLT7M9z6N21-134Tft53vyYLAEUeo85JA7zhVcck1o63oc-1AmbWf4kXhZR7FrRjKodA6TDKScrvbfJdh",
			"14F2JkzcJBhkGFExfPQLsWxzUtZaME3JSs", "1FNqi66JVzE3kou4QQX4SHDAxvYLVpDg2U",
			"1EQ7zx9HLdmBdfUUwkA2fnCiKF2M5bEzwd", "1Db9bf82bZT5hnrZjjqoagGv2nQr6X5dLV",
			"1MS76xiUL311BSxkSmP4iT2zN74e62QyUP", "1PeCuCMcHcvBHzQTKr1egZRjZJqQ1UnLb1",
			"1Fp1oVFRGBpShbWLL5VjRsAs7sVKKHNE5y", "141Np5ZGtNhbms7ArCrSmALN8q7vVUXDTw",
			"1GVSLaHWsaMHkEqFYdrFbraHxLx8Mi5RX", "1Du2DiSoiZKEK22jPHWvRkyAeJH7KWFckQ",
			"16Ztzi7YofzJx1Wm6xreM1VsrQybcsWem5", "1B3zoYVVRNXkv7TegHQ2xrGbrq4pdZyG2m",
			"13pCKj73DbGgGcteSvZns19vF1XywyNsJU", "1M5VHFAvwrTgeFVASZHeiAbETMrbaUWHqE",
			"13rUkZv3mmTqSpRMaPmKhnBJ3f139ncAeA", "17hGzZMUzMB3r1MCeHefohyYkfxXPKfKN1",
			"1KVcWB6VZhmAJgaWez6pgETEoX8nmAjzmE", "1J5qXLjQ3o5QJYnzriQdsLhMNCnQVBGU9a",
			"1J4Z4VQtrnFw3MBPtgeXyAgqf1jedNhQzS", "16YBpNdzKe58KmwPA3cBaf6zhqXCdTHiGp",
			"1BLrpCrTXa194ZszTNYoXiT6VESR4nyo8M", "1K414ZEozoqWr5zjMpFjYuKtGTXSAJb6QE",
			"1AokP9W85pRrH1TdvvfyAVuMvPUpi3Jyie",
			"2-1MpUNJTgD1Ej8cQBz2d9EiaKmAWWaLGvcc-1Gb179YipBzUMRnUgzEjLR4dAaZWzHnwZw-1NbpXbBLDEwi8QN26cA3zY9nLFuxqfCPjN",
			"15E1CktEoJGbwm4XNS9aKk7ijP93jbWNuV", "17kGd5dKGSssNn477Rjd8kmJdsrUSM626Q",
			"1ByBfJpqvZs6QGutzh134PEqYMpsM8Xwkq", "1BUZTuymLjkwEouiQuyR2Cp2RwLFKQ8Pdc",
			"133b63y8JB8zFCVnE6YhP442nnSPKj4YfF",
			"2-1AJxRNCFEgXQYjEKQNuFFkRbXtETg63KEb-14E9Fyoyt4a1USHPXhsBKAUrbEkYwyN4bt-18D6iK5n6H9jetHKJBReWaKT2NdG6Y9XD9",
			"1GGaL5BZM4LMScS1mphamvC9PmkTDT9VFw", "1AmmLPhnrTQAse2dWzTPnV6mHYjhYVCr3j",
			"1CM64CTCd5FCCX7kb29g3mghM8GkT5c6UX", "1888888UhkzqRGNWBVcmkXcFXeca676BuY",
			"14G3vYt7hSHkpf8e8A8c3nTvbegTSv7GzP", "1KDhNhA3m1mg1CoB6CT4tgzjAKBLqUrLmG",
			"17q6DdCN4wnURrsBVduMjkpAGygmiwaoHg", "1Nwg73ThKbEtTJMxL8Fmp3nXPoB6F2sANs",
			"15sqodDr9GR1JsuKa3kTU1a75w52Bw44wG", "1BCC9SkVuBmnWr1gocQgCzTZEjdvupjK7T",
			"1KTKP2kacPMA824UJ7BN9PxUx4fman9M6m" };

	public static String[] listOfAddressesTestNet = { "1AktLQnreNm585z1r1QbAjgrrJCuYuXtZm",
			"1FuMDoQh7fj7NTm1p6zqhgfpTXBRVsXm54", "1Naira51gy4pc6hnSeokQ2mCm7tQ5eZyZU",
			"14WMdsXApZjz1ybye79nA3JJf6h2R5T2SH", "1EranfPkcFFAwbXDjzTix1m3AxkXjr7ZDD",
			"1MhLm4VaZ7XWPPsFrZnqUfUUehNhG84Ww2", "1AA6KycbJ32bTphqFKZTkSCYhaeh348jkE",
			"1JJuVWjefhYk7VfFi5D7UovVdA19X7hCv5", "13aKeNqzEzNrdFV1SfbZ4Spg8x8QqM1VpW",
			"1BH2KAuUpiuhkszMA9fenviQRbn6pHgGE", "19MxTmoksJEj9CgzifZKcy145Q6sDpMo7t",
			"1GHkwabM8z66PiTfcNCTTYkNnvdF22SQwY", "1D7w471ZDkbtp8p8QZoMGduzJ26V9u2Jdm",
			"1EwBpn9NNPGE174NvkSjhtdAsFdUCiHdk7", "18LLinoGvUvSSQXLovt3fDWXwMSfmS1UDG",
			"1B7jF7NDfFSdToSiPvYWnHAxEUmNaMfYe", "13bgUr8JugfD2rb5PjYe9CzTPTGPNdBaTT",
			"1PACs4zsCX3CLeybt1ijj74Jj4q71wrBeL", "1FvqVxaCtsxtNTBTYndhsS9Xa5LxqjZGoo",
			"1NTQfZFBN61ke2Dxb7dQ4d4oNEMsf5fq1u",
			"2-1P7JAqMWndrh7r96mUtgtHJpi3DDjB6W6Q-1M9sfm4KVEBQtCBFvesq2GuCJGeBTfNWYP-1JwefaNfiTd4WzKnXirdMTmzp3eaoo341A",
			"1DLbmALgy7saTfpEsbweP1es9CmVVzHpts", "19ToAspzr9VoRW5VgiSEGwSP5xHgdM64Z",
			"18Z18VmxKiXRz3Zd45ejRv8vtkFUa7e9GS", "19Zd1HCUXyveUvj7QwHEqWiF3To7wuv5PQ",
			"1CbhHTFFaUjmDwwFYucSLKRmD6pUtKbVNN", "1PXbfNGNGB65kTTd7bxeD7RBkPiL1AJDiR",
			"1FTqLnbjCUiF84cbQMxaguQ8sU9rikKhwW", "13jRQ6xKNiRq7uvMFVmV9kQnPVbxRe385W",
			"14CpY8dRsjfLBzussdADgAQwFKbeWuz8j3", "17gvkFpYV9842qZriAtN9S2L28sZnJx4oE",
			"17SnP5CDEUKPZBa8yXTUqrvtnM3VHB2VkV", "1LJudB5t6sq8NTsDj2Cg7SvUq23z6Q1qju",
			"19m3QXzPX9gwZ3iD3gq2RS9EuqwkP6GkdV", "1NMTFJ278TJHFoFPZwxV9ZyseD6inn2Zj9",
			"12LDkCjLBJJSXpfrTcR8dMFVMtrvmF2Prr", "1MneNPGDvscZdn1aGcVKcRvLoauCBTRfaB",
			"1NXscUA1e7EDDaPEDwNgqP53BF3a3yMQEh", "1QEEd5GjiWaeE6ZdgB7UPteyDQpMs6PRSp",
			"196PUcEJfiNYMdDaXhnqgqzzA3cjHpLmSt", "1JPVXfTmC6xwbYW2RgSeM6iKZP3NsMBByT",
			"12S34uZ2HDiVgdsY2sV8nZpQZHwsENiqoH", "1BEbASKFwGuJfwLHFh1jSTWSr6RMviN9hs",
			"1pr62K6d6MYddi6LfY5oPoaY5AEfn7Utx", "1NU9xzCWnrZCXHa3rRZtdi1QqvNpRMRhvH",
			"1BemsM9i3gjshwmJ2txfjXXnFHfqWs8Huq", "14erK2S3EDZ1YnKnkRJqiTopfkvvCQRXPD",
			"1CiYTFjgVWJG3GFAXehmyp38Z96uHPTqut", "1BeyK8UPBAuvKahaeLbdDBwuqu1WJRdnca",
			"186MNGTmgdoesFNhCvQAts43K9ys13DjRK", "18u8n8GjQJavb6Cs9WywR2ZhfJxUKP3YJU",
			"12py9NUvkLQBqbBS4De8miMREhjpUrcKZp", "1JoGVcZdSXHKKH2W5YjxwqectqsTT2QAj5",
			"1friNTBnsATv3Voyujd5Fn2eDAYXcNE9S", "18jsbdMm2AyiAv54rrFC5ncnQiLTh8FzoP",
			"17cHZHiKHpUk8jmSYCKqpzT3YcaPom16Ev", "149pNpMNVQCCzgXHwckkkd1u34efahQicQ",
			"14MKronX9pQJui5vVENveDqwKFE6bxWVk4", "1J23z9TRWiZRDHyE6xQTsTnkA2g8cPp3UY",
			"1HVPu1KhFCrjvzZLHvmU1zvmmCx35rCFbk", "12VZ3shGNn8wkt6tWYSDSr1ehiz2NDGBhP",
			"19NL1hXyD6omKMMbomui9z1o9mgYENXKva", "1MrEo9uD8tAgHML5JLzo9biUkKH7ggcsp",
			"1J8kXzvDLHuQQTTNt7bNmMKUz25RPJC8wz", "13Qnwu6UUHrPfa2bo89v4kqKkUrDW8Z2Cf",
			"128XumH2QsZ4BGo6jEuTDjShcretUxDqLi", "1GL6C71jDeDsszFnkdYdQsYLxtJL2arGWm",
			"1PuntP7sziEBVfDHWsPXiDEN2ZQo1YibwD", "18szmRivgPcsPVes3QNyR1Xne6ND4xj8DK",
			"1Le5NkD6aYnzZPKCiadVAPzcbWsH6CjbYJ", "1Fd6r9JrvB1W2MgU4n9vkjzG2NJ5Rx8b2A",
			"14f4hDX2z9cwkpZuSZqiK2uj3aakNFnoHx", "179ConMuWNepwGCkqaiJuFfqEhCvGANGwH",
			"1Fm6b2qFJvp5HiTtBJn8vwsgynm18yD2Ru", "1DyHXmfFqfyX2HHt6RhqXdePDwGELwUbJx",
			"1B8vxvHfc9whEkhKyUoE9fMVNUbAaKehfU", "1GYvGYrUqLGzQoErLhsegPw3nygpNFwZR9",
			"16LUs7scDuJvGk4A4LaR24DwHiXuwsZuVf", "17MDYNKvWD7JzB1aoxX7pnCiPYUrC8LQgn",
			"1LtDqWvBoFDF4aoAYzBMmsgGQ4g4HsmYJo", "1PhcxuPuhGnDH7GeAcYTDkky1cdcZt6mZT",
			"13iCYHRpivtTNhYKc7sgC2AxzS3SzNTuVD", "18jbntdbaQLEkc8sknUed5QyJ2AgAy3a5V",
			"1KeBxECUCqQ53MoJSbYvuKCe8idDGUHoxR", "1Mmm4c8PZvNttyzZLJJwyMR1QkFMA5GF5P",
			"1JainHFBinv3cCELLJW7scdxucu3hU9mgk", "13L6Qb9VJXS4PvGPbtWwyz8roHwC7DEHZX",
			"1Gx4nmEt8VyNeSjn675o3bHZ7VSaZjS5yk", "17o4Dyfc7GMbwMgaeXhQQ74nqDcdmP7RVM",
			"1PpUpmpGN848rmiVg29CHj6StP9GSFvCMe", "19fLMHcFPX3CiHXTDUnNJg3ES23yXQTYbv",
			"1K44nMoCmDEf5Wa1v9Y2UFUiM8Qai99RQj", "12B571aTWKsrq6fohnQXjT1nhHcC2617rx",
			"1Gc1Zz8VTrnTrq5DLa4wZ8CmD2AaSaUJ4t", "12bycbUapdamVGhrnvUJiooqVQQ95Mei4S",
			"14UwzH27d7YGKvkzQLbp2EbnAQErkSHTKB", "19yegjdDMdx3ArzASkxkpkpyycSpmag6sW",
			"1LmJ7z3WezMRWyKx1ZHYdTgg9cX23bHNHJ", "1ATMQmcUszWtFpbLs8M8iZsZhS6oZCk7bN",
			"1GK62XAceZtLQmikjNUVCQrrLRgkAN8GfJ", "1KKV9aNR1TnWnHNzqpX17Jio8NMoXLycCt",
			"1JFAUpkrjwU6cn8Af2npwaQ3c8b4eMsbSw",
			"2-13vY9JGHRae2z4s3wKWtCBdbS7q3qwhguJ-1FSto1iU7P4rbWFejPHzUT7Fkyr2YNSQFS-1PdNdK4YuAcPsz49iFhyMuWmW76qtSzkkZ",
			"1KKVprQg2zYLceAYbx8kJxbzGFUn6bUX1C", "1BgLudnKgbYGuJkvyDLzS3qgNaeJ51ngoQ",
			"12cNxny1B7mmMMNTZgrsXGiqk5hCZCJnGu",
			"2-18sKBw7LDtVDCF9qW6fgL5Cix6t8EJbazL-1ZUK8tvVP5MgYxA9K81ryLwSG57xpbsdh-1PywAgQx2PrywqaWqEwDZ4ZyE4akqsEG77",
			"12XRgca2NLFWTEsmm9gg3FqfDSYhbnCXcD", "1D8AJUBvkFLiLi7qqzLNe1SnD7sA3duVNP",
			"2-1HTTuVyKtcDcQJtFUykU3tWSJUu3Z89RVy-16XsaUzxBqXRyEScspkvNhgtmKZgohSjWf-128iuf7pgWu1y4RUj7VttEVHpU5uoASPmW",
			"1MVqoPXUTJ2BgFetBFojvtauotz7TGvnka", "1LzCmnXqvNApPN8BLcyUWnddc7x4QQ5caR",
			"1BrKwsVmHvHxnbgGnnQ4U2KC4rV1a2nUS3", "18brPBR9c2eNuHMtzEhr9ayTxCBmipzrk7",
			"14LEPr49hNgfM9EaDU67UFTwWey8rZc3wU", "1M7rCmfjBGxwGRtZXv5K8Nzdy3A7v4QTPm",
			"13NeNTpwFW2EMeh8B6q75t6Vxj4pGBMAKW", "1HW2VtmAuomDjBPAqVVWPWfRuDx8S3qzMK",
			"1154rigDAHKtLQKRZgS3cz9PUxouck1c9k", "1Ha2sMvSMgow6Dz6bqhXLfx1d73JNPHn8y",
			"1H1sAkEMqJ6w2yms3V26nzSKcjtkVdzkkv", "18iYdBTcAY2hPZiKHqDz5D61YjrKgJ7gyY",
			"2-1F8kmFiJkFqtbAhkUEDdwipitGNZULGEec-1L6N25HHcUupvb2d89xcrAnsWeuFbBS3k7-14xVGvBQWgDDE8Lr4HBAepA7VHZWsXzJWh",
			"1Pw6qbzQivyb7kBpaBrQedcXJt7d4uQu9G", "1AZMaW5vswDpboeGfNVA1ueoNab1nfHfCe",
			"18fjqZuCQ3ZM5XoGjAKfus5PsRfoZmf4EH", "1BP1Ai1CurMqtpvLx7jU1NB38oe2Vrm1b6",
			"16Z2uhUBhg6VWk3eXEpgbbXVTm5Phxove9", "14W27o3HBYxVRkeJj36Qj6RHHDj8ga4fPY",
			"19n1xVC4JWsx8GhtfoxdDfoN4Ensz3mg1N", "1KeFywuPFoZVUkmzZC2qgNco8qU9rPVTvR",
			"12v4d15AuopsVzudct7J8LJMm7qH95SxA1", "1J75pvyVUyRt7JpW6RQHzYi99QzSwkfjAS",
			"1GmY61vTsBjvY9cqvQY6LS9BBy6JSqjBYm", "17y4rTjnqLe6AST5Ygv4DixagaRj4o75Wf",
			"1NUtkYVqjF6rmUkSrZEdmmRjcq3fgWAag3", "14ndafdPdqB2wLQddXT1N4kaVzvgdgi9eY",
			"2-1DNxCpKHDBHYzmmasV1gC3166ndbLR5tUX-16bvdPbFynNXWWyKXuFnm9KrcziKVXxFhd-1Fi14hM1XP4noNwjHHjJF4FgWF7r1kNdau",
			"1G2pGEM88fqnMtZdJNCLF8wh7T6YQSkWog", "1F1NVgJe6KTNRucyZTZvWoEqBUFnNWRSMY",
			"1DMHGapZs43By6siPrzZUgWxCFt95V65nk", "1HLEZL9LxSmCeZMr3XkMsWYSUCC2EXUSaL",
			"1KF6tLemSoVjgtQ7WiFNseqKdnf1HZh27V", "1CcYJtqKDTMPtsPfiuYJjsJoL9nj5f3b3s",
			"135uZKXDhYYhiLisdCvyx3fLhKS1mbp8nE", "1K5pv63rag715mN2REYcfMbQs7RyukPuSK",
			"129yMeagKaaEEyQQz88zAfuaeY8YYz26wL",
			"2-1MgJG1Lan1f5gz9S1MZFfL7AmkKjCyDjcP-1KjUMwz1GAXRzzQMEjUEJEbqsHR5TkQjDV-1PFZvwoszumZi6cYqYjJxAJbY7nRhP6q8H",
			"1nMrSrMAiEDWopZKPUFQC6fBx5EGAsEdH", "13ujSkcNMmzmNHfqr7p8ddnH9KHZc5ngx7",
			"1EePAYbyhidFDhKsu3KsfnY8BtmcTqttrb", "12iDQNAbfVduDhfWhFevyLCJBGCJHd7NmH",
			"1HLFG8EuA6vsfUi5tw37PcUCnASqA4x1w3", "1G82zewBq8NzpC1gFHVsPRC6K67mFNon6a",
			"13sUuLiyLU2kdxRpfsNUJXgLR1xiNdadBG", "1M5xPWSfQw4qAhSibZuaSWrtukso1eMmBq",
			"1P21GkkUFt8ME2U65SbGwutP9EZcuQoFs", "1Eq8bqMRBxADqFVW1YUP1zYyufYBp4kfs1",
			"1Gd4niapnmcn6UJpByhBJ2c6uxkpy1k1MJ",
			"2-1J6YrmvRZMvw7idk4JhLChhxs4aBSFX3ni-1L4EoMA6BPeVAvedykpDZNUmQLK6JQGefs-14HnMuS3tqXBtoejZg9pusiMzrZR2Jnezv",
			"2-16n4kKvWVqJhwQY4dMdt9M4uVcxdajpLFr-1PFU1NvhqSXwgw3RMBWHgvFyqgPgkZbnkP-15q28uDQk7NNKn7hLWW39w8UHryqPt1gM7",
			"1KazZQVFXT1LdTAKPh4b36N1ETwJSJv4Rq",
			"2-149YJRmvksFwxhVsdBkWM4yhgrwzTSzkhD-17S81r4HEYhxx6jCYwbUj72F5SwhdUpvSA-1M8nxGnxwkurzkfq6GTLb1cPf8PPasrpzm",
			"1BZCTgfergVvwwUx5kMDFPHYKjrk3fnf5W", "1FndprLxopAaZFMkWDmACEhmS2W9s5CVfH",
			"1LhuczTy4Ep4VVXQHraL2jAN2AZS95z1nE", "1odajeaZaCpHrxMPKq4b37KbsKnGRkxwt",
			"1zuDxdhaLrEyzmSbKrBRnJputgccSY5wD", "17GiWvV6Sb4damGNKUFzNdesBxYXG7cknm",
			"16XNyCf9rEnn7qxPeaQPDXz4JY1FhzDbDA", "15MvcgPMpWLZuRtTFHhE8LBTGP45CE7Cos",
			"17Awa5AbqkFiUmW9PxdBhYFGs9n4BWBTgf", "1EDTRFD6JFb2N6EYzAcB2RwESLLfUbT9a5",
			"1P37KAuSLGUmoRTQYWqeXtTCRuLsvszuZp", "14d1PAGLCdLA4t5KHzPhCAf5V6ekYhDCME",
			"1LZNS7UNiL3Q74RuGTzY593jMP8t2hKfDb", "1B84kRYre9YHXoASmAAVvaTRuEHNQ1tQCQ",
			"1NJJs32wnaA6NAjhTGPg1KyKv5R3PrEw1Z", "19bCRByV1WE51vfRqEKtweJknfzKevm7wM",
			"1EmrdgNEfsqDwfGF4LygEFyowTTZTPV5Uh", "1LcEW8ycTnjcSdQRZ1o5fz1c3GEFSwZtxQ",
			"1B7R2yexLNxESAhTS6K8fY9epLXtzP2QN", "1Q4P6QW5TmtB8u5QsSoo8FihD7AphHML1w",
			"18YLC2tD1yGEdaNitXuaueyFPJ9yeJRwgL", "1BVap5sg6gTNg9VkU5eu5t4JNjbLAxEpRM",
			"155VUG4hd9pGYsQeuyMvrbGgzpP7nSzJSn", "1EY7UYP49CdEwVWExmekAKGUwGPppepV1R",
			"1HoYtFhQbYnx9UY1dfWfoM3gksuEP8397m", "17qB4S8KJhovDj2ag8iFtQfcW2isy3ZJj",
			"12NZvfXZ7VpuxCfWJ1GJ3A927An1vW1Uh2", "15QmJV44C8Zs2AqATqBpvgp8poyCcABY53",
			"1614UqptoSZd5SznRsvoK8tzYZuR7TUfpE", "19FH6UgAHbRSwNr7sGJxNYW7p8NA8ZxwYp",
			"19AKeSTDAWT4VB9WUAK3aiw5KSfLKooTKy", "1M6pdtoTPe36pXtChNZvH4c8mSB6vzSJHJ",
			"1KW1eim7pRcduC2EaTBCPQHy9jCBp4BsX2", "1HJn55Y6wbtwpSh2rauTy8Un5Cduk12gaT",
			"2-1HPRacvTUSPXzFB1xuYkAftZ9R9kiLz3y9-1PxGBYYjjc5aKVe38s9JEwqHPinGVntyn2-1Mh1bm4MJA9LFreL5fLynKR4GEaEns4xGU",
			"1DHKTB55ZPEQ4Ng1rxwe39i5jiF5ebmYHf", "195zUtZLEHYvwDTbPNkzHBBF1SoZThDZyy",
			"1GR4CaxvHwBgDVyBXroPDPkjNTj4HRkKkH", "122R8pEGWT4ABeh9BWsz4DPhJ8xyYMq9TQ",
			"1LM4KFSY6gT9HAyQFzKhkXK3A2LafLax1y", "1DsTZJvGo4ztdsBMdEpeqHHMdaTcZXMd2N",
			"1L6KsD32NSo5kcDeDAwNiYuUdpozS7mMPs", "1LXzyxj848i1d8QTnoVegaYziSYz7z6DNi",
			"18RQH8yB7qMapzqRQ4Um8qdHvs1EcoKhsY", "1Ahauxg3uxhcnxnSdmqKVhomz3MF61ynxA",
			"14HADDEQz5KY6V4VyP96War1UqQdq66W1e", "1DksYUW6J9wGuArxM6n1eMhjG86mqxnmA8",
			"1GzikhiugNF4uHxsWaLHgLBsD4GUeMQiDy", "1ELZkisGYyocYdSsd6MTsbXAyHWwwPBuGz",
			"1CiBuR4aYoAfhKt1AhMKiJMGwkSCaQuWxm", "1KHsvBy9B7vxMgoNL1n64ChfTAczbp1ASh",
			"19Ua5ieT7V4opixMv6aSc2pCK3Rp4sJz9T", "1GQjLN8jEVWLEPxVXgYszbwhhDurmxar9r",
			"1NArvaEWFrt4R6G6Uxar6XNM4s3h4sBqkw", "1GxENbATk1HfUx2ijYaSvRDENXBymF91mw",
			"2-1FbmCy3vduzEYjyBmYsLyUtVG9YUiuhbvB-1aq8giKn7Fx6DvU1wr3egymmnr6u2CKWW-1374o987v7F52Rvxtu64xZRnLFmfmNK4dY",
			"18FCNW11Bv5emmMRj7P1pek9hxXXCZqda4", "19HrkEdm6sbwdm5cGoPAaY2ccxpUYAA5mm",
			"13tLKf7nhd7t1rgnT5mVMNpEDyQbbbiABJ", "1Nqf2fnLHEt1qLxGHFDmVspy2c4b36FeFo",
			"1ABpYSgv6tcUpF4ryiQarGy5Rqfy1qwC9v", "1AMbK69tF4rpjotkAekQoEiFiA9xZaRZ1J",
			"1JEhp9E4aLfAw1HCaXTAQMUGRLGmbP7Xh4", "1HhRfRPDLcUk8J1zpURFug1xSpacaeXCtL",
			"2-1E9TZGqarDJgFDrj3amLhTcgEqSJuYGjfh-12mA33A8uh5HfKKrQtVGMDMwRapcmR5XpG-1DC5JMZzh8KNSjgQgGEwRCjN6FbVe8x27o",
			"169R7xxSRxZXmxNX3YoKNTtaYQwHLNZNao", "17736rfhafvjFk3usfo9zQFR3PfHsvtiYQ",
			"15imAyVyDtsuwUdLccY52epJt7Kakg7ZnP", "1HsFYGTJddaCspLWCmxydg8vWpeRPMrEmF",
			"1B5WmWZi8twk83iaN3YbjmZEYgvQRU5D2P", "1CQ6koPvnna4d5ENNhaEzRHmLEDegMaP2S",
			"1LvVTc9TyxYVYdPQcqKxpNoJfbJNdGNs1b", "16bekfRKhSKpUkz6brFCyKXwqJobzmM6mi",
			"13ckSm9CJL2SPqiQVrDC2C1UxLBUV2Y1Xe", "14Tc6PU1kzLnmooPHXdsy4ePtPxMgoXEYj",
			"1MaL859XbZ6AKrzCj8Q73QTGA5fVZ3mhYm", "1HNPGJmb1bSUk6tsbJgsFjVc8iDJ2uNdgN",
			"16E4kUAf739pK7c48J5uJpcdB3fUN39Fcb", "1ELCSHBK1JQQwDC96MFRAY2MtYYuVF3bwG",
			"1GC2SoDHDUzctaKGGn9mhhBu6j4qaWCHFN", "1CkH38kWDAf6d9EwVU38s6fjvAShu6tfvg",
			"14FPA9qD3hFDtLjZHDy2JUDkixFtEXDiwx", "1CVUSDGjd3jWrmGU7Vb2VzN3z6eMaxZ5dn",
			"1LovVqqwnLYzVUjFsycPDtx334BmCtcha7", "1Q7aBEendYyjtrA4wLPY2aHMYfD3kuPBXX",
			"1FT3ko6cZwTsNWxgroujBpsB3wrpu4NQ2a", "1B9f9HChAAonTUxJPNU9PkUu2zRLVA1sLv",
			"14yZJxhDKQkgaK19PEUpguaPtBrv2hbJxe", "1KDm91GSEjob7PyVF5GUPmSiMg4KMSAXsY",
			"18VD8S7Tc9u9B87u5DZAbbshzxLhmqtE9R",
			"2-1EahcFcCVHF8N48Gv3oejk5xhQLbcBmfW4-14JM5jNjgGZYCAiV9Nvk457HRPboT182FR-1MVCzDaTgDXPjzUna3DJoPG9o7QcbR6o9N",
			"2-13pLi61HGaK13ToqR4nfBDBSq8PuCg4XiX-1MyVGJUTdfE6S2na5ZAmWAbimFWzmMBu1X-1KScv7hKYeBT6FsUaAy2mtsUFrf163vz6x",
			"1CEagPKG8WL6oX5baJ16uWacD5kQRCCQvk", "12knCup7U5V8oQ3FWPD6Be9L4HfFYD4MDZ",
			"15vUxBo9S8Seob94aAhdsGps7MEzBoViPa", "16HLVBKgSmTWq7igbtVmxKU2YujuckpEnA",
			"1PCmmJu8c7BgUXjnM8QqTQ1Yu5YM7MeBDa", "1NKbkA4KxwGayoK5cBeQzPMVXCXAbcLtdp",
			"1JuW2mEzofGwKrYfVx5wW67hrPvpBAJd43", "1DxKXSnBxYcfrMqRtzrX9WbFYPktSRa5E3",
			"16tQCYcfJMJ2n36T9chCbNUQiB4hZeCB6j", "1NWg1Mga4n5CWLwQPrhkQdLJ9fJdJy8zbV",
			"1LBKdycznxbx5egJBqz19ijugwHcHFY1jm", "1PCNXireXa6mZGub7vJ8jRCMnKj5D3sVtt",
			"17hdJnw7J5jvWx75nPenQP8hfstQSnw7uG", "14skxeEv8cniqDm2TxjPumM5sJpghqZo1b",
			"1MNStNrkraqYaawKSNaZ5d6GqzJm4EGrJY", "1PBpVC2P5YrMyabyFD4QiS5jvhGCxCKANa",
			"2-19QKxdx5aq9L8Z7nBPrBhQCQLP8tD4SyQQ-1LFmQMM76N7N2GvDqmCLAMCfH7GvgLP8dL-12NStY7wC9cFzUurSYd7ffShCY4stzecwr",
			"1815FyZMLvjTKiTSmxVhnw81imJZeScdtm", "19tCadLqSh3hD7v9W2AomqKj7WpoQFjJYr",
			"16SxRuDSeUQp1mDnaLTHBMjWsUtPXup1dZ", "1EdmcmNLjtzxyrGcZ7TsMB7Up7aQ4qSFcr",
			"172aiWHKukWFGE11vUFniZVU7kp6ZRUZyU", "17KWUsihUPxeiVYxFZqTJxFtno5rxvV89g",
			"1GVdqzA4ULCctJJZRwKX1mRpYdHfEL1WwY", "1Voidf1WWAwqMd2uMLr5uXisRyjPnWRA7",
			"18ZjiPMDuMdMqvknMdhbpDKBRbBXywCNKC", "1HgVAQfFqSycVMxRUtmWUc772RS1BQU2XF",
			"1JyuxSxPsd2A9HfF2X7EbFFZfSAPZPs4M5", "16K2qfVXgwzAJkXun933zPp1CxpqjeGLbR",
			"1HcpjzYFoonrhFYDs3AdUxbS7Qu6fS1XYN", "176bEJsG2tHEo4bsA17AguZN9jMU6LmYBX",
			"1HqxgWn4yc76auxd5GTAfP1ShzHmYHg6RA", "1Mo2U2did2AFKX45sFg1tbK2z8K8LuHWyu",
			"13UCGZ4qnGwMiP4PqqUx4Qi6tqQfvcqt2m", "1FBSjuR2vFqCuSW5QbfSG6uz3T7RVEwhGB",
			"13UtpxQJuoTigEeeuq4HrkUvp1uyPxYXEu", "1NkATnRy31UTDpkuzEru2o3nGzE5JYm3p8",
			"1DE6puFK3MFZxMtnFiAWcLewQNqpn7GxDV", "1EAesPzaxxiffDVHAvHJZWxqNNyH1yq2YZ",
			"1LE1hYNegqX2CQaPsuw3ovQrbMKuAacbFd", "1GVDe4y5VwZ8jHNuF8HxXuKzKSXPfjF223",
			"1FzxRgEPRcsjzQ3X2tbZPUkfa5ktH9vG1Q", "1uCjf49oZSLaduGNF8nPgCAeEQDzYqEgC",
			"17gJqvqZ26i22p3sKaKRqAb12sZF64meFY", "1AZ52RzySPbdbTQZwxkUnAuYCnrQgQ1UHD",
			"1CmpnV341LScP1iakDSJqsf6EGinJjuP6G", "115docnYkRJopTHTeEgTAii7Jv7N5wBhSz",
			"1KyDFiDR21fo9XL4Dp76UJV8ThfdzYuqFf", "16y4wASazPdKEGDcoxDAL3CWrfBse3g5B6",
			"144EvecNyPVS3wS4uuVgcQrnK6UreKFk4v", "1GV6FRbvNuunDxRdJ5UfT7oHefwB6ugcZd",
			"1FDg5uY5SVEQexpwmvZcnLGxFRuy4q7NQp", "1B5nabx9sJs6somo3PhN2zC65iVG8uFTiQ",
			"1ACajk8H6fknesYhJVSVw62faDQsUfxzSC", "1Kf6ggJHdRS8oeyjDSx4MouG9Bdzr61EUf",
			"1FtdAZvrTYNNkgUR6WHmM8q9r6mt2g7tT3", "17aVroAkjg5dS7h3fdfKav72kDgivavc4d",
			"1KrJ6e325yXVrNpd6NgYcuiRfvTcbZbBAR", "1JHyxrRhvhpKmDvkcVL6m6v4pZs7iE2N87",
			"1K1eaDW9TvBeVxo5xafQRoY5BGhKMXsRnn", "1C9Hg66TbbQtoaWP3Y31MCQp2JC2TmVPd5",
			"1JMGMnkv4skseqfkcSizUFdT1JuN9wVUke", "1MqE1mkEX5Jj1VBsHuEkGDiaFFpHA1E5LF",
			"1D3miCgxVoV3yuJDPG4RAtXu8qnLdRE6j5", "12ucKWqPxXBxctauuPcJY154rTm1bnPNDM",
			"1J15wnNEE44Nn7yVH6hc5gVc4EZn6ejghM", "1MzNRG1WUtLgKTkpxMnUAh3F257Wkg1JQw",
			"1STmcsewju8ykyuUGkmjVEuPiVyjXc9Pj", "1L4kRvtiQvgBj4J79GhLyop1VNoYkFaN1J",
			"17MLkiXDu4iFoaUoMPhaUrvjVSSMuBkJhH", "12wspBHpbn3UvZvmNUFQ4tWephVcx9Nt6Y",
			"18q3cUrw6g7CGs1mPYuxcQ5XaBFqU4sR27", "1D3rqNoj9CjYFqSv7nzL54BFaZMUGkQ29A",
			"1HP81SXjHkhYoEAxuVWmJXVpxffwZWMZs5", "1MqjkKzZjTTvXHvuNZzEqSSPhidhu8H44Y",
			"1EPwygdMJXY7wAQUMhQuBZGkXA5h47R3dn", "1McnyFoBSC1eeBaXPA876MTP7PEQGrNAWc",
			"12GTLir6rEfwJpShcSqYxybjoZYVhF9t8d", "16ayhut16RXFeKYTnWaBhCj7DNocRL3u8Q",
			"1BexuDc3UUpMRuxE9YV6TqVWTv9dhEvucF", "14T3ub4q64EFdLsKuvumRabscLUddh1aKc",
			"1Kv6jbP9JWHagfrQjjfVzMcnKEGynZ7pf3", "1Lu7yvwyfsPno1rdA4mu2reGsC6FKHnmso",
			"17ASKQx5PxCM42Ym8MEvaeAPjRwCP3asXK", "19ZFUNJ2Jdwm4yR2Y2uQ4MRNdE6L3fQFZH",
			"1BDt2KN7GcBAvzKgTLUDTogg1zZiWKmhaW", "1JuTYh6yGiSgdD5HebFYAgDrhzXN2vioR1",
			"13StFxJgNBiHMatJqnkBGt1damZPZV4qeM", "13Qr22PY9862o2AMwGiJjELbPFg1EVqjvY",
			"1594FrR5ZbS2npVC3irmtNfKV3n92nQ8Zq", "1DuQF15bDFp64qsxEDJXYabshnkudvfWar",
			"1KcXadtYBJgavHKNfE3VfmTJyVZdXUpCmN", "196fbmwTBkFm3xjUisMBSggmV4xyR5642W",
			"1EQx6f1zYK8LpUa7mWBPTEJ9Ny34jTvio", "1ArturoQZAbmeYtZgJFyRnK5GsRLitcBfH",
			"1Ptc1krYh1NAidPiqovQRjy8YtpcYH4kdL", "1F5C89a5s9tfMYdUxcM7JfaBdZB6i9BKDP",
			"1JRuH2BFgtFgiMaVtCPzvnTY1PYAmEJEG", "1DnXbLXgAoVxwKVmXtETaEPcDLxhb8HHox",
			"1HXC9ra6x5VZy4GRSCNSaDSWw3aNjzidwi", "16d9C5nBjh78JwasrPg9CimwUBBoGArfSr",
			"1EAhDVNZK4kPAoBswQeDWeHkV4taVkqfsb", "1ZcgfRp7sEt3XYr6QMc4x4AkYKHfrkLAE",
			"1K4iZ9FFDx5CyAWKgfTH3hSCnVRtrmm1eS", "1Annm6pLezYsCDR8q32zFGoWqWkERDwaGu",
			"2-17CXevWLZVTooW5H1kCLJkv7tiXZFfkbom-1DFhbHytfbWUpKiy3hoaCatD59oRg7djn5-1J442e3ijRbKEH4VZWxVm4QstYBVBCr1va",
			"17hz5ebzoig5jqvNbQbLeQ5y23TNW5i8sn", "1DEDnBdU8FCjkacRf6Ttc2CQAS7FNFrfwS",
			"1hRHm9hxrvFuyUugH64iQxCdyxeFThWUf", "19oo6gsF6qf6C4CX1yFR7fv5wHPLm78Hjo",
			"17dfDBiqCmFbUvPaXDACNk37TbkYTQ7bQB", "1D7E61eVfnbZJdk4cPWFaDYyQ9usqY8CxL",
			"16oCBjNuxStiQq5RbvdgkCgCnkSpewCr8N", "1Fwpiz9CpFJvtca1aaJP7oQKq51FSF4R1X",
			"1DsBd2BSefpZ7NWKJYgjCWHem6VXSip2PL",
			"2-1EdBSiNBmP6TkJt4EoCi4vPf9MMoWQACXN-15LicSLzBPAAeUznRwLXRNdyGGidAqbENJ-18NmmFBVDF28meU9MFtLP4D3GVjThYH8HA",
			"1BxVCHGP9FZA2BcvLtLma1V3pDJAuo44DK", "1Kk1uqZqZiWWGFzZ8onYGWWG3mRVR6P94H",
			"2-12MBcoBnjxdVBpceCaPCdnq1Y3p2U4kY2L-17KTsyEggmpMUjHJBNfjE7WraHaMBCrmVd-1MQyNFQCDo8wm9WksryTmWc41GW1xHppUG",
			"1C1ZXeQUJQGi8GL7iyjoi6zfGd4EC25981", "1JAnM7Xy1xJ7Ufzv46CNiVwrRfV2esVotL",
			"16wNqcVCy57GdtTKcPfQaQmtmkyGZtMcBA", "1FecDmcz9uLbDFrFQz7jbxCi9mFNHnTisW",
			"1GtTeRLhsvyFjS65Jcs3fL1R3Myhxi6S2o", "1AdWkjLpaCteeKznVbCXLXFoxymJeKz2Xz",
			"1EccR9M3dfsVTPxvrEygFWoxyLRt6r4tnZ", "12oipr5BLUhanfsPy9xG5X4Foai4Gh9FNv",
			"1MsaU3qFkz84Lid6RfiACSf5qqLbDgdDwz", "18GfHvyRd3UWRB8iVQpKJg1trVAbL8vq5G",
			"1JW3wHLApAgwpFTYg5KTMc8MJe4b2E1vtd", "1Cy2FFLrqDTFzeScfieawPafAXj2i8ybK3",
			"18dz62bLpfMepyg59nCnkBrufrQAz2H5gt", "18eiw3eMbW7iLDvwrpyJoVRA9drL2DQeHa",
			"1Jgdk1NcMVck9nXPjJSE4LxBtG9SsEEWRy", "1KFVdnGMVPQ2gGJJZNKWQq5pJP1yVjUcLb",
			"1Cw65HfhdehZfu3MEPwpUhdcooBSpy2rjo", "1CDz6WgBbNNXBhbtsRCLBXHvnzZdF4YMeJ",
			"1JmFmpbKq7S36J7w2zpXPhJHFFj9Zc4Gbr", "1Kr5JqiaCGo5SugDPdFSd8KJ5GofVhzhqW",
			"1G1tyHcpFQzQyxMFxna8mB99kPfaQjdLPW",
			"2-1CPjYDawNh8TDmWi3be5fz1oLT7M9z6N21-134Tft53vyYLAEUeo85JA7zhVcck1o63oc-1AmbWf4kXhZR7FrRjKodA6TDKScrvbfJdh",
			"14F2JkzcJBhkGFExfPQLsWxzUtZaME3JSs", "1FNqi66JVzE3kou4QQX4SHDAxvYLVpDg2U",
			"1EQ7zx9HLdmBdfUUwkA2fnCiKF2M5bEzwd", "1Db9bf82bZT5hnrZjjqoagGv2nQr6X5dLV",
			"1MS76xiUL311BSxkSmP4iT2zN74e62QyUP", "1PeCuCMcHcvBHzQTKr1egZRjZJqQ1UnLb1",
			"1Fp1oVFRGBpShbWLL5VjRsAs7sVKKHNE5y", "141Np5ZGtNhbms7ArCrSmALN8q7vVUXDTw",
			"1GVSLaHWsaMHkEqFYdrFbraHxLx8Mi5RX", "1Du2DiSoiZKEK22jPHWvRkyAeJH7KWFckQ",
			"16Ztzi7YofzJx1Wm6xreM1VsrQybcsWem5", "1B3zoYVVRNXkv7TegHQ2xrGbrq4pdZyG2m",
			"13pCKj73DbGgGcteSvZns19vF1XywyNsJU", "1M5VHFAvwrTgeFVASZHeiAbETMrbaUWHqE",
			"13rUkZv3mmTqSpRMaPmKhnBJ3f139ncAeA", "17hGzZMUzMB3r1MCeHefohyYkfxXPKfKN1",
			"1KVcWB6VZhmAJgaWez6pgETEoX8nmAjzmE", "1J5qXLjQ3o5QJYnzriQdsLhMNCnQVBGU9a",
			"1J4Z4VQtrnFw3MBPtgeXyAgqf1jedNhQzS", "16YBpNdzKe58KmwPA3cBaf6zhqXCdTHiGp",
			"1BLrpCrTXa194ZszTNYoXiT6VESR4nyo8M", "1K414ZEozoqWr5zjMpFjYuKtGTXSAJb6QE",
			"1AokP9W85pRrH1TdvvfyAVuMvPUpi3Jyie",
			"2-1MpUNJTgD1Ej8cQBz2d9EiaKmAWWaLGvcc-1Gb179YipBzUMRnUgzEjLR4dAaZWzHnwZw-1NbpXbBLDEwi8QN26cA3zY9nLFuxqfCPjN",
			"15E1CktEoJGbwm4XNS9aKk7ijP93jbWNuV", "17kGd5dKGSssNn477Rjd8kmJdsrUSM626Q",
			"1ByBfJpqvZs6QGutzh134PEqYMpsM8Xwkq", "1BUZTuymLjkwEouiQuyR2Cp2RwLFKQ8Pdc",
			"133b63y8JB8zFCVnE6YhP442nnSPKj4YfF",
			"2-1AJxRNCFEgXQYjEKQNuFFkRbXtETg63KEb-14E9Fyoyt4a1USHPXhsBKAUrbEkYwyN4bt-18D6iK5n6H9jetHKJBReWaKT2NdG6Y9XD9",
			"1GGaL5BZM4LMScS1mphamvC9PmkTDT9VFw", "1AmmLPhnrTQAse2dWzTPnV6mHYjhYVCr3j",
			"1CM64CTCd5FCCX7kb29g3mghM8GkT5c6UX", "1888888UhkzqRGNWBVcmkXcFXeca676BuY",
			"14G3vYt7hSHkpf8e8A8c3nTvbegTSv7GzP", "1KDhNhA3m1mg1CoB6CT4tgzjAKBLqUrLmG",
			"17q6DdCN4wnURrsBVduMjkpAGygmiwaoHg", "1Nwg73ThKbEtTJMxL8Fmp3nXPoB6F2sANs",
			"15sqodDr9GR1JsuKa3kTU1a75w52Bw44wG", "1BCC9SkVuBmnWr1gocQgCzTZEjdvupjK7T",
			"1KTKP2kacPMA824UJ7BN9PxUx4fman9M6m" };
	public static Long[] amounts = null;
	public static Long[] amountsMainNet = { 35359300970792L, 2379442055262L, 1615111143216L, 129260240064737L,
			4035447421353L, 33943008310830L, 2615692205087L, 3071436598146L, 234456078583L, 62412255256299L,
			1888153206525L, 10587687501703L, 2796486144137L, 299133277327L, 131292648767412L, 513808958657L,
			61376593378682L, 12249777978017L, 35174368414465L, 819049759505L, 2637817372644L, 9549853183082L,
			4106835448452L, 3470312567053L, 3790830563695L, 61893892778448L, 1015280431037L, 94925797382912L,
			354388450164L, 193530840854L, 2697335620492L, 38247080037888L, 9500840649716L, 15266250651099L,
			237151560362L, 2967692665205L, 20475654067055L, 389048635765L, 1435272526555L, 1971628462346L,
			2306063817104L, 2350383677614L, 1769354454893L, 1023812199382L, 20018660369643L, 35020460053674L,
			18141067397059L, 69117479817501L, 10365308540184L, 16423804313446L, 11430278621954L, 55879132656852L,
			22761454434982L, 442753352449L, 36699440962611L, 7488056900934L, 154607735177339L, 337990979174L,
			37811533264832L, 10238121993822L, 17417052056987L, 315683327920L, 3227138605167L, 2884507924005L,
			198462360931L, 202309951451L, 1149042062718L, 9900722155542L, 32811759028801L, 3997012896819L,
			29020651972591L, 9246106431017L, 544828455714L, 20309149304920L, 82209887487487L, 600409588715497L,
			3207115413897L, 1994221848835L, 1020375792675L, 10246312491417L, 106476468735754L, 1818096131369L,
			28073643462694L, 4177596524853L, 9269778533222L, 88607943938038L, 32610314498840L, 954057093513L,
			9675657267000L, 4323797333496L, 2803170745137L, 54290485795017L, 974458289532L, 380856558197L,
			89341363972718L, 13765477884913L, 3165856173307L, 9288684299845L, 2688530835577L, 2826859239394L,
			1016287653690L, 360436402686L, 72571379395233L, 30612119158073L, 633907329766L, 906111204503L,
			1510122994088L, 25730448194849L, 20476243987645L, 24875144739779L, 7233991568026L, 92546311685504L,
			19489165790782L, 18267858954166L, 437792682162L, 20476243987645L, 4226905820652L, 19088251677210L,
			1845460988264L, 4237116700232L, 4167334340098L, 394673283022L, 55438522121196L, 118779989645680L,
			2048355642396L, 42419967532315L, 5762893514252L, 17577749550695L, 18744800287301L, 628894315108L,
			594182365538L, 87550593159873L, 255202570215L, 59779256752805L, 146147277264770L, 1234933966574L,
			393298720340L, 4573576463986L, 61428731962935L, 1969416522411L, 9197850874651L, 2046436367088L,
			1948837581204L, 2936982249116L, 220022006007866L, 1057523621263L, 20476243987645L, 20891448549577L,
			15339803462649L, 6662284092185L, 5069983954777L, 3972900869756L, 222973499372L, 17764501646846L,
			22523868386409L, 21564044449488L, 42004501381886L, 185787155123L, 2293339326616L, 4765521253888L,
			388224530139L, 198016023066L, 1187370148101L, 953052629854L, 34701090685836L, 4403107387910L,
			38583846866397L, 111359326146535L, 199371036578943L, 17657995514406L, 20819338611380L, 9214309794440L,
			5119060996911L, 1308796960893L, 2343695774641L, 7678275503922L, 32075795032606L, 9015232011216L,
			1857397564173L, 182927432778L, 19185814027998L, 2781514470693L, 4350885855929L, 20885768867397L,
			22938014640659L, 1114846167426L, 10310308268225L, 6921796548383L, 14844141691864L, 45522908869965L,
			19337094920109L, 5825183322826L, 331386521502L, 53596094650519L, 1240890404823L, 388432452473L,
			549493820183361L, 2670127495294L, 6525124066639L, 1112945479044L, 3567385131127L, 678161653113L,
			9637343307359L, 8528189725345L, 9304762338282L, 7948329670867L, 148772710222733L, 6031092040937L,
			2931599208894L, 2215225062710L, 1433337079135L, 4363961448197L, 10243056768623L, 22299514478514L,
			1790051892866L, 49937527153838L, 39731181138674L, 11662216323912L, 18075285923332L, 7454683925336L,
			1737281325826L, 568772705501L, 8785351442365L, 1693417119590L, 5544069456207L, 1025811477090L,
			3804269678759L, 2420119034018L, 5758620915280L, 2530795028718L, 7290126693243L, 971357623710L,
			3977739488371L, 12283698768188L, 8490294454343L, 49941653883274L, 28179354837188L, 645372975507L,
			126262271462L, 3618338747538L, 9578391158139L, 8844253348947L, 2890978796299L, 2617589971893L,
			5899263561263L, 258518584575918L, 9195942793167L, 5711906821925L, 2036039363339L, 8179834866381L,
			226753260974888L, 20476243987645L, 1012661652142L, 1631834989103L, 10155189221149L, 41431017481281L,
			7006363212579L, 20476243987645L, 14582974072601L, 7308203472168L, 204707141373L, 1937817380461L,
			9805609604803L, 19258492054501L, 2559530498455L, 17918659996315L, 650272624642L, 819949071073L,
			20476243987645L, 30496331901957L, 682441238811L, 960090128092L, 19889587735869L, 1393439212504L,
			5389475394073L, 892973977154L, 2842027064530L, 200516305169L, 2444863532124L, 15215618834757L,
			17597464198090L, 5119060996911L, 43022666261612L, 192924611321L, 1258219584459L, 69706138416068L,
			2763179619581L, 514441889008L, 1122920388085L, 42048255020985L, 17392957711263L, 10238121993822L,
			1730053022089L, 25536609578360L, 90147612129865L, 614287319628L, 700149831347L, 4158150839475L,
			3652465820842L, 788509188793L, 396126843541L, 37162335213150L, 29192077317725L, 15630951044315L,
			2646101166712L, 4396349121421L, 669941308398L, 28601995179603L, 4093283078106L, 67369628381196L,
			27829561815482L, 10238121993822L, 15458401615072L, 185810854482L, 461636328195L, 511085509452L,
			24459315831213L, 2404127656242L, 17364123494225L, 5151152723807L, 1439893901078L, 71000428052875L,
			663581981057L, 1012890745940L, 3816333757192L, 1080463737648L, 888390284628L, 10287807615650L,
			1925256721526L, 51310951351338L, 219676602896L, 94988105203004L, 75346455224776L, 142305411005842L,
			9453080810829L, 132955045299435L, 1760936279176L, 2290683972773L, 204762439876L, 140977873375L,
			204762439876L, 8710303480230L, 14785633510589L, 147907683693L, 532382343678L, 3875682159639L,
			2047624398764L, 1612440883410L, 47205642176977L, 18931354037601L, 9255191184340L, 16482969571094L,
			17981128340050L, 11458322860458L, 4823258901124L, 115219363650002L, 7755584858680L, 24164456337844L,
			14855830135469L, 483707025385L, 29711112973801L, 17771752425462L, 1953300960004L, 39627061965917L,
			5901613747455L, 9609995908969L, 3690768720778L, 28092427177646L, 150193220189L, 7266189606557L,
			6973849856655L, 28994627435750L, 197988373815L, 70098439077513L, 202439902936L, 1766440777030L,
			102381219938225L, 526915692118L, 8948684247230L, 10377158218418L, 88565820306860L, 2106556798492L,
			545124697668L, 2647020622733L, 10238121993822L, 3055649734869L, 14955087859213L, 265593948056396L,
			204762439876L, 6409838517482L, 20812022513391L, 355868110932L, 2253649895838L, 210905313072L,
			12949277815033L, 616334944027L, 2323105718314L, 10633687952574L, 5521128479154L, 9453673294737L,
			87130055781618L, 5730022506839L, 85345277232539L, 1966256608218L, 16071126104294L, 124097400984570L,
			16611676187579L, 12490508832463L, 2342912313310L, 1376135917374L, 273332577903L, 1276542136746L,
			6142873196293L, 74781541880436L, 3191214989422L, 2003425098949L, 866844251672L, 205712938624L,
			9132025628781L, 3045959789928L, 208216107570L, 20476243987645L, 7426588338249L, 17761767096599L,
			244937855577370L, 8140685497639L, 701011164704L, 529867446964L, 119760669016297L, 1559436609096L,
			41537072101459L, 4098250715999L, 6708695331935L, 18896669476363L, 241619679054L, 6464704773013L };

	public static Long[] amountsTestNet = { 353593009707920L, 35359300970792L, 2379442055262L, 1615111143216L, 129260240064737L,
			4035447421353L, 33943008310830L, 2615692205087L, 3071436598146L, 234456078583L, 62412255256299L,
			1888153206525L, 10587687501703L, 2796486144137L, 299133277327L, 131292648767412L, 513808958657L,
			61376593378682L, 12249777978017L, 35174368414465L, 819049759505L, 2637817372644L, 9549853183082L,
			4106835448452L, 3470312567053L, 3790830563695L, 61893892778448L, 1015280431037L, 94925797382912L,
			354388450164L, 193530840854L, 2697335620492L, 38247080037888L, 9500840649716L, 15266250651099L,
			237151560362L, 2967692665205L, 20475654067055L, 389048635765L, 1435272526555L, 1971628462346L,
			2306063817104L, 2350383677614L, 1769354454893L, 1023812199382L, 20018660369643L, 35020460053674L,
			18141067397059L, 69117479817501L, 10365308540184L, 16423804313446L, 11430278621954L, 55879132656852L,
			22761454434982L, 442753352449L, 36699440962611L, 7488056900934L, 154607735177339L, 337990979174L,
			37811533264832L, 10238121993822L, 17417052056987L, 315683327920L, 3227138605167L, 2884507924005L,
			198462360931L, 202309951451L, 1149042062718L, 9900722155542L, 32811759028801L, 3997012896819L,
			29020651972591L, 9246106431017L, 544828455714L, 20309149304920L, 82209887487487L, 600409588715497L,
			3207115413897L, 1994221848835L, 1020375792675L, 10246312491417L, 106476468735754L, 1818096131369L,
			28073643462694L, 4177596524853L, 9269778533222L, 88607943938038L, 32610314498840L, 954057093513L,
			9675657267000L, 4323797333496L, 2803170745137L, 54290485795017L, 974458289532L, 380856558197L,
			89341363972718L, 13765477884913L, 3165856173307L, 9288684299845L, 2688530835577L, 2826859239394L,
			1016287653690L, 360436402686L, 72571379395233L, 30612119158073L, 633907329766L, 906111204503L,
			1510122994088L, 25730448194849L, 20476243987645L, 24875144739779L, 7233991568026L, 92546311685504L,
			19489165790782L, 18267858954166L, 437792682162L, 20476243987645L, 4226905820652L, 19088251677210L,
			1845460988264L, 4237116700232L, 4167334340098L, 394673283022L, 55438522121196L, 118779989645680L,
			2048355642396L, 42419967532315L, 5762893514252L, 17577749550695L, 18744800287301L, 628894315108L,
			594182365538L, 87550593159873L, 255202570215L, 59779256752805L, 146147277264770L, 1234933966574L,
			393298720340L, 4573576463986L, 61428731962935L, 1969416522411L, 9197850874651L, 2046436367088L,
			1948837581204L, 2936982249116L, 220022006007866L, 1057523621263L, 20476243987645L, 20891448549577L,
			15339803462649L, 6662284092185L, 5069983954777L, 3972900869756L, 222973499372L, 17764501646846L,
			22523868386409L, 21564044449488L, 42004501381886L, 185787155123L, 2293339326616L, 4765521253888L,
			388224530139L, 198016023066L, 1187370148101L, 953052629854L, 34701090685836L, 4403107387910L,
			38583846866397L, 111359326146535L, 199371036578943L, 17657995514406L, 20819338611380L, 9214309794440L,
			5119060996911L, 1308796960893L, 2343695774641L, 7678275503922L, 32075795032606L, 9015232011216L,
			1857397564173L, 182927432778L, 19185814027998L, 2781514470693L, 4350885855929L, 20885768867397L,
			22938014640659L, 1114846167426L, 10310308268225L, 6921796548383L, 14844141691864L, 45522908869965L,
			19337094920109L, 5825183322826L, 331386521502L, 53596094650519L, 1240890404823L, 388432452473L,
			549493820183361L, 2670127495294L, 6525124066639L, 1112945479044L, 3567385131127L, 678161653113L,
			9637343307359L, 8528189725345L, 9304762338282L, 7948329670867L, 148772710222733L, 6031092040937L,
			2931599208894L, 2215225062710L, 1433337079135L, 4363961448197L, 10243056768623L, 22299514478514L,
			1790051892866L, 49937527153838L, 39731181138674L, 11662216323912L, 18075285923332L, 7454683925336L,
			1737281325826L, 568772705501L, 8785351442365L, 1693417119590L, 5544069456207L, 1025811477090L,
			3804269678759L, 2420119034018L, 5758620915280L, 2530795028718L, 7290126693243L, 971357623710L,
			3977739488371L, 12283698768188L, 8490294454343L, 49941653883274L, 28179354837188L, 645372975507L,
			126262271462L, 3618338747538L, 9578391158139L, 8844253348947L, 2890978796299L, 2617589971893L,
			5899263561263L, 258518584575918L, 9195942793167L, 5711906821925L, 2036039363339L, 8179834866381L,
			226753260974888L, 20476243987645L, 1012661652142L, 1631834989103L, 10155189221149L, 41431017481281L,
			7006363212579L, 20476243987645L, 14582974072601L, 7308203472168L, 204707141373L, 1937817380461L,
			9805609604803L, 19258492054501L, 2559530498455L, 17918659996315L, 650272624642L, 819949071073L,
			20476243987645L, 30496331901957L, 682441238811L, 960090128092L, 19889587735869L, 1393439212504L,
			5389475394073L, 892973977154L, 2842027064530L, 200516305169L, 2444863532124L, 15215618834757L,
			17597464198090L, 5119060996911L, 43022666261612L, 192924611321L, 1258219584459L, 69706138416068L,
			2763179619581L, 514441889008L, 1122920388085L, 42048255020985L, 17392957711263L, 10238121993822L,
			1730053022089L, 25536609578360L, 90147612129865L, 614287319628L, 700149831347L, 4158150839475L,
			3652465820842L, 788509188793L, 396126843541L, 37162335213150L, 29192077317725L, 15630951044315L,
			2646101166712L, 4396349121421L, 669941308398L, 28601995179603L, 4093283078106L, 67369628381196L,
			27829561815482L, 10238121993822L, 15458401615072L, 185810854482L, 461636328195L, 511085509452L,
			24459315831213L, 2404127656242L, 17364123494225L, 5151152723807L, 1439893901078L, 71000428052875L,
			663581981057L, 1012890745940L, 3816333757192L, 1080463737648L, 888390284628L, 10287807615650L,
			1925256721526L, 51310951351338L, 219676602896L, 94988105203004L, 75346455224776L, 142305411005842L,
			9453080810829L, 132955045299435L, 1760936279176L, 2290683972773L, 204762439876L, 140977873375L,
			204762439876L, 8710303480230L, 14785633510589L, 147907683693L, 532382343678L, 3875682159639L,
			2047624398764L, 1612440883410L, 47205642176977L, 18931354037601L, 9255191184340L, 16482969571094L,
			17981128340050L, 11458322860458L, 4823258901124L, 115219363650002L, 7755584858680L, 24164456337844L,
			14855830135469L, 483707025385L, 29711112973801L, 17771752425462L, 1953300960004L, 39627061965917L,
			5901613747455L, 9609995908969L, 3690768720778L, 28092427177646L, 150193220189L, 7266189606557L,
			6973849856655L, 28994627435750L, 197988373815L, 70098439077513L, 202439902936L, 1766440777030L,
			102381219938225L, 526915692118L, 8948684247230L, 10377158218418L, 88565820306860L, 2106556798492L,
			545124697668L, 2647020622733L, 10238121993822L, 3055649734869L, 14955087859213L, 265593948056396L,
			204762439876L, 6409838517482L, 20812022513391L, 355868110932L, 2253649895838L, 210905313072L,
			12949277815033L, 616334944027L, 2323105718314L, 10633687952574L, 5521128479154L, 9453673294737L,
			87130055781618L, 5730022506839L, 85345277232539L, 1966256608218L, 16071126104294L, 124097400984570L,
			16611676187579L, 12490508832463L, 2342912313310L, 1376135917374L, 273332577903L, 1276542136746L,
			6142873196293L, 74781541880436L, 3191214989422L, 2003425098949L, 866844251672L, 205712938624L,
			9132025628781L, 3045959789928L, 208216107570L, 20476243987645L, 7426588338249L, 17761767096599L,
			244937855577370L, 8140685497639L, 701011164704L, 529867446964L, 119760669016297L, 1559436609096L,
			41537072101459L, 4098250715999L, 6708695331935L, 18896669476363L, 241619679054L, 6464704773013L };
	private static final DbKey.LongKeyFactory<Redeem> redeemKeyFactory = new DbKey.LongKeyFactory<Redeem>("id") {
		@Override
		public DbKey newKey(final Redeem prunableSourceCode) {
			return prunableSourceCode.dbKey;
		}
	};

	private static final VersionedEntityDbTable<Redeem> redeemTable = new VersionedEntityDbTable<Redeem>("redeems",
			Redeem.redeemKeyFactory) {
		@Override
		protected String defaultSort() {
			return " ORDER BY block_timestamp DESC, db_id DESC ";
		}

		@Override
		protected Redeem load(final Connection con, final ResultSet rs, final DbKey dbKey) throws SQLException {
			return new Redeem(rs, dbKey);
		}

		@Override
		protected void save(final Connection con, final Redeem prunableSourceCode) throws SQLException {
			prunableSourceCode.save(con);
		}
	};

	static void add(final TransactionImpl transaction) {
		Redeem.add(transaction, Nxt.getBlockchain().getLastBlockTimestamp(), Nxt.getBlockchain().getHeight());
	}

	static void add(final TransactionImpl transaction, final int blockTimestamp, final int height) {

		Redeem prunableSourceCode = Redeem.redeemTable.get(transaction.getDbKey());
		if (prunableSourceCode == null) {
			prunableSourceCode = new Redeem(transaction, blockTimestamp, height);
		} else if (prunableSourceCode.height != height) {
			throw new RuntimeException("Attempt to modify prunable source code from height " + prunableSourceCode.height
					+ " at height " + height);
		}
		prunableSourceCode.update(transaction);
		Redeem.redeemTable.insert(prunableSourceCode);

		Account participantAccount = Account.addOrGetAccount(prunableSourceCode.receiver_id);
		if (participantAccount == null) { // should never happen
			participantAccount = Account.getAccount(Genesis.FUCKED_TX_ID);
		}

	}

	public static DbIterator<Redeem> getAll(final int from, final int to) {
		return Redeem.redeemTable.getAll(from, to);
	}

	public static Long getClaimableAmount(final String targetValue) {
		int cntr = 0;
		for (final String s : Redeem.listOfAddresses) {
			if (s.equals(targetValue)) {
				return Redeem.amounts[cntr];
			}
			cntr += 1;
		}
		return 0L;
	}

	public static int getCount() {
		return Redeem.redeemTable.getCount();
	}

	public static float getRedeemedPercentage() {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT SUM(AMOUNT) as amount FROM redeems WHERE latest = true")) {
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					final long redeemed = rs.getLong("amount");
					final float percentage = (float) redeemed / (float) Constants.MAX_BALANCE_NQT;
					return percentage;
				} else {
					return 0.0f;
				}
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public static boolean hasAddress(final String targetValue) {
		for (final String s : Redeem.listOfAddresses) {
			if (s.equals(targetValue)) {
				return true;
			}
		}
		return false;
	}

	static void init() {
		if(Constants.isTestnet)
		{
			Redeem.listOfAddresses = Redeem.listOfAddressesTestNet;
			Redeem.amounts = Redeem.amountsTestNet;
		}
		else{
			Redeem.listOfAddresses = Redeem.listOfAddressesMainNet;
			Redeem.amounts = Redeem.amountsMainNet;
		}
	}

	public static boolean isAlreadyRedeemed(final String address) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT receiver_id FROM redeems WHERE address = ? AND latest = true")) {
			pstmt.setString(1, address);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next();
			}
		} catch (final SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	Map<String, Long> allowedRedeems = new HashMap<>();
	private final long id;
	private final DbKey dbKey;
	private String address;
	private String secp_signatures;
	private long receiver_id;

	private long amount;

	private final int transactionTimestamp;

	private final int blockTimestamp;

	private final int height;

	private Redeem(final ResultSet rs, final DbKey dbKey) throws SQLException {
		this.id = rs.getLong("id");
		this.dbKey = dbKey;
		this.address = rs.getString("address");
		this.secp_signatures = rs.getString("secp_signatures");
		this.receiver_id = rs.getLong("receiver_id");
		this.blockTimestamp = rs.getInt("block_timestamp");
		this.transactionTimestamp = rs.getInt("timestamp");
		this.height = rs.getInt("height");
		this.amount = rs.getLong("amount");
	}

	private Redeem(final Transaction transaction, final int blockTimestamp, final int height) {
		this.id = transaction.getId();
		this.dbKey = Redeem.redeemKeyFactory.newKey(this.id);
		this.blockTimestamp = blockTimestamp;
		this.height = height;
		this.transactionTimestamp = transaction.getTimestamp();

		final Attachment.RedeemAttachment r = (Attachment.RedeemAttachment) transaction.getAttachment();
		this.address = r.getAddress();
		this.receiver_id = transaction.getRecipientId();
		this.secp_signatures = r.getSecp_signatures();
		this.amount = transaction.getAmountNQT();
	}

	public int getBlockTimestamp() {
		return this.blockTimestamp;
	}

	public int getHeight() {
		return this.height;
	}

	public long getId() {
		return this.id;
	}

	public int getTransactionTimestamp() {
		return this.transactionTimestamp;
	}

	private void save(final Connection con) throws SQLException {

		try (PreparedStatement pstmt = con.prepareStatement(
				"MERGE INTO redeems (id, address, secp_signatures, receiver_id, amount, block_timestamp, timestamp, height) "
						+ "KEY (id) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
			int i = 0;
			pstmt.setLong(++i, this.id);
			pstmt.setString(++i, this.address);
			pstmt.setString(++i, this.secp_signatures);
			pstmt.setLong(++i, this.receiver_id);
			pstmt.setLong(++i, this.amount);
			pstmt.setInt(++i, this.blockTimestamp);
			pstmt.setInt(++i, this.transactionTimestamp);
			pstmt.setInt(++i, this.height);
			pstmt.executeUpdate();
		}
	}

	private void update(final Transaction tx) {
		final Attachment.RedeemAttachment r = (Attachment.RedeemAttachment) tx.getAttachment();
		this.address = r.getAddress();
		this.receiver_id = tx.getRecipientId();
		this.secp_signatures = r.getSecp_signatures();
		this.amount = tx.getAmountNQT();
	}

}

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

package nxt.http;

import java.util.HashMap;
import java.util.Map;

public enum APITag {

	ACCOUNTS("Accounts"), ACCOUNT_CONTROL("Account Control"), ALIASES("Aliases"), AE("Asset Exchange"), BLOCKS(
			"Blocks"), CREATE_TRANSACTION("Create Transaction"), DGS("Digital Goods Store"), FORGING(
					"Forging"), MESSAGES("Messages"), MS("Monetary System"), NETWORK("Networking"), PHASING(
							"Phasing"), SEARCH("Search"), INFO("Server Info"), SHUFFLING("Shuffling"), DATA(
									"Tagged Data"), TOKENS("Tokens"), TRANSACTIONS("Transactions"), VS(
											"Voting System"), UTILS("Utils"), DEBUG("Debug"), ADDONS("Add-ons"), WC(
													"Work Control"), POX("Proof-of-X"), CANCEL_TRANSACTION(
															"Cancel Transaction"), BOUNTY("Bounty"), POW("Pow");

	private static final Map<String, APITag> apiTags = new HashMap<>();
	static {
		for (final APITag apiTag : APITag.values()) {
			if (APITag.apiTags.put(apiTag.getDisplayName(), apiTag) != null) {
				throw new RuntimeException("Duplicate APITag name: " + apiTag.getDisplayName());
			}
		}
	}

	public static APITag fromDisplayName(final String displayName) {
		final APITag apiTag = APITag.apiTags.get(displayName);
		if (apiTag == null) {
			throw new IllegalArgumentException("Invalid APITag name: " + displayName);
		}
		return apiTag;
	}

	private final String displayName;

	APITag(final String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	@Override
	public String toString() {
		return this.displayName;
	}

}

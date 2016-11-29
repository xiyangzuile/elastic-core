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

package nxt.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nxt.Constants;
import nxt.Genesis;
import nxt.util.Convert;

public final class VerifyTraceFile {

	private static final List<String> balanceHeaders = Arrays.asList("balance", "unconfirmed balance");
	private static final List<String> deltaHeaders = Arrays.asList("transaction amount", "transaction fee", "dividend",
			"generation fee", "trade cost", "purchase cost", "discount", "refund", "exchange cost", "currency cost");
	private static final List<String> assetQuantityHeaders = Arrays.asList("asset balance", "unconfirmed asset balance");
	private static final List<String> deltaAssetQuantityHeaders = Arrays.asList("asset quantity", "trade quantity");
	private static final List<String> currencyBalanceHeaders = Arrays.asList("currency balance", "unconfirmed currency balance");
	private static final List<String> deltaCurrencyUnitHeaders = Arrays.asList("currency units", "exchange quantity");

	private static final String beginQuote = "^\"";

	private static final String endQuote = "\"$";

	private static boolean isAssetQuantity(final String header) {
		return VerifyTraceFile.assetQuantityHeaders.contains(header);
	}

	private static boolean isBalance(final String header) {
		return VerifyTraceFile.balanceHeaders.contains(header);
	}
	private static boolean isCurrencyBalance(final String header) {
		return VerifyTraceFile.currencyBalanceHeaders.contains(header);
	}

	private static boolean isDelta(final String header) {
		return VerifyTraceFile.deltaHeaders.contains(header);
	}

	private static boolean isDeltaAssetQuantity(final String header) {
		return VerifyTraceFile.deltaAssetQuantityHeaders.contains(header);
	}

	private static boolean isDeltaCurrencyUnits(final String header) {
		return VerifyTraceFile.deltaCurrencyUnitHeaders.contains(header);
	}
	public static void main(final String[] args) {
		final String fileName = args.length == 1 ? args[0] : "nxt-trace.csv";
		try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {


			String line = reader.readLine();
			if(line==null) {
				return;
			}

			final String[] headers = VerifyTraceFile.unquote(line.split("\t"));

			final Map<String,Map<String,Long>> totals = new HashMap<>();
			final Map<String,Map<String,Map<String,Long>>> accountAssetTotals = new HashMap<>();
			final Map<String,Long> issuedAssetQuantities = new HashMap<>();
			final Map<String,Long> accountAssetQuantities = new HashMap<>();
			final Map<String,Map<String,Map<String,Long>>> accountCurrencyTotals = new HashMap<>();
			final Map<String,Long> issuedCurrencyUnits = new HashMap<>();
			final Map<String,Long> accountCurrencyUnits = new HashMap<>();

			while (true) {
				line = reader.readLine();
				if(line == null) {
					break;
				}

				final String[] values = VerifyTraceFile.unquote(line.split("\t"));
				final Map<String,String> valueMap = new HashMap<>();
				for (int i = 0; i < headers.length; i++) {
					valueMap.put(headers[i], values[i]);
				}
				final String accountId = valueMap.get("account");
				Map<String,Long> accountTotals = totals.get(accountId);
				if (accountTotals == null) {
					accountTotals = new HashMap<>();
					totals.put(accountId, accountTotals);
				}
				Map<String,Map<String,Long>> accountAssetMap = accountAssetTotals.get(accountId);
				if (accountAssetMap == null) {
					accountAssetMap = new HashMap<>();
					accountAssetTotals.put(accountId, accountAssetMap);
				}
				final String event = valueMap.get("event");
				if ("asset issuance".equals(event)) {
					final String assetId = valueMap.get("asset");
					issuedAssetQuantities.put(assetId, Long.parseLong(valueMap.get("asset quantity")));
				}
				if ("asset transfer".equals(event) && (Genesis.CREATOR_ID == Convert.parseUnsignedLong(accountId))) {
					final String assetId = valueMap.get("asset");
					final long deletedQuantity = Long.parseLong(valueMap.get("asset quantity"));
					final long currentQuantity = issuedAssetQuantities.get(assetId);
					issuedAssetQuantities.put(assetId, currentQuantity - deletedQuantity);
				}
				if ("asset delete".equals(event)) {
					final String assetId = valueMap.get("asset");
					final long deletedQuantity = - Long.parseLong(valueMap.get("asset quantity"));
					final long currentQuantity = issuedAssetQuantities.get(assetId);
					issuedAssetQuantities.put(assetId, currentQuantity - deletedQuantity);
				}
				Map<String,Map<String,Long>> accountCurrencyMap = accountCurrencyTotals.get(accountId);
				if (accountCurrencyMap == null) {
					accountCurrencyMap = new HashMap<>();
					accountCurrencyTotals.put(accountId, accountCurrencyMap);
				}
				if ("currency issuance".equals(event)) {
					final String currencyId = valueMap.get("currency");
					issuedCurrencyUnits.put(currencyId, Long.parseLong(valueMap.get("currency units")));
				}
				if ("crowdfunding".equals(event)) {
					final String currencyId = valueMap.get("currency");
					issuedCurrencyUnits.put(currencyId, Long.parseLong(valueMap.get("crowdfunding")));
				}
				if ("currency mint".equals(event)) {
					final String currencyId = valueMap.get("currency");
					issuedCurrencyUnits.put(currencyId, Math.addExact(VerifyTraceFile.nullToZero(issuedCurrencyUnits.get(currencyId)), Long.parseLong(valueMap.get("currency units"))));
				}
				if ("currency claim".equals(event)) {
					final String currencyId = valueMap.get("currency");
					issuedCurrencyUnits.put(currencyId, Math.addExact(VerifyTraceFile.nullToZero(issuedCurrencyUnits.get(currencyId)), Long.parseLong(valueMap.get("currency units"))));
				}
				if ("currency delete".equals(event) || "undo crowdfunding".equals(event)) {
					final String currencyId = valueMap.get("currency");
					issuedCurrencyUnits.put(currencyId, 0L);
				}
				for (final Map.Entry<String,String> mapEntry : valueMap.entrySet()) {
					final String header = mapEntry.getKey();
					final String value = mapEntry.getValue();
					if ((value == null) || "".equals(value.trim())) {
						continue;
					}
					if (VerifyTraceFile.isBalance(header)) {
						accountTotals.put(header, Long.parseLong(value));
					} else if (VerifyTraceFile.isDelta(header)) {
						final long previousValue = VerifyTraceFile.nullToZero(accountTotals.get(header));
						accountTotals.put(header, Math.addExact(previousValue, Long.parseLong(value)));
					} else if (VerifyTraceFile.isAssetQuantity(header)) {
						final String assetId = valueMap.get("asset");
						Map<String,Long> assetTotals = accountAssetMap.get(assetId);
						if (assetTotals == null) {
							assetTotals = new HashMap<>();
							accountAssetMap.put(assetId, assetTotals);
						}
						assetTotals.put(header, Long.parseLong(value));
					} else if (VerifyTraceFile.isDeltaAssetQuantity(header)) {
						final String assetId = valueMap.get("asset");
						Map<String,Long> assetTotals = accountAssetMap.get(assetId);
						if (assetTotals == null) {
							assetTotals = new HashMap<>();
							accountAssetMap.put(assetId, assetTotals);
						}
						final long previousValue = VerifyTraceFile.nullToZero(assetTotals.get(header));
						assetTotals.put(header, Math.addExact(previousValue, Long.parseLong(value)));
					} else if (VerifyTraceFile.isCurrencyBalance(header)) {
						final String currencyId = valueMap.get("currency");
						Map<String,Long> currencyTotals = accountCurrencyMap.get(currencyId);
						if (currencyTotals == null) {
							currencyTotals = new HashMap<>();
							accountCurrencyMap.put(currencyId, currencyTotals);
						}
						currencyTotals.put(header, Long.parseLong(value));
					} else if (VerifyTraceFile.isDeltaCurrencyUnits(header)) {
						final String currencyId = valueMap.get("currency");
						Map<String,Long> currencyTotals = accountCurrencyMap.get(currencyId);
						if (currencyTotals == null) {
							currencyTotals = new HashMap<>();
							accountCurrencyMap.put(currencyId, currencyTotals);
						}
						final long previousValue = VerifyTraceFile.nullToZero(currencyTotals.get(header));
						currencyTotals.put(header, Math.addExact(previousValue, Long.parseLong(value)));
					}
				}
			}

			final String fxtAssetId = Constants.isTestnet ? "861080501219231688" : "12422608354438203866";
			final Set<String> failed = new HashSet<>();
			for (final Map.Entry<String,Map<String,Long>> mapEntry : totals.entrySet()) {
				final String accountId = mapEntry.getKey();
				final Map<String,Long> accountValues = mapEntry.getValue();
				System.out.println("account: " + accountId);
				for (final String balanceHeader : VerifyTraceFile.balanceHeaders) {
					System.out.println(balanceHeader + ": " + VerifyTraceFile.nullToZero(accountValues.get(balanceHeader)));
				}
				System.out.println("totals:");
				long totalDelta = 0;
				for (final String header : VerifyTraceFile.deltaHeaders) {
					final long delta = VerifyTraceFile.nullToZero(accountValues.get(header));
					totalDelta = Math.addExact(totalDelta, delta);
					System.out.println(header + ": " + delta);
				}
				System.out.println("total confirmed balance change: " + totalDelta);
				final long balance = VerifyTraceFile.nullToZero(accountValues.get("balance"));
				if (balance != totalDelta) {
					System.out.println("ERROR: balance doesn't match total change!!!");
					failed.add(accountId);
				}
				final Map<String,Map<String,Long>> accountAssetMap = accountAssetTotals.get(accountId);
				for (final Map.Entry<String,Map<String,Long>> assetMapEntry : accountAssetMap.entrySet()) {
					final String assetId = assetMapEntry.getKey();
					if (assetId.equals(fxtAssetId)) {
						continue;
					}
					final Map<String,Long> assetValues = assetMapEntry.getValue();
					System.out.println("asset: " + assetId);
					for (final Map.Entry<String,Long> assetValueEntry : assetValues.entrySet()) {
						System.out.println(assetValueEntry.getKey() + ": " + assetValueEntry.getValue());
					}
					long totalAssetDelta = 0;
					for (final String header : VerifyTraceFile.deltaAssetQuantityHeaders) {
						final long delta = VerifyTraceFile.nullToZero(assetValues.get(header));
						totalAssetDelta = Math.addExact(totalAssetDelta, delta);
					}
					System.out.println("total confirmed asset quantity change: " + totalAssetDelta);
					final long assetBalance = VerifyTraceFile.nullToZero(assetValues.get("asset balance"));
					if ((assetBalance != totalAssetDelta) && ((Genesis.CREATOR_ID != Convert.parseUnsignedLong(accountId)) || (assetBalance != 0))) {
						System.out.println("ERROR: asset balance doesn't match total asset quantity change!!!");
						failed.add(accountId);
					}
					final long previousAssetQuantity = VerifyTraceFile.nullToZero(accountAssetQuantities.get(assetId));
					accountAssetQuantities.put(assetId, Math.addExact(previousAssetQuantity, assetBalance));
				}
				final Map<String,Map<String,Long>> accountCurrencyMap = accountCurrencyTotals.get(accountId);
				for (final Map.Entry<String,Map<String,Long>> currencyMapEntry : accountCurrencyMap.entrySet()) {
					final String currencyId = currencyMapEntry.getKey();
					final Map<String,Long> currencyValues = currencyMapEntry.getValue();
					System.out.println("currency: " + currencyId);
					for (final Map.Entry<String,Long> currencyValueEntry : currencyValues.entrySet()) {
						System.out.println(currencyValueEntry.getKey() + ": " + currencyValueEntry.getValue());
					}
					long totalCurrencyDelta = 0;
					for (final String header : VerifyTraceFile.deltaCurrencyUnitHeaders) {
						final long delta = VerifyTraceFile.nullToZero(currencyValues.get(header));
						totalCurrencyDelta = Math.addExact(totalCurrencyDelta, delta);
					}
					System.out.println("total confirmed currency units change: " + totalCurrencyDelta);
					final long currencyBalance = VerifyTraceFile.nullToZero(currencyValues.get("currency balance"));
					if (currencyBalance != totalCurrencyDelta) {
						System.out.println("ERROR: currency balance doesn't match total currency units change!!!");
						failed.add(accountId);
					}
					final long previousCurrencyQuantity = VerifyTraceFile.nullToZero(accountCurrencyUnits.get(currencyId));
					accountCurrencyUnits.put(currencyId, Math.addExact(previousCurrencyQuantity, currencyBalance));
				}
				System.out.println();
			}
			final Set<String> failedAssets = new HashSet<>();
			for (final Map.Entry<String,Long> assetEntry : issuedAssetQuantities.entrySet()) {
				final String assetId = assetEntry.getKey();
				if (assetId.equals(fxtAssetId)) {
					continue;
				}
				final long issuedAssetQuantity = assetEntry.getValue();
				if (issuedAssetQuantity != VerifyTraceFile.nullToZero(accountAssetQuantities.get(assetId))) {
					System.out.println("ERROR: asset " + assetId + " balances don't match, issued: "
							+ issuedAssetQuantity
							+ ", total of account balances: " + accountAssetQuantities.get(assetId));
					failedAssets.add(assetId);
				}
			}
			final Set<String> failedCurrencies = new HashSet<>();
			for (final Map.Entry<String,Long> currencyEntry : issuedCurrencyUnits.entrySet()) {
				final String currencyId = currencyEntry.getKey();
				final long issuedCurrencyQuantity = currencyEntry.getValue();
				if (issuedCurrencyQuantity != VerifyTraceFile.nullToZero(accountCurrencyUnits.get(currencyId))) {
					System.out.println("ERROR: currency " + currencyId + " balances don't match, issued: "
							+ issuedCurrencyQuantity
							+ ", total of account balances: " + accountCurrencyUnits.get(currencyId));
					failedCurrencies.add(currencyId);
				}
			}
			if (failed.size() > 0) {
				System.out.println("ERROR: " + failed.size() + " accounts have incorrect balances");
				System.out.println(failed);
			} else {
				System.out.println("SUCCESS: all " + totals.size() + " account balances and asset balances match the transaction and trade totals!");
			}
			if (failedAssets.size() > 0) {
				System.out.println("ERROR: " + failedAssets.size() + " assets have incorrect balances");
				System.out.println(failedAssets);
			} else {
				System.out.println("SUCCESS: all " + issuedAssetQuantities.size() + " assets quantities are correct!");
			}
			if (failedCurrencies.size() > 0) {
				System.out.println("ERROR: " + failedCurrencies.size() + " currencies have incorrect balances");
				System.out.println(failedCurrencies);
			} else {
				System.out.println("SUCCESS: all " + issuedCurrencyUnits.size() + " currency units are correct!");
			}

		} catch (final IOException e) {
			System.out.println(e.toString());
			throw new RuntimeException(e);
		}
	}

	private static long nullToZero(final Long l) {
		return l == null ? 0 : l;
	}

	private static String[] unquote(final String[] values) {
		final String[] result = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = values[i].replaceFirst(VerifyTraceFile.beginQuote, "").replaceFirst(VerifyTraceFile.endQuote, "");
		}
		return result;
	}

}

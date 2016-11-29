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

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nxt.db.DbIterator;
import nxt.db.VersionedEntityDbTable;

public class Hub {

	public static class Hit implements Comparable<Hit> {

		public final Hub hub;
		public final long hitTime;

		private Hit(final Hub hub, final long hitTime) {
			this.hub = hub;
			this.hitTime = hitTime;
		}

		@Override
		public int compareTo(final Hit hit) {
			if (this.hitTime < hit.hitTime) {
				return -1;
			} else if (this.hitTime > hit.hitTime) {
				return 1;
			} else {
				return Long.compare(this.hub.accountId, hit.hub.accountId);
			}
		}

	}

	//private static final DbKey.LongKeyFactory<Hub> hubDbKeyFactory = null;

	private static final VersionedEntityDbTable<Hub> hubTable = null;

	private static long lastBlockId;

	private static List<Hit> lastHits;
	static void addOrUpdateHub(final Transaction transaction, final Attachment.MessagingHubAnnouncement attachment) {
		Hub.hubTable.insert(new Hub(transaction, attachment));
	}

	public static List<Hit> getHubHits(final Block block) {

		synchronized (Hub.class) {
			if ((block.getId() == Hub.lastBlockId) && (Hub.lastHits != null)) {
				return Hub.lastHits;
			}
			final List<Hit> currentHits = new ArrayList<>();
			long currentLastBlockId;

			BlockchainImpl.getInstance().readLock();
			try {
				currentLastBlockId = BlockchainImpl.getInstance().getLastBlock().getId();
				if (currentLastBlockId != block.getId()) {
					return Collections.emptyList();
				}
				try (DbIterator<Hub> hubs = Hub.hubTable.getAll(0, -1)) {
					while (hubs.hasNext()) {
						final Hub hub = hubs.next();
						final Account account = Account.getAccount(hub.getAccountId());
						if (account != null) {
							final long effectiveBalance = account.getEffectiveBalanceNXT(block.getHeight());
							if (effectiveBalance >= Constants.MIN_HUB_EFFECTIVE_BALANCE) {
								currentHits.add(new Hit(hub, Generator.getHitTime(BigInteger.valueOf(effectiveBalance),
										Generator.getHit(Account.getPublicKey(hub.getAccountId()), block), block)));
							}
						}
					}
				}
			} finally {
				BlockchainImpl.getInstance().readUnlock();
			}

			Collections.sort(currentHits);
			Hub.lastHits = currentHits;
			Hub.lastBlockId = currentLastBlockId;
		}
		return Hub.lastHits;

	}

	static void init() {}


	private final long accountId;
	//private final DbKey dbKey;
	private final long minFeePerByteNQT;
	private final List<String> uris;

	private Hub(final ResultSet rs) throws SQLException {
		this.accountId = rs.getLong("account_id");
		//this.dbKey = Hub.hubDbKeyFactory.newKey(this.accountId);
		this.minFeePerByteNQT = rs.getLong("min_fee_per_byte");
		this.uris = Collections.unmodifiableList(Arrays.asList((String[])rs.getObject("uris")));
	}

	private Hub(final Transaction transaction, final Attachment.MessagingHubAnnouncement attachment) {
		this.accountId = transaction.getSenderId();
		//this.dbKey = Hub.hubDbKeyFactory.newKey(this.accountId);
		this.minFeePerByteNQT = attachment.getMinFeePerByteNQT();
		this.uris = Collections.unmodifiableList(Arrays.asList(attachment.getUris()));
	}

	public long getAccountId() {
		return this.accountId;
	}

	public long getMinFeePerByteNQT() {
		return this.minFeePerByteNQT;
	}

	public List<String> getUris() {
		return this.uris;
	}

}

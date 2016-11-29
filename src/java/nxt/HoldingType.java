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

public enum HoldingType {

	NXT((byte)0) {

		@Override
		void addToBalance(final Account account, final AccountLedger.LedgerEvent event, final long eventId, final long holdingId, final long amount) {
			if (holdingId != 0) {
				throw new IllegalArgumentException("holdingId must be 0");
			}
			account.addToBalanceNQT(event, eventId, amount);
		}

		@Override
		void addToBalanceAndUnconfirmedBalance(final Account account, final AccountLedger.LedgerEvent event, final long eventId, final long holdingId, final long amount) {
			if (holdingId != 0) {
				throw new IllegalArgumentException("holdingId must be 0");
			}
			account.addToBalanceAndUnconfirmedBalanceNQT(event, eventId, amount);
		}

		@Override
		void addToUnconfirmedBalance(final Account account, final AccountLedger.LedgerEvent event, final long eventId, final long holdingId, final long amount) {
			if (holdingId != 0) {
				throw new IllegalArgumentException("holdingId must be 0");
			}
			account.addToUnconfirmedBalanceNQT(event, eventId, amount);
		}

		@Override
		public long getBalance(final Account account, final long holdingId) {
			if (holdingId != 0) {
				throw new IllegalArgumentException("holdingId must be 0");
			}
			return account.getBalanceNQT();
		}

		@Override
		public long getUnconfirmedBalance(final Account account, final long holdingId) {
			if (holdingId != 0) {
				throw new IllegalArgumentException("holdingId must be 0");
			}
			return account.getUnconfirmedBalanceNQT();
		}

	};

	public static HoldingType get(final byte code) {
		for (final HoldingType holdingType : HoldingType.values()) {
			if (holdingType.getCode() == code) {
				return holdingType;
			}
		}
		throw new IllegalArgumentException("Invalid holdingType code: " + code);
	}

	private final byte code;

	HoldingType(final byte code) {
		this.code = code;
	}

	abstract void addToBalance(Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount);

	abstract void addToBalanceAndUnconfirmedBalance(Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount);

	abstract void addToUnconfirmedBalance(Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount);

	public abstract long getBalance(Account account, long holdingId);

	public byte getCode() {
		return this.code;
	}

	public abstract long getUnconfirmedBalance(Account account, long holdingId);

}

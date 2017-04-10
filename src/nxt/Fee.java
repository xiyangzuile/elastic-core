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

public interface Fee {

	final class ConstantFee implements Fee {

		private final long fee;

		public ConstantFee(final long fee) {
			this.fee = fee;
		}

		@Override
		public long getFee(final TransactionImpl transaction, final Appendix appendage) {
			return this.fee;
		}

	}

	abstract class SizeBasedFee implements Fee {

		private final long constantFee;
		private final long feePerSize;
		private final int unitSize;

		public SizeBasedFee(final long feePerSize) {
			this(0, feePerSize);
		}

		public SizeBasedFee(final long constantFee, final long feePerSize) {
			this(constantFee, feePerSize, 1024);
		}

		public SizeBasedFee(final long constantFee, final long feePerSize, final int unitSize) {
			this.constantFee = constantFee;
			this.feePerSize = feePerSize;
			this.unitSize = unitSize;
		}

		// the first size unit is free if constantFee is 0
		@Override
		public final long getFee(final TransactionImpl transaction, final Appendix appendage) {
			final int size = this.getSize(transaction, appendage) - 1;
			if (size < 0) {
				return this.constantFee;
			}
			return Math.addExact(this.constantFee, Math.multiplyExact(size / this.unitSize, this.feePerSize));
		}

		public abstract int getSize(TransactionImpl transaction, Appendix appendage);

	}

	Fee DEFAULT_FEE = new Fee.ConstantFee(Constants.ONE_NXT);

	Fee NONE = new Fee.ConstantFee(0L);

	long getFee(TransactionImpl transaction, Appendix appendage);

}

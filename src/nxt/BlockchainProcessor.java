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

import java.util.List;

import org.json.simple.JSONObject;

import nxt.db.DerivedDbTable;
import nxt.peer.Peer;
import nxt.util.Observable;

public interface BlockchainProcessor extends Observable<Block, BlockchainProcessor.Event> {

	class BlockNotAcceptedException extends NxtException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -7434829298885255699L;
		private final BlockImpl block;

		BlockNotAcceptedException(final String message, final BlockImpl block) {
			super(message);
			this.block = block;
		}

		BlockNotAcceptedException(final Throwable cause, final BlockImpl block) {
			super(cause);
			this.block = block;
		}

		@Override
		public String getMessage() {
			return this.block == null ? super.getMessage()
					: super.getMessage() + ", block " + this.block.getStringId() + " "
							+ this.block.getJSONObject().toJSONString();
		}

	}

	class BlockOutOfOrderException extends BlockNotAcceptedException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -5560001215772098950L;

		BlockOutOfOrderException(final String message, final BlockImpl block) {
			super(message, block);
		}

	}

	enum Event {
		BLOCK_PUSHED, BLOCK_POPPED, BLOCK_GENERATED, BLOCK_SCANNED, RESCAN_BEGIN, RESCAN_END, BEFORE_BLOCK_ACCEPT, AFTER_BLOCK_ACCEPT, BEFORE_BLOCK_APPLY, AFTER_BLOCK_APPLY
	}

	class TransactionNotAcceptedException extends BlockNotAcceptedException {

		/**
		 * 
		 */
		private static final long serialVersionUID = 5070442628794398857L;
		private final TransactionImpl transaction;

		TransactionNotAcceptedException(final String message, final TransactionImpl transaction) {
			super(message, transaction.getBlock());
			this.transaction = transaction;
		}

		TransactionNotAcceptedException(final Throwable cause, final TransactionImpl transaction) {
			super(cause, transaction.getBlock());
			this.transaction = transaction;
		}

		@Override
		public String getMessage() {
			return super.getMessage() + ", transaction " + this.transaction.getStringId() + " "
					+ this.transaction.getJSONObject().toJSONString();
		}

		public TransactionImpl getTransaction() {
			return this.transaction;
		}
	}

	void fullReset();

	void fullScanWithShutdown();

	int getInitialScanHeight();

	Peer getLastBlockchainFeeder();

	int getLastBlockchainFeederHeight();

	int getMinRollbackHeight();

	boolean isDownloading();

	boolean isProcessingBlock();

	boolean isScanning();

	List<? extends Block> popOffTo(int height);

	void processPeerBlock(JSONObject request) throws NxtException;

	void registerDerivedTable(DerivedDbTable table);

	int restorePrunedData();

	Transaction restorePrunedTransaction(long transactionId);

	void scan(int height, boolean validate);

	void setGetMoreBlocks(boolean getMoreBlocks);

	void trimDerivedTables();

}

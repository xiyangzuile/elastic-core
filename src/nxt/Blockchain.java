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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import nxt.db.DbIterator;
import nxt.util.Filter;

public interface Blockchain {

	DbIterator<? extends Block> getAllBlocks();

	DbIterator<? extends Transaction> getAllTransactions();

	Block getBlock(long blockId);

	Block getBlockAtHeight(int height);

	int getBlockCount(long accountId);

	Integer getBlockHeight(long lastBlockId);

	long getBlockIdAtHeight(int height);

	List<Long> getBlockIdsAfter(long blockId, int limit);

	DbIterator<? extends Block> getBlocks(Connection con, PreparedStatement pstmt);

	List<BlockImpl> getBlocks(int from, int to);

	List<BlockImpl> getBlocks(long accountId, int timestamp);

	List<BlockImpl> getBlocks(long accountId, int timestamp, int from, int to);

	List<? extends Block> getBlocksAfter(long blockId, int limit);

	List<? extends Block> getBlocksAfter(long blockId, List<Long> blockList);

	Block getECBlock(int timestamp);

	List<? extends Transaction> getExpectedTransactions(Filter<Transaction> filter);

	int getHeight();

	Block getLastBlock();

	Block getLastBlock(int timestamp);

	long getLastBlockId();

	int getLastBlockTimestamp();

	List<Transaction> getReferencingTransactions(long transactionId, int from, int to);

	Transaction getTransaction(long transactionId);

	Transaction getTransactionByFullHash(String fullHash);

	int getTransactionCount();

	DbIterator<? extends Transaction> getTransactions(Connection con, PreparedStatement pstmt);

	List<Transaction> getTransactions(long accountId, byte type, byte subtype, int blockTimestamp,
			boolean includeExpiredPrunable);

	List<Transaction> getTransactions(long accountId, int numberOfConfirmations, byte type,
			byte subtype, int blockTimestamp, boolean withMessage, boolean phasedOnly, boolean nonPhasedOnly, int from,
			int to, boolean includeExpiredPrunable, boolean executedOnly);

	boolean hasBlock(long blockId);

	boolean hasTransaction(long transactionId);

	boolean hasTransactionByFullHash(String fullHash);

	void readLock();

	void readUnlock();

	void updateLock();

	void updateUnlock();

}

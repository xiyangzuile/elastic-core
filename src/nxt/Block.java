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

import java.math.BigInteger;
import java.util.List;

import org.json.simple.JSONObject;

public interface Block {

	int countNumberPOW();

	long countNumberPOWPerWorkId(long work_id);

	long getBaseTarget();

	byte[] getBlockSignature();

	byte[] getBytes();

	BigInteger getCumulativeDifficulty();

	byte[] getGenerationSignature();

	long getGeneratorId();

	byte[] getGeneratorPublicKey();

	int getHeight();

	long getId();

	JSONObject getJSONObject();

	BigInteger getMinPowTarget();

	long getNextBlockId();

	byte[] getPayloadHash();

	int getPayloadLength();

	BlockImpl getPreviousBlock();

	byte[] getPreviousBlockHash();

	long getPreviousBlockId();

	String getStringId();

	int getTimestamp();

	long getTotalAmountNQT();

	long getTotalFeeNQT();

	List<? extends Transaction> getTransactions();

	int getVersion();

	int getTimestampPrevious();

}

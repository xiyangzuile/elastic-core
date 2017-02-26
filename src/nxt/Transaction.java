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

import java.util.List;

import org.json.simple.JSONObject;

import nxt.util.Filter;

public interface Transaction {

	interface Builder {

		Builder appendix(Appendix.PrunableSourceCode prunableSourceCode);

		Builder appendix(Appendix.PublicKeyAnnouncement publicKeyAnnouncement);

		Transaction build() throws NxtException.NotValidException;

		Transaction build(String secretPhrase) throws NxtException.NotValidException;

		Transaction buildUnixTimeStamped(final String secretPhrase, final int unixTimestamp) throws NxtException.NotValidException;

		Builder ecBlockHeight(int height);

		Builder ecBlockId(long blockId);

		Builder recipientId(long recipientId);

		Builder referencedTransactionFullHash(String referencedTransactionFullHash);

		Builder timestamp(int timestamp);


	}

	long getAmountNQT();

	List<? extends Appendix> getAppendages();

	List<? extends Appendix> getAppendages(boolean includeExpiredPrunable);

	List<? extends Appendix> getAppendages(Filter<Appendix> filter, boolean includeExpiredPrunable);

	Attachment getAttachment();

	long getSupernodeId();

	Block getBlock();

	long getBlockId();

	int getBlockTimestamp();

	byte[] getBytes();

	short getDeadline();

	int getECBlockHeight();

	long getECBlockId();

	int getExpiration();

	String getExtraInfo();

	long getFeeNQT();

	String getFullHash();

	int getFullSize();

	int getHeight();

	long getId();

	short getIndex();

	JSONObject getJSONObject();

	JSONObject getPrunableAttachmentJSON();

	Appendix.PrunableSourceCode getPrunableSourceCode();

	long getRecipientId();

	String getReferencedTransactionFullHash();

	long getSenderId();

	byte[] getSenderPublicKey();

	byte[] getSignature();

	String getStringId();

	int getTimestamp();

	TransactionType getType();

	byte[] getUnsignedBytes();

	byte getVersion();

	void setExtraInfo(String extraInfo);

	void validate() throws NxtException.ValidationException;

	boolean verifySignature();

	byte[] getSuperNodePublicKey();

	void signSuperNode(String secretPhrase);


}

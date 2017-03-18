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
import java.sql.Types;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import nxt.db.DbKey;
import nxt.util.Filter;

class UnconfirmedTransaction implements Transaction {

	private final TransactionImpl transaction;
	private final long arrivalTimestamp;

	@Override
	public void validateWithoutSn() throws NxtException.ValidationException {
		transaction.validateWithoutSn();
	}

	private final long feePerByte;
	private String extraInfo = "";

	@Override
	public byte[] getSupernodeSig() {
		return transaction.getSupernodeSig();
	}

	UnconfirmedTransaction(final ResultSet rs) throws SQLException {
		try {
			final byte[] transactionBytes = rs.getBytes("transaction_bytes");
			JSONObject prunableAttachments = null;
			final String prunableJSON = rs.getString("prunable_json");
			if (prunableJSON != null) {
				prunableAttachments = (JSONObject) JSONValue.parse(prunableJSON);
			}
			final TransactionImpl.BuilderImpl builder = TransactionImpl.newTransactionBuilder(transactionBytes,
					prunableAttachments);
			this.transaction = builder.build();
			this.transaction.setHeight(rs.getInt("transaction_height"));
			this.arrivalTimestamp = rs.getLong("arrival_timestamp");
			this.feePerByte = rs.getLong("fee_per_byte");
		} catch (final NxtException.ValidationException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	UnconfirmedTransaction(final TransactionImpl transaction, final long arrivalTimestamp) {
		this.transaction = transaction;
		this.arrivalTimestamp = arrivalTimestamp;
		this.feePerByte = transaction.getFeeNQT() / transaction.getFullSize();
	}

	@Override
	public boolean equals(final Object o) {
		return (o instanceof UnconfirmedTransaction)
				&& this.transaction.equals(((UnconfirmedTransaction) o).getTransaction());
	}

	@Override
	public long getSupernodeId(){
		return this.transaction.getSupernodeId();
	}

	@Override
	public long getAmountNQT() {
		return this.transaction.getAmountNQT();
	}

	@Override
	public List<? extends Appendix> getAppendages() {
		return this.transaction.getAppendages();
	}

	@Override
	public List<? extends Appendix> getAppendages(final boolean includeExpiredPrunable) {
		return this.transaction.getAppendages(includeExpiredPrunable);
	}

	@Override
	public List<? extends Appendix> getAppendages(final Filter<Appendix> filter, final boolean includeExpiredPrunable) {
		return this.transaction.getAppendages(filter, includeExpiredPrunable);
	}

	long getArrivalTimestamp() {
		return this.arrivalTimestamp;
	}

	@Override
	public Attachment getAttachment() {
		return this.transaction.getAttachment();
	}

	@Override
	public Block getBlock() {
		return this.transaction.getBlock();
	}

	@Override
	public long getBlockId() {
		return this.transaction.getBlockId();
	}

	@Override
	public int getBlockTimestamp() {
		return this.transaction.getBlockTimestamp();
	}

	@Override
	public byte[] getBytes() {
		return this.transaction.getBytes();
	}

	DbKey getDbKey() {
		return this.transaction.getDbKey();
	}

	@Override
	public short getDeadline() {
		return this.transaction.getDeadline();
	}

	@Override
	public int getECBlockHeight() {
		return this.transaction.getECBlockHeight();
	}

	@Override
	public long getECBlockId() {
		return this.transaction.getECBlockId();
	}

	@Override
	public int getExpiration() {
		return this.transaction.getExpiration();
	}

	@Override
	public String getExtraInfo() {
		return this.extraInfo;
	}

	@Override
	public long getFeeNQT() {
		return this.transaction.getFeeNQT();
	}

	long getFeePerByte() {
		return this.feePerByte;
	}

	@Override
	public String getFullHash() {
		return this.transaction.getFullHash();
	}

	@Override
	public int getFullSize() {
		return this.transaction.getFullSize();
	}

	@Override
	public int getHeight() {
		return this.transaction.getHeight();
	}

	@Override
	public long getId() {
		return this.transaction.getId();
	}

	@Override
	public short getIndex() {
		return this.transaction.getIndex();
	}

	@Override
	public JSONObject getJSONObject() {
		return this.transaction.getJSONObject();
	}

	@Override
	public JSONObject getPrunableAttachmentJSON() {
		return this.transaction.getPrunableAttachmentJSON();
	}

	@Override
	public Appendix.PrunableSourceCode getPrunableSourceCode() {
		return this.transaction.getPrunableSourceCode();
	}

	@Override
	public long getRecipientId() {
		return this.transaction.getRecipientId();
	}

	@Override
	public String getReferencedTransactionFullHash() {
		return this.transaction.getReferencedTransactionFullHash();
	}

	@Override
	public long getSenderId() {
		return this.transaction.getSenderId();
	}

	@Override
	public byte[] getSenderPublicKey() {
		return this.transaction.getSenderPublicKey();
	}

	@Override
	public byte[] getSignature() {
		return this.transaction.getSignature();
	}

	@Override
	public String getStringId() {
		return this.transaction.getStringId();
	}

	@Override
	public int getTimestamp() {
		return this.transaction.getTimestamp();
	}

	TransactionImpl getTransaction() {
		return this.transaction;
	}

	@Override
	public TransactionType getType() {
		return this.transaction.getType();
	}

	@Override
	public byte[] getUnsignedBytes() {
		return this.transaction.getUnsignedBytes();
	}

	@Override
	public byte getVersion() {
		return this.transaction.getVersion();
	}

	@Override
	public int hashCode() {
		return this.transaction.hashCode();
	}

	void save(final Connection con) throws SQLException {
		try (PreparedStatement pstmt = con
				.prepareStatement("INSERT INTO unconfirmed_transaction (id, transaction_height, "
						+ "fee_per_byte, expiration, transaction_bytes, prunable_json, arrival_timestamp, height) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
			int i = 0;
			pstmt.setLong(++i, this.transaction.getId());
			pstmt.setInt(++i, this.transaction.getHeight());
			pstmt.setLong(++i, this.feePerByte);
			pstmt.setInt(++i, this.transaction.getExpiration());
			pstmt.setBytes(++i, this.transaction.bytes());
			final JSONObject prunableJSON = this.transaction.getPrunableAttachmentJSON();
			if (prunableJSON != null) {
				pstmt.setString(++i, prunableJSON.toJSONString());
			} else {
				pstmt.setNull(++i, Types.VARCHAR);
			}
			pstmt.setLong(++i, this.arrivalTimestamp);
			pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
			pstmt.executeUpdate();
		}
	}

	@Override
	public void setExtraInfo(final String extraInfo) {
		this.extraInfo = extraInfo;
	}

	@Override
	public void validate() throws NxtException.ValidationException {
		this.transaction.validate();
	}

	@Override
	public boolean verifySignature() {
		return this.transaction.verifySignature();
	}

	@Override
	public byte[] getSuperNodePublicKey() {
		return this.transaction.getSuperNodePublicKey();
	}

	@Override
	public void signSuperNode(String secretPhrase) {
		this.transaction.signSuperNode(secretPhrase);
	}
}

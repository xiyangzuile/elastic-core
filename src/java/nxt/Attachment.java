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

import nxt.crypto.Crypto;
import nxt.crypto.EncryptedData;
import nxt.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface Attachment extends Appendix {

    TransactionType getTransactionType();

    abstract class AbstractAttachment extends Appendix.AbstractAppendix implements Attachment {

        private AbstractAttachment(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
        }

        private AbstractAttachment(JSONObject attachmentData) {
            super(attachmentData);
        }

        private AbstractAttachment(int version) {
            super(version);
        }

        private AbstractAttachment() {}

        @Override
        final String getAppendixName() {
            return getTransactionType().getName();
        }

        @Override
        final void validate(Transaction transaction) throws NxtException.ValidationException {
            getTransactionType().validateAttachment(transaction);
        }

        @Override
        final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            getTransactionType().apply((TransactionImpl) transaction, senderAccount, recipientAccount);
        }

        @Override
        public final Fee getBaselineFee(Transaction transaction) {
            return getTransactionType().getBaselineFee(transaction);
        }

        @Override
        public final Fee getNextFee(Transaction transaction) {
            return getTransactionType().getNextFee(transaction);
        }

        @Override
        public final int getBaselineFeeHeight() {
            return getTransactionType().getBaselineFeeHeight();
        }

        @Override
        public final int getNextFeeHeight() {
            return getTransactionType().getNextFeeHeight();
        }

 
        final int getFinishValidationHeight(Transaction transaction) {
            return Nxt.getBlockchain().getHeight();
        }

    }

    abstract class EmptyAttachment extends AbstractAttachment {

        private EmptyAttachment() {
            super(0);
        }

        @Override
        final int getMySize() {
            return 0;
        }

        @Override
        final void putMyBytes(ByteBuffer buffer) {
        }

        @Override
        final void putMyJSON(JSONObject json) {
        }

        @Override
        final boolean verifyVersion(byte transactionVersion) {
        	// FIXME (Schauen wieso das vorher nur alte blocks gecheckt hat. ist das attaCHMENT obsolet?)
            return getVersion() == 0;
        }

    }

    EmptyAttachment ORDINARY_PAYMENT = new EmptyAttachment() {

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Payment.ORDINARY;
        }

    };

    // the message payload is in the Appendix
    EmptyAttachment ARBITRARY_MESSAGE = new EmptyAttachment() {

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ARBITRARY_MESSAGE;
        }

    };
    

    final class MessagingHubAnnouncement extends AbstractAttachment {

        private final long minFeePerByteNQT;
        private final String[] uris;

        MessagingHubAnnouncement(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.minFeePerByteNQT = buffer.getLong();
            int numberOfUris = buffer.get();
            if (numberOfUris > Constants.MAX_HUB_ANNOUNCEMENT_URIS) {
                throw new NxtException.NotValidException("Invalid number of URIs: " + numberOfUris);
            }
            this.uris = new String[numberOfUris];
            for (int i = 0; i < uris.length; i++) {
                uris[i] = Convert.readString(buffer, buffer.getShort(), Constants.MAX_HUB_ANNOUNCEMENT_URI_LENGTH);
            }
        }

        MessagingHubAnnouncement(JSONObject attachmentData) throws NxtException.NotValidException {
            super(attachmentData);
            this.minFeePerByteNQT = (Long) attachmentData.get("minFeePerByte");
            try {
                JSONArray urisData = (JSONArray) attachmentData.get("uris");
                this.uris = new String[urisData.size()];
                for (int i = 0; i < uris.length; i++) {
                    uris[i] = (String) urisData.get(i);
                }
            } catch (RuntimeException e) {
                throw new NxtException.NotValidException("Error parsing hub terminal announcement parameters", e);
            }
        }

        public MessagingHubAnnouncement(long minFeePerByteNQT, String[] uris) {
            this.minFeePerByteNQT = minFeePerByteNQT;
            this.uris = uris;
        }

        @Override
        int getMySize() {
            int size = 8 + 1;
            for (String uri : uris) {
                size += 2 + Convert.toBytes(uri).length;
            }
            return size;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(minFeePerByteNQT);
            buffer.put((byte) uris.length);
            for (String uri : uris) {
                byte[] uriBytes = Convert.toBytes(uri);
                buffer.putShort((short)uriBytes.length);
                buffer.put(uriBytes);
            }
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("minFeePerByteNQT", minFeePerByteNQT);
            JSONArray uris = new JSONArray();
            Collections.addAll(uris, this.uris);
            attachment.put("uris", uris);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.HUB_ANNOUNCEMENT;
        }

        public long getMinFeePerByteNQT() {
            return minFeePerByteNQT;
        }

        public String[] getUris() {
            return uris;
        }

    }
    

    public final static class WorkIdentifierCancellation extends AbstractAttachment {

        public long getWorkId() {
			return workId;
		}

		private final long workId;

		WorkIdentifierCancellation(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.workId = buffer.getLong();
        }

		WorkIdentifierCancellation(JSONObject attachmentData) {
            super(attachmentData);
            this.workId = Convert.parseUnsignedLong((String)attachmentData.get("id"));
        }

        public WorkIdentifierCancellation(long workId) {
            this.workId = workId;
        }


        @Override
        int getMySize() {
            return 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(this.workId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("id", Convert.toUnsignedLong(this.workId));
        }

        @Override
        public TransactionType getTransactionType() {
        	return TransactionType.WorkControl.CANCEL_TASK;
        }
    }
    
    public final static class WorkIdentifierCancellationRequest extends AbstractAttachment {

        public long getWorkId() {
			return workId;
		}

		private final long workId;

		WorkIdentifierCancellationRequest(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.workId = buffer.getLong();
        }

		WorkIdentifierCancellationRequest(JSONObject attachmentData) {
            super(attachmentData);
            this.workId = Convert.parseUnsignedLong((String)attachmentData.get("id"));
        }

        public WorkIdentifierCancellationRequest(long workId) {
            this.workId = workId;
        }

        @Override
        int getMySize() {
            return 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(this.workId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("id", Convert.toUnsignedLong(this.workId));
        }

        @Override
        public TransactionType getTransactionType() {
        	return TransactionType.WorkControl.CANCEL_TASK_REQUEST;
        }
    }
    
    public final static class WorkIdentifierBountyPayment extends AbstractAttachment {

        public long getWorkId() {
			return workId;
		}

		private final long workId;

		WorkIdentifierBountyPayment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.workId = buffer.getLong();
        }

		WorkIdentifierBountyPayment(JSONObject attachmentData) {
            super(attachmentData);
            this.workId = Convert.parseUnsignedLong((String)attachmentData.get("id"));
        }

        public WorkIdentifierBountyPayment(long workId) {
            this.workId = workId;
        }

        @Override
        int getMySize() {
            return 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(this.workId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("id", Convert.toUnsignedLong(this.workId));
        }

        @Override
        public TransactionType getTransactionType() {
        	return TransactionType.WorkControl.BOUNTY_PAYOUT;
        }
    }
    
    public final static class PiggybackedProofOfWork extends AbstractAttachment {

        public long getWorkId() {
			return workId;
		}

		private final long workId;
        private final int[] input;
		

		public int[] getInput() {
			return input;
		}

		PiggybackedProofOfWork(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.workId = buffer.getLong();
            
            int numInputVars = buffer.get();
            if (numInputVars > WorkLogicManager.getInstance().getMaxNumberInputInts() || numInputVars < WorkLogicManager.getInstance().getMinNumberInputInts()) {
                throw new NxtException.NotValidException("Invalid number of state variables: " + numInputVars);
            }
            this.input = new int[numInputVars];
            for (int i = 0; i < numInputVars; i++) {
            	input[i] = buffer.getInt();
            }
        }

		PiggybackedProofOfWork(JSONObject attachmentData) {
            super(attachmentData);
            this.workId = Convert.parseUnsignedLong((String)attachmentData.get("id"));

            JSONArray inputRaw = (JSONArray) attachmentData.get("input");
            this.input = new int[inputRaw.size()];
            for (int i = 0; i < inputRaw.size(); i++) {
            	input[i] = ((Long) inputRaw.get(i)).intValue();
            }
        }

        public PiggybackedProofOfWork(long workId, int[] input) {
            this.workId = workId;
            this.input = input;
        }

     

        @Override
        int getMySize() {
            return 8 + 1 /*number of inputs*/ + 4*this.input.length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(this.workId);
            buffer.put((byte)(input.length&0xFF));
            for (int i = 0; i < input.length; i++) {
            	buffer.putInt(input[i]);
            }
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("id", Convert.toUnsignedLong(this.workId));
   
            JSONArray input2 = new JSONArray();
            for(int i=0;i<input.length;++i){
            	input2.add(input[i]);
            }
            attachment.put("input", input2);
        }

        @Override
        public TransactionType getTransactionType() {
        	return TransactionType.WorkControl.PROOF_OF_WORK;
        }

		
    }
    
    public final static class PiggybackedProofOfBounty extends AbstractAttachment {

        public long getWorkId() {
			return workId;
		}

		private final long workId;
        private final int[] input;
		

        public int[] getInput() {
			return input;
		}

		PiggybackedProofOfBounty(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.workId = buffer.getLong();            
            int numberOfOptions = (int)buffer.get();
            if (numberOfOptions > WorkLogicManager.getInstance().getMaxNumberInputInts() || numberOfOptions < WorkLogicManager.getInstance().getMinNumberInputInts()) {
                throw new NxtException.NotValidException("Invalid number of input variables: " + numberOfOptions);
            }
            this.input = new int[numberOfOptions];
            for (int i = 0; i < numberOfOptions; i++) {
            	input[i] = buffer.getInt();
            }
        }

        PiggybackedProofOfBounty(JSONObject attachmentData) {
            super(attachmentData);
            this.workId = Convert.parseUnsignedLong((String)attachmentData.get("id"));
            JSONArray inputRaw = (JSONArray) attachmentData.get("input");
            this.input = new int[inputRaw.size()];
            for (int i = 0; i < inputRaw.size(); i++) {
            	input[i] = ((Long) inputRaw.get(i)).intValue();
            }
        }

        public PiggybackedProofOfBounty(long workId, int[] input) {
            this.workId = workId;
            this.input = input;
        }

        @Override
        int getMySize() {
            return 8 + 1 /*number of inputs*/ + 4*this.input.length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
        	 buffer.putLong(this.workId);
             buffer.put((byte)(input.length&0xFF));
             for (int i = 0; i < input.length; i++) {
             	buffer.putInt(input[i]);
             }
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("id", Convert.toUnsignedLong(this.workId));
            
            JSONArray input2 = new JSONArray();
            for(int i=0;i<input.length;++i){
            	input2.add(input[i]);
            }
            attachment.put("input", input2);
        }

        @Override
        public TransactionType getTransactionType() {
        	return TransactionType.WorkControl.BOUNTY;
        }

    }

    public final static class WorkCreation extends AbstractAttachment {

        private final String workTitle;
        private final int deadline;
        private final long xelPerPow;
        private final int bountyLimit;

        WorkCreation(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.workTitle = Convert.readString(buffer, buffer.getShort(), Constants.MAX_TITLE_LENGTH);
            this.deadline = buffer.getInt();
            this.bountyLimit = buffer.getInt();
            this.xelPerPow = buffer.getLong();
        }

        WorkCreation(JSONObject attachmentData) {
            super(attachmentData);
            
            this.workTitle = ((String) attachmentData.get("title")).trim();
            this.deadline = ((Long) attachmentData.get("deadline")).intValue();
            this.bountyLimit = ((Long) attachmentData.get("bountyLimit")).intValue();
            this.xelPerPow = ((Long) attachmentData.get("xel_per_pow")).longValue();
        }

        public WorkCreation(String workTitle, byte workLanguage, byte[] programmCode, byte numberInputVars, int deadline, int bountyLimit, long xel_per_pow) {
        	this.workTitle = workTitle;
            this.deadline = deadline;
            this.bountyLimit = bountyLimit;
            this.xelPerPow = xel_per_pow;
        }

        @Override
        int getMySize() {
            int size = 2 + Convert.toBytes(workTitle).length + 4 + 8 + 4;
            return size;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.workTitle);
            buffer.putShort((short)name.length);
            buffer.put(name);
            buffer.putInt(this.deadline);
            buffer.putInt(this.bountyLimit);
            buffer.putLong(this.xelPerPow);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("title", this.workTitle);
            attachment.put("deadline", this.deadline);
            attachment.put("bountyLimit", this.bountyLimit);
            attachment.put("xel_per_pow", this.xelPerPow);
            
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.WorkControl.NEW_TASK;
        }

        public String getWorkTitle() {
			return workTitle;
		}

		public int getBountyLimit() {
			return bountyLimit;
		}
		
		public long getXelPerPow() {
			return xelPerPow;
		}

		public int getDeadline() {
			return deadline;
		}

    }
    
    final class MessagingAccountInfo extends AbstractAttachment {

        private final String name;
        private final String description;

        MessagingAccountInfo(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_NAME_LENGTH);
            this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH);
        }

        MessagingAccountInfo(JSONObject attachmentData) {
            super(attachmentData);
            this.name = Convert.nullToEmpty((String) attachmentData.get("name"));
            this.description = Convert.nullToEmpty((String) attachmentData.get("description"));
        }

        public MessagingAccountInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.name);
            byte[] description = Convert.toBytes(this.description);
            buffer.put((byte)name.length);
            buffer.put(name);
            buffer.putShort((short) description.length);
            buffer.put(description);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", name);
            attachment.put("description", description);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ACCOUNT_INFO;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

    }

    final class AccountControlEffectiveBalanceLeasing extends AbstractAttachment {

        private final int period;

        AccountControlEffectiveBalanceLeasing(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.period = Short.toUnsignedInt(buffer.getShort());
        }

        AccountControlEffectiveBalanceLeasing(JSONObject attachmentData) {
            super(attachmentData);
            this.period = ((Long) attachmentData.get("period")).intValue();
        }

        public AccountControlEffectiveBalanceLeasing(int period) {
            this.period = period;
        }

        @Override
        int getMySize() {
            return 2;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putShort((short)period);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("period", period);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
        }

        public int getPeriod() {
            return period;
        }
    }
}

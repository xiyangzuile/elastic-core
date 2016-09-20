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

import nxt.Account.ControlType;
import nxt.AccountLedger.LedgerEvent;
import nxt.Attachment.AbstractAttachment;
import nxt.NxtException.NotValidException;
import nxt.NxtException.ValidationException;
import nxt.util.Convert;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.json.simple.JSONObject;

import ElasticPL.ASTCompilationUnit;
import ElasticPL.ElasticPLParser;
import ElasticPL.ParseException;
import ElasticPL.RuntimeEstimator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public abstract class TransactionType {

    private static final byte TYPE_PAYMENT = 0;
    private static final byte TYPE_MESSAGING = 1;
    private static final byte TYPE_ACCOUNT_CONTROL = 2;
    private static final byte TYPE_WORK_CONTROL = 3;
    private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;
    private static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
    private static final byte SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT = 1;
    private static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 2;
    private static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;
    private static final byte SUBTYPE_WORK_CONTROL_NEW_TASK = 0;
	private static final byte SUBTYPE_WORK_CONTROL_CANCEL_TASK = 1;
	private static final byte SUBTYPE_WORK_CONTROL_PROOF_OF_WORK = 2;
	private static final byte SUBTYPE_WORK_CONTROL_BOUNTY = 3;
	private static final byte SUBTYPE_WORK_CONTROL_BOUNTY_PAYOUT = 4;
	private static final byte SUBTYPE_WORK_CONTROL_CANCEL_TASK_REQUEST = 5;

    public static TransactionType findTransactionType(byte type, byte subtype) {
        switch (type) {
            case TYPE_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return Payment.ORDINARY;
                    default:
                        return null;
                }
            case TYPE_MESSAGING:
                switch (subtype) {
                    case SUBTYPE_MESSAGING_ARBITRARY_MESSAGE:
                        return Messaging.ARBITRARY_MESSAGE;
                    case SUBTYPE_MESSAGING_ACCOUNT_INFO:
                        return Messaging.ACCOUNT_INFO;
                        
                    default:
                        return null;
                }
           
            case TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
                    default:
                        return null;
                }
            case TYPE_WORK_CONTROL:
    			switch (subtype) {
    			case SUBTYPE_WORK_CONTROL_NEW_TASK:
    				return TransactionType.WorkControl.NEW_TASK;
    			case SUBTYPE_WORK_CONTROL_PROOF_OF_WORK:
    				return TransactionType.WorkControl.PROOF_OF_WORK;
    			case SUBTYPE_WORK_CONTROL_BOUNTY:
    				return TransactionType.WorkControl.BOUNTY;
    			case SUBTYPE_WORK_CONTROL_CANCEL_TASK_REQUEST:
    				return TransactionType.WorkControl.CANCEL_TASK_REQUEST;
    			default:
    				return null;
    			}
            default:
                return null;
        }
    }


    public boolean zeroFeeTransaction() {
		return false;
	}


	public boolean moneyComesFromNowhere() {
		return false;
	}


	public boolean specialDepositTX() {
		return false;
	}


	TransactionType() {}

    public abstract byte getType();

    public abstract byte getSubtype();

    public abstract LedgerEvent getLedgerEvent();

    abstract Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException;

    abstract Attachment.AbstractAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException;

    abstract void validateAttachment(Transaction transaction) throws NxtException.ValidationException;

    // return false iff double spending
    final boolean applyUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        long amountNQT = transaction.getAmountNQT();
        long feeNQT = transaction.getFeeNQT();
        if (transaction.referencedTransactionFullHash() != null) {
            feeNQT = Math.addExact(feeNQT, Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
        }
        long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
        if (senderAccount.getUnconfirmedBalanceNQT() < totalAmountNQT
                && !(transaction.getTimestamp() == 0 && Arrays.equals(transaction.getSenderPublicKey(), Genesis.CREATOR_PUBLIC_KEY))) {
            return false;
        }
        senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getId(), -amountNQT, -feeNQT);
        if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getId(), amountNQT, feeNQT);
            return false;
        }
        return true;
    }

    abstract boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    final void apply(TransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        long amount = transaction.getAmountNQT();
        long transactionId = transaction.getId();
        senderAccount.addToBalanceNQT(getLedgerEvent(), transactionId, -amount, -transaction.getFeeNQT());
        
        if (recipientAccount != null) {
            recipientAccount.addToBalanceAndUnconfirmedBalanceNQT(getLedgerEvent(), transactionId, amount);
        }
        applyAttachment(transaction, senderAccount, recipientAccount);
    }

    abstract void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount);

    final void undoUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getId(),
                transaction.getAmountNQT(), transaction.getFeeNQT());
        if (transaction.referencedTransactionFullHash() != null) {
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getId(), 0,
                    Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
        }
    }

    abstract void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    // isBlockDuplicate and isDuplicate share the same duplicates map, but isBlockDuplicate check is done first
    boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, boolean exclusive) {
        return isDuplicate(uniqueType, key, duplicates, exclusive ? 0 : Integer.MAX_VALUE);
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, int maxCount) {
        Map<String,Integer> typeDuplicates = duplicates.get(uniqueType);
        if (typeDuplicates == null) {
            typeDuplicates = new HashMap<>();
            duplicates.put(uniqueType, typeDuplicates);
        }
        Integer currentCount = typeDuplicates.get(key);
        if (currentCount == null) {
            typeDuplicates.put(key, maxCount > 0 ? 1 : 0);
            return false;
        }
        if (currentCount == 0) {
            return true;
        }
        if (currentCount < maxCount) {
            typeDuplicates.put(key, currentCount + 1);
            return false;
        }
        return true;
    }

    boolean isPruned(long transactionId) {
        return false;
    }

    public abstract boolean canHaveRecipient();

    public boolean mustHaveRecipient() {
        return canHaveRecipient();
    }

   
    Fee getBaselineFee(Transaction transaction) {
        return Fee.DEFAULT_FEE;
    }

    Fee getNextFee(Transaction transaction) {
        return getBaselineFee(transaction);
    }

    int getBaselineFeeHeight() {
        return 0;
    }

    int getNextFeeHeight() {
        return Integer.MAX_VALUE;
    }

    long[] getBackFees(Transaction transaction) {
        return Convert.EMPTY_LONG;
    }

    public abstract String getName();

    @Override
    public final String toString() {
        return getName() + " type: " + getType() + ", subtype: " + getSubtype();
    }

    public static abstract class Payment extends TransactionType {

        private Payment() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_PAYMENT;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            if (recipientAccount == null) {
                Account.getAccount(Genesis.CREATOR_ID).addToBalanceAndUnconfirmedBalanceNQT(getLedgerEvent(),
                        transaction.getId(), transaction.getAmountNQT());
            }
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public final boolean canHaveRecipient() {
            return true;
        }

        public static final TransactionType ORDINARY = new Payment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
            }

            @Override
            public final LedgerEvent getLedgerEvent() {
                return LedgerEvent.ORDINARY_PAYMENT;
            }

            @Override
            public String getName() {
                return "OrdinaryPayment";
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                if (transaction.getAmountNQT() <= 0 || transaction.getAmountNQT() >= Constants.MAX_BALANCE_NQT) {
                    throw new NxtException.NotValidException("Invalid ordinary payment");
                }
            }

        };

    }

    public static abstract class Messaging extends TransactionType {

        private Messaging() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_MESSAGING;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        public final static TransactionType ARBITRARY_MESSAGE = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ARBITRARY_MESSAGE;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ARBITRARY_MESSAGE;
            }

            @Override
            public String getName() {
                return "ArbitraryMessage";
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return Attachment.ARBITRARY_MESSAGE;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return Attachment.ARBITRARY_MESSAGE;
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment attachment = transaction.getAttachment();
                if (transaction.getAmountNQT() != 0) {
                    throw new NxtException.NotValidException("Invalid arbitrary message: " + attachment.getJSONObject());
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new NxtException.NotValidException("Sending messages to Genesis not allowed.");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean mustHaveRecipient() {
                return false;
            }

           

        };

        public static final TransactionType HUB_ANNOUNCEMENT = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.HUB_ANNOUNCEMENT;
            }

            @Override
            public String getName() {
                return "HubAnnouncement";
            }

            @Override
            Attachment.MessagingHubAnnouncement parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingHubAnnouncement(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingHubAnnouncement parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.MessagingHubAnnouncement(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingHubAnnouncement attachment = (Attachment.MessagingHubAnnouncement) transaction.getAttachment();
                Hub.addOrUpdateHub(transaction, attachment);
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.MessagingHubAnnouncement attachment = (Attachment.MessagingHubAnnouncement) transaction.getAttachment();
                if (attachment.getMinFeePerByteNQT() < 0 || attachment.getMinFeePerByteNQT() > Constants.MAX_BALANCE_NQT
                        || attachment.getUris().length > Constants.MAX_HUB_ANNOUNCEMENT_URIS) {
                    // cfb: "0" is allowed to show that another way to determine the min fee should be used
                    throw new NxtException.NotValidException("Invalid hub terminal announcement: " + attachment.getJSONObject());
                }
                for (String uri : attachment.getUris()) {
                    if (uri.length() > Constants.MAX_HUB_ANNOUNCEMENT_URI_LENGTH) {
                        throw new NxtException.NotValidException("Invalid URI length: " + uri.length());
                    }
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }
        };
        
        public static final Messaging ACCOUNT_INFO = new Messaging() {

            private final Fee ACCOUNT_INFO_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT, 2 * Constants.ONE_NXT, 32) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction.getAttachment();
                    return attachment.getName().length() + attachment.getDescription().length();
                }
            };

            @Override
            public byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_INFO;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_INFO;
            }

            @Override
            public String getName() {
                return "AccountInfo";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return ACCOUNT_INFO_FEE;
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingAccountInfo(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.MessagingAccountInfo(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo)transaction.getAttachment();
                if (attachment.getName().length() > Constants.MAX_ACCOUNT_NAME_LENGTH
                        || attachment.getDescription().length() > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH) {
                    throw new NxtException.NotValidException("Invalid account info issuance: " + attachment.getJSONObject());
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction.getAttachment();
                senderAccount.setAccountInfo(attachment.getName(), attachment.getDescription());
            }

            @Override
            boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                return isDuplicate(Messaging.ACCOUNT_INFO, getName(), duplicates, true);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }


        };

    }

    public static abstract class AccountControl extends TransactionType {

        private AccountControl() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_ACCOUNT_CONTROL;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        public static final TransactionType EFFECTIVE_BALANCE_LEASING = new AccountControl() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public String getName() {
                return "EffectiveBalanceLeasing";
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(buffer, transactionVersion);
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing) transaction.getAttachment();
                Account.getAccount(transaction.getSenderId()).leaseEffectiveBalance(transaction.getRecipientId(), attachment.getPeriod());
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing)transaction.getAttachment();
                if (transaction.getSenderId() == transaction.getRecipientId()) {
                    throw new NxtException.NotValidException("Account cannot lease balance to itself");
                }
                if (transaction.getAmountNQT() != 0) {
                    throw new NxtException.NotValidException("Transaction amount must be 0 for effective balance leasing");
                }
                if (attachment.getPeriod() < Constants.LEASING_DELAY || attachment.getPeriod() > 65535) {
                    throw new NxtException.NotValidException("Invalid effective balance leasing period: " + attachment.getPeriod());
                }
                byte[] recipientPublicKey = Account.getPublicKey(transaction.getRecipientId());
                if (recipientPublicKey == null) {
                    throw new NxtException.NotCurrentlyValidException("Invalid effective balance leasing: "
                            + " recipient account " + Long.toUnsignedString(transaction.getRecipientId()) + " not found or no public key published");
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new NxtException.NotValidException("Leasing to Genesis account not allowed");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

         

        };

    };

	public static abstract class WorkControl extends TransactionType {

		private WorkControl() {
		}

		@Override
		public final byte getType() {
			return TransactionType.TYPE_WORK_CONTROL;
		}

		@Override
		boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
			return true;
		}

		@Override void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
		}

		public final static TransactionType NEW_TASK = new WorkControl() {

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_NEW_TASK;
			}

			@Override
			Attachment.WorkCreation parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.WorkCreation(buffer, transactionVersion);
			}

			@Override
			Attachment.WorkCreation parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
		try{
				return new Attachment.WorkCreation(attachmentData);
		}catch(Exception e){
			e.printStackTrace();
			throw e;
		}
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
				try{
				Attachment.WorkCreation attachment = (Attachment.WorkCreation) transaction.getAttachment();
				Work.addWork(transaction, attachment);
				}catch(Exception e){
					e.printStackTrace();
					throw e;
				}
			}

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				Attachment.WorkCreation attachment = (Attachment.WorkCreation) transaction.getAttachment();
				
				// Immediately fail attachment validation if transaction has no SourceCode Appendix
				if(transaction.getPrunableSourceCode() == null) {
					throw new NxtException.NotValidException("Work creation transaction MUST come with a source code appendix");
				}
				
				// Check for correct title length
				if (attachment.getWorkTitle().length() > Constants.MAX_TITLE_LENGTH || attachment.getWorkTitle().length() < 1) {
					throw new NxtException.NotValidException("User provided POW Algorithm has incorrect title length");
		        }
				
				// Verify Deadline 
				if(attachment.getDeadline() > Constants.MAX_DEADLINE_FOR_WORK || attachment.getDeadline() < Constants.MIN_DEADLINE_FOR_WORK){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct deadline");
	        	}
				
				// Verify Bounty Limit
				if(attachment.getBountyLimit() > Constants.MAX_WORK_BOUNTY_LIMIT || attachment.getBountyLimit() < Constants.MIN_WORK_BOUNTY_LIMIT){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct bounty limit");
	        	}
				
				// Verify XEL per Pow
				if(attachment.getXelPerPow() < Constants.MIN_XEL_PER_POW){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct xel/pow price");
	        	}
				
				// minimal payout check
				if(transaction.getAmountNQT() < Constants.PAY_FOR_AT_LEAST_X_POW*attachment.getXelPerPow() ){
					throw new NxtException.NotValidException("You must attach XEL for at least 20 POW submissions, i.e., " + (Constants.PAY_FOR_AT_LEAST_X_POW*attachment.getXelPerPow()) + " XEL");
				}
				
				
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public boolean specialDepositTX() {
				return true;

			}

			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_CREATION;
			}

			@Override
			public String getName() {
				return "WorkCreation";
			}

		};

		public final static TransactionType CANCEL_TASK_REQUEST = new WorkControl() {

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_CANCEL_TASK_REQUEST;
			}

			@Override
			Attachment.WorkIdentifierCancellationRequest parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierCancellationRequest(buffer, transactionVersion);
			}

			@Override
			Attachment.WorkIdentifierCancellationRequest parseAttachment(JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierCancellationRequest(attachmentData);
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
				Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction
						.getAttachment();
				Work.getWork(attachment.getWorkId()).natural_timeout();
			}
			
			@Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
				
				// Whitelist one specific, broken TX on the testnet (TODO, FIXME, TOREMOVE)
				List<Long> valid = new ArrayList<Long>();
				valid.add(Convert.parseUnsignedLong("7598634667181063828"));
				valid.add(Convert.parseUnsignedLong("2649304177138953307"));
				valid.add(Convert.parseUnsignedLong("10947343728076427070"));
				valid.add(Convert.parseUnsignedLong("16510862602090746736"));
				valid.add(Convert.parseUnsignedLong("12295851544522530488"));
				valid.add(Convert.parseUnsignedLong("4910487200763544778"));
				valid.add(Convert.parseUnsignedLong("17361899352291164537"));
				valid.add(Convert.parseUnsignedLong("16952754390074337318"));
				if(valid.contains(transaction.getId()))
					return false;
				
                Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction.getAttachment();
                return isDuplicate(WorkControl.CANCEL_TASK_REQUEST, String.valueOf(attachment.getWorkId()), duplicates, true);
            }
			
			@Override
            boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction.getAttachment();
                return isDuplicate(WorkControl.CANCEL_TASK_REQUEST, String.valueOf(attachment.getWorkId()), duplicates, true);
            }

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction
						.getAttachment();
				
				Work w = Work.getWorkByWorkId(attachment.getWorkId());
				
				if(w==null){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " does not exist yet");
				}
				
				// Whitelist one specific, broken TX on the testnet (TODO, FIXME, TOREMOVE)
				List<Long> valid = new ArrayList<Long>();
				valid.add(Convert.parseUnsignedLong("15378102222798893417"));
				valid.add(Convert.parseUnsignedLong("17933960539120946680"));
				valid.add(Convert.parseUnsignedLong("9543329167509234430"));
				
				if(w.isClosed() && !valid.contains(transaction.getId())){
					throw new NxtException.NotValidException("Work " + attachment.getWorkId() + " is already closed");
				}
				
				if (w.getSender_account_id() != transaction
						.getSenderId()) {
					throw new NxtException.NotValidException("Only the work creator can cancel this work");
				}
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			@Override
			public boolean moneyComesFromNowhere() {
				return false;
			}

			@Override
			public boolean zeroFeeTransaction() {
				return false;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_CANCELLATION_REQUEST;
			}

			@Override
			public String getName() {
				return "WorkIdentifierCancellationRequest";
			}
		};

		public final static TransactionType PROOF_OF_WORK = new WorkControl() {

			private LRUCache soft_unblock_cache = new LRUCache(50);
			
			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_PROOF_OF_WORK;
			}

			@Override
			Attachment.PiggybackedProofOfWork parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfWork(buffer, transactionVersion);
			}

			@Override
			Attachment.PiggybackedProofOfWork parseAttachment(JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfWork(attachmentData);
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
				
				Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
				PowAndBounty.addPow(transaction, attachment);
				PowAndBounty obj = PowAndBounty.getPowOrBountyById(transaction.getId());
				obj.applyPowPayment();
			}
			
			@Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction.getAttachment();
                return isDuplicate(WorkControl.PROOF_OF_WORK, Convert.toHexString(attachment.getHash()), duplicates, true);
            }
			
			@Override
            boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction.getAttachment();
                return isDuplicate(WorkControl.PROOF_OF_WORK, Convert.toHexString(attachment.getHash()), duplicates, true);
            }

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				
				if(transaction.getDeadline() != 3){
					throw new NxtException.NotValidException("POW/Bounties must have a dead line of 3 minutes");
				}
				
				Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
						
				Work w = Work.getWorkByWorkId(attachment.getWorkId());	
				
				if(w==null){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " does not exist");
				}
				
				byte[] hash = attachment.getHash();
				if(PowAndBounty.hasHash(hash)){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " already has this submission, dropping duplicate");
				}
				
				Long rel_id;
				
				if(transaction.getBlock() != null)
					rel_id = transaction.getBlock().getPreviousBlockId();
				else
					rel_id = BlockchainImpl.getInstance().getLastBlockId();
				
				BigInteger soft_unblock_target = soft_unblock_cache.get(rel_id);
				BlockImpl b = BlockchainImpl.getInstance().getBlock(rel_id);
				BigInteger real_block_target = b.getMinPowTarget();

				if(soft_unblock_target == null){
					soft_unblock_target = real_block_target;
					int counter = Constants.POW_VERIFICATION_UNBLOCK_WHEN_VALID_IN_LAST_BLOCKS;
					while (counter > 0){
						counter --;
						b = b.getPreviousBlock();
						if(b == null)
							break;
						BigInteger tempDiff = b.getMinPowTarget();
						if (tempDiff.compareTo(soft_unblock_target) > 0) {
							soft_unblock_target = tempDiff;
						}
					}
					soft_unblock_cache.set(rel_id, soft_unblock_target);
				}
				
				Executioner.POW_CHECK_RESULT valid = Executioner.POW_CHECK_RESULT.ERROR;
				
				
				if(PrunableSourceCode.isPrunedByWorkId(attachment.getWorkId())){
					// If the tx is already pruned we assume POW is valid!
					// no need to execute after all! We assume that the pruning is happened long enough ago
					valid = Executioner.POW_CHECK_RESULT.OK;
				}else{
					PrunableSourceCode code = nxt.PrunableSourceCode.getPrunableSourceCodeByWorkId(attachment.getWorkId());
					try {
						valid = Executioner.executeProofOfWork(code.getSource(), attachment.getWorkId(), attachment.getInput(), real_block_target, soft_unblock_target);
					} catch (Exception e1) {
						e1.printStackTrace();
						throw new NxtException.NotValidException(
								"Proof of work is invalid: causes ElasticPL function to crash");
					}
					if (valid == Executioner.POW_CHECK_RESULT.ERROR) {
						throw new NxtException.NotValidException(
								"Proof of work is invalid: does neither meet target " + real_block_target.toString(16) + " nor the soft block target " + soft_unblock_target.toString(16));
					}
					if (valid == Executioner.POW_CHECK_RESULT.SOFT_UNBLOCKED) {
						throw new NxtException.LostValidityException(
								"Proof of work became invalid: block target changed recently");
					}
				}
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}

			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			public boolean moneyComesFromNowhere() {
				return true;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_POW;
			}

			@Override
			public String getName() {
				return "PiggybackedProofOfWork";
			}

		};
		public final static TransactionType BOUNTY = new WorkControl() {

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_BOUNTY;
			}

			@Override
			Attachment.PiggybackedProofOfBounty parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBounty(buffer, transactionVersion);
			}

			@Override
			Attachment.PiggybackedProofOfBounty parseAttachment(JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBounty(attachmentData);
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {

				Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				PowAndBounty.addBounty(transaction, attachment);
				PowAndBounty obj = PowAndBounty.getPowOrBountyById(transaction.getId());
				obj.applyBounty();
			}
			
			@Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction.getAttachment();
                return isDuplicate(WorkControl.BOUNTY, Convert.toHexString(attachment.getHash()), duplicates, true);
            }
			
			@Override
            boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction.getAttachment();
                return isDuplicate(WorkControl.BOUNTY, Convert.toHexString(attachment.getHash()), duplicates, true);
            }
			
	
			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				
						
				Work w = Work.getWorkByWorkId(attachment.getWorkId());
				
				if(w==null){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " does not exist");
				}
				
				byte[] hash = attachment.getHash();
				if(PowAndBounty.hasHash(hash)){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " already has this submission, dropping duplicate");
				}
				
				long rel_id = transaction.getBlockId();
				boolean valid = false;
				
				if(PrunableSourceCode.isPrunedByWorkId(attachment.getWorkId())){
					// If the tx is already pruned we assume POW is valid!
					// no need to execute after all! We assume that the pruning is happened long enough ago
					valid = true;
				}else{
					PrunableSourceCode code = nxt.PrunableSourceCode.getPrunableSourceCodeByWorkId(attachment.getWorkId());
					try {
						valid = Executioner.executeBountyHooks(code.getSource(), attachment.getInput());
					} catch (Exception e1) {
						e1.printStackTrace();
						throw new NxtException.NotValidException(
								"Bounty is invalid: causes ElasticPL function to crash");
					}
					if (!valid) {
						System.err.println("POW was not valid!!");
						throw new NxtException.NotValidException(
								"Bounty is invalid: does not meet requirement");
					}
				}
				
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}

			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			public boolean moneyComesFromNowhere() {
				return false;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_BOUNTY;
			}

			@Override
			public String getName() {
				return "PiggybackedProofOfBounty";
			}
		};
		
	};

}

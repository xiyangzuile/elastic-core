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
import nxt.NxtException.ValidationException;
import nxt.util.Convert;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.json.simple.JSONObject;

import ElasticPL.ASTCompilationUnit;
import ElasticPL.ElasticPLParser;
import ElasticPL.RuntimeEstimator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;


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
    			case SUBTYPE_WORK_CONTROL_CANCEL_TASK:
    				return TransactionType.WorkControl.CANCEL_TASK;
    			case SUBTYPE_WORK_CONTROL_PROOF_OF_WORK:
    				return TransactionType.WorkControl.PROOF_OF_WORK;
    			case SUBTYPE_WORK_CONTROL_BOUNTY:
    				return TransactionType.WorkControl.BOUNTY;
    			case SUBTYPE_WORK_CONTROL_BOUNTY_PAYOUT:
    				return TransactionType.WorkControl.BOUNTY_PAYOUT;
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
				return new Attachment.WorkCreation(attachmentData);
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
				Attachment.WorkCreation attachment = (Attachment.WorkCreation) transaction.getAttachment();
				// To calculate the WorkID i just take the TxID and calculate +
				// 1
				WorkLogicManager.getInstance().createNewWork(transaction.getId(), transaction.getId(),
						transaction.getSenderId(), transaction.getBlockId(), transaction.getBlock().getHeight(),
						transaction.getAmountNQT(), transaction.getFeeNQT(), attachment);
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
				if(WorkLogicManager.getInstance().checkDeadline(attachment.getDeadline()) == false){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct deadline");
	        	}
				
				// Verify Bounty Limit
				if(WorkLogicManager.getInstance().checkDeadline(attachment.getBountyLimit()) == false){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct bounty limit");
	        	}
				
				// Verify XEL per Pow
				if(WorkLogicManager.getInstance().isPowPriceCorrect(attachment.getXelPerPow()) == false){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct xel/pow price");
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

		public final static TransactionType CANCEL_TASK = new WorkControl() {

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_CANCEL_TASK;
			}

			@Override
			Attachment.WorkIdentifierCancellation parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierCancellation(buffer, transactionVersion);
			}

			@Override
			Attachment.WorkIdentifierCancellation parseAttachment(JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierCancellation(attachmentData);
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
				Attachment.WorkIdentifierCancellation attachment = (Attachment.WorkIdentifierCancellation) transaction
						.getAttachment();
				WorkLogicManager.getInstance().cancelWork(transaction, attachment);
			}

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				Attachment.WorkIdentifierCancellation attachment = (Attachment.WorkIdentifierCancellation) transaction
						.getAttachment();

				if (WorkLogicManager.getInstance().isStillPending(attachment.getWorkId()) == false) {
					throw new NxtException.NotValidException("Cannot cancel already cancelled or finished work");
				}
				
				
				if (WorkLogicManager.getInstance().getTransactionInitiator(attachment.getWorkId()) != transaction
						.getRecipientId()) {
					throw new NxtException.NotValidException("The receipient must be the work initiator");
				}
			}

			@Override
			public boolean canHaveRecipient() {
				return true;
			}

			@Override
			public boolean mustHaveRecipient() {
				return true;
			}

			@Override
			public boolean moneyComesFromNowhere() {
				return true;
			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_CANCELLATION;
			}

			@Override
			public String getName() {
				return "WorkIdentifierCancellation";
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
		
			}

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction
						.getAttachment();
				
				if (WorkLogicManager.getInstance().doesWorkExist(attachment.getWorkId()) == false){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " does not exist yet");
				}

				if (WorkLogicManager.getInstance().isStillPending(attachment.getWorkId(),
						transaction.getSenderId()) == false) {
					throw new NxtException.NotValidException("Cannot cancel already cancelled or finished work " + attachment.getWorkId() + ", issued in block: " + transaction.getBlockId());
				}
				
				if (WorkLogicManager.getInstance().getTransactionInitiator(attachment.getWorkId()) != transaction
						.getSenderId()) {
					throw new NxtException.NotValidException("The receipient must be the work initiator");
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
				return true;
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
				// TODO FIXME! Dismisses Exception might cause hard forks,
				// inspect!
				Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
				try {
					WorkLogicManager.getInstance().createNewProofOfWork(attachment.getWorkId(), transaction.getId(),
							transaction.getSenderId(), transaction.getBlockId(), transaction.getAmountNQT(),
							attachment);
					WorkLogicManager.getInstance().removePowToUnconfirmed(attachment.getWorkId(), transaction.getId());

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			@Override
			void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
				Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
				WorkLogicManager.getInstance().removePowToUnconfirmed(attachment.getWorkId(), transaction.getId());
			}
			
			@Override
			boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount){
				Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
				
				// TODO, check for amount, because oterwise we can DOS here.
				// Check if the "paid out amount" is okay, should match the "price per XEL" price in the work package
				long getXelPerPow_mustBe = WorkLogicManager.getInstance().getXelPerPow(attachment.getWorkId());
				if(transaction.getAmountNQT() != getXelPerPow_mustBe){
					return false;
				}
				
				HashSet<Long> uniqueSet = new HashSet<Long>();
				uniqueSet.add(attachment.getWorkId());
				
				HashMap<Long, Long> powXelLeft = WorkLogicManager.getInstance().getPOWFundLeft(uniqueSet, true);
				//System.out.println("POW RECEIVED UNCONFIRMED, work " + attachment.getWorkId() + " has POW FUND left: " + ((Long)powXelLeft.get(attachment.getWorkId())));
				if(((Long)powXelLeft.get(attachment.getWorkId())) < transaction.getAmountNQT()){
					System.out.println("Discarded POW Submission for work_id=" + attachment.getWorkId() + ", POW fund exceeded!");
					return false;
				}
				
				WorkLogicManager.getInstance().addPowToUnconfirmed(attachment.getWorkId(), transaction.getId(), transaction.getAmountNQT());
				return true;
			}

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				// Validate Bounty
				Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
				
				if (WorkLogicManager.getInstance().doesWorkExist(attachment.getWorkId()) == false){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " does not exist yet");
				}
				
				// No submissions for cancelled work please!
				if (WorkLogicManager.getInstance().isStillPending(attachment.getWorkId()) == false) {
					throw new NxtException.NotValidException("Cannot submit POW " + transaction.getId() + " for an already cancelled or finished work " + attachment.getWorkId());
				}
				
				// Check if the "paid out amount" is okay, should match the "price per XEL" price in the work package
				long getXelPerPow_mustBe = WorkLogicManager.getInstance().getXelPerPow(attachment.getWorkId());
				if(transaction.getAmountNQT() != getXelPerPow_mustBe){
					throw new NxtException.NotValidException("POW" + transaction.getId() + "Transaction spends wrong amount of XEL");
				}
				
				if(!WorkLogicManager.getInstance().isUniquePOW(transaction.getId(), attachment.getInput())){
					throw new NxtException.NotValidException("POW" + transaction.getId() + "with those inputs has already been submitted earlier");
				}

				if (transaction.getBlock() == null) // in this case,
													// heuristically
													// pre-validate with current
													// block as next prev (if
													// does not validate in
													// block, then it is gonna
													// be kicked later
					WorkLogicManager.getInstance().validatePOW(transaction.getId(), attachment,
							transaction.getAmountNQT(), BlockchainImpl.getInstance().getLastBlock().getId());
				else
					WorkLogicManager.getInstance().validatePOW(transaction.getId(), attachment,
							transaction.getAmountNQT(), transaction.getBlock().getPreviousBlockId());

				
			}

			@Override
			public boolean canHaveRecipient() {
				return true;
			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}

			@Override
			public boolean mustHaveRecipient() {
				return true;
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
				// TODO FIXME! Dismisses Exception might cause hard forks,
				// inspect!
				Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				try {
					WorkLogicManager.getInstance().createNewBounty(attachment.getWorkId(), transaction.getId(),
							transaction.getSenderId(), transaction.getBlockId(), transaction.getAmountNQT(),
							attachment);
					// Remove this one from the unconfirmed counter! TODO FIXME, check if this woks fine here!
					WorkLogicManager.getInstance().removeBountyToUnconfirmed(attachment.getWorkId(), transaction.getId());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			@Override
			void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
				Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				WorkLogicManager.getInstance().removeBountyToUnconfirmed(attachment.getWorkId(), transaction.getId());
			}
			
			@Override
			boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount){
				
				if (transaction.getAmountNQT() != 0) {
					return false;
				}
				
				Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				
				int confirmedLeft = WorkLogicManager.getInstance().getBountyNumberLeft(attachment.getWorkId());
				int unconfirmedQueued = WorkLogicManager.getInstance().getBountyUnconfirmed(attachment.getWorkId());
				Integer bountiesLeft = confirmedLeft - unconfirmedQueued;
				if(bountiesLeft <= 0){
					System.out.println("Discarded Bounty Submission for work_id=" + attachment.getWorkId() + ", number exceeded! confirmed left = " + confirmedLeft + ", unconfirmed pending = " + unconfirmedQueued);
					return false;
				}
				
				WorkLogicManager.getInstance().addBountyToUnconfirmed(attachment.getWorkId(), transaction.getId());
				return true;
			}


			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				

				Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				
				if (WorkLogicManager.getInstance().doesWorkExist(attachment.getWorkId()) == false){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " does not exist yet");
				}
				
				// No submissions for cancelled work please!
				if (WorkLogicManager.getInstance().isStillPending(attachment.getWorkId()) == false) {
					throw new NxtException.NotValidException("Cannot submit POW for an already cancelled or finished work");
				}
				
				// There are no amounts here, payout will be done in separate
				// bounty payout transaction when the work was cancelled or
				// timeouted
				if (transaction.getAmountNQT() != 0) {
					throw new NxtException.NotValidException("Bounty submissions must not transfer any amount of XEL");
				}
				

				// Check if those inputs are already submitted in the DB
				if(!WorkLogicManager.getInstance().isUniqueBounty(transaction.getId(), attachment.getInput())){
					throw new NxtException.NotValidException("Bounty submission has already been submitted earlier");
				}
				
				// Validate Bounty by executing code
				WorkLogicManager.getInstance().validateBounty(transaction.getId(), attachment);
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

		public final static TransactionType BOUNTY_PAYOUT = new WorkControl() {

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_BOUNTY_PAYOUT;
			}

			@Override
			Attachment.WorkIdentifierBountyPayment parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierBountyPayment(buffer, transactionVersion);
			}

			@Override
			Attachment.WorkIdentifierBountyPayment parseAttachment(JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierBountyPayment(attachmentData);
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
				// here, nothing gets applies
			}

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				// Validation will be done in the block itself
			}

			@Override
			public boolean canHaveRecipient() {
				return true;
			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}

			@Override
			public boolean mustHaveRecipient() {
				return true;
			}

			public boolean moneyComesFromNowhere() {
				return true;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_BOUNTY_PAYOUT;
			}

			@Override
			public String getName() {
				// TODO Auto-generated method stub
				return "BountyPayout";
			}
		};
	};

}

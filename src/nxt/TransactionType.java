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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import nxt.util.IPValidator;
import nxt.util.Logger;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;
import org.json.simple.JSONObject;

import nxt.AccountLedger.LedgerEvent;
import nxt.NxtException.NotValidException;
import nxt.util.Convert;

public abstract class TransactionType {

	public static abstract class AccountControl extends TransactionType {

		public static final TransactionType EFFECTIVE_BALANCE_LEASING = new AccountControl() {

			@Override
			void applyAttachment(final Transaction transaction, final Account senderAccount,
					final Account recipientAccount) {
				final Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing) transaction
						.getAttachment();
				Account.getAccount(transaction.getSenderId()).leaseEffectiveBalance(transaction.getRecipientId(),
						attachment.getPeriod());
			}

			@Override
			public boolean canHaveRecipient() {
				return true;
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
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
			}

			@Override
			Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(final ByteBuffer buffer,
					final byte transactionVersion) throws NxtException.NotValidException {
				return new Attachment.AccountControlEffectiveBalanceLeasing(buffer, transactionVersion);
			}

			@Override
			Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.AccountControlEffectiveBalanceLeasing(attachmentData);
			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {
				final Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing) transaction
						.getAttachment();
				if (transaction.getSenderId() == transaction.getRecipientId()) {
					throw new NxtException.NotValidException("Account cannot lease balance to itself");
				}
				if (transaction.getAmountNQT() != 0) {
					throw new NxtException.NotValidException(
							"Transaction amount must be 0 for effective balance leasing");
				}
				if ((attachment.getPeriod() < Constants.LEASING_DELAY) || (attachment.getPeriod() > 65535)) {
					throw new NxtException.NotValidException(
							"Invalid effective balance leasing period: " + attachment.getPeriod());
				}
				final byte[] recipientPublicKey = Account.getPublicKey(transaction.getRecipientId());
				if (recipientPublicKey == null) {
					throw new NxtException.NotCurrentlyValidException("Invalid effective balance leasing: "
							+ " recipient account " + Long.toUnsignedString(transaction.getRecipientId())
							+ " not found or no public key published");
				}
				if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
					throw new NxtException.NotValidException("Leasing to Genesis account not allowed");
				}
			}

		};

		private AccountControl() {
		}

		@Override
		final boolean applyAttachmentUnconfirmed(final Transaction transaction, final Account senderAccount) {
			return true;
		}

		@Override
		public final byte getType() {
			return TransactionType.TYPE_ACCOUNT_CONTROL;
		}

		@Override
		final void undoAttachmentUnconfirmed(final Transaction transaction, final Account senderAccount) {
		}

	}

	public static abstract class Messaging extends TransactionType {

		public static final TransactionType SUPERNODE_ANNOUNCEMENT = new Messaging() {

			@Override
			void applyAttachment(final Transaction transaction, final Account senderAccount,
					final Account recipientAccount) {
				final Attachment.MessagingSupernodeAnnouncement attachment = (Attachment.MessagingSupernodeAnnouncement) transaction
						.getAttachment();

				Account senderAcc = Account.getAccount(transaction.getSenderId());

				if(attachment.getGuardNodeBlockId()!=0){
					Account blockAccount = Account.getAccount(attachment.getGuardNodeBlockId());
					blockAccount.invalidateSupernodeDeposit();
				}else {
					try {
						senderAcc.refreshSupernodeDeposit(attachment.getUris());
					} catch (IOException e) {
						// abviously, this failed! Just pass through .. was a nonsense transaction without any impact
					}
				}
            }

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.SUPERNODE_ANNOUNCEMENT;
			}

			@Override
			public String getName() {
				return "HubAnnouncement";
			}

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_MESSAGING_SUPERNODE_ANNOUNCEMENT;
			}

			@Override
			Attachment.MessagingSupernodeAnnouncement parseAttachment(final ByteBuffer buffer, final byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.MessagingSupernodeAnnouncement(buffer, transactionVersion);
			}

			@Override
			Attachment.MessagingSupernodeAnnouncement parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.MessagingSupernodeAnnouncement(attachmentData);
			}

			@Override
			boolean isDuplicate(final Transaction transaction,
								final Map<TransactionType, Map<String, Integer>> duplicates) {


					return TransactionType.isDuplicate(Messaging.SUPERNODE_ANNOUNCEMENT,
						String.valueOf(transaction.getSenderId()), duplicates, true);
			}

			@Override
			boolean isUnconfirmedDuplicate(final Transaction transaction,
										   final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.MessagingSupernodeAnnouncement attachment = (Attachment.MessagingSupernodeAnnouncement) transaction
						.getAttachment();
				final boolean duplicate = TransactionType.isDuplicate(Messaging.SUPERNODE_ANNOUNCEMENT,
						String.valueOf(transaction.getSenderId()), duplicates, true);

				return duplicate;
			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {
				final Attachment.MessagingSupernodeAnnouncement attachment = (Attachment.MessagingSupernodeAnnouncement) transaction
						.getAttachment();

				int howManyUrls = attachment.getUris().length;
				long guard_id = attachment.getGuardNodeBlockId();

				if(guard_id == 0 && (howManyUrls<=0 || howManyUrls > Constants.MAX_SUPERNODE_ANNOUNCEMENT_URIS))
				{
					throw new NxtException.NotValidException("Invalid URI number");
				}
				else if(guard_id != 0 && (howManyUrls!=0))
				{
					throw new NxtException.NotValidException("Invalid URI number, must be 0 for guard node block");
				}

				for (final String uri : attachment.getUris()) {
					if (uri.length() > Constants.MAX_SUPERNODE_ANNOUNCEMENT_URI_LENGTH) {
						throw new NxtException.NotValidException("Invalid URI length: " + uri.length());
					}

					// Check if URL has the correct format (IPv4 address)
					if(IPValidator.getInstance().validate(uri) == false){
						throw new NxtException.NotValidException("URIs contain invalid SN IP-addresses");
					}
				}

				// Check if this node either is already a supernode (extend SN membership)
				// or if it has enough funds to cover the amount required to become a super node
				Account senderAcc = Account.getAccount(transaction.getSenderId());
				if(senderAcc == null)
					throw new NxtException.NotValidException("Sender account " + transaction.getSenderId() + " had no activity before. Please do something else first!");

				if(guard_id == 0 && (!senderAcc.isSuperNode() && !senderAcc.isGuardNode() && senderAcc.getUnconfirmedBalanceNQT()<Constants.SUPERNODE_DEPOSIT_AMOUNT)){
					throw new NxtException.NotValidException("Your guaranteed balance does not cover the required super node deposit");
				}
				if(guard_id != 0 && (!senderAcc.isGuardNode())){
					throw new NxtException.NotValidException("Invalid guard node");
				}

			}
		};

		public static final Messaging ACCOUNT_INFO = new Messaging() {

			private final Fee ACCOUNT_INFO_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT, 2 * Constants.ONE_NXT, 32) {
				@Override
				public int getSize(final TransactionImpl transaction, final Appendix appendage) {
					final Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction
							.getAttachment();
					return attachment.getName().length() + attachment.getDescription().length();
				}
			};

			@Override
			void applyAttachment(final Transaction transaction, final Account senderAccount,
					final Account recipientAccount) {
				final Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction
						.getAttachment();
				senderAccount.setAccountInfo(attachment.getName(), attachment.getDescription());
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			Fee getBaselineFee(final Transaction transaction) {
				return this.ACCOUNT_INFO_FEE;
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
			public byte getSubtype() {
				return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_INFO;
			}

			@Override
			boolean isBlockDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				return TransactionType.isDuplicate(Messaging.ACCOUNT_INFO, this.getName(), duplicates, true);
			}

			@Override
			Attachment.MessagingAccountInfo parseAttachment(final ByteBuffer buffer, final byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.MessagingAccountInfo(buffer, transactionVersion);
			}

			@Override
			Attachment.MessagingAccountInfo parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.MessagingAccountInfo(attachmentData);
			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {
				final Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction
						.getAttachment();
				if ((attachment.getName().length() > Constants.MAX_ACCOUNT_NAME_LENGTH)
						|| (attachment.getDescription().length() > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH)) {
					throw new NxtException.NotValidException(
							"Invalid account info issuance: " + attachment.getJSONObject());
				}
			}

		};

		private Messaging() {
		}

		@Override
		final boolean applyAttachmentUnconfirmed(final Transaction transaction, final Account senderAccount) {
			return true;
		}

		@Override
		public final byte getType() {
			return TransactionType.TYPE_MESSAGING;
		}

		@Override
		final void undoAttachmentUnconfirmed(final Transaction transaction, final Account senderAccount) {
		}

	}

	public static abstract class Payment extends TransactionType {

		public static final TransactionType ORDINARY = new Payment() {

			@Override
			public final LedgerEvent getLedgerEvent() {
				return LedgerEvent.ORDINARY_PAYMENT;
			}

			@Override
			public String getName() {
				return "OrdinaryPayment";
			}

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
			}

			@Override
			Attachment.EmptyAttachment parseAttachment(final ByteBuffer buffer, final byte transactionVersion)
					throws NxtException.NotValidException {
				return Attachment.ORDINARY_PAYMENT;
			}

			@Override
			Attachment.EmptyAttachment parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				return Attachment.ORDINARY_PAYMENT;
			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {
				if ((transaction.getAttachment() != null)
						&& (transaction.getAttachment() instanceof Attachment.RedeemAttachment)) {
					throw new NxtException.NotValidException("Invalid attachment found");
				}
				if ((transaction.getAmountNQT() <= 0) || (transaction.getAmountNQT() >= Constants.MAX_BALANCE_NQT)) {
					throw new NxtException.NotValidException("Invalid ordinary payment");
				}
			}

		};

		public static final TransactionType REDEEM = new Payment() {

			@Override
			void applyAttachment(final Transaction transaction, final Account senderAccount,
					final Account recipientAccount) {
				Redeem.add((TransactionImpl) transaction);
			}

			@Override
			public final LedgerEvent getLedgerEvent() {
				return LedgerEvent.REDEEM_PAYMENT;
			}

			@Override
			public String getName() {
				return "RedeemPayment";
			}

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_PAYMENT_REDEEM;
			}

			@Override
			boolean isDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.RedeemAttachment attachment = (Attachment.RedeemAttachment) transaction
						.getAttachment();
				return TransactionType.isDuplicate(Payment.REDEEM, String.valueOf(attachment.getAddress()), duplicates,
						true);
			}

			@Override
			boolean isUnconfirmedDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.RedeemAttachment attachment = (Attachment.RedeemAttachment) transaction
						.getAttachment();
				boolean duplicate = TransactionType.isDuplicate(Payment.REDEEM, String.valueOf(attachment.getAddress()),
						duplicates, true);
				if (duplicate == false) {
					duplicate = Redeem.isAlreadyRedeemed(attachment.getAddress());
					if(duplicate)
						transaction.setExtraInfo("Genesis entry already redeemed.");
				}
				return duplicate;
			}

			@Override
			public boolean mustHaveRecipient() {
				return true;
			}

			@Override
			Attachment.RedeemAttachment parseAttachment(final ByteBuffer buffer, final byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.RedeemAttachment(buffer, transactionVersion);
			}

			@Override
			Attachment.RedeemAttachment parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.RedeemAttachment(attachmentData);
			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {
				final Attachment.RedeemAttachment attachment = (Attachment.RedeemAttachment) transaction
						.getAttachment();

				if (transaction.getFeeNQT() != 0) {
					throw new NxtException.NotValidException("You have to send a redeem TX without any fees");
				}

				if (!attachment.getAddress().matches("[a-zA-Z0-9-;]*")) {
					throw new NxtException.NotValidException(
							"Invalid characters in redeem transaction: fields.address");
				}

				// Check if this "address" is a valid entry in the "genesis
				// block" claim list
				if (Redeem.hasAddress(attachment.getAddress()) == false) {
					throw new NxtException.NotValidException("You have no right to claim from genesis");
				}

				// Check if the amountNQT matches the "allowed" amount
				final Long claimableAmount = Redeem.getClaimableAmount(attachment.getAddress());
				if ((claimableAmount <= 0) || (claimableAmount != transaction.getAmountNQT())) {
					throw new NxtException.NotValidException("You can only claim exactly " + claimableAmount + " NQT");
				}

				if (!attachment.getSecp_signatures().matches("[a-zA-Z0-9+/=-]*")) {
					throw new NxtException.NotValidException(
							"Invalid characters in redeem transaction: fields.secp_signatures");
				}
				if (transaction.getRecipientId() == 0) {
					throw new NxtException.NotValidException("Invalid receiver ID in redeem transaction");
				}

				// Finally, do the costly SECP signature verification checks
				final ArrayList<String> signedBy = new ArrayList<>();
				final ArrayList<String> signatures = new ArrayList<>();
				final ArrayList<String> addresses = new ArrayList<>();
				int need = 0;
				int gotsigs = 0;
				final String addy = attachment.getAddress();
				final String sigs = attachment.getSecp_signatures();
				if (addy.indexOf("-") >= 0) {
					final String[] multiples = addy.split(";")[0].split("-");
					need = Integer.valueOf(multiples[0]);
					for (int i = 1; i < multiples.length; ++i) {
						addresses.add(multiples[i]);
					}
				} else {
					need = 1;
					addresses.add(addy);
				}
				if (sigs.indexOf("-") >= 0) {
					final String[] multiples = sigs.split("-");
					gotsigs = multiples.length;
					for (final String multiple : multiples) {
						signatures.add(multiple);
					}
				} else {
					gotsigs = 1;
					signatures.add(sigs);
				}

				if (signatures.size() != need) {
					throw new NxtException.NotValidException("You have to provide exactly " + String.valueOf(need)
							+ " signatures, you provided " + gotsigs);
				}

				Logger.logDebugMessage("Found REDEEM transaction");
				Logger.logDebugMessage("========================");
				final String message = "I hereby confirm to redeem "
						+ String.valueOf(transaction.getAmountNQT()).replace("L", "") + " NQT-XEL from genesis entry "
						+ attachment.getAddress() + " to account "
						+ Convert.toUnsignedLong(transaction.getRecipientId()).replace("L", "");
				Logger.logDebugMessage("String to sign:\t" + message);
				Logger.logDebugMessage("We need " + String.valueOf(need) + " signatures from these addresses:");
				for (int i = 0; i < addresses.size(); ++i) {
					Logger.logDebugMessage(" -> " + addresses.get(i));
				}
				Logger.logDebugMessage("We got " + String.valueOf(gotsigs) + " signatures:");
				for (int i = 0; i < signatures.size(); ++i) {
					Logger.logDebugMessage(
							" -> " + signatures.get(i).substring(0, Math.min(12, signatures.get(i).length())) + "...");
					ECKey result;
					try {
						new ECKey();
						result = ECKey.signedMessageToKey(message, signatures.get(i));
					} catch (final SignatureException e) {
						throw new NxtException.NotValidException("Invalid signatures provided");
					}

					if (result == null) {
						throw new NxtException.NotValidException("Invalid signatures provided");
					}

					final String add = result.toAddress(MainNetParams.get()).toString();
					signedBy.add(add);

				}
				Logger.logDebugMessage("These addresses seem to have signed:");
				for (int i = 0; i < signedBy.size(); ++i) {
					Logger.logDebugMessage(" -> " + signedBy.get(i));
				}

				addresses.retainAll(signedBy);
				Logger.logDebugMessage("We matched " + String.valueOf(need) + " signatures from these addresses:");
				for (int i = 0; i < addresses.size(); ++i) {
					Logger.logDebugMessage(" -> " + addresses.get(i));
				}
				if (addresses.size() != need) {
					Logger.logDebugMessage(
							"== " + String.valueOf(addresses.size()) + " out of " + String.valueOf(need) + " matched!");
					throw new NxtException.NotValidException(
							"You have to provide exactly " + String.valueOf(need) + " correct signatures");
				}
			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}
		};

		private Payment() {
		}

		@Override
		void applyAttachment(final Transaction transaction, final Account senderAccount,
				final Account recipientAccount) {
			if (recipientAccount == null) {
				Account.getAccount(Genesis.FUCKED_TX_ID).addToBalanceAndUnconfirmedBalanceNQT(this.getLedgerEvent(),
						transaction.getId(), transaction.getAmountNQT());
			}
		}

		@Override
		final boolean applyAttachmentUnconfirmed(final Transaction transaction, final Account senderAccount) {
			return true;
		}

		@Override
		public final boolean canHaveRecipient() {
			return true;
		}

		@Override
		public final byte getType() {
			return TransactionType.TYPE_PAYMENT;
		}

		@Override
		final void undoAttachmentUnconfirmed(final Transaction transaction, final Account senderAccount) {
		}

	}

	public static abstract class WorkControl extends TransactionType {

		public final static TransactionType NEW_TASK = new WorkControl() {

			@Override
			void applyAttachment(final Transaction transaction, final Account senderAccount,
					final Account recipientAccount) {
				try {
					final Attachment.WorkCreation attachment = (Attachment.WorkCreation) transaction.getAttachment();
					Work.addWork(transaction, attachment);
				} catch (final Exception e) {
					e.printStackTrace();
					throw e;
				}
			}

			@Override
			public boolean canHaveRecipient() {
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

			@Override public boolean mustHaveSupernodeSignature() {
				return true; // NEW WORK must go through supernode
			}

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_NEW_TASK;
			}

			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			@Override
			Attachment.WorkCreation parseAttachment(final ByteBuffer buffer, final byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.WorkCreation(buffer, transactionVersion);
			}

			@Override
			Attachment.WorkCreation parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				try {
					return new Attachment.WorkCreation(attachmentData);
				} catch (final Exception e) {
					e.printStackTrace();
					throw e;
				}
			}

			@Override
			public boolean specialDepositTX() {
				return true;

			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {
				final Attachment.WorkCreation attachment = (Attachment.WorkCreation) transaction.getAttachment();

				// Immediately fail attachment validation if transaction has no
				// SourceCode Appendix
				if (transaction.getPrunableSourceCode() == null) {
					throw new NxtException.NotValidException(
							"Work creation transaction MUST come with a source code appendix");
				}

				// Now, source must not be pruned if the transaction is "young"
				if (!transaction.getPrunableSourceCode().hasPrunableData()
						&& !(transaction.getTimestamp() < (Nxt.getEpochTime() - Constants.MIN_PRUNABLE_LIFETIME))) {
					throw new NxtException.NotValidException(
							"Script kiddie, stay home! Please refrain from pruning unpruneable data");
				}

				// Check for correct title length
				if ((attachment.getWorkTitle().length() > Constants.MAX_TITLE_LENGTH)
						|| (attachment.getWorkTitle().length() < 1)) {
					throw new NxtException.NotValidException("User provided POW Algorithm has incorrect title length");
				}

				// Verify Deadline
				if ((attachment.getDeadline() > Constants.MAX_DEADLINE_FOR_WORK)
						|| (attachment.getDeadline() < Constants.MIN_DEADLINE_FOR_WORK)) {
					throw new NxtException.NotValidException(
							"User provided POW Algorithm does not have a correct deadline");
				}

				// Verify Bounty Limit
				if ((attachment.getBountyLimit() > Constants.MAX_WORK_BOUNTY_LIMIT)
						|| (attachment.getBountyLimit() < Constants.MIN_WORK_BOUNTY_LIMIT)) {
					throw new NxtException.NotValidException(
							"User provided POW Algorithm does not have a correct bounty limit");
				}

				// Verify XEL per Pow
				if (attachment.getXelPerPow() < Constants.MIN_XEL_PER_POW) {
					throw new NxtException.NotValidException(
							"User provided POW Algorithm does not have a correct xel/pow price");
				}

				// Verify XEL per Bounty
				if (attachment.getXelPerBounty() < Constants.MIN_XEL_PER_BOUNTY) {
					throw new NxtException.NotValidException(
							"User provided POW Algorithm does not have a correct xel/bounty price");
				}

				// minimal payout check
				if (transaction.getAmountNQT() < ((Constants.PAY_FOR_AT_LEAST_X_POW * attachment.getXelPerPow())
						+ (attachment.getXelPerBounty() * attachment.getBountyLimit()))) {
					throw new NxtException.NotValidException(
							"You must attach XEL for at least 20 POW submissions and all bounties, i.e., "
									+ ((Constants.PAY_FOR_AT_LEAST_X_POW * attachment.getXelPerPow())
											+ (attachment.getXelPerBounty() * attachment.getBountyLimit()))
									+ " XEL");
				}
			}

		};

		public final static TransactionType CANCEL_TASK_REQUEST = new WorkControl() {

			@Override
			void applyAttachment(final Transaction transaction, final Account senderAccount,
					final Account recipientAccount) {
				final Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction
						.getAttachment();
				Work.getWork(attachment.getWorkId()).natural_timeout(transaction.getBlock());
			}

			@Override
			public boolean canHaveRecipient() {
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

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_CANCEL_TASK_REQUEST;
			}

			@Override
			boolean isDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {

				final Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction
						.getAttachment();
				return TransactionType.isDuplicate(WorkControl.CANCEL_TASK_REQUEST,
						String.valueOf(attachment.getWorkId()), duplicates, true);
			}

			@Override
			boolean isUnconfirmedDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction
						.getAttachment();
				final boolean duplicate = TransactionType.isDuplicate(WorkControl.CANCEL_TASK_REQUEST,
						String.valueOf(attachment.getWorkId()), duplicates, true);

				if (duplicate == false) {
					final Work w = Work.getWorkByWorkId(attachment.getWorkId());
					if (w == null) {
						return true; // Assume tx with invalid work is duplicate
										// to prevent it getting to the lower
										// system levels
					}
					if (w.isClosed()) {
						return true;
					}
					if (w.isClose_pending()) {
						return true;
					}
				}

				return duplicate;
			}



			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			@Override
			Attachment.WorkIdentifierCancellationRequest parseAttachment(final ByteBuffer buffer,
					final byte transactionVersion) throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierCancellationRequest(buffer, transactionVersion);
			}

			@Override
			Attachment.WorkIdentifierCancellationRequest parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierCancellationRequest(attachmentData);
			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {
				final Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction
						.getAttachment();

				final Work w = Work.getWorkByWorkId(attachment.getWorkId());

				if (w == null) {
					throw new NxtException.NotCurrentlyValidException(
							"Work " + attachment.getWorkId() + " does not exist yet");
				}

				if (w.isClosed()) {
					throw new NxtException.NotCurrentlyValidException(
							"Work " + attachment.getWorkId() + " is already closed");
				}

				if (w.getSender_account_id() != transaction.getSenderId()) {
					throw new NxtException.NotValidException("Only the work creator can cancel this work");
				}
			}

			@Override
			public boolean zeroFeeTransaction() {
				return false;
			}
		};

		public final static TransactionType PROOF_OF_WORK = new WorkControl() {

			// private final LRUCache soft_unblock_cache = new LRUCache(50);

			@Override
			void applyAttachment(final Transaction transaction, final Account senderAccount,
					final Account recipientAccount) throws NotValidException {

				final Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
				PowAndBounty.addPow(transaction, attachment);
				final PowAndBounty obj = PowAndBounty.getPowOrBountyById(transaction.getId());
				try {
					obj.applyPowPayment(transaction.getBlock(), transaction.getSupernodeId());
				} catch (final Exception e) {
					throw new NotValidException(e.getMessage());
				}

			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override public boolean mustHaveSupernodeSignature() {
				return true; // NEW WORK must go through supernode
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_POW;
			}

			@Override
			public String getName() {
				return "PiggybackedProofOfWork";
			}

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_PROOF_OF_WORK;
			}

			@Override
			boolean isDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
				return TransactionType.isDuplicate(WorkControl.PROOF_OF_WORK, Convert.toHexString(attachment.getHash()),
						duplicates, true);
			}

			@Override
			boolean isUnconfirmedDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
				boolean duplicate = TransactionType.isDuplicate(WorkControl.PROOF_OF_WORK,
						Convert.toHexString(attachment.getHash()), duplicates, true);
				if (duplicate == false) {
					// This is required to limit the amount of unconfirmed POWs
					// to not exceed either the money or the hard limit per
					// block.
					final Work w = Work.getWork(attachment.getWorkId());

					if (w.isClose_pending()) {
						transaction.setExtraInfo("work already closed");
						return true;
					}
					if (w.isClosed()) {
						transaction.setExtraInfo("work already closed");
						return true;
					}

					final long bal_fund = w.getBalance_pow_fund();
					final long xel_per_pow = w.getXel_per_pow();
					final long how_many_left = Math.floorDiv(bal_fund, xel_per_pow);
					int left = Integer.MAX_VALUE;
					if (how_many_left < left) {
						left = (int) how_many_left;
					}

					boolean soft_throttling = false;
					if (left > Constants.MAX_POWS_PER_BLOCK) {
						soft_throttling = true;
						left = Constants.MAX_POWS_PER_BLOCK;
					}

					if (left <= 0) {
						transaction.setExtraInfo("work ran out of funds");
						duplicate = true;
					} else {
						duplicate = TransactionType.isDuplicate(WorkControl.PROOF_OF_WORK,
								String.valueOf(attachment.getWorkId()), duplicates, left);
						if (soft_throttling) {
							transaction.setExtraInfo("maximum pows per block reached");
						} else {
							transaction.setExtraInfo("work ran out of funds");
						}
					}
				}
				return duplicate;
			}



			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			@Override
			Attachment.PiggybackedProofOfWork parseAttachment(final ByteBuffer buffer, final byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfWork(buffer, transactionVersion);
			}

			@Override
			Attachment.PiggybackedProofOfWork parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfWork(attachmentData);
			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {

				if (transaction.getDeadline() != 3) {
					throw new NxtException.NotValidException("POW/Bounties must have a dead line of 3 minutes");
				}

				final Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();

				final Work w = Work.getWorkByWorkId(attachment.getWorkId());

				if (w == null) {
					throw new NxtException.NotCurrentlyValidException(
							"Work " + Convert.toUnsignedLong(attachment.getWorkId()) + " does not exist");
				}

				final byte[] hash = attachment.getHash();
				if (PowAndBounty.hasHash(attachment.getWorkId(), hash)) {
					throw new NxtException.NotCurrentlyValidException(
							"Work " + Convert.toUnsignedLong(attachment.getWorkId())
									+ " already has this submission, dropping duplicate");
				}

				// final BigInteger real_block_target = w.getWork_min_pow_target_bigint();


			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}

		};

		public final static TransactionType BOUNTY_ANNOUNCEMENT = new WorkControl() {

			@Override
			void applyAttachment(final Transaction transaction, final Account senderAccount,
					final Account recipientAccount) throws NxtException.NotValidException {

				final Attachment.PiggybackedProofOfBountyAnnouncement attachment = (Attachment.PiggybackedProofOfBountyAnnouncement) transaction
						.getAttachment();
				final PowAndBountyAnnouncements obj = PowAndBountyAnnouncements.addBountyAnnouncement(transaction,
						attachment);
				try {
					obj.applyBountyAnnouncement(transaction.getBlock());
				} catch (final Exception e) {
					throw new NxtException.NotValidException(e.getMessage());
				}
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_BOUNTY_ANNOUNCEMENT;
			}

			@Override
			public String getName() {
				return "PiggybackedProofOfBountyAnnouncement";
			}

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_BOUNTY_ANNOUNCEMENT;
			}

			@Override
			boolean isDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.PiggybackedProofOfBountyAnnouncement attachment = (Attachment.PiggybackedProofOfBountyAnnouncement) transaction
						.getAttachment();
				return TransactionType.isDuplicate(WorkControl.BOUNTY_ANNOUNCEMENT,
						Convert.toHexString(attachment.getHashAnnounced()), duplicates, true);
			}

			@Override
			boolean isUnconfirmedDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.PiggybackedProofOfBountyAnnouncement attachment = (Attachment.PiggybackedProofOfBountyAnnouncement) transaction
						.getAttachment();
				boolean duplicate = TransactionType.isDuplicate(WorkControl.BOUNTY_ANNOUNCEMENT,
						Convert.toHexString(attachment.getHashAnnounced()), duplicates, true);
				if (duplicate == false) {
					// This is required to limit the amount of unconfirmed BNT
					// Announcements to not exceed the requested bounty # by the
					// requester.
					// But first, check out how many more we want from what has
					// been already confirmed!
					final Work w = Work.getWork(attachment.getWorkId());

					if (w.isClose_pending()) {
						return true;
					}
					if (w.isClosed()) {
						return true;
					}

					final int count_wanted = w.getBounty_limit();
					final int count_has_announcements = w.getReceived_bounty_announcements();
					final int left_wanted = count_wanted - count_has_announcements;
					if (left_wanted <= 0) {
						transaction.setExtraInfo("no more bounty announcement slots available");
						duplicate = true;
					} else {
						duplicate = TransactionType.isDuplicate(WorkControl.BOUNTY_ANNOUNCEMENT,
								String.valueOf(attachment.getWorkId()), duplicates, left_wanted);
					}
				}
				return duplicate;
			}



			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			@Override
			Attachment.PiggybackedProofOfBountyAnnouncement parseAttachment(final ByteBuffer buffer,
					final byte transactionVersion) throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBountyAnnouncement(buffer, transactionVersion);
			}

			@Override
			Attachment.PiggybackedProofOfBountyAnnouncement parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBountyAnnouncement(attachmentData);
			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {
				final Attachment.PiggybackedProofOfBountyAnnouncement attachment = (Attachment.PiggybackedProofOfBountyAnnouncement) transaction
						.getAttachment();
				final Account acc = Account.getAccount(transaction.getSenderId());
				if ((acc == null) || (acc.getGuaranteedBalanceNQT(1,
						Nxt.getBlockchain().getHeight()) < Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION)) {
					throw new NxtException.NotValidException(
							"You cannot cover the " + Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION
									+ " NQT deposit fee for your bounty announcement with confirmed funds.");
				}

				final Work w = Work.getWorkByWorkId(attachment.getWorkId());

				if (w == null) {
					throw new NxtException.NotCurrentlyValidException(
							"Work " + attachment.getWorkId() + " does not exist");
				}

				if (PrunableSourceCode.isPrunedByWorkId(w.getId())) {
					// If the tx is already pruned AND the transaction timestamp
					// is old enough, we assume submission is valid!
					// no need to execute after all! We assume that the pruning
					// is happened long enough ago
					// This is valid, because MIN_PRUNABLE_LIFETIME is longer
					// than the maximum work timeout length! We are just
					// catching up the blockchain here, not actually "submitting
					// live work"
					if (!(transaction.getTimestamp() < (Nxt.getEpochTime() - Constants.MIN_PRUNABLE_LIFETIME))) {
						throw new NxtException.NotValidException(
								"Bounty announcement is invalid: references a work package with pruned source code when it should not be pruned");
					}
				}

				final byte[] hash = attachment.getHashAnnounced();
				if (PowAndBountyAnnouncements.hasHash(attachment.getWorkId(), hash)) {
					throw new NxtException.NotCurrentlyValidException(
							"Work " + Convert.toUnsignedLong(attachment.getWorkId())
									+ " already has this submission, dropping duplicate");
				}

			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}
		};

		public final static TransactionType BOUNTY = new WorkControl() {

			@Override
			void applyAttachment(final Transaction transaction, final Account senderAccount,
					final Account recipientAccount) throws NotValidException {

				final Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				PowAndBounty.addBounty(transaction, attachment);
				final PowAndBounty obj = PowAndBounty.getPowOrBountyById(transaction.getId());
				try {
					obj.applyBounty(transaction.getBlock(), transaction.getSupernodeId());
				} catch (final Exception e) {
					throw new NotValidException(e.getMessage());
				}
			}

			@Override public boolean mustHaveSupernodeSignature() {
				return true; // NEW WORK must go through supernode
			}

			@Override
			public boolean canHaveRecipient() {
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

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_BOUNTY;
			}

			@Override
			boolean isDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				return TransactionType.isDuplicate(WorkControl.BOUNTY, Convert.toHexString(attachment.getHash()),
						duplicates, true);
			}

			@Override
			boolean isUnconfirmedDuplicate(final Transaction transaction,
					final Map<TransactionType, Map<String, Integer>> duplicates) {
				final Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				final boolean duplicate = TransactionType.isDuplicate(WorkControl.BOUNTY,
						Convert.toHexString(attachment.getHash()), duplicates, true);
				if (duplicate == false) {
					// This is required to limit the amount of unconfirmed BNT
					// Announcements to not exceed the requested bounty # by the
					// requester.
					// But first, check out how many more we want from what has
					// been already confirmed!
					final Work w = Work.getWork(attachment.getWorkId());

					if (w.isClosed()) {
						transaction.setExtraInfo("work is already closed, you missed the reveal period");
						return true;
					}

				}
				return duplicate;
			}



			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			@Override
			Attachment.PiggybackedProofOfBounty parseAttachment(final ByteBuffer buffer, final byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBounty(buffer, transactionVersion);
			}

			@Override
			Attachment.PiggybackedProofOfBounty parseAttachment(final JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBounty(attachmentData);
			}

			@Override
			void validateAttachment(final Transaction transaction) throws NxtException.ValidationException {
				final Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();

				final Work w = Work.getWorkByWorkId(attachment.getWorkId());

				if (w == null) {
					throw new NxtException.NotCurrentlyValidException(
							"Work " + Convert.toUnsignedLong(attachment.getWorkId()) + " does not exist");
				}

				// check if we had an announcement for this bounty earlier
				final boolean hadAnnouncement = PowAndBountyAnnouncements.hasValidHash(attachment.getWorkId(),
						attachment.getHash());
				if (!hadAnnouncement) {
					throw new NxtException.NotCurrentlyValidException("Work "
							+ Convert.toUnsignedLong(attachment.getWorkId())
							+ " has not yet seen a \"counted\" bounty announcement for this submission with work_id "
							+ attachment.getWorkId() + ", hash " + Convert.toHexString(attachment.getHash())
							+ " and multi " + Convert.toHexString(attachment.getMultiplicator()));
				}

				final byte[] hash = attachment.getHash();
				if (PowAndBounty.hasHash(attachment.getWorkId(), hash)) {
					throw new NxtException.NotCurrentlyValidException(
							"Work " + Convert.toUnsignedLong(attachment.getWorkId())
									+ " already has this submission, dropping duplicate");
				}

				transaction.getBlockId();
			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}
		};

		private WorkControl() {
		}

		@Override
		boolean applyAttachmentUnconfirmed(final Transaction transaction, final Account senderAccount) {
			return true;
		}

		@Override
		public final byte getType() {
			return TransactionType.TYPE_WORK_CONTROL;
		}

		@Override
		void undoAttachmentUnconfirmed(final Transaction transaction, final Account senderAccount) {
		}

	}

	private static final byte TYPE_PAYMENT = 0;
	private static final byte TYPE_MESSAGING = 1;
	private static final byte TYPE_ACCOUNT_CONTROL = 2;
	private static final byte TYPE_WORK_CONTROL = 3;
	private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;
	private static final byte SUBTYPE_PAYMENT_REDEEM = 1;
	private static final byte SUBTYPE_MESSAGING_SUPERNODE_ANNOUNCEMENT = 1;
	private static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 2;
	private static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;
	private static final byte SUBTYPE_WORK_CONTROL_NEW_TASK = 0;

	private static final byte SUBTYPE_WORK_CONTROL_PROOF_OF_WORK = 2;

	private static final byte SUBTYPE_WORK_CONTROL_BOUNTY = 3;

	private static final byte SUBTYPE_WORK_CONTROL_BOUNTY_ANNOUNCEMENT = 4;

	private static final byte SUBTYPE_WORK_CONTROL_CANCEL_TASK_REQUEST = 5;

	public static TransactionType findTransactionType(final byte type, final byte subtype) {
		switch (type) {
		case TYPE_PAYMENT:
			switch (subtype) {
			case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
				return Payment.ORDINARY;
			case SUBTYPE_PAYMENT_REDEEM:
				return Payment.REDEEM;
			default:
				return null;
			}
		case TYPE_MESSAGING:
			switch (subtype) {
			case SUBTYPE_MESSAGING_SUPERNODE_ANNOUNCEMENT:
				return Messaging.SUPERNODE_ANNOUNCEMENT;
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
			case SUBTYPE_WORK_CONTROL_BOUNTY_ANNOUNCEMENT:
				return TransactionType.WorkControl.BOUNTY_ANNOUNCEMENT;
			case SUBTYPE_WORK_CONTROL_CANCEL_TASK_REQUEST:
				return TransactionType.WorkControl.CANCEL_TASK_REQUEST;
			default:
				return null;
			}
		default:
			return null;
		}
	}

	static boolean isDuplicate(final TransactionType uniqueType, final String key,
			final Map<TransactionType, Map<String, Integer>> duplicates, final boolean exclusive) {
		return TransactionType.isDuplicate(uniqueType, key, duplicates, exclusive ? 0 : Integer.MAX_VALUE);
	}

	static boolean isDuplicate(final TransactionType uniqueType, final String key,
			final Map<TransactionType, Map<String, Integer>> duplicates, final int maxCount) {
		Map<String, Integer> typeDuplicates = duplicates.get(uniqueType);
		if (typeDuplicates == null) {
			typeDuplicates = new HashMap<>();
			duplicates.put(uniqueType, typeDuplicates);
		}
		final Integer currentCount = typeDuplicates.get(key);
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

	static boolean isDuplicateOnlyCheck(final TransactionType uniqueType, final String key,
			final Map<TransactionType, Map<String, Integer>> duplicates, final int maxCount) {
		Map<String, Integer> typeDuplicates = duplicates.get(uniqueType);
		if (typeDuplicates == null) {
			typeDuplicates = new HashMap<>();
			duplicates.put(uniqueType, typeDuplicates);
		}
		final Integer currentCount = typeDuplicates.get(key);
		if (currentCount == null) {
			return false;
		}
		if (currentCount == 0) {
			return true;
		}
		if (currentCount < maxCount) {
			return false;
		}
		return true;
	}

	TransactionType() {
	}

	final void apply(final TransactionImpl transaction, final Account senderAccount, final Account recipientAccount)
			throws NotValidException {
		final long amount = transaction.getAmountNQT();
		final long transactionId = transaction.getId();
		senderAccount.addToBalanceNQT(this.getLedgerEvent(), transactionId, -amount, -transaction.getFeeNQT());

		if (recipientAccount != null) {
			recipientAccount.addToBalanceAndUnconfirmedBalanceNQT(this.getLedgerEvent(), transactionId, amount);
		}
		this.applyAttachment(transaction, senderAccount, recipientAccount);
	}

	abstract void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount)
			throws NotValidException;

	abstract boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

	// return false iff double spending
	final boolean applyUnconfirmed(final TransactionImpl transaction, final Account senderAccount) {
		final long amountNQT = transaction.getAmountNQT();
		long feeNQT = transaction.getFeeNQT();
		if (transaction.referencedTransactionFullHash() != null) {
			feeNQT = Math.addExact(feeNQT, Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
		}
		final long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
		if ((senderAccount.getUnconfirmedBalanceNQT() < totalAmountNQT) && !((transaction.getTimestamp() == 0)
				&& Arrays.equals(transaction.getSenderPublicKey(), Genesis.CREATOR_PUBLIC_KEY))) {
			return false;
		}
		senderAccount.addToUnconfirmedBalanceNQT(this.getLedgerEvent(), transaction.getId(), -amountNQT, -feeNQT);
		if (!this.applyAttachmentUnconfirmed(transaction, senderAccount)) {
			senderAccount.addToUnconfirmedBalanceNQT(this.getLedgerEvent(), transaction.getId(), amountNQT, feeNQT);
			return false;
		}
		return true;
	}

	public abstract boolean canHaveRecipient();

	long[] getBackFees(final Transaction transaction) {
		return Convert.EMPTY_LONG;
	}

	Fee getBaselineFee(final Transaction transaction) {
		return Fee.DEFAULT_FEE;
	}

	int getBaselineFeeHeight() {
		return 0;
	}

	public abstract LedgerEvent getLedgerEvent();

	public abstract String getName();

	Fee getNextFee(final Transaction transaction) {
		return this.getBaselineFee(transaction);
	}

	int getNextFeeHeight() {
		return Integer.MAX_VALUE;
	}

	public abstract byte getSubtype();

	public abstract byte getType();

	// isBlockDuplicate and isDuplicate share the same duplicates map, but
	// isBlockDuplicate check is done first
	boolean isBlockDuplicate(final Transaction transaction,
			final Map<TransactionType, Map<String, Integer>> duplicates) {
		return false;
	}

	boolean isDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
		return false;
	}

	boolean isPruned(final long transactionId) {
		return false;
	}

	boolean isUnconfirmedDuplicate(final Transaction transaction,
			final Map<TransactionType, Map<String, Integer>> duplicates) {
		return false;
	}

	public boolean mustHaveSupernodeSignature() {
		return false;
	}

	public boolean mustHaveRecipient() {
		return this.canHaveRecipient();
	} // TODO :check

	abstract Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion)
			throws NxtException.NotValidException;

	abstract Attachment.AbstractAttachment parseAttachment(JSONObject attachmentData)
			throws NxtException.NotValidException;

	public boolean specialDepositTX() {
		return false;
	}

	@Override
	public final String toString() {
		return this.getName() + " type: " + this.getType() + ", subtype: " + this.getSubtype();
	}

	abstract void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

	final void undoUnconfirmed(final TransactionImpl transaction, final Account senderAccount) {
		this.undoAttachmentUnconfirmed(transaction, senderAccount);
		senderAccount.addToUnconfirmedBalanceNQT(this.getLedgerEvent(), transaction.getId(), transaction.getAmountNQT(),
				transaction.getFeeNQT());
		if (transaction.referencedTransactionFullHash() != null) {
			senderAccount.addToUnconfirmedBalanceNQT(this.getLedgerEvent(), transaction.getId(), 0,
					Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
		}
	}

	abstract void validateAttachment(Transaction transaction) throws NxtException.ValidationException;;

	public boolean zeroFeeTransaction() {
		return false;
	};

}

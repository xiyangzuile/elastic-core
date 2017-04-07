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

package nxt.http;

import java.util.Arrays;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import nxt.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.TransactionType.Payment;
import nxt.crypto.Crypto;
import nxt.util.Convert;

abstract class CreateTransaction extends APIServlet.APIRequestHandler {

	private static final String[] commonParameters = new String[] { "secretPhrase", "publicKey", "feeNQT", "deadline",
			"referencedTransactionFullHash", "broadcast", "message", "messageIsText", "messageIsPrunable",
			"messageToEncrypt", "messageToEncryptIsText", "encryptedMessageData", "encryptedMessageNonce",
			"encryptedMessageIsPrunable", "compressMessageToEncrypt", "messageToEncryptToSelf",
			"messageToEncryptToSelfIsText", "encryptToSelfMessageData", "encryptToSelfMessageNonce",
			"compressMessageToEncryptToSelf", "phased", "phasingFinishHeight", "phasingVotingModel", "phasingQuorum",
			"phasingMinBalance", "phasingHolding", "phasingMinBalanceModel", "phasingWhitelisted", "phasingWhitelisted",
			"phasingWhitelisted", "phasingLinkedFullHash", "phasingLinkedFullHash", "phasingLinkedFullHash",
			"phasingHashedSecret", "phasingHashedSecretAlgorithm", "recipientPublicKey", "ecBlockId", "ecBlockHeight" };

	private static String[] addCommonParameters(final String[] parameters) {
		final String[] result = Arrays.copyOf(parameters,
				parameters.length + CreateTransaction.commonParameters.length);
		System.arraycopy(CreateTransaction.commonParameters, 0, result, parameters.length,
				CreateTransaction.commonParameters.length);
		return result;
	}

	CreateTransaction(final APITag[] apiTags, final String... parameters) {
		super(apiTags, CreateTransaction.addCommonParameters(parameters));
		if (!this.getAPITags().contains(APITag.CREATE_TRANSACTION)) {
			throw new RuntimeException(
					"CreateTransaction API " + this.getClass().getName() + " is missing APITag.CREATE_TRANSACTION tag");
		}
	}

	CreateTransaction(final String fileParameter, final APITag[] apiTags, final String... parameters) {
		super(fileParameter, apiTags, CreateTransaction.addCommonParameters(parameters));
		if (!this.getAPITags().contains(APITag.CREATE_TRANSACTION)) {
			throw new RuntimeException(
					"CreateTransaction API " + this.getClass().getName() + " is missing APITag.CREATE_TRANSACTION tag");
		}
	}

	@Override
	protected final boolean allowRequiredBlockParameters() {
		return false;
	}

	final JSONStreamAware createTransaction(final HttpServletRequest req, final Account senderAccount,
			final Attachment attachment) throws NxtException {
		return this.createTransaction(req, senderAccount, 0, 0, attachment);
	}

	final JSONStreamAware createTransaction(final HttpServletRequest req, final Account senderAccount,
			final long recipientId, final long amountNQT) throws NxtException {
		return this.createTransaction(req, senderAccount, recipientId, amountNQT, Attachment.ORDINARY_PAYMENT);
	}

	final JSONStreamAware createTransaction(final HttpServletRequest req, final Account senderAccount,
			final long recipientId, final long amountNQT, final Attachment attachment) throws NxtException {
		final String deadlineValue = req.getParameter("deadline");
		final String referencedTransactionFullHash = Convert
				.emptyToNull(req.getParameter("referencedTransactionFullHash"));
		final String secretPhrase = ParameterParser.getSecretPhrase(req, false);
		final String publicKeyValue = Convert.emptyToNull(req.getParameter("publicKey"));
		final boolean broadcast = !"false".equalsIgnoreCase(req.getParameter("broadcast")) && (secretPhrase != null);

		Appendix.PrunableSourceCode prunableSourceCode = null;
		if (attachment.getTransactionType() == TransactionType.WorkControl.NEW_TASK) {
			prunableSourceCode = (Appendix.PrunableSourceCode) ParameterParser.getSourceCode(req);
		}
		Appendix.PublicKeyAnnouncement publicKeyAnnouncement = null;
		final String recipientPublicKey = Convert.emptyToNull(req.getParameter("recipientPublicKey"));
		if (recipientPublicKey != null) {
			publicKeyAnnouncement = new Appendix.PublicKeyAnnouncement(Convert.parseHexString(recipientPublicKey));
		}

		if ((secretPhrase == null) && (publicKeyValue == null)) {
			return JSONResponses.MISSING_SECRET_PHRASE;
		} else if (deadlineValue == null) {
			return JSONResponses.MISSING_DEADLINE;
		}

		short deadline;
		try {
			deadline = Short.parseShort(deadlineValue);
			if (deadline < 1) {
				return JSONResponses.INCORRECT_DEADLINE;
			}
		} catch (final NumberFormatException e) {
			return JSONResponses.INCORRECT_DEADLINE;
		}

		final long feeNQT = ParameterParser.getFeeNQT(req);
		final int ecBlockHeight = ParameterParser.getInt(req, "ecBlockHeight", 0, Integer.MAX_VALUE, false);
		long ecBlockId = ParameterParser.getUnsignedLong(req, "ecBlockId", false);
		if ((ecBlockId != 0) && (ecBlockId != Nxt.getBlockchain().getBlockIdAtHeight(ecBlockHeight))) {
			return JSONResponses.INCORRECT_EC_BLOCK;
		}
		if ((ecBlockId == 0) && (ecBlockHeight > 0)) {
			ecBlockId = Nxt.getBlockchain().getBlockIdAtHeight(ecBlockHeight);
		}

		final JSONObject response = new JSONObject();

		// shouldn't try to get publicKey from senderAccount as it may have not
		// been set yet
		byte[] publicKey = null;
		if (attachment instanceof Attachment.RedeemAttachment) {
			publicKey = Convert.parseHexString(Genesis.REDEEM_ID_PUBKEY);
		} else {
			publicKey = secretPhrase != null ? Crypto.getPublicKey(secretPhrase)
					: Convert.parseHexString(publicKeyValue);
		}

		try {
			final Transaction.Builder builder = Nxt
					.newTransactionBuilder(publicKey, amountNQT, feeNQT, deadline, attachment)
					.referencedTransactionFullHash(referencedTransactionFullHash);
			if (attachment.getTransactionType().canHaveRecipient()) {
				builder.recipientId(recipientId);
			}

			builder.appendix(publicKeyAnnouncement);
			builder.appendix(prunableSourceCode);

			if (ecBlockId != 0) {
				builder.ecBlockId(ecBlockId);
				builder.ecBlockHeight(ecBlockHeight);
			}
			Transaction transaction = null;
			if(attachment!=null && Objects.equals(attachment.getTransactionType(), Payment.REDEEM)){
				transaction = builder.buildUnixTimeStamped(secretPhrase, ((Attachment.RedeemAttachment)attachment).getRequiredTimestamp());
			}else{
				transaction = builder.build(secretPhrase);
			}
			try {
				if (Math.addExact(amountNQT, transaction.getFeeNQT()) > senderAccount.getUnconfirmedBalanceNQT()) {
					return JSONResponses.NOT_ENOUGH_FUNDS;
				}
			} catch (final ArithmeticException e) {
				return JSONResponses.NOT_ENOUGH_FUNDS;
			}
			final JSONObject transactionJSON = JSONData.unconfirmedTransaction(transaction);
			response.put("transactionJSON", transactionJSON);
			try {
				response.put("unsignedTransactionBytes", Convert.toHexString(transaction.getUnsignedBytes()));
			} catch (final NxtException.NotYetEncryptedException ignore) {
			}
			if (secretPhrase != null) {
				response.put("transaction", transaction.getStringId());
				response.put("fullHash", transactionJSON.get("fullHash"));
				response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
				response.put("signatureHash", transactionJSON.get("signatureHash"));
			}
			if (broadcast) {
				Nxt.getTransactionProcessor().broadcast(transaction);

				// Now, if transaction was my redeem transaction, and we are below the 5000 block threshold ... mine block immediately
				if ((secretPhrase != null) && (transaction.getType() == Payment.REDEEM) && Nxt.getBlockchain().getHeight()<4998 && Nxt.getBlockchainProcessor().isDownloading()==false && Nxt.getBlockchainProcessor().isScanning() == false) {
					try {
						BlockchainProcessorImpl.getInstance().generateBlock(Crypto.getPublicKey(secretPhrase),
								Nxt.getEpochTime());
					} catch (final Exception e) {
						// fall through
					}
				}

				response.put("broadcasted", true);
			} else {
				// No full validation here for SN tx, since it would naturally fail
				if(transaction.getType().mustHaveSupernodeSignature()==false)
					transaction.validate();
				else
					transaction.validateWithoutSn();

				response.put("broadcasted", false);
			}
		} catch (final NxtException.NotYetEnabledException e) {
			return JSONResponses.FEATURE_NOT_AVAILABLE;
		} catch (final NxtException.InsufficientBalanceException e) {
			throw e;
		} catch (final NxtException.ValidationException e) {
			if (broadcast) {
				response.clear();
			}
			response.put("broadcasted", false);
			JSONData.putException(response, e);
		}
		return response;

	}

	@Override
	protected final boolean requirePost() {
		return true;
	}

}

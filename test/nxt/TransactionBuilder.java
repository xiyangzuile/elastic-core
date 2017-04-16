package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;

import java.util.Objects;


class TransactionBuilder  {


    public static Transaction make(Attachment attachment, Appendix appdx, String secretPhrase, long recipientId, long amountNQT, boolean simPrune) throws Exception{
        return make(attachment, appdx,secretPhrase,recipientId,amountNQT,simPrune,true);
    }

    public static Transaction make(Attachment attachment, Appendix appdx, String secretPhrase, long recipientId, long amountNQT, boolean simPrune, boolean broadcast) throws Exception{

        final int ecBlockHeight = 1;
        long ecBlockId = 0;

        if (ecBlockHeight > 0) ecBlockId = Nxt.getBlockchain().getBlockIdAtHeight(ecBlockHeight);

        long feeNQT = 0;
        if(!attachment.getTransactionType().zeroFeeTransaction()){
            // todo: set fees here
        }
        short deadline = 1440;

        byte[] publicKey;
        if (attachment instanceof Attachment.RedeemAttachment)
            publicKey = Convert.parseHexString(Genesis.REDEEM_ID_PUBKEY);
        else publicKey = secretPhrase != null ? Crypto.getPublicKey(secretPhrase)
                : null; // todo check

        final Transaction.Builder builder = Nxt
                .newTransactionBuilder(publicKey, amountNQT, feeNQT, deadline, attachment)
                .referencedTransactionFullHash(null);
        if (attachment.getTransactionType().canHaveRecipient()) builder.recipientId(recipientId);
        if (ecBlockId != 0) {
            builder.ecBlockId(ecBlockId);
            builder.ecBlockHeight(ecBlockHeight);
        }

        if (appdx != null) if (appdx instanceof Appendix.PrunableSourceCode)
            builder.appendix((Appendix.PrunableSourceCode) appdx);

        Transaction transaction;
        //noinspection ConstantConditions
        if(Objects.equals(attachment.getTransactionType(), TransactionType.Payment.REDEEM))
            transaction = builder.buildUnixTimeStamped(secretPhrase, ((Attachment.RedeemAttachment) attachment).getRequiredTimestamp());
        else transaction = builder.build(secretPhrase);

        if (simPrune) for (Appendix a : transaction.getAppendages())
            if (a instanceof Appendix.PrunableSourceCode) ((Appendix.PrunableSourceCode) a).simulatePruning();

        if(broadcast) Nxt.getTransactionProcessor().broadcast(transaction);

        return transaction;
    }

    public static Transaction makeSupernodeSigned(Attachment attachment, Appendix appdx, String secretPhrase, long recipientId, long amountNQT, boolean simPrune) throws Exception{
        return makeSupernodeSigned(attachment, appdx,secretPhrase,recipientId,amountNQT,simPrune,true);
    }

    public static Transaction makeSupernodeSigned(Attachment attachment, Appendix appdx, String secretPhrase, long recipientId, long amountNQT, boolean simPrune, boolean broadcast) throws Exception{

        final int ecBlockHeight = 1;
        long ecBlockId = 0;

        ecBlockId = Nxt.getBlockchain().getBlockIdAtHeight(ecBlockHeight);

        long feeNQT = 0;
        if(!attachment.getTransactionType().zeroFeeTransaction()){
            // todo: set fees here
        }
        short deadline = 1440;

        byte[] publicKey;
        if (attachment instanceof Attachment.RedeemAttachment)
            publicKey = Convert.parseHexString(Genesis.REDEEM_ID_PUBKEY);
        else publicKey = secretPhrase != null ? Crypto.getPublicKey(secretPhrase)
                : null; // todo check

        final Transaction.Builder builder = Nxt
                .newTransactionBuilder(publicKey, amountNQT, feeNQT, deadline, attachment)
                .referencedTransactionFullHash(null);
        if (attachment.getTransactionType().canHaveRecipient()) builder.recipientId(recipientId);
        if (ecBlockId != 0) {
            builder.ecBlockId(ecBlockId);
            builder.ecBlockHeight(ecBlockHeight);
        }

        if (appdx != null) if (appdx instanceof Appendix.PrunableSourceCode)
            builder.appendix((Appendix.PrunableSourceCode) appdx);

        Transaction transaction;
        //noinspection ConstantConditions
        if(Objects.equals(attachment.getTransactionType(), TransactionType.Payment.REDEEM))
            transaction = builder.buildUnixTimeStamped(secretPhrase, ((Attachment.RedeemAttachment) attachment).getRequiredTimestamp());
        else transaction = builder.build(secretPhrase);
        transaction.signSuperNode(secretPhrase);

        if (simPrune) for (Appendix a : transaction.getAppendages())
            if (a instanceof Appendix.PrunableSourceCode) ((Appendix.PrunableSourceCode) a).simulatePruning();

        if(broadcast) Nxt.getTransactionProcessor().broadcast(transaction);

        return transaction;
    }

}

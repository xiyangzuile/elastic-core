package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;

/**
 * Created by beavis on 07/12/2016.
 */
public class TransactionBuilder  {

    public static void make(Attachment attachment, Appendix appdx, String secretPhrase, long recipientId, long amountNQT, boolean simPrune) throws Exception{

        final int ecBlockHeight = 1;
        long ecBlockId = 0;

        if ((ecBlockId != 0) && (ecBlockId != Nxt.getBlockchain().getBlockIdAtHeight(ecBlockHeight))) {
            throw new Exception("Failed setting ecBlock");
        }
        if ((ecBlockId == 0) && (ecBlockHeight > 0)) {
            ecBlockId = Nxt.getBlockchain().getBlockIdAtHeight(ecBlockHeight);
        }

        long feeNQT = 0;
        if(attachment.getTransactionType().zeroFeeTransaction() == false){
            // todo: set fees here
        }
        short deadline = 1440;

        byte[] publicKey = null;
        if (attachment instanceof Attachment.RedeemAttachment) {
            publicKey = Convert.parseHexString(Genesis.REDEEM_ID_PUBKEY);
        } else {
            publicKey = secretPhrase != null ? Crypto.getPublicKey(secretPhrase)
                    : Crypto.getPublicKey(secretPhrase);
        }

        final Transaction.Builder builder = Nxt
                .newTransactionBuilder(publicKey, amountNQT, feeNQT, deadline, attachment)
                .referencedTransactionFullHash(null);
        if (attachment.getTransactionType().canHaveRecipient()) {
            builder.recipientId(recipientId);
        }
        if (ecBlockId != 0) {
            builder.ecBlockId(ecBlockId);
            builder.ecBlockHeight(ecBlockHeight);
        }

        if (appdx != null){
            if (appdx instanceof Appendix.PrunableSourceCode)
                builder.appendix((Appendix.PrunableSourceCode)appdx);
        }

        final Transaction transaction = builder.build(secretPhrase);

        if (simPrune){
            for(Appendix a : transaction.getAppendages()){
                if (a instanceof Appendix.PrunableSourceCode){
                    ((Appendix.PrunableSourceCode)a).simulatePruning();
                }
            }
        }

        Nxt.getTransactionProcessor().broadcast(transaction);
    }

}

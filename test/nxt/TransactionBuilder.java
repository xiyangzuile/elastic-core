package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;

/**
 * Created by beavis on 07/12/2016.
 */
public class TransactionBuilder  {

    public static void make(Attachment attachment, String secretPhrase, long recipientId, long amountNQT) throws Exception{

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
        final Transaction transaction = builder.build(secretPhrase);
        Nxt.getTransactionProcessor().broadcast(transaction);
    }

}

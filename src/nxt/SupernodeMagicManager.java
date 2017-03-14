package nxt;

import nxt.crypto.Crypto;
import nxt.peer.Peer;
import nxt.util.Convert;
import nxt.util.Logger;
import org.bitcoinj.core.BlockChain;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SupernodeMagicManager {


    private static SupernodeMagicManager instance;
    private  ScheduledExecutorService exec = null;
    private SupernodeMagicManager () {}


    public static synchronized SupernodeMagicManager getInstance () {
        if (SupernodeMagicManager.instance == null) {
            SupernodeMagicManager.instance = new SupernodeMagicManager ();
        }
        return SupernodeMagicManager.instance;
    }
    public static void make(Attachment attachment, Appendix appdx, String secretPhrase, long recipientId, long amountNQT) throws Exception{

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


        Nxt.getTransactionProcessor().broadcast(transaction);
    }
    public void initialized(){
        exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(new Runnable() {
            public int lastSent = 0;

            @Override
            public void run() {
                // First we have to issue a become supernode TX broadcast as soon as we can
                if(Nxt.getBlockchainProcessor().isDownloading() == true || Nxt.getBlockchainProcessor().isScanning() == true){
                    // TODO, in the future check if you are connected here correctly to the network
                    Logger.logInfoMessage("SuperNode Logic Delayed: Still Processing the Blockchain.");
                    return;
                }

                // Do not do anything until the last known block is not older than 1 hour
                if(Nxt.getBlockchain().getLastBlock() == null || (Nxt.getEpochTime() - Nxt.getBlockchain().getLastBlockTimestamp() > 60*60)){
                    Logger.logInfoMessage("SuperNode Logic Delayed: Last known block is older than one hour.");
                    return;
                }

                Account sn = Nxt.getSnAccount();
                // Check if we should fire the "update callback right now!"
                // This happens either when requested or when there are only 6 blocks left in the SN status
                // Also, always leave 2 blocks space between such tries
                if((sn.isSuperNode() && sn.supernodeExpires()<=6 && (Nxt.getBlockchain().getHeight()-this.lastSent)>=2) || Nxt.becomeSupernodeNow){
                    Nxt.becomeSupernodeNow = false;
                    lastSent = Nxt.getBlockchain().getHeight();
                    Logger.logInfoMessage("*** WE ARE TRYING TO OBTAIN/Refresh SUPERNODE STATUS NOW ***");
                            String[] uris = new String[1];
                            uris[0] = "127.0.0.1";
                            final Attachment sna = new Attachment.MessagingSupernodeAnnouncement(uris, 0);
                            boolean success = false;
                            try {
                                make(sna, null, Nxt.supernodePass, sn.getId(), 0);
                                success = true;
                            } catch (Exception e) {
                                Logger.logErrorMessage(e.getMessage());
                            }

                }else if(sn!= null && sn.isSuperNode()){
                    Logger.logInfoMessage("Congrats! You are a supernode (Expiring in " + sn.supernodeExpires() + " blocks!) We do magic in the background.");
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }




}

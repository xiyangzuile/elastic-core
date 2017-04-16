package nxt;

import nxt.crypto.Crypto;
import nxt.peer.Peer;
import nxt.util.Convert;
import nxt.util.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class SNJob {
    Peer peer;
    TransactionImpl t;
}
public class SupernodeMagicManager {

    private static ExecutorService fixedPool;
    private static SupernodeMagicManager instance;

    private SupernodeMagicManager () {}


    public static synchronized SupernodeMagicManager getInstance () {
        if (SupernodeMagicManager.instance == null) SupernodeMagicManager.instance = new SupernodeMagicManager();
        return SupernodeMagicManager.instance;
    }

    public static void add( TransactionImpl t, Peer p){
        SNJob j = new SNJob();
        j.peer = p;
        j.t = t;
        Runnable aRunnable = () -> {
            SNJob cop;
            try {
                // Do dummy work
                t.signSuperNode(Nxt.supernodePass);
                boolean wasGood = true;

                Nxt.getTransactionProcessor().broadcast(t);
            } catch (NxtException.ValidationException e) {
                // Did not work after all
            }
        };
        fixedPool.submit(aRunnable); // submit to work pool
    }
    private static void make(Attachment attachment, Appendix appdx, String secretPhrase, long recipientId, long amountNQT) throws Exception{



        long feeNQT = 0;
        short deadline = 1440;

        byte[] publicKey;
        if (attachment instanceof Attachment.RedeemAttachment)
            publicKey = Convert.parseHexString(Genesis.REDEEM_ID_PUBKEY);
        else publicKey = secretPhrase != null ? Crypto.getPublicKey(secretPhrase)
                : null; // TODO check

        final Transaction.Builder builder = Nxt
                .newTransactionBuilder(publicKey, amountNQT, feeNQT, deadline, attachment)
                .referencedTransactionFullHash(null);
        if (attachment.getTransactionType().canHaveRecipient()) builder.recipientId(recipientId);

        if (appdx != null) if (appdx instanceof Appendix.PrunableSourceCode)
            builder.appendix((Appendix.PrunableSourceCode) appdx);

        final Transaction transaction = builder.build(secretPhrase);


        Nxt.getTransactionProcessor().broadcast(transaction);
    }
    public void initialized(){

        fixedPool = Executors.newFixedThreadPool(4);

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(new Runnable() {
            public int lastSent = 0;

            @Override
            public void run() {
                // First we have to issue a become supernode TX broadcast as soon as we can

                if(Nxt.getBlockchainProcessor().isDownloading() || Nxt.getBlockchainProcessor().isScanning() || Nxt.getBlockchain().getLastBlock() == null || Nxt.getBlockchain().getLastBlock().getTimestamp() < Nxt.getEpochTime() - 15 * 60){
                    // Assume that Chain is synced if its not downloading and the last known block is not older than 15 minutes
                    Logger.logInfoMessage("SuperNode Logic Delayed: Still Processing the Blockchain.");
                    return;
                }

                // Do not do anything until the last known block is not older than 1 hour
                if(Nxt.getBlockchain().getLastBlock() == null || (Nxt.getEpochTime() - Nxt.getBlockchain().getLastBlockTimestamp() > 60*60)){
                    Logger.logInfoMessage("SuperNode Logic Delayed: Last known block is older than one hour.");
                    return;
                }

                if(Nxt.getSnAccount() == null || (!Nxt.getSnAccount().isSuperNode() && !Nxt.getSnAccount().canBecomeSupernode())){
                    Logger.logInfoMessage("SuperNode Logic Delayed: Please fund your account as soon as possible.");
                    return;
                }

                if((Nxt.getSnAccount() == null || (!Nxt.getSnAccount().isSuperNode()) && Nxt.snrenew == false){
                    Logger.logInfoMessage("SuperNode Logic Delayed: Please become supernode first.");
                    return;
                }

                Account sn = Nxt.getSnAccount();
                // Check if we should fire the "update callback right now!"
                // This happens either when requested or when there are only 6 blocks left in the SN status
                // Also, always leave 2 blocks space between such tries
                if((sn.isSuperNode() && sn.supernodeExpires()<=6 && (Nxt.getBlockchain().getHeight()-this.lastSent)>=2) || Nxt.becomeSupernodeNow || (!sn.isSuperNode() && sn.canBecomeSupernode())){
                    Nxt.becomeSupernodeNow = false;
                    lastSent = Nxt.getBlockchain().getHeight();
                    Logger.logInfoMessage("*** WE ARE TRYING TO OBTAIN/Refresh SUPERNODE STATUS NOW ***");
                            String[] uris = new String[1];
                            uris[0] = Nxt.externalIPSN;
                            final Attachment sna = new Attachment.MessagingSupernodeAnnouncement(uris, 0);
                            boolean success = false;
                            try {
                                make(sna, null, Nxt.supernodePass, sn.getId(), 0);
                            } catch (Exception e) {
                                Logger.logErrorMessage(e.getMessage());
                            }

                }else if(sn.isSuperNode())
                    Logger.logInfoMessage("Congrats! You are a supernode (Expiring in " + sn.supernodeExpires() + " blocks!) We do magic in the background.");
            }
        }, 0, 5, TimeUnit.SECONDS);
    }




}

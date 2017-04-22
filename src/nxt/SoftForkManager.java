package nxt;


import nxt.db.DbIterator;
import nxt.util.Logger;

import java.util.ArrayList;
import java.util.List;

class VotedFeature{


    // TODO: Warnings and Shutdowns when sth happens regarding forks

    private int feature_index;

    public VotedFeature(int feature_index, String description, boolean armed) {
        this.feature_index = feature_index;
        this.description = description;
        this.armed = armed;
    }

    private String description;

    public int getFeature_index() {
        return feature_index;
    }

    public void setFeature_index(int feature_index) {
        this.feature_index = feature_index;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isArmed() {
        return armed;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
    }

    private boolean armed;
}

public class SoftForkManager {
    private long implemented_map = 0; // implemented features map

    private long featureBitmask = 0;
    private long featureBitmaskPossible = 0;

    public long getFeatureBitmaskLive() {
        return featureBitmaskLive;
    }

    private long featureBitmaskLive = 0;

    private List<VotedFeature> features = new ArrayList<>();

    private static SoftForkManager instance = null;

    public static SoftForkManager getInstance() {
        return SoftForkManager.instance;
    }

    public long getFeatureBitmask() {
        return featureBitmask;
    }

    public VotedFeature getFeature(int index){
        if (index < 0 || index >= features.size())
            return null;
        return features.get(index);
    }


    public boolean incompatibleToLiveMap(long readMask){
        // if live bitmask has bits where our map doesnt, this means that we immediately need a software update
        for(int i=0;i<64;++i){
            if((featureBitmaskLive & 1L<<i) > 0 && (readMask & 1L<<i) == 0){
                return true;
            }
        }
        return false;
    }

    public static void init() throws NxtException.NotValidException {
        if(instance==null) instance = new SoftForkManager();
    }
    public boolean isLive(int index){
        return (featureBitmaskLive & 1L<<index) > 0;
    }

    private void addFeature(VotedFeature v){
        features.add(v);
        if(v.isArmed())
            featureBitmask |= 1L<<v.getFeature_index();
    }

    private void updateLiveMap(){
        long map = 0;
        try(DbIterator<Fork> dbIt = Fork.getLockedInForks(0,-1)){
            while(dbIt.hasNext())
                map |= 1L<<dbIt.next().getId();
        }
        this.featureBitmaskLive = map;
    }

    private void updatePotentialMap(){
        long map = 0;
        try(DbIterator<Fork> dbIt = Fork.getPotentialForks(0,-1)){
            while(dbIt.hasNext())
                map |= 1L<<dbIt.next().getId();
        }
        this.featureBitmaskPossible = map;
    }

    public boolean isArmedByConfig(int idx){
        // Override if feature is live
        if(isLive(idx))
            return true;

        return Nxt.getBooleanPropertySilent("nxt.soft_fork_voting_feature_" + String.valueOf(idx));
    }



    private SoftForkManager() throws NxtException.NotValidException {


        updateLiveMap();


        // Check if this vote is possible: You can only vote if you have implemented those features
        if(needsUrgentUpdate() == true){
            throw new NxtException.NotValidException("There has been a soft-fork, but your version does not implement the new feature. Update immediately!");
        }

        updatePotentialMap();
        if(needsPotentialUpdate() == true){
            System.err.println("[!!!!] At least one soft-fork which is not implemented in your version has reached a critical vote level! You should update your software immediately!!!");
        }

        // Fill ALL 64 features here, regardless whether they are active or not
        // Set relevant ones to true and add description. Then, they are automatically up or voting!
        addFeature(new VotedFeature(0, "", isArmedByConfig(0)));
        addFeature(new VotedFeature(1, "", isArmedByConfig(1)));
        addFeature(new VotedFeature(2, "", isArmedByConfig(2)));
        addFeature(new VotedFeature(3, "", isArmedByConfig(3)));
        addFeature(new VotedFeature(4, "", isArmedByConfig(4)));
        addFeature(new VotedFeature(5, "", isArmedByConfig(5)));
        addFeature(new VotedFeature(6, "", isArmedByConfig(6)));
        addFeature(new VotedFeature(7, "", isArmedByConfig(7)));
        addFeature(new VotedFeature(8, "", isArmedByConfig(8)));
        addFeature(new VotedFeature(9, "", isArmedByConfig(9)));
        addFeature(new VotedFeature(10, "", isArmedByConfig(10)));
        addFeature(new VotedFeature(11, "", isArmedByConfig(11)));
        addFeature(new VotedFeature(12, "", isArmedByConfig(12)));
        addFeature(new VotedFeature(13, "", isArmedByConfig(13)));
        addFeature(new VotedFeature(14, "", isArmedByConfig(14)));
        addFeature(new VotedFeature(15, "", isArmedByConfig(15)));
        addFeature(new VotedFeature(16, "", isArmedByConfig(16)));
        addFeature(new VotedFeature(17, "", isArmedByConfig(17)));
        addFeature(new VotedFeature(18, "", isArmedByConfig(18)));
        addFeature(new VotedFeature(19, "", isArmedByConfig(19)));
        addFeature(new VotedFeature(20, "", isArmedByConfig(20)));
        addFeature(new VotedFeature(21, "", isArmedByConfig(21)));
        addFeature(new VotedFeature(22, "", isArmedByConfig(22)));
        addFeature(new VotedFeature(23, "", isArmedByConfig(23)));
        addFeature(new VotedFeature(24, "", isArmedByConfig(24)));
        addFeature(new VotedFeature(25, "", isArmedByConfig(25)));
        addFeature(new VotedFeature(26, "", isArmedByConfig(26)));
        addFeature(new VotedFeature(27, "", isArmedByConfig(27)));
        addFeature(new VotedFeature(28, "", isArmedByConfig(28)));
        addFeature(new VotedFeature(29, "", isArmedByConfig(29)));
        addFeature(new VotedFeature(30, "", isArmedByConfig(30)));
        addFeature(new VotedFeature(31, "", isArmedByConfig(31)));
        addFeature(new VotedFeature(32, "", isArmedByConfig(32)));
        addFeature(new VotedFeature(33, "", isArmedByConfig(33)));
        addFeature(new VotedFeature(34, "", isArmedByConfig(34)));
        addFeature(new VotedFeature(35, "", isArmedByConfig(35)));
        addFeature(new VotedFeature(36, "", isArmedByConfig(36)));
        addFeature(new VotedFeature(37, "", isArmedByConfig(37)));
        addFeature(new VotedFeature(38, "", isArmedByConfig(38)));
        addFeature(new VotedFeature(39, "", isArmedByConfig(39)));
        addFeature(new VotedFeature(40, "", isArmedByConfig(40)));
        addFeature(new VotedFeature(41, "", isArmedByConfig(41)));
        addFeature(new VotedFeature(42, "", isArmedByConfig(42)));
        addFeature(new VotedFeature(43, "", isArmedByConfig(43)));
        addFeature(new VotedFeature(44, "", isArmedByConfig(44)));
        addFeature(new VotedFeature(45, "", isArmedByConfig(45)));
        addFeature(new VotedFeature(46, "", isArmedByConfig(46)));
        addFeature(new VotedFeature(47, "", isArmedByConfig(47)));
        addFeature(new VotedFeature(48, "", isArmedByConfig(48)));
        addFeature(new VotedFeature(49, "", isArmedByConfig(49)));
        addFeature(new VotedFeature(50, "", isArmedByConfig(50)));
        addFeature(new VotedFeature(51, "", isArmedByConfig(51)));
        addFeature(new VotedFeature(52, "", isArmedByConfig(52)));
        addFeature(new VotedFeature(53, "", isArmedByConfig(53)));
        addFeature(new VotedFeature(54, "", isArmedByConfig(54)));
        addFeature(new VotedFeature(55, "", isArmedByConfig(55)));
        addFeature(new VotedFeature(56, "", isArmedByConfig(56)));
        addFeature(new VotedFeature(57, "", isArmedByConfig(57)));
        addFeature(new VotedFeature(58, "", isArmedByConfig(58)));
        addFeature(new VotedFeature(59, "", isArmedByConfig(59)));
        addFeature(new VotedFeature(60, "", isArmedByConfig(60)));
        addFeature(new VotedFeature(61, "", isArmedByConfig(61)));
        addFeature(new VotedFeature(62, "", isArmedByConfig(62)));
        addFeature(new VotedFeature(63, "", isArmedByConfig(63)));

        // Check if this vote is possible: You can only vote if you have implemented those features
        if(needsUrgentUpdate(false) == true){
            throw new NxtException.NotValidException("You cannot vote for features that your version has not implemented. Please update your software first.");
        }
    }


    public boolean needsUrgentUpdate(){
        return needsUrgentUpdate(true);
    }

    public boolean needsUrgentUpdate(boolean checkLive){
        if(checkLive) {
            for (int i = 0; i < 64; ++i) {
                if ((featureBitmaskLive & 1L << i) > 0 && (implemented_map & 1L << i) == 0) {
                    return true;
                }
            }
            return false;
        }else{
            for (int i = 0; i < 64; ++i) {
                if ((featureBitmask & 1L << i) > 0 && (implemented_map & 1L << i) == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean needsPotentialUpdate(){
            for (int i = 0; i < 64; ++i) {
                if ((featureBitmaskPossible & 1L << i) > 0 && (implemented_map & 1L << i) == 0) {
                    return true;
                }
            }
            return false;
    }


    // this method must be called AFTER the new block has been recorded!!!! (Not before)
    public void recordNewVote(BlockImpl block) {



        // First get block at height curentH - SOFTFORK_CONFIRMATIONS, this is the block that falls out of the sliding window! Account for it in the next loops
        int getHeight = block.getHeight() - Constants.BLOCKS_TO_LOCKIN_SOFT_FORK;
        long ignoringmask = 0;
        if(getHeight<1){
            // pass, chain not long enough
        }
        else{
            ignoringmask = BlockchainImpl.getInstance().getBlockAtHeight(getHeight).getSoftforkVotes();
        }

        long currmask = block.getSoftforkVotes();

        Logger.logDebugMessage("Fork-Counter: falling out: " + getHeight + ", currHeight: " + block.getHeight() + ", curr voted: " + currmask + ", live mask: " + featureBitmaskLive + ", critical: " + featureBitmaskPossible + ", ignoring: " + ignoringmask);

        for(int i=0;i<64;++i){
            // Only count votes that are not live, and that do not currently fall out at the rear end of the sliding window
            if((featureBitmaskLive & 1L<<i) == 0 && (currmask & 1L<<i) > 0 && (ignoringmask & 1L<<i) == 0){
                // new vote found
                Fork f = Fork.getFork(i);
                Logger.logDebugMessage("  -> Increasing feature " + i + " count to " + f.sliding_count);
                f.sliding_count++;
                if(f.sliding_count > Constants.BLOCKS_MUST_BE_FULFILLED_TO_LOCKIN_SOFT_FORK) f.sliding_count = Constants.BLOCKS_MUST_BE_FULFILLED_TO_LOCKIN_SOFT_FORK; // safe guard
                f.store();
            }
            // And reduce those who are not currently voted but fall out at rear end
            else  if((featureBitmaskLive & 1L<<i) == 0 && (currmask & 1L<<i) == 0 && (ignoringmask & 1L<<i) > 0){
                // new vote found
                Fork f = Fork.getFork(i);
                Logger.logDebugMessage("  -> Decreasing feature " + i + " count to " + f.sliding_count);
                f.sliding_count--;
                if(f.sliding_count<0) f.sliding_count = 0; // safe guard
                f.store();
            }
        }
        updateLiveMap();

        updatePotentialMap();

        if(needsUrgentUpdate() == true){
            System.err.println("There has been a soft-fork, but your version does not implement the new feature. Update immediately!");
        }
        else if(needsPotentialUpdate() == true){
            System.err.println("[!!!!] At least one soft-fork which is not implemented in your version has reached a critical vote level! You should update your software immediately!!!");
        }
    }
}

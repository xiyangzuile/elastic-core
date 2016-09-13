package nxt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringJoiner;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import javax.xml.bind.ValidationException;

import org.json.simple.JSONObject;

import ElasticPL.ParseException;
import nxt.Attachment.PiggybackedProofOfBounty;
import nxt.Attachment.PiggybackedProofOfWork;
import nxt.Attachment.WorkCreation;
import nxt.Attachment.WorkIdentifierCancellation;
import nxt.NxtException.NotValidException;
import nxt.crypto.Crypto;
import nxt.db.DbIterator;
import nxt.db.DbUtils;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.util.Time;



public class WorkLogicManager {
	
	// This is for unconfirmedLimiting
	HashMap<Long, HashMap<Long, Long>> unconfirmedSumPOWIDs = new HashMap<Long, HashMap<Long, Long>>();
	HashMap<Long, HashSet<Long>> unconfirmedCountBountyIDs = new HashMap<Long, HashSet<Long>>();
	
	// Configuration variables
	public BigInteger least_possible_target = new BigInteger("0000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",16);
	public int account_for_n_last_blocks = 12;
	public int target_number_of_pow_per_block = 10;
	public int target_number_of_pow_n_blocks = target_number_of_pow_per_block
			* account_for_n_last_blocks;

	// Cache
	public boolean dirty = true;
	public long lastBlock = 0;
	public BigInteger lastDiff = least_possible_target;

	private static final WorkLogicManager instance = new WorkLogicManager();

	public static WorkLogicManager getInstance() {
		return instance;
	}
	
	public void addBountyToUnconfirmed(Long workId, Long bountyId){
		if(!unconfirmedCountBountyIDs.containsKey(workId)){
			HashSet<Long> newSet = new HashSet<Long>();
			newSet.add(bountyId);
			unconfirmedCountBountyIDs.put(workId, newSet);
		}else{
			HashSet<Long> oldSet = unconfirmedCountBountyIDs.get(workId);
			oldSet.add(bountyId);
			unconfirmedCountBountyIDs.remove(workId);
			unconfirmedCountBountyIDs.put(workId, oldSet);
		}
	}
	public void removeBountyToUnconfirmed(Long workId, Long bountyId){
		if(unconfirmedCountBountyIDs.containsKey(workId)){
			HashSet<Long> oldSet = unconfirmedCountBountyIDs.get(workId);
			oldSet.remove(bountyId);
			unconfirmedCountBountyIDs.remove(workId);
			
			if(oldSet.size()>0)
				unconfirmedCountBountyIDs.put(workId, oldSet);
		}
	}
	
	public int getBountyUnconfirmed(Long workId){
		if(unconfirmedCountBountyIDs.containsKey(workId)){
			HashSet<Long> oldSet = unconfirmedCountBountyIDs.get(workId);
			return oldSet.size();
		}
		return 0;
	}
	
	
	public void addPowToUnconfirmed(Long workId, Long bountyId, long amount){
		if(!unconfirmedSumPOWIDs.containsKey(workId)){
			HashMap<Long, Long> newSet = new HashMap<Long, Long>();
			unconfirmedSumPOWIDs.put(workId, newSet);
		}
		
		HashMap<Long, Long> set = unconfirmedSumPOWIDs.get(workId);
		
		if(!set.containsKey(bountyId)){
			// unconfirmedSumPOWIDs.remove(workId);
			set.put(bountyId, amount);
			unconfirmedSumPOWIDs.put(workId, set);
		}		
	}
	
	
	public void removePowToUnconfirmed(Long workId, Long bountyId){
		if(!unconfirmedSumPOWIDs.containsKey(workId)){
			return;
		}
		
		HashMap<Long, Long> set = unconfirmedSumPOWIDs.get(workId);
		
		if(set.containsKey(bountyId)){
			unconfirmedSumPOWIDs.remove(workId);
			set.remove(bountyId);
			if(set.size()>0)
				unconfirmedSumPOWIDs.put(workId, set);
		}	
	}
	
	public long getPowUnconfirmed(Long workId){
		if(unconfirmedSumPOWIDs.containsKey(workId)){
			HashMap<Long, Long> set = unconfirmedSumPOWIDs.get(workId);
			long sum = 0;
			for(Long key : set.keySet()){
				sum += set.get(key);
			}
			return sum;
		}
		return 0L;
	}

	// This is an adaptation of DARK GRAVITY WAVE to retarget the difficulty of
	// proof of work functions
	public BigInteger getMinPowTarget(long lastBlockId) {

		// Check if we have a cached value and dirty flag is false, prevents DOS
		if (!dirty && lastBlock == lastBlockId)
			return lastDiff;

		// Genesis specialty
		if (lastBlockId == 0)
			return least_possible_target;

		// try to cycle over the last 12 blocks, or - if height is smaller -
		// over entire blockchain
		int go_back_counter = Math.min(account_for_n_last_blocks,
				BlockchainImpl.getInstance().getHeight());
		int original_back_counter = go_back_counter;

		// ... and count the number of PoW transactions inside those blocks
		int pow_counter = 0;
		BlockImpl b = BlockchainImpl.getInstance().getBlock(lastBlockId);
		BigInteger last_pow_target = b.getLastPowTarget();
		System.out.println("Summarizing last POW");
		while (go_back_counter > 0) {
			pow_counter += b.countNumberPOW();
			System.out.println("    BID " + b.getId() + " has " + b.countNumberPOW() + " POW submissions our of " + b.getTransactions().size() + " TX (Total sent " + b.getTotalAmountNQT() + ")!");
			b = b.getPreviousBlock();
			go_back_counter -= 1;
		}
		
		// scale up if there are not yet 12 blocks there, avoids MADNESS
		if(original_back_counter<account_for_n_last_blocks){
			System.out.println("!!!! GOBACK COUNTER SKEWED -> " + original_back_counter + " / should be " + account_for_n_last_blocks);
			
			double scaledCounter = (double)pow_counter;
			
			scaledCounter = scaledCounter / original_back_counter;
			scaledCounter = scaledCounter * account_for_n_last_blocks;
			System.out.println("!!!!SCALED POW NUMBER -> " + pow_counter + " / upscaled to " + scaledCounter);

			pow_counter = (int)scaledCounter;
		}

		// if no PoW was sent during last n blocks, something is strange, give
		// back the lowest possible target
		if (pow_counter == 0)
			return least_possible_target;

		// Check the needed adjustment here, but make sure we do not adjust more
		// than * 2 or /2.
		// This will prevent huge difficulty jumps, yet it will quickly
		// (exponentially) approxiamate the desired number
		// of PoW packages per block.
		BigDecimal new_pow_target = new BigDecimal(last_pow_target);
		System.out.println("!!!! RATIO target / real !!!! -> " + target_number_of_pow_n_blocks + " / " + pow_counter);
		double factor = (double)target_number_of_pow_n_blocks / (double)pow_counter; // RETARGETING

		// limits
		if (factor > 2)
			factor = 2;
		if (factor < 0.5)
			factor = (double) 0.5;
		
		BigDecimal factorDec = new BigDecimal(factor);
		System.out.println("!!!! FACTOR FOR NEW POW !!!! -> " + factor);

		// Apply the retarget: Adjust target so that we again - on average -
		// reach n PoW per block
		new_pow_target = new_pow_target.multiply(factorDec);
		BigInteger converted_new_pow = new_pow_target.toBigInteger();
		
		if(converted_new_pow.compareTo(least_possible_target)==1) converted_new_pow = least_possible_target;

		// Cache value, dirty may be set to true on blockchain reorganization
		dirty = false;
		lastBlock = lastBlockId;
		lastDiff = converted_new_pow;

		return converted_new_pow;
	}

	// Just in case we need it in the future, but i think this can be safely
	// removed
	public double round(final double value, final int frac) {
		return Math.round(Math.pow(10.0, frac) * value) / Math.pow(10.0, frac);
	}

	// TODO, FIXME: If we want to make those dynamic, we need to fix the following four functions
	public double getPercentWork(long workId) {
		return 0.60d;
	}

	public double getPercentBounty(long workId) {
		return 0.40d;
	}
	
	public HashMap<Long, Double> getPercentWorks(Set<Long> ids) {
		HashMap<Long, Double> res = new HashMap<Long, Double> ();
		for(Long l:ids){
			res.put(l, 0.60d);
		}
		return res;
	}

	public HashMap<Long, Double> getPercentBountys(Set<Long> ids) {
		HashMap<Long, Double> res = new HashMap<Long, Double> ();
		for(Long l:ids){
			res.put(l, 0.40d);
		}
		return res;
	}

	private String dd(long d) {
		return Convert.toUnsignedLong(d);

	}
	
	public long maxWorstCaseExecutionTime(){
		return Constants.MAX_WORK_WCET_TIME;
	}

	private JSONObject workEntryLite(long workId, String workTitle,
			byte[] source, BigInteger estimatedTarget,
			long xel_per_pow, boolean prune_status, String language) {
		JSONObject response = new JSONObject();

		response.put("workId", dd(workId));
		response.put("source", Ascii85.encode(source));
		response.put("language", language);
		response.put("title", workTitle);
		response.put("xel_per_pow", xel_per_pow);
		response.put("pruned", prune_status);
		if (estimatedTarget != null)
			response.put("min_pow_target", estimatedTarget.toString(16)); // this is only for
																// the miner, no
																// need to
																// include it in
																// the other
																// workEntry
																// functions
																// also, only
																// needed from
																// "getNRandomWorks"
																// function. So
																// if 0 arg,
																// then just
																// ignore this
																// entry

		return response;

	}

	private JSONObject workEntry(byte version, long workId, long referenced_tx,
			long block_created, long block_closed, long cancellation_tx,
			long last_payment_tx, String title, String account,
			String language, 
			long balance_original, long paid_bounties, long paid_pow,
			int bounties_connected, int pow_connected, int timeout_at_block,
			int script_size_bytes, long fee, int block_created_h,
			int bounty_limit, long xel_per_pow, boolean prune_status) {
		JSONObject response = new JSONObject();
		double work_percentage = getPercentWork(workId);
		double bounty_percentage = getPercentBounty(workId);
		response.put("workId", dd(workId));
		response.put("version", version);
		response.put("referenced_tx", dd(referenced_tx));
		response.put("bounty_limit", bounty_limit);
		response.put("block_created", dd(block_created));
		response.put("block_height_created", block_created_h);
		response.put("block_closed", dd(block_closed));
		response.put("cancellation_tx", dd(cancellation_tx));
		response.put("last_payment_tx", dd(last_payment_tx));
		response.put("title", title);
		response.put("account", account);
		response.put("language", language);
		response.put("percent_work", work_percentage);
		response.put("percent_bounties", bounty_percentage);
		response.put("balance_original", dd(balance_original));
		response.put("balance_original_pow", dd(this.getOriginalPoWBalanceByOrigBalance(balance_original,work_percentage,bounty_percentage)));
		response.put("balance_original_bounty", dd(this.getOriginalBountyBalanceByOrigBalance(balance_original,work_percentage,bounty_percentage)));
		response.put("xel_per_pow", xel_per_pow);
		response.put("pruned", prune_status);

		// long balance_work =
		// balance_original*getPercentWork()/100-(pow_connected*getCurrentPowReward());
		// long balance_bounties = balance_original*getPercentBounty()/100;

		response.put("balance_remained", dd(balance_original - paid_pow
				- paid_bounties));
		response.put("paid_bounties", dd(paid_bounties));
		response.put("paid_pow", dd(paid_pow));

		double done = 100 - Math
				.round(((balance_original - paid_pow - paid_bounties) * 1.0 / balance_original) * 100.0);

		response.put("percent_done", done);
		response.put("pow_connected", pow_connected);
		response.put("bounties_connected", bounties_connected);
		response.put("timeout_at_block", timeout_at_block);
		response.put("script_size_bytes", script_size_bytes);
		response.put("fee", fee);

		return response;
	}

	public byte[] compress(String text) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			OutputStream out = new DeflaterOutputStream(baos);
			out.write(text.getBytes("UTF-8"));
			out.close();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		return baos.toByteArray();
	}

	public String decompress(byte[] bytes) {
		InputStream in = new InflaterInputStream(
				new ByteArrayInputStream(bytes));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			byte[] buffer = new byte[8192];
			int len;
			while ((len = in.read(buffer)) > 0)
				baos.write(buffer, 0, len);
			return new String(baos.toByteArray(), "UTF-8");
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public byte getLanguageByte(String language) {
		if (language.equalsIgnoreCase("ElasticPL")) {
			return (byte) 0x01;
		}
		return 0;
	}

	public String getLanguageString(short language) {
		if (language == 0x01) {
			return "ElasticPL";
		}
		return "?";
	}

	public boolean checkWorkLanguage(byte w) {
		return (w == 1); // only allow 1 for now = ElasticPL
	}

	public boolean checkDeadline(int deadlineInt) {
		return (deadlineInt >= Constants.MIN_DEADLINE_FOR_WORK && deadlineInt <= Constants.MAX_DEADLINE_FOR_WORK);
	}

	public boolean checkNumberVariables(byte numberInputVarsByte) {
		return (numberInputVarsByte >= getMinNumberInputInts()
				&& numberInputVarsByte <= getMaxNumberInputInts());
	}

	public int getMaxNumberInputInts() {
		return Constants.MAX_INTS_FOR_WORK;
	}

	public int getMinNumberInputInts() {
		return Constants.MIN_INTS_FOR_WORK;
	}

	
	public long getCreatorOfWork(long workId) {
		// TODO, think about caching such things
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT sender_account_id FROM work WHERE id = ?")) {
			int i = 0;
			pstmt.setLong(++i, workId);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				long result = check.getLong(1);
				return result;
			} else {
				throw new RuntimeException(
						"Work does not exist, what are you doing??");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public boolean haveWork(long workId) {
		// TODO, think about caching such things
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT COUNT(*) FROM work WHERE id = ?")) {
			int i = 0;
			pstmt.setLong(++i, workId);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				int result = check.getInt(1);
				//System.out.println("haveWork: " + pstmt.toString() + ", result: " + result);
				return (result >= 1) ? true : false;
			} else {
				throw new RuntimeException(
						"Cannot decide if work exists or not");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public void cancelWork(Transaction t, WorkIdentifierCancellation attachment) {
		if (!Db.db.isInTransaction()) {
			try {
				Db.db.beginTransaction();
				cancelWork(t, attachment);
				this.cleanMempoolDeeply();
				Db.db.commitTransaction();
				
			} catch (Exception e) {
				Logger.logErrorMessage(e.toString(), e);
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
			return;
		}
		try {
			try (Connection con = Db.db.getConnection();
					PreparedStatement pstmt = con
							.prepareStatement("UPDATE work SET payback_transaction_id = ? WHERE id = ?")) {
				int i = 0;
				pstmt.setLong(++i, t.getId());
				pstmt.setLong(++i, attachment.getWorkId());
				pstmt.executeUpdate();
			}

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public boolean isStillPending(long workId, long senderId) {
		// NOTE: here, we do not need to check if balance is left, this will be checked in the block creation routine
		// or better: the block-tx-automatic-adding-routine
		
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT count(*) FROM work WHERE id = ? and sender_account_id = ? and payback_transaction_id is null and last_payment_transaction_id is null")) {
			int i = 0;
			pstmt.setLong(++i, workId);
			pstmt.setLong(++i, senderId);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				if (check.getInt(1) == 0)
					return false;
			} else {
				throw new RuntimeException(
						"Cannot decide if work is still pending");
			}
			return true;
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public boolean doesWorkExist(long workId) {
		
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT count(*) FROM work WHERE id = ?")) {
			int i = 0;
			pstmt.setLong(++i, workId);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				if (check.getInt(1) == 0)
					return false;
			} else {
				// TODO, check if RuntimeException is really the way to go
				throw new RuntimeException(
						"Cannot decide if work is still pending");
			}
			return true;
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public boolean isStillPending(long workId) {
		// NOTE: here, we do not need to check if balance is left, this will be checked in the block creation routine
		// or better: the block-tx-automatic-adding-routine
		
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT count(*) FROM work WHERE id = ? and payback_transaction_id is null and last_payment_transaction_id is null")) {
			int i = 0;
			pstmt.setLong(++i, workId);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				if (check.getInt(1) == 0)
					return false;
			} else {
				throw new RuntimeException(
						"Cannot decide if work is still pending");
			}
			return true;
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public void cleanMempoolDeeply(){
		Set<Long> pruneIds = new HashSet<Long>();
		try (DbIterator<? extends Transaction> transactions = Nxt.getTransactionProcessor().getAllUnconfirmedTransactions()) {
            while (transactions.hasNext()) {
                Transaction transaction = transactions.next();
                if(transaction.getType() == TransactionType.WorkControl.PROOF_OF_WORK){
                	Attachment.PiggybackedProofOfWork att = (Attachment.PiggybackedProofOfWork)transaction.getAttachment();
                	if(this.isStillPending(att.getWorkId()) == false)
                		pruneIds.add(att.getWorkId());
                }
                else if(transaction.getType() == TransactionType.WorkControl.BOUNTY){
                	Attachment.PiggybackedProofOfBounty att = (Attachment.PiggybackedProofOfBounty)transaction.getAttachment();
                	if(this.isStillPending(att.getWorkId()) == false)
                		pruneIds.add(att.getWorkId());
				}
                else if(transaction.getType() == TransactionType.WorkControl.CANCEL_TASK_REQUEST){
                	Attachment.WorkIdentifierCancellationRequest att = (Attachment.WorkIdentifierCancellationRequest)transaction.getAttachment();
                	if(this.isStillPending(att.getWorkId()) == false)
                		pruneIds.add(att.getWorkId());
				}
            }
        }
		Nxt.getTransactionProcessor().clearUnconfirmedTransactionsWithIds(pruneIds);
	}

	

	public void createNewWork(long workId, long txId, long senderId,
			long blockId, int blockHeight, long amountNQT, long feeNQT,
			WorkCreation attachment) {
		if (!Db.db.isInTransaction()) {
			try {
				Db.db.beginTransaction();
				createNewWork(workId, txId, senderId, blockId, blockHeight,
						amountNQT, feeNQT, attachment);
				Db.db.commitTransaction();
			} catch (Exception e) {
				Logger.logErrorMessage(e.toString(), e);
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
			return;
		}

		try {
			try (Connection con = Db.db.getConnection();
					PreparedStatement pstmt = con
							.prepareStatement("INSERT INTO work (id, work_title, version_id, deadline, original_amount,"
									+ "fee, referenced_transaction_id, block_id, included_block_height, sender_account_id, bounties_limit, xel_per_pow) "
									+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
				int i = 0;
				pstmt.setLong(++i, workId);
				pstmt.setString(++i, attachment.getWorkTitle());
				pstmt.setShort(++i, attachment.getVersion());
				pstmt.setInt(++i, attachment.getDeadline());
				pstmt.setLong(++i, amountNQT);
				pstmt.setLong(++i, feeNQT);
				pstmt.setLong(++i, txId);
				pstmt.setLong(++i, blockId);
				pstmt.setLong(++i, blockHeight);
				pstmt.setLong(++i, senderId);
				pstmt.setInt(++i, attachment.getBountyLimit());
				pstmt.setLong(++i, attachment.getXelPerPow());
				pstmt.executeUpdate();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	private byte[] convertIntArrayToBytes(int[] x) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);

		dos.writeInt(x.length);
		for (int i = 0; i < x.length; ++i) {
			dos.writeInt(x[i]);
		}

		dos.flush();
		return bos.toByteArray();
	}

	public void createNewProofOfWork(long workId, long txId, long senderId,
			long blockId, long PayOutAmountNQT,
			PiggybackedProofOfWork attachment) throws IOException {
		if (!Db.db.isInTransaction()) {
			try {
				Db.db.beginTransaction();
				createNewProofOfWork(workId, txId, senderId, blockId,
						PayOutAmountNQT, attachment);
				Db.db.commitTransaction();
			} catch (Exception e) {
				Logger.logErrorMessage(e.toString(), e);
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
			return;
		}

		try {
			try (Connection con = Db.db.getConnection();
					PreparedStatement pstmt = con
							.prepareStatement("INSERT INTO proof_of_work (id, work_id, block_id, sender_account_id, payout_amount, input ) "
									+ " VALUES (?, ?, ?, ?, ?, ?)")) {
				int i = 0;
				pstmt.setLong(++i, txId);
				pstmt.setLong(++i, workId);
				pstmt.setLong(++i, blockId);
				pstmt.setLong(++i, senderId);
				pstmt.setLong(++i, PayOutAmountNQT);

				byte[] input = convertIntArrayToBytes(attachment.getInput());
				pstmt.setBytes(++i, input);
				pstmt.executeUpdate();
			}

			// NO NEED TO UPDATE ANY LAST_PAYMENT_ID! This should be set in the cancellation TX that will 
			// be created on block creation TODO FIXME

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public void createNewBounty(long workId, long txId, long senderId,
			long blockId, long PayOutAmountNQT,
			PiggybackedProofOfBounty attachment) throws IOException {
		if (!Db.db.isInTransaction()) {
			try {
				Db.db.beginTransaction();
				createNewBounty(workId, txId, senderId, blockId,
						PayOutAmountNQT, attachment);
				Db.db.commitTransaction();
			} catch (Exception e) {
				Logger.logErrorMessage(e.toString(), e);
				Db.db.rollbackTransaction();
				throw e;
			} finally {
				Db.db.endTransaction();
			}
			return;
		}
		try {

			try (Connection con = Db.db.getConnection();
					PreparedStatement pstmt = con
							.prepareStatement("INSERT INTO bounty_submission (id, work_id, referenced_transaction_id, block_id, sender_account_id, input) "
									+ " VALUES (?, ?, ?, ?, ?, ?)")) {
				int i = 0;
				pstmt.setLong(++i, txId);
				pstmt.setLong(++i, workId);
				pstmt.setLong(++i, txId);
				pstmt.setLong(++i, blockId);
				pstmt.setLong(++i, senderId);
				byte[] input = convertIntArrayToBytes(attachment.getInput());
				pstmt.setBytes(++i, input);
				pstmt.executeUpdate();
			}

			// NO NEED TO UPDATE ANY LAST_PAYMENT_ID! This should be set in the cancellation TX that will 
						// be created on block creation TODO FIXME

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}



	public DbIterator<JSONObject> getWorkList(Account account, int from,
			int to, long onlyOneId) {

		Connection con = null;

		try {
			StringBuilder buf = new StringBuilder();
			buf.append("select w.*, fj.payout_amount_pow as payout_amount_pow, sj.pow_submitted as pow_submitted, tj.bounties_submitted as bounties_submitted from WORK w  " + 
						"LEFT JOIN " +  
						"( " +  
						"  SELECT SUM(payout_amount) AS payout_amount_pow, work_id " +  
						"  FROM proof_of_work  " +  
						"  GROUP BY work_id " +  
						") fj " +  
						"ON fj.work_id = w.id  " +  
						"LEFT JOIN  " +  
						"( " +  
						"  SELECT COUNT(id) AS pow_submitted, work_id " +  
						"  FROM proof_of_work  " +  
						"  GROUP BY work_id " +  
						") sj " +  
						"ON sj.work_id = w.id  " +  
						"LEFT JOIN  " +  
						"( " +  
						"  SELECT COUNT(id) AS bounties_submitted, work_id " +  
						"  FROM bounty_submission  " +  
						"  GROUP BY work_id " +  
						") tj " +  
						"ON tj.work_id = w.id  " +  
						"WHERE w.sender_account_id = ?");

			if (onlyOneId != 0) {
				buf.append(" AND w.id = ?");
			}
			
			buf.append(" GROUP BY w.id ORDER BY included_block_height DESC");
			buf.append(DbUtils.limitsClause(from, to));
			con = Db.db.getConnection();
			PreparedStatement pstmt;
			int i = 0;
			pstmt = con.prepareStatement(buf.toString());

			pstmt.setLong(++i, account.getId());
			if (onlyOneId != 0) {
				pstmt.setLong(++i, onlyOneId);
			}

			return new DbIterator<>(con, pstmt,
					new DbIterator.ResultSetReader<JSONObject>() {
						@Override
						public JSONObject get(Connection con, ResultSet rs)
								throws NxtException.ValidationException,
								SQLException {
							JSONObject ret = null;

							long workId = rs.getLong("id");
							String work_title = rs.getString("work_title");
							byte version = rs.getByte("version_id");

							int deadline = rs.getInt("deadline");
							long amount = rs.getLong("original_amount");
							long amount_paid_pow = rs
									.getLong("payout_amount_pow");
							long fee = rs.getLong("fee");
							long referencedTx = rs
									.getLong("referenced_transaction_id");
							long block_id = rs.getLong("block_id");
							long senderId = rs.getLong("sender_account_id");
							
							
							String languageString = "";
							byte[] code = new byte[]{};
							boolean prune_status = false;
							
							if(nxt.PrunableSourceCode.isPrunedByWorkId(workId)){
								prune_status = true;
							}else{
								nxt.PrunableSourceCode srcode = nxt.PrunableSourceCode.getPrunableSourceCodeByWorkId(workId);
								languageString = getLanguageString(srcode.getLanguage());
								code = srcode.getSource();
							}

							int num_bounties = rs.getInt("bounties_submitted");
							int num_pow = rs.getInt("pow_submitted");

							long last_payment = rs
									.getLong("last_payment_transaction_id");
							long last_cancel = rs
									.getLong("payback_transaction_id");
							int bounties_limit = rs.getInt("bounties_limit");

							long xel_per_pow = rs.getInt("xel_per_pow");

							int h = BlockchainImpl.getInstance()
									.getBlock(block_id).getHeight();
							
							// Calculate bounty payment on demand
							// FIXME: Can we do this more efficient? Maybe in SQL and take care of rounding error there?
							long amount_paid_bounties = 0;
							if(num_bounties > 0) {
								amount_paid_bounties = getOriginalBountyBalance(workId);
							}

							ret = workEntry(version, workId, referencedTx,
									block_id, last_payment, last_cancel,
									last_payment, work_title,
									Crypto.rsEncode(senderId), languageString,
									amount,
									amount_paid_bounties, amount_paid_pow,
									num_bounties, num_pow, h + deadline,
									code.length, fee, h, bounties_limit,
									xel_per_pow, prune_status);

							return ret;
						}
					});
		} catch (SQLException e) {
			e.printStackTrace();
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	public DbIterator<Long> getWorkTimingOutAt(int blockHeightNow) {

		Connection con = null;

		try {
			StringBuilder buf = new StringBuilder();
			buf.append("SELECT * FROM work WHERE payback_transaction_id is null and last_payment_transaction_id is null and (included_block_height + deadline) = ?");

			con = Db.db.getConnection();
			PreparedStatement pstmt;
			int i = 0;
			pstmt = con.prepareStatement(buf.toString());
			pstmt.setInt(++i, blockHeightNow);

			return new DbIterator<>(con, pstmt,
					new DbIterator.ResultSetReader<Long>() {
						@Override
						public Long get(Connection con, ResultSet rs)
								throws NxtException.ValidationException,
								SQLException {
							JSONObject ret = null;
							long workId = rs.getLong("id");
							return workId;
						}
					});
		} catch (SQLException e) {
			e.printStackTrace();
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	public DbIterator<JSONObject> getWorkById(long onlyOneId) {

		Connection con = null;

		try {
			StringBuilder buf = new StringBuilder();
			buf.append("SELECT * FROM work WHERE id = ?");

			con = Db.db.getConnection();
			PreparedStatement pstmt;
			int i = 0;
			pstmt = con.prepareStatement(buf.toString());

			pstmt.setLong(++i, onlyOneId);

			return new DbIterator<>(con, pstmt,
					new DbIterator.ResultSetReader<JSONObject>() {
						@Override
						public JSONObject get(Connection con, ResultSet rs)
								throws NxtException.ValidationException,
								SQLException {
							JSONObject ret = null;

							long workId = rs.getLong("id");
							byte[] code = new byte[]{};
							boolean prune_status = false;
							String languageString = "";
							if(nxt.PrunableSourceCode.isPrunedByWorkId(workId)){
								prune_status = true;
							}else{
								nxt.PrunableSourceCode srcode = nxt.PrunableSourceCode.getPrunableSourceCodeByWorkId(workId);
								languageString = getLanguageString(srcode.getLanguage());
								code = srcode.getSource();
							}
							long xel_per_pow = rs.getLong("xel_per_pow");
							String workTitle = rs.getString("work_title");
							ret = workEntryLite(workId, workTitle,
									code, null, xel_per_pow, prune_status, languageString);

							return ret;
						}
					});
		} catch (SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	public DbIterator<JSONObject> getNRandomWorks(int n) {

		Connection con = null;

		try {
			StringBuilder buf = new StringBuilder();
			buf.append("SELECT * FROM work WHERE payback_transaction_id is null and last_payment_transaction_id is null ORDER BY RAND() limit ?");

			con = Db.db.getConnection();
			PreparedStatement pstmt;
			int i = 0;
			pstmt = con.prepareStatement(buf.toString());

			pstmt.setInt(++i, n);

			return new DbIterator<>(con, pstmt,
					new DbIterator.ResultSetReader<JSONObject>() {
						@Override
						public JSONObject get(Connection con, ResultSet rs)
								throws NxtException.ValidationException,
								SQLException {
							JSONObject ret = null;

							long workId = rs.getLong("id");
							byte[] code = new byte[]{};
							boolean prune_status = false;
							String languageString = "";
							if(nxt.PrunableSourceCode.isPrunedByWorkId(workId)){
								prune_status = true;
							}else{
								nxt.PrunableSourceCode srcode = nxt.PrunableSourceCode.getPrunableSourceCodeByWorkId(workId);
								languageString = getLanguageString(srcode.getLanguage());
								code = srcode.getSource();
							}
							long xel_per_pow = rs.getLong("xel_per_pow");
							BigInteger estimatedTarget = getMinPowTarget(BlockchainImpl
									.getInstance().getLastBlock().getId()); // last
																			// block
																			// will
																			// most
																			// likely
																			// be
																			// the
																			// next
																			// "prev"
																			// so
																			// its
																			// correct
																			// to
																			// use
																			// it
																			// here
																			// for
																			// diff
																			// calculation
							String workTitle = rs.getString("work_title");
							ret = workEntryLite(workId, workTitle, 
									code, estimatedTarget,
									xel_per_pow, prune_status, languageString);

							return ret;
						}
					});
		} catch (SQLException e) {
			DbUtils.close(con);
			throw new RuntimeException(e.toString(), e);
		}
	}

	

	public ArrayList<Long> getAccountIdsFromReceivedBounties(long id) {

		ArrayList<Long> ret = new ArrayList<Long>();

		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT sender_account_id from bounty_submission WHERE work_id = ?")) {
			int i = 0;
			pstmt.setLong(++i, id);
			ResultSet check = pstmt.executeQuery();
			while (check.next()) {
				ret.add((long) check.getLong(1)); // TODO FIXME
			}

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

		return ret;
	}
	public ArrayList<Quartett<Integer,Long,String,Long>> getDataForPlot(long id, int limit_minutes) {

		ArrayList<Quartett<Integer,Long,String,Long>> ret = new ArrayList<Quartett<Integer,Long,String,Long>>();

		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT count(proof_of_work.id), block.min_pow_target, block.timestamp FROM proof_of_work INNER JOIN transaction ON transaction.id = proof_of_work.id INNER JOIN block on block.id = proof_of_work.block_id WHERE work_id=? AND block.timestamp > ? GROUP BY proof_of_work.block_id ORDER BY block.timestamp DESC")) {
			int i = 0;
			pstmt.setLong(++i, id);
			pstmt.setInt(++i, Nxt.getEpochTime()-limit_minutes*60);
			ResultSet check = pstmt.executeQuery();
			while (check.next()) {
				long stime = (long) check.getInt(3);
				stime = stime + (Constants.EPOCH_BEGINNING/1000);		
				Quartett<Integer,Long,String,Long> d = new Quartett<Integer,Long,String,Long>((int) check.getInt(1),stime,(String) check.getString(2), 0L); 
				ret.add(d);
			}

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}

		Collections.reverse(ret);
		return ret;
	}

	public long getOriginalBalance(long id) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT (original_amount) as res FROM work WHERE id = ?")) {
			int i = 0;
			pstmt.setLong(++i, id);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				return check.getLong(1);
			} else {
				throw new RuntimeException(
						"Cannot get remaining balance from DB");
			}

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public HashMap<Long, Long> getOriginalBalances(Set<Long> ids) {
		
		HashMap<Long, Long> resultMap = new HashMap<Long, Long>();
		StringJoiner sj = new StringJoiner(",","","");
		for(int i=0;i<ids.size();++i)
			sj.add("?");

	
			
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT WORK.id, (original_amount) as res FROM work WHERE id IN (" + sj.toString() + ")")) {
			int i = 0;
			Iterator<Long> fillSql = ids.iterator();
			while(fillSql.hasNext())
				pstmt.setLong(++i, fillSql.next());
			
			ResultSet check = pstmt.executeQuery();

			while (check.next()) {
				long id = check.getLong(1);
				long left = check.getLong(2);
				resultMap.put(id, left);
			} 
			return resultMap;

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public long getXelPerPow(long id) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT (xel_per_pow) as res FROM work WHERE id = ?")) {
			int i = 0;
			pstmt.setLong(++i, id);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				return check.getLong(1);
			} else {
				throw new RuntimeException(
						"Cannot get remaining balance from DB");
			}

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public long getOriginalPoWBalance(long id) {
		long bal_original = this.getOriginalBalance(id);
		long bal_pow = (long) Math.abs(bal_original*WorkLogicManager.getInstance().getPercentWork(id));
		long bal_bounty = (long) Math.abs(bal_original*WorkLogicManager.getInstance().getPercentBounty(id)); 
		long total = bal_pow + bal_bounty;
		long rounding_error = bal_original - total;
		bal_pow = bal_pow + rounding_error;
		return bal_pow;
	}
	
	
	public long getOriginalBountyBalance(long id) {
		long bal_original = this.getOriginalBalance(id);
		long bal_pow = (long) Math.abs(bal_original*WorkLogicManager.getInstance().getPercentWork(id));
		long bal_bounty = (long) Math.abs(bal_original*WorkLogicManager.getInstance().getPercentBounty(id)); 
		long total = bal_pow + bal_bounty;
		long rounding_error = bal_original - total;
		bal_pow = bal_pow + rounding_error;
		return bal_bounty;
	}
	public long getOriginalPoWBalanceByOrigBalance(long bal_original, double per_pow, double per_bounty) {
		long bal_pow = (long) Math.abs(bal_original*per_pow);
		System.out.println("REQUESTED POW BAL: " + bal_original + " * " + per_pow + " = " + bal_pow); 
		long bal_bounty = (long) Math.abs(bal_original*per_bounty); 
		long total = bal_pow + bal_bounty;
		long rounding_error = bal_original - total;
		bal_pow = bal_pow + rounding_error;
		return bal_pow;
	}
	
	public long getOriginalBountyBalanceByOrigBalance(long bal_original, double per_pow, double per_bounty) {
		long bal_pow = (long) Math.abs(bal_original*per_pow);
		long bal_bounty = (long) Math.abs(bal_original*per_bounty); 
		long total = bal_pow + bal_bounty;
		long rounding_error = bal_original - total;
		bal_pow = bal_pow + rounding_error;
		return bal_bounty;
	}
	
	public long getTotalPowPayments(long id) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT SUM(payout_amount) FROM proof_of_work WHERE work_id = ?")) {
			int i = 0;
			pstmt.setLong(++i, id);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				long result = check.getLong(1);
				return result;
			} else {
				throw new RuntimeException(
						"Cannot decide if work already has pow submissions or not");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public HashMap<Long, Long> getTotalPowPaymentsMultiple(Set<Long> ids) {
		
		HashMap<Long, Long> resultMap = new HashMap<Long, Long>();
		StringJoiner sj = new StringJoiner(",","","");
		for(int i=0;i<ids.size();++i)
			sj.add("?");

			
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT work_id,SUM(payout_amount) FROM proof_of_work WHERE work_id IN (" + sj.toString() + ") GROUP BY work_id")) {
			int i = 0;
			Iterator<Long> fillSql = ids.iterator();
			while(fillSql.hasNext())
				pstmt.setLong(++i, fillSql.next());
			
			ResultSet check = pstmt.executeQuery();
			while (check.next()) {
				long id = check.getLong(1);
				long left = check.getLong(2);
				resultMap.put(id, left);
			} 
			return resultMap;
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public long getRemainingBalance(long id, boolean hasUnconfirmedBountySubmissions) {
		long pow_bal_orig = this.getOriginalPoWBalance(id);
		long bounty_bal_orig = this.getOriginalBountyBalance(id);
		
		long remaining = pow_bal_orig + bounty_bal_orig;
		
		// here remove all submitted pow
		long paid_out_pow_submission = getTotalPowPayments(id);
		remaining = remaining - paid_out_pow_submission;
		
		// and here add bounty bal only if no bounty submitted yet
		int bounty_number_submitted = getBountyNumber(id);
		if(bounty_number_submitted > 0 || hasUnconfirmedBountySubmissions) {
			remaining = remaining - bounty_bal_orig;
		}
		
		return remaining;
	}
	
	public long totalPayoutSoFar(long id) {
		long bounty_bal_orig = this.getOriginalBountyBalance(id);
		long paidout = 0;
		
		// here add all submitted pow
		long paid_out_pow_submission = getTotalPowPayments(id);
		paidout = paidout + paid_out_pow_submission;
		
		// and here add bounty bal only if no bounty submitted yet
		int bounty_number_submitted = getBountyNumber(id);
		if(bounty_number_submitted > 0) {
			paidout = paidout + bounty_bal_orig;
		}
		
		return paidout;
	}
	

	public long getTransactionInitiator(long id) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT sender_account_id FROM work WHERE id = ?")) {
			int i = 0;
			pstmt.setLong(++i, id);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				return check.getLong(1);
			} else {
				throw new RuntimeException("Cannot get transaction initiator");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public void validatePOW(long id, PiggybackedProofOfWork attachment,
			long amount, long previousBlockId) throws NxtException.NotValidException {
		boolean valid  = false;
		try {
			System.out.println("Validating POW for work = "
					+ attachment.getWorkId());

			Executioner e = getExecutioner(attachment.getWorkId());
			valid = e.executeProofOfWork(attachment.getInput(), getMinPowTarget(previousBlockId));
		
		} catch (Exception e1) {
			e1.printStackTrace();
			throw new NxtException.NotValidException(
					"Proof of work is invalid: causes ElasticPL function to crash");
		}
		if (!valid) {
			throw new NxtException.NotValidException(
					"Proof of work " + id + " is invalid: does not meet target");
		}
	}

	private Executioner getExecutioner(long id) {
		// TODO FIXME Check if Executioner is cached
		Executioner e = null;

		if (e == null) {
			// Create a new one and cache
			try (DbIterator<JSONObject> obj = getWorkById(id)) {
				if (obj.hasNext()) {
					JSONObject o = obj.next();
					int num_input = (int) o.get("num_input");
					String source = (String) o.get("source");
					e = new Executioner(source, num_input, id);
				} else {
					throw new RuntimeException(
							"Cannot grab source code for work id = " + id);
				}
			} catch (ParseException e1) {
				throw new RuntimeException(
						"Source code has invalid code for work id = " + id);
			}
		}
		return e;
	}

	public void validateBounty(long id, PiggybackedProofOfBounty attachment) throws NotValidException {
		boolean valid = false;
		try {
			Executioner e = getExecutioner(attachment.getWorkId());
			valid = e.executeBountyHooks(attachment.getInput());
			
		} catch (Exception e1) {
			throw new NxtException.NotValidException("Bounty is invalid");
		}
		if (!valid) {
			throw new NxtException.NotValidException("Bounty is invalid");
		}
	}

	public int getOpenNumber(long id) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT COUNT(*) FROM work WHERE sender_account_id = ? and payback_transaction_id is null and last_payment_transaction_id is null")) {
			int i = 0;
			pstmt.setLong(++i, id);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				int result = check.getInt(1);
				return result;
			} else {
				throw new RuntimeException(
						"Cannot decide if work exists or not");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public int getBountyNumber(long id) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT COUNT(*) FROM bounty_submission WHERE work_id = ?")) {
			int i = 0;
			pstmt.setLong(++i, id);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				int result = check.getInt(1);
				System.out.println("Work " + id + " has gotten " + result + " bounties.");
				return result;
			} else {
				throw new RuntimeException(
						"Cannot decide if work already has bounty submissions or not");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public HashMap<Long, Integer> getBountyNumberLeft(Set<Long> ids) {
		HashMap<Long, Integer> resultMap = new HashMap<Long, Integer>();
		StringJoiner sj = new StringJoiner(",","","");
		for(int i=0;i<ids.size();++i)
			sj.add("?");

		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT WORK.id, BOUNTIES_LIMIT-COUNT( BOUNTY_SUBMISSION.ID) LEFT_BOUNTIES FROM WORK LEFT JOIN BOUNTY_SUBMISSION ON BOUNTY_SUBMISSION.work_id=WORK.id WHERE WORK.id IN (" + sj.toString() + ")")) {
			int i = 0;
			Iterator<Long> fillSql = ids.iterator();
			while(fillSql.hasNext())
				pstmt.setLong(++i, fillSql.next());
			
			ResultSet check = pstmt.executeQuery();
			
			while (check.next()) {
				long id = check.getLong(1);
				int left = check.getInt(2);
				resultMap.put(id, left);
			} 
			return resultMap;

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	
	public Integer getBountyNumberLeft(Long id) {
		HashMap<Long, Integer> resultMap = new HashMap<Long, Integer>();
		
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT WORK.id, BOUNTIES_LIMIT-COUNT( BOUNTY_SUBMISSION.ID) LEFT_BOUNTIES FROM WORK LEFT JOIN BOUNTY_SUBMISSION ON BOUNTY_SUBMISSION.work_id=WORK.id WHERE WORK.id = ?")) {
			int i = 0;
			pstmt.setLong(++i, id);
			
			ResultSet check = pstmt.executeQuery();
			
			if (check.next()) {
				int left = check.getInt(2);
				return left;
			}else{
				return 0; // TODO FIXME
			}

		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public int getClosedNumber(long id) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT COUNT(*) FROM work WHERE sender_account_id = ? and (payback_transaction_id is not null or last_payment_transaction_id is not null)")) {
			int i = 0;
			pstmt.setLong(++i, id);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				int result = check.getInt(1);
				return result;
			} else {
				throw new RuntimeException(
						"Cannot decide if work exists or not");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public int GetHowManyPowAboveMe(long xelPerPowInt) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT COUNT(*) FROM work WHERE xel_per_pow >= ? and payback_transaction_id is null and last_payment_transaction_id is null")) {
			int i = 0;
			pstmt.setLong(++i, xelPerPowInt);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				int result = check.getInt(1);
				return result;
			} else {
				throw new RuntimeException(
						"Cannot decide if work exists or not");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public Triplet<Long, Long, Long> GetMinMaxAvgActualPowPrice() {
		long resMin, resMax, resAvg;
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT coalesce(MIN(XEL_PER_POW),0),coalesce(MAX(XEL_PER_POW),0),coalesce(AVG(XEL_PER_POW),0) "
								+ "FROM WORK WHERE PAYBACK_TRANSACTION_ID IS NULL AND LAST_PAYMENT_TRANSACTION_ID IS NULL")) {
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				resMin = check.getLong(1);
				resMax = check.getLong(2);
				resAvg = check.getLong(3);
			} else {
				throw new RuntimeException(
						"Cannot get average values for work rewards");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e.toString(), e);
		}
		return new Triplet<Long, Long, Long>(resMin, resMax, resAvg);
	}

	public long GetMinPossiblePowPrice() {
		return Constants.MIN_WORK_POW_REWARD; // FIXME average*2 or, if no other work online, a fixed
					// constant
	}

	public long GetMaxPossiblePowPrice() {
		return Constants.MAX_WORK_POW_REWARD; // FIXME average*2 or, if no other work online, a fixed
						// constant
	}

	public boolean isPowPriceCorrect(long xel_per_pow) {
		if (xel_per_pow <= GetMinPossiblePowPrice())
			return false;
		if (xel_per_pow >= GetMaxPossiblePowPrice())
			return false;
		return true;
	}

	public TransactionImpl createOffTheRecordZeroFeeTransaction(Account senderAccount, long recipientId, long amountNQT,
			Attachment attachment, String secretPhrase) throws NxtException {

		short deadline = 1;
		long feeNQT = 0;


		// shouldn't try to get publicKey from senderAccount as it may have not
		// been set yet
		byte[] publicKey = Crypto.getPublicKey(secretPhrase);

		try {
			Transaction.Builder builder = Nxt.newTransactionBuilder(publicKey,
					amountNQT, feeNQT, deadline, attachment).referencedTransactionFullHash(null);
			if (attachment.getTransactionType().canHaveRecipient()) {
				builder.recipientId(recipientId);
			}
			if (attachment.getTransactionType().zeroFeeTransaction()) {
				builder.zeroFee();
			}
			TransactionImpl transaction = (TransactionImpl) builder.build(secretPhrase);
			try {
                if (attachment.getTransactionType().moneyComesFromNowhere()==false && Convert.safeAdd(amountNQT, transaction.getFeeNQT()) > senderAccount.getUnconfirmedBalanceNQT()) {
                    throw new NxtException.NotValidException("Insufficient funds in user account, cannot create off the record transaction.");
                }
            } catch (ArithmeticException e) {
            	throw new NxtException.NotValidException("Insufficient funds in user account, cannot create off the record transaction.");
            }
			
			transaction.validate();
			
			return transaction;
			
		} catch (NxtException.NotYetEnabledException e) {
			throw e;
		} catch (NxtException.ValidationException e) {
			throw e;
		}
	}

	public boolean isUniqueBounty(long id, int[] input) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT count(*) FROM bounty_submission WHERE work_id = ? and input = ?")) {
			int i = 0;
			pstmt.setLong(++i, id);
			byte inputaar[] = convertIntArrayToBytes(input);
			pstmt.setBytes(++i, inputaar);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				if (check.getInt(1) == 0)
					return true;
			} else {
				throw new RuntimeException(
						"Cannot decide if work is still pending");
			}
			return false;
		} catch (SQLException | IOException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}
	public boolean isUniquePOW(long id, int[] input) {
		try (Connection con = Db.db.getConnection();
				PreparedStatement pstmt = con
						.prepareStatement("SELECT count(*) FROM proof_of_work WHERE work_id = ? and input = ?")) {
			int i = 0;
			pstmt.setLong(++i, id);
			byte inputaar[] = convertIntArrayToBytes(input);
			pstmt.setBytes(++i, inputaar);
			ResultSet check = pstmt.executeQuery();
			if (check.next()) {
				if (check.getInt(1) == 0)
					return true;
			} else {
				throw new RuntimeException(
						"Cannot decide if work is still pending");
			}
			return false;
		} catch (SQLException | IOException e) {
			throw new RuntimeException(e.toString(), e);
		}
	}

	public HashMap<Long, Long> getPOWFundLeft(Set<Long> affectedPOWIDs, boolean with_unconfirmed) {
		
		// Fetch original balances
		HashMap<Long, Long> origBalances = getOriginalBalances(affectedPOWIDs);
		HashMap<Long, Long> powPayouts = this.getTotalPowPaymentsMultiple(affectedPOWIDs);
		
		// Now break down limits to 60%/40%! IMPORTANT: Account for rounding errors
		HashMap<Long, Double> powProportions = this.getPercentWorks(affectedPOWIDs);
		HashMap<Long, Double> bountyProportions = this.getPercentBountys(affectedPOWIDs);

		for(Long key : origBalances.keySet()){
			long bal_original = origBalances.get(key);
			double percent_work = powProportions.get(key);
			double percent_bounty = bountyProportions.get(key);
			long bal_pow = (long) Math.abs(bal_original*percent_work);
			long bal_bounty = (long) Math.abs(bal_original*percent_bounty); 
			long total = bal_pow + bal_bounty;
			long rounding_error = bal_original - total;
			bal_pow = bal_pow + rounding_error;
			
			// substract already paid out
			if(powPayouts.containsKey(key))
				bal_pow = bal_pow - powPayouts.get(key);
			
			// and subtract unconfirmed
			if(with_unconfirmed){
				long unconfPow = this.getPowUnconfirmed(key);
				bal_pow = bal_pow - unconfPow;
			}
			
			
			origBalances.remove(key);
			origBalances.put(key, bal_pow);
		}
		
		// Finally, return the POWFundLeftArray		
		return origBalances;
	}
}

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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import nxt.crypto.Crypto;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;
import nxt.util.ThreadPool;

public final class Generator implements Comparable<Generator> {

	/**
	 * Active generator
	 */
	public static class ActiveGenerator implements Comparable<ActiveGenerator> {
		private final long accountId;
		private long hitTime;
		private long effectiveBalanceNXT;
		private byte[] publicKey;

		public ActiveGenerator(final long accountId) {
			this.accountId = accountId;
			this.hitTime = Long.MAX_VALUE;
		}

		@Override
		public int compareTo(final ActiveGenerator obj) {
			return (this.hitTime < obj.hitTime ? -1 : (this.hitTime > obj.hitTime ? 1 : 0));
		}

		@Override
		public boolean equals(final Object obj) {
			return ((obj != null) && (obj instanceof ActiveGenerator)
					&& (this.accountId == ((ActiveGenerator) obj).accountId));
		}

		public long getAccountId() {
			return this.accountId;
		}

		public long getEffectiveBalance() {
			return this.effectiveBalanceNXT;
		}

		public long getHitTime() {
			return this.hitTime;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(this.accountId);
		}

		private void setLastBlock(final Block lastBlock) {
			if (this.publicKey == null) {
				this.publicKey = Account.getPublicKey(this.accountId);
				if (this.publicKey == null) {
					this.hitTime = Long.MAX_VALUE;
					return;
				}
			}
			final int height = lastBlock.getHeight();
			final Account account = Account.getAccount(this.accountId, height);
			if (account == null) {
				this.hitTime = Long.MAX_VALUE;
				return;
			}

			// For the first X blocks, do not use effective but pseudo-effective balance. This helps the system to bootstrap.
            // Here, no confirmations are needed (but leasing is accounted for)
            if(height > Constants.FIRST_X_BLOCKS_PSEUDO_EFFECTIVE_BALANCE)
			    this.effectiveBalanceNXT = Math.max(account.getEffectiveBalanceNXT(height), 0);
            else
                this.effectiveBalanceNXT = Math.max(account.getPseudoEffectiveBalanceNXT(height), 0);

			if (this.effectiveBalanceNXT == 0) {
				this.hitTime = Long.MAX_VALUE;
				return;
			}
			final BigInteger effectiveBalance = BigInteger.valueOf(this.effectiveBalanceNXT);
			final BigInteger hit = Generator.getHit(this.publicKey, lastBlock);
			this.hitTime = Generator.getHitTime(effectiveBalance, hit, lastBlock);
		}
	}

	public enum Event {
		GENERATION_DEADLINE, START_FORGING, STOP_FORGING
	}

	private static final int MAX_FORGERS = Nxt.getIntProperty("nxt.maxNumberOfForgers");

	private static final byte[] fakeForgingPublicKey = Nxt.getBooleanProperty("nxt.enableFakeForging")
			? Account.getPublicKey(Convert.parseAccountId(Nxt.getStringProperty("nxt.fakeForgingAccount"))) : null;
	private static final String fakeForgingAccountId = Nxt.getBooleanProperty("nxt.enableFakeForging")
			? Nxt.getStringProperty("nxt.fakeForgingAccount") : null;

	private static final Listeners<Generator, Event> listeners = new Listeners<>();
	private static final ConcurrentMap<String, Generator> generators = new ConcurrentHashMap<>();
	private static final Collection<Generator> allGenerators = Collections
			.unmodifiableCollection(Generator.generators.values());
	private static volatile List<Generator> sortedForgers = null;
	private static long lastBlockId;

	private static int delayTime = Constants.FORGING_DELAY;

	private static final Runnable generateBlocksThread = new Runnable() {

		private volatile boolean logged;

		@Override
		public void run() {

			try {
				try {
					BlockchainImpl.getInstance().updateLock();
					try {
						Block lastBlock = Nxt.getBlockchain().getLastBlock();
						if ((lastBlock == null) || (lastBlock.getHeight() < Constants.LAST_KNOWN_BLOCK)) {
							return;
						}
						final int generationLimit = Nxt.getEpochTime() - Generator.delayTime;
						if ((lastBlock.getId() != Generator.lastBlockId) || (Generator.sortedForgers == null)) {
							Generator.lastBlockId = lastBlock.getId();
							if (lastBlock.getTimestamp() > (Nxt.getEpochTime() - 600)) {
								final Block previousBlock = Nxt.getBlockchain()
										.getBlock(lastBlock.getPreviousBlockId());
								for (final Generator generator : Generator.generators.values()) {
									generator.setLastBlock(previousBlock);
									if ((generator.getHitTime() > 0)
											&& (generator.getTimestamp(generationLimit) < lastBlock.getTimestamp())) {
										Logger.logDebugMessage("Pop off: " + generator.toString()
												+ " will pop off last block " + lastBlock.getStringId());
										final List<BlockImpl> poppedOffBlock = BlockchainProcessorImpl.getInstance()
												.popOffTo(previousBlock);
										for (final BlockImpl block : poppedOffBlock) {
											TransactionProcessorImpl.getInstance()
													.processLater(block.getTransactions());
										}
										lastBlock = previousBlock;
										Generator.lastBlockId = previousBlock.getId();
										break;
									}
								}
							}
							final List<Generator> forgers = new ArrayList<>();
							for (final Generator generator : Generator.generators.values()) {
								generator.setLastBlock(lastBlock);

								if (generator.effectiveBalance.signum() > 0 || Generator.allowsFakeForging(generator.getPublicKey())) {
									forgers.add(generator);
								}
							}
							Collections.sort(forgers);
							Generator.sortedForgers = Collections.unmodifiableList(forgers);
							this.logged = false;
						}
						if (!this.logged) {
							for (final Generator generator : Generator.sortedForgers) {
								if ((generator.getHitTime() - generationLimit) > 60) {
									break;
								}
								Logger.logDebugMessage(generator.toString());
								this.logged = true;
							}
						}
						for (final Generator generator : Generator.sortedForgers) {
							if ((generator.getHitTime() > generationLimit)
									|| generator.forge(lastBlock, generationLimit)) {
								return;
							}
						}
					} finally {
						BlockchainImpl.getInstance().updateUnlock();
					}
				} catch (final Exception e) {
					Logger.logMessage("Error in block generation thread", e);
				}
			} catch (final Throwable t) {
				Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
				t.printStackTrace();
				System.exit(1);
			}

		}

	};

	static {
		if (!Constants.isLightClient) {
			ThreadPool.scheduleThread("GenerateBlocks", Generator.generateBlocksThread, 500, TimeUnit.MILLISECONDS);
		}
	}

	/** Active block generators */
	private static final Set<Long> activeGeneratorIds = new HashSet<>();

	/** Active block identifier */
	private static long activeBlockId;

	/** Sorted list of generators for the next block */
	private static final List<ActiveGenerator> activeGenerators = new ArrayList<>();

	/** Generator list has been initialized */
	private static boolean generatorsInitialized = false;

	public static boolean addListener(final Listener<Generator> listener, final Event eventType) {
		return Generator.listeners.addListener(listener, eventType);
	}

	static boolean allowsFakeForging(final byte[] publicKey) {
		boolean result = Constants.isTestnet && (publicKey != null) && fakeForgingAccountId.equals(Convert.toUnsignedLong(Account.getId(publicKey)));
		return result;
	}

    static boolean allowsFakeForgingInPrincipal() {
        boolean result = Constants.isTestnet && fakeForgingAccountId != null;
        return result;
    }

	public static Collection<Generator> getAllGenerators() {
		return Generator.allGenerators;
	}

	public static Generator getGenerator(final String secretPhrase) {
		return Generator.generators.get(secretPhrase);
	}

	public static int getGeneratorCount() {
		return Generator.generators.size();
	}

	static BigInteger getHit(final byte[] publicKey, final Block block) {
		if (Generator.allowsFakeForging(publicKey)) {
			return BigInteger.ZERO;
		}

		final MessageDigest digest = Crypto.sha256();
		digest.update(block.getGenerationSignature());
		final byte[] generationSignatureHash = digest.digest(publicKey);
		return new BigInteger(1,
				new byte[] { generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5],
						generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2],
						generationSignatureHash[1], generationSignatureHash[0] });
	}

	static long getHitTime(final BigInteger effectiveBalance, final BigInteger hit, final Block block) {
		final float scaledHitTIme = Redeem.getRedeemedPercentage();
		final float inverse = (float) (1.0 / scaledHitTIme);
		final long absolute = (long) inverse;
		final BigInteger factor = BigInteger.valueOf(absolute);

		Logger.logDebugMessage("Generator: Hit for [Bal: " + effectiveBalance.toString() + "]: in " + ((block.getTimestamp() + (hit.divide(BigInteger.valueOf(block.getBaseTarget())
				.multiply(effectiveBalance.multiply(factor))).longValue())) - Nxt.getEpochTime()) + "seconds");
		return block.getTimestamp() + hit.divide(BigInteger.valueOf(block.getBaseTarget())
				.multiply(effectiveBalance.multiply(factor))).longValue();
	}

	/**
	 * Return a list of generators for the next block. The caller must hold the
	 * blockchain read lock to ensure the integrity of the returned list.
	 *
	 * @return List of generator account identifiers
	 */
	public static List<ActiveGenerator> getNextGenerators() {
		List<ActiveGenerator> generatorList;
		final Blockchain blockchain = Nxt.getBlockchain();
		synchronized (Generator.activeGenerators) {
			if (!Generator.generatorsInitialized) {
				Generator.activeGeneratorIds
						.addAll(BlockDb.getBlockGenerators(Math.max(1, blockchain.getHeight() - 10000)));
				for (final Long activeGeneratorId : Generator.activeGeneratorIds) {
					Generator.activeGenerators.add(new ActiveGenerator(activeGeneratorId));
				}
				Logger.logDebugMessage(Generator.activeGeneratorIds.size() + " block generators found");
				Nxt.getBlockchainProcessor().addListener(block -> {
					final long generatorId = block.getGeneratorId();
					synchronized (Generator.activeGenerators) {
						if (!Generator.activeGeneratorIds.contains(generatorId)) {
							Generator.activeGeneratorIds.add(generatorId);
							Generator.activeGenerators.add(new ActiveGenerator(generatorId));
						}
					}
				}, BlockchainProcessor.Event.BLOCK_PUSHED);
				Generator.generatorsInitialized = true;
			}
			final long blockId = blockchain.getLastBlock().getId();
			if (blockId != Generator.activeBlockId) {
				Generator.activeBlockId = blockId;
				final Block lastBlock = blockchain.getLastBlock();
				for (final ActiveGenerator generator : Generator.activeGenerators) {
					generator.setLastBlock(lastBlock);
				}
				Collections.sort(Generator.activeGenerators);
			}
			generatorList = new ArrayList<>(Generator.activeGenerators);
		}
		return generatorList;
	}

	public static long getNextHitTime(final long lastBlockId, final int curTime) {
		BlockchainImpl.getInstance().readLock();
		try {
			if ((lastBlockId == Generator.lastBlockId) && (Generator.sortedForgers != null)) {
				for (final Generator generator : Generator.sortedForgers) {
					if (generator.getHitTime() >= (curTime - Constants.FORGING_DELAY)) {
						return generator.getHitTime();
					}
				}
			}
			return 0;
		} finally {
			BlockchainImpl.getInstance().readUnlock();
		}
	}

	public static List<Generator> getSortedForgers() {
		final List<Generator> forgers = Generator.sortedForgers;
		return forgers == null ? Collections.emptyList() : forgers;
	}

	static void init() {
	}

	public static boolean removeListener(final Listener<Generator> listener, final Event eventType) {
		return Generator.listeners.removeListener(listener, eventType);
	}

	static void setDelay(final int delay) {
		Generator.delayTime = delay;
	}

	public static Generator startForging(final String secretPhrase) {
		if (Generator.generators.size() >= Generator.MAX_FORGERS) {
			throw new RuntimeException(
					"Cannot forge with more than " + Generator.MAX_FORGERS + " accounts on the same node");
		}

		final long id = Account.getId(Crypto.getPublicKey(secretPhrase));
		if (id == Genesis.REDEEM_ID) {
			throw new RuntimeException("Cannot forge with REDEEM account");
		}

		final Generator generator = new Generator(secretPhrase);
		final Generator old = Generator.generators.putIfAbsent(secretPhrase, generator);
		if (old != null) {
			Logger.logDebugMessage(old + " is already forging");
			return old;
		}
		Generator.listeners.notify(generator, Event.START_FORGING);
		Logger.logInfoMessage(generator + " started");
		return generator;
	}

	public static int stopForging() {
		final int count = Generator.generators.size();
		final Iterator<Generator> iter = Generator.generators.values().iterator();
		while (iter.hasNext()) {
			final Generator generator = iter.next();
			iter.remove();
			Logger.logDebugMessage(generator + " stopped");
			Generator.listeners.notify(generator, Event.STOP_FORGING);
		}
		Nxt.getBlockchain().updateLock();
		try {
			Generator.sortedForgers = null;
		} finally {
			Nxt.getBlockchain().updateUnlock();
		}
		return count;
	}

	public static Generator stopForging(final String secretPhrase) {
		final Generator generator = Generator.generators.remove(secretPhrase);
		if (generator != null) {
			Nxt.getBlockchain().updateLock();
			try {
				Generator.sortedForgers = null;
			} finally {
				Nxt.getBlockchain().updateUnlock();
			}
			Logger.logDebugMessage(generator + " stopped");
			Generator.listeners.notify(generator, Event.STOP_FORGING);
		}
		return generator;
	}

	static boolean verifyHit(final BigInteger hit, final BigInteger effectiveBalance, final Block previousBlock,
			final int timestamp) {

		final float scaledHitTIme = Redeem.getRedeemedPercentage();
		Logger.logDebugMessage("[!!] Up to now, " + String.valueOf(scaledHitTIme) + " of all XEL have been redeemed.");
		final float inverse = (float) (1.0 / scaledHitTIme);
		final long absolute = (long) inverse;
		final BigInteger factor = BigInteger.valueOf(absolute);

		final int elapsedTime = timestamp - previousBlock.getTimestamp();
		if (elapsedTime <= 0) {
			return false;
		}
		final BigInteger effectiveBaseTarget = BigInteger.valueOf(previousBlock.getBaseTarget())
				.multiply(effectiveBalance.multiply(factor));
		final BigInteger prevTarget = effectiveBaseTarget.multiply(BigInteger.valueOf(elapsedTime - 1));
		final BigInteger target = prevTarget.add(effectiveBaseTarget);
		return hit.compareTo(target) < 0
                && (hit.compareTo(prevTarget) >= 0
                || (Constants.isTestnet ? elapsedTime > 300 : elapsedTime > 3600)
                || Constants.isOffline);
	}

	private final long accountId;

	private final String secretPhrase;

	private final byte[] publicKey;

	private volatile long hitTime;

	private volatile BigInteger hit;

	private volatile BigInteger effectiveBalance;

	private volatile long deadline;

	private Generator(final String secretPhrase) {
		this.secretPhrase = secretPhrase;
		this.publicKey = Crypto.getPublicKey(secretPhrase);
		this.accountId = Account.getId(this.publicKey);
		Nxt.getBlockchain().updateLock();
		try {
			if (Nxt.getBlockchain().getHeight() >= Constants.LAST_KNOWN_BLOCK) {
				this.setLastBlock(Nxt.getBlockchain().getLastBlock());
			}
			Generator.sortedForgers = null;
		} finally {
			Nxt.getBlockchain().updateUnlock();
		}
	}

	@Override
	public int compareTo(final Generator g) {
		final int i = this.hit.multiply(g.effectiveBalance).compareTo(g.hit.multiply(this.effectiveBalance));
		if (i != 0) {
			return i;
		}
		return Long.compare(this.accountId, g.accountId);
	}

	boolean forge(final Block lastBlock, final int generationLimit)
			throws BlockchainProcessor.BlockNotAcceptedException {
		final int timestamp = this.getTimestamp(generationLimit);

		// fake forging, skip hit verification
        if (Generator.allowsFakeForging(this.getPublicKey())){
            // pass
        } else {
            if (!Generator.verifyHit(this.hit, this.effectiveBalance, lastBlock, timestamp)) {
                Logger.logErrorMessage(this.toString() + " failed to forge at " + timestamp + " height "
                        + lastBlock.getHeight() + " last timestamp " + lastBlock.getTimestamp());
                return false;
            }
        }

		final int start = Nxt.getEpochTime();
		while (true) {
			try {
				BlockchainProcessorImpl.getInstance().generateBlock(this.secretPhrase, timestamp);
				Generator.setDelay(Constants.FORGING_DELAY);
				return true;
			} catch (final BlockchainProcessor.TransactionNotAcceptedException e) {
				// the bad transaction has been expunged, try again
				if ((Nxt.getEpochTime() - start) > 10) { // give up after trying
															// for 10 s
					throw e;
				}
			}
		}
	}

	public long getAccountId() {
		return this.accountId;
	}

	public long getDeadline() {
		return this.deadline;
	}

	public long getHitTime() {
		return this.hitTime;
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	private int getTimestamp(final int generationLimit) {
		return ((generationLimit - this.hitTime) > 3600) ? generationLimit : (int) this.hitTime + 1;
	}

	private void setLastBlock(final Block lastBlock) {
		final int height = lastBlock.getHeight();
		final Account account = Account.getAccount(this.accountId, height);
		if (account == null) {
			this.effectiveBalance = BigInteger.ZERO;
		} else {
			this.effectiveBalance = BigInteger.valueOf(Math.max(account.getEffectiveBalanceNXT(height), 0));
		}
		if (this.effectiveBalance.signum() == 0) {
			this.hitTime = 0;
			this.hit = BigInteger.ZERO;
			return;
		}
		this.hit = Generator.getHit(this.publicKey, lastBlock);
		this.hitTime = Generator.getHitTime(this.effectiveBalance, this.hit, lastBlock);
		this.deadline = Math.max(this.hitTime - lastBlock.getTimestamp(), 0);
		Generator.listeners.notify(this, Event.GENERATION_DEADLINE);
	}

	@Override
	public String toString() {
		return "Forger " + Long.toUnsignedString(this.accountId) + " deadline " + this.getDeadline() + " hit "
				+ this.hitTime;
	}
}

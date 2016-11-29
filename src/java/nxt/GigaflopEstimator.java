package nxt;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import elastic.pl.interpreter.ParseException;
import nxt.crypto.Crypto;
import nxt.db.DbIterator;
import nxt.util.Logger;

public class GigaflopEstimator {

	// This measurement can be unprecise. See:
	// http://elastic-project.com/gigaflop_estimation

	// Experimentally evaluated
	private static final double gigaflops_6700hq_i7_per_core = 3.34f;
	private static final double average_pow_per_360s_on_6700hq_simplest_program = 22.657342657342657;

	// Durations for simple work package
	private static double baseline_duration_mseconds = 0;

	// Map for durations of other work packages and their stretch factors
	private static Map<Long, Double> durations_map = new HashMap<>();
	private static Map<Long, Double> stretch_factor_map = new HashMap<>();

	// Some Stuff
	public static MessageDigest dig = Crypto.md5();

	// Simplest program ever
	private static byte[] simple_source = "verify m[1]==1;".getBytes();

	// When something goes wrong, retry in 10 seconds
	private static boolean failed = false;
	private static long last_failed = System.currentTimeMillis();

	// only estimate once every 60 s
	private static double estimation = 0;
	private static long last_estimation = 0;

	// only once per 30 seconds, max!
	public static double estimateGigaflops() {
		if (GigaflopEstimator.last_estimation >= (System.currentTimeMillis() - (30 * 1000))) {
			return GigaflopEstimator.estimation;
		}

		// Re-estimate now - Fill in all missing execution times, but never more
		// than 20 at a time (should avoid DOS by many many newly added works)
		int intrinsic_counter = 0;
		double totalGigaFlops = 0;
		final Block currentBlock = Nxt.getBlockchain().getLastBlock();

		try (DbIterator<Work> open = Work.getAllActive()) {
			while (open.hasNext()) {
				final Work next = open.next();
				if (GigaflopEstimator.has_already_measured(next.getWork_id()) == false) {
					intrinsic_counter++;
					if (PrunableSourceCode.isPrunedByWorkId(next.getWork_id())) {
						continue;
					}

					// Here, we assume that we have the source code!!!
					GigaflopEstimator.measure_and_store_source(next.getWork_id(),
							PrunableSourceCode.getPrunableSourceCode(next.getWork_id()).getSource());
				}

				// Safe harbour
				if (GigaflopEstimator.has_already_measured(next.getWork_id()) == true) {
					// Did this job have POWs in the last 10 blocks?
					if (currentBlock.getId() != next.getBlock_id()) {
						// Do standard retargeting (yet to be peer reviewed)

						long PastBlocksMass = 0;
						final int account_for_blocks_max = 10;
						long seconds_passed = 0;
						Block b = currentBlock;
						int counter = 0;
						while (true) {
							if ((b == null) || (b.getId() == next.getBlock_id())) {
								break;
							}
							counter = counter + 1;
							PastBlocksMass += b.countNumberPOWPerWorkId(next.getId());
							seconds_passed = currentBlock.getTimestamp() - b.getTimestamp();
							if (seconds_passed < 0)
							{
								seconds_passed = 60 * counter; // Crippled
							}
							// timestamps,
							// assume
							// everything
							// went
							// smoothly!
							// Should not
							// happen
							// anyway!
							if ((b.getPreviousBlock() == null) || (counter == account_for_blocks_max)) {
								break;
							}
							b = b.getPreviousBlock();
						}

						if (PastBlocksMass == 0) {
							// no POW in last 10 seconds, ignore this job
							continue;
						}

						if (seconds_passed < 1) {
							seconds_passed = 1;
						}
						// Normalize the time span so we always work with "360
						// second windows"
						double pows_per_360_seconds = ((PastBlocksMass * 360.0) / seconds_passed);

						// get the target scaler
						final BigDecimal workTarget = new BigDecimal(next.getWork_min_pow_target_bigint());
						final BigDecimal minTarget = new BigDecimal(Constants.least_possible_target);
						double factor = 0;
						try{
							final BigDecimal quot = workTarget.divide(minTarget);
							factor = quot.doubleValue();
						}catch(final Exception e){
							factor = 1; // FIXME TODO, make this more precise maybe?
						}

						// scale number
						pows_per_360_seconds = pows_per_360_seconds*factor*GigaflopEstimator.stretch_factor_map.get(next.getWork_id());

						// Estimate flops on this job
						totalGigaFlops += (pows_per_360_seconds/GigaflopEstimator.average_pow_per_360s_on_6700hq_simplest_program) * GigaflopEstimator.gigaflops_6700hq_i7_per_core;
					}
					GigaflopEstimator.estimation = totalGigaFlops;
					// break if limit of evaluations reached
					if (intrinsic_counter == 20) {
						break;
					}
				}
			}
		}
		GigaflopEstimator.last_estimation = System.currentTimeMillis();
		return GigaflopEstimator.estimation;
	}

	public static String estimateText(){
		String ret = "";
		final double gflops = GigaflopEstimator.estimateGigaflops();
		if(gflops > 1000000) {
			ret = GigaflopEstimator.round(gflops/1000000,2) + " Pflops";
		} else if(gflops > 1000) {
			ret = GigaflopEstimator.round(gflops/1000,2) + " Tflops";
		} else {
			ret = GigaflopEstimator.round(gflops,2) + " Gflops";
		}
		return ret;
	}

	public static int get_repetitions(final long firstTime) {
		if (firstTime == 0)
		{
			return 25; // arbitrary
		}

		// We do not want to measure longer than 0.1 seconds but at least one
		// repetition
		final double factor = 500.0 / firstTime; // TODO FIXME
		return Math.max((int) Math.floor(factor), 1);
	}

	public static boolean has_already_measured(final Long workId) {
		return GigaflopEstimator.durations_map.containsKey(workId);
	}

	public static boolean has_already_measured_baseline() {
		return GigaflopEstimator.baseline_duration_mseconds != 0;
	}

	public static void measure_and_store_source(final long workid, final byte[] src) {
		GigaflopEstimator.measure_baseline();
		if (!GigaflopEstimator.has_already_measured_baseline()) {
			return;
		}

		try {
			final double dur = GigaflopEstimator.measure_source(src, false);

			if (dur == 0)
			{
				return; // Impossible, ignore this crap
			}

			Logger.logInfoMessage("We measured, that work_id " + workid + " can be executed in "
					+ GigaflopEstimator.baseline_duration_mseconds + " MILLISECONDS on this machine ...");
			GigaflopEstimator.durations_map.put(workid, dur);

			// Update baseline if neccessary, should not happen but hey ...
			// doesn't hurt
			if (dur < GigaflopEstimator.baseline_duration_mseconds) {
				GigaflopEstimator.baseline_duration_mseconds = dur;
			}

			final double stretch_factor = dur / GigaflopEstimator.baseline_duration_mseconds;
			GigaflopEstimator.stretch_factor_map.put(workid, stretch_factor);

		} catch (final ParseException e) {
			// pass
		}
	}

	public static void measure_baseline() {
		if (!GigaflopEstimator.has_already_measured_baseline() && (!GigaflopEstimator.failed || (GigaflopEstimator.last_failed < (System.currentTimeMillis() - (10 * 1000))))) {
			try {
				GigaflopEstimator.baseline_duration_mseconds = GigaflopEstimator.measure_source(GigaflopEstimator.simple_source, true);
				Logger.logInfoMessage("We measured, that the simplest program executed in " + GigaflopEstimator.baseline_duration_mseconds
						+ " MILLISECONDS on this machine ...");
			} catch (final ParseException e) {
				GigaflopEstimator.failed = true;
				GigaflopEstimator.last_failed = System.currentTimeMillis();
				GigaflopEstimator.baseline_duration_mseconds = 0;
				Logger.logErrorMessage(
						"could not evaluate the simple program in the gigaflop estimator! Retrying soon ...");
			}
		}
	}

	private static double measure_source(final byte[] src, final boolean debug) throws ParseException {
		double dur = 0;

		long startTime = System.currentTimeMillis();
		Executioner.executeProofOfWork(src, GigaflopEstimator.personalizedIntStreamPseudo(Genesis.CREATOR_PUBLIC_KEY, 12345678, 87654321),
				Constants.least_possible_target, Constants.least_possible_target);
		long estimatedTime = System.currentTimeMillis() - startTime;
		if (debug) {
			Logger.logInfoMessage("First measurement: " + estimatedTime + " MILLISECONDS on this machine ...");
		}

		// Be more precise on fast systems, if it took less than 0.1 second,
		// repeat the measurement to fill that 0.1s
		if (estimatedTime < 100) {
			int rept = GigaflopEstimator.get_repetitions(estimatedTime);
			startTime = System.currentTimeMillis();
			for (int i = 1; i <= rept; ++i) {
				Executioner.executeProofOfWork(src,
						GigaflopEstimator.personalizedIntStreamPseudo(Genesis.CREATOR_PUBLIC_KEY, 12345678 + i, 87654321 + i),
						Constants.least_possible_target, Constants.least_possible_target);
			}
			estimatedTime = (System.currentTimeMillis() - startTime);
			if (debug) {
				Logger.logInfoMessage("Second measurement (" + rept + " repetitions): " + estimatedTime
						+ " MILLISECONDS on this machine ...");
			}

			// And do it once again, be more precise
			if (estimatedTime < 100) {
				if (rept > 0) {
					rept = rept * GigaflopEstimator.get_repetitions(estimatedTime);
				} else {
					rept = 50 * GigaflopEstimator.get_repetitions(estimatedTime);
				}
				startTime = System.currentTimeMillis();
				for (int i = 1; i <= rept; ++i) {
					Executioner.executeProofOfWork(src,
							GigaflopEstimator.personalizedIntStreamPseudo(Genesis.CREATOR_PUBLIC_KEY, 12345678 + i, 87654321 + i),
							Constants.least_possible_target, Constants.least_possible_target);
				}
				estimatedTime = (System.currentTimeMillis() - startTime);
				if (debug) {
					Logger.logInfoMessage("Third measurement (" + rept + " repetitions): " + estimatedTime
							+ " MILLISECONDS on this machine ...");
				}

				dur = (estimatedTime * 1.0) / rept;
			} else {
				dur = (estimatedTime * 1.0) / rept;
			}
		} else {
			dur = (estimatedTime);
		}
		return dur;
	}

	public static int[] personalizedIntStreamPseudo(final byte[] publicKey, final long blockId, final long workId) {
		final int[] stream = new int[12];
		GigaflopEstimator.dig.reset();
		GigaflopEstimator.dig.update(publicKey); // do public key instead of multiplicator! Just
		// to have the same amount of "hashing
		// overhead".
		GigaflopEstimator.dig.update(publicKey);

		final byte[] b1 = new byte[16];
		for (int i = 0; i < 8; ++i) {
			b1[i] = (byte) (workId >> ((8 - i - 1) << 3));
		}

		for (int i = 0; i < 8; ++i) {
			b1[i + 8] = (byte) (blockId >> ((8 - i - 1) << 3));
		}

		GigaflopEstimator.dig.update(b1);

		byte[] digest = GigaflopEstimator.dig.digest();
		int ln = digest.length;
		if (ln == 0) {
			digest = new byte[4];
			digest[0] = 0x01;
			digest[1] = 0x01;
			digest[2] = 0x01;
			digest[3] = 0x01;
			ln = 4;
		}
		for (int i = 0; i < 12; ++i) {
			int got = GigaflopEstimator.toInt(digest, (i * 4) % ln);
			if (i > 4) {
				got = got ^ stream[i - 3];
			}
			stream[i] = got;

		}
		return stream;

	}

	public static void purge_by_id(final Long workId) {
		GigaflopEstimator.durations_map.remove(workId);
		GigaflopEstimator.stretch_factor_map.remove(workId);
	}
	public static double round(double value, final int places) {
		if (places < 0) {
			throw new IllegalArgumentException();
		}

		final long factor = (long) Math.pow(10, places);
		value = value * factor;
		final long tmp = Math.round(value);
		return (double) tmp / factor;
	}
	public static int toInt(final byte[] bytes, final int offset) {
		int ret = 0;
		for (int i = 0; (i < 4) && ((i + offset) < bytes.length); i++) {
			ret <<= 8;
			ret |= bytes[i + offset] & 0xFF;
		}
		return ret;
	}

}

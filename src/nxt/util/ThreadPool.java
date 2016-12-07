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

package nxt.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import nxt.Nxt;

public final class ThreadPool {

	private static volatile ScheduledExecutorService scheduledThreadPool;
	private static Map<Runnable, Long> backgroundJobs = new HashMap<>();
	private static List<Runnable> beforeStartJobs = new ArrayList<>();
	private static List<Runnable> lastBeforeStartJobs = new ArrayList<>();
	private static List<Runnable> afterStartJobs = new ArrayList<>();

	public static synchronized void runAfterStart(final Runnable runnable) {
		ThreadPool.afterStartJobs.add(runnable);
	}

	private static void runAll(final List<Runnable> jobs) {
		final List<Thread> threads = new ArrayList<>();
		final StringBuffer errors = new StringBuffer();
		for (final Runnable runnable : jobs) {
			final Thread thread = new Thread() {
				@Override
				public void run() {
					try {
						runnable.run();
					} catch (final Throwable t) {
						errors.append(t.getMessage()).append('\n');
						throw t;
					}
				}
			};
			thread.setDaemon(true);
			thread.start();
			threads.add(thread);
		}
		for (final Thread thread : threads) {
			try {
				thread.join();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		if (errors.length() > 0) {
			throw new RuntimeException("Errors running startup tasks:\n" + errors.toString());
		}
	}

	public static synchronized void runBeforeStart(final Runnable runnable, final boolean runLast) {
		if (ThreadPool.scheduledThreadPool != null) {
			throw new IllegalStateException("Executor service already started");
		}
		if (runLast) {
			ThreadPool.lastBeforeStartJobs.add(runnable);
		} else {
			ThreadPool.beforeStartJobs.add(runnable);
		}
	}

	public static synchronized void scheduleThread(final String name, final Runnable runnable, final int delay) {
		ThreadPool.scheduleThread(name, runnable, delay, TimeUnit.SECONDS);
	}

	public static synchronized void scheduleThread(final String name, final Runnable runnable, final int delay,
			final TimeUnit timeUnit) {
		if (ThreadPool.scheduledThreadPool != null) {
			throw new IllegalStateException("Executor service already started, no new jobs accepted");
		}
		if (!Nxt.getBooleanProperty("nxt.disable" + name + "Thread")) {
			ThreadPool.backgroundJobs.put(runnable, timeUnit.toMillis(delay));
		} else {
			Logger.logMessage("Will not run " + name + " thread");
		}
	}

	public static void shutdown() {
		if (ThreadPool.scheduledThreadPool != null) {
			Logger.logShutdownMessage("Stopping background jobs...");
			ThreadPool.shutdownExecutor("scheduledThreadPool", ThreadPool.scheduledThreadPool, 10);
			ThreadPool.scheduledThreadPool = null;
			Logger.logShutdownMessage("...Done");
		}
	}

	public static void shutdownExecutor(final String name, final ExecutorService executor, final int timeout) {
		Logger.logShutdownMessage("shutting down " + name);
		executor.shutdown();
		try {
			executor.awaitTermination(timeout, TimeUnit.SECONDS);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (!executor.isTerminated()) {
			Logger.logShutdownMessage("some threads in " + name + " didn't terminate, forcing shutdown");
			executor.shutdownNow();
		}
	}

	public static synchronized void start(final int timeMultiplier) {
		if (ThreadPool.scheduledThreadPool != null) {
			throw new IllegalStateException("Executor service already started");
		}

		Logger.logDebugMessage("Running " + ThreadPool.beforeStartJobs.size() + " tasks...");
		ThreadPool.runAll(ThreadPool.beforeStartJobs);
		ThreadPool.beforeStartJobs = null;

		Logger.logDebugMessage("Running " + ThreadPool.lastBeforeStartJobs.size() + " final tasks...");
		ThreadPool.runAll(ThreadPool.lastBeforeStartJobs);
		ThreadPool.lastBeforeStartJobs = null;

		Logger.logDebugMessage("Starting " + ThreadPool.backgroundJobs.size() + " background jobs");
		ThreadPool.scheduledThreadPool = Executors.newScheduledThreadPool(ThreadPool.backgroundJobs.size());
		for (final Map.Entry<Runnable, Long> entry : ThreadPool.backgroundJobs.entrySet()) {
			ThreadPool.scheduledThreadPool.scheduleWithFixedDelay(entry.getKey(), 0,
					Math.max(entry.getValue() / timeMultiplier, 1), TimeUnit.MILLISECONDS);
		}
		ThreadPool.backgroundJobs = null;

		Logger.logDebugMessage("Starting " + ThreadPool.afterStartJobs.size() + " delayed tasks");
		final Thread thread = new Thread() {
			@Override
			public void run() {
				ThreadPool.runAll(ThreadPool.afterStartJobs);
				ThreadPool.afterStartJobs = null;
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

	private ThreadPool() {
	} // never

}

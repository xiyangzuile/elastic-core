package nxt.util;

import nxt.Nxt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ThreadPool {

    private static volatile ScheduledExecutorService scheduledThreadPool;
    private static Map<Runnable,Long> backgroundJobs = new HashMap<>();
    private static List<Runnable> beforeStartJobs = new ArrayList<>();
    private static List<Runnable> lastBeforeStartJobs = new ArrayList<>();
    private static List<Runnable> afterStartJobs = new ArrayList<>();

    private static final ExecutorService executorService = new ThreadPoolExecutor(0, Runtime.getRuntime().availableProcessors(),
            60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());


    public static synchronized void runBeforeStart(Runnable runnable, boolean runLast) {
        if (scheduledThreadPool != null) {
            throw new IllegalStateException("Executor service already started");
        }
        if (runLast) {
            lastBeforeStartJobs.add(runnable);
        } else {
            beforeStartJobs.add(runnable);
        }
    }

    public static synchronized void runAfterStart(Runnable runnable) {
        afterStartJobs.add(runnable);
    }

    public static synchronized void scheduleThread(String name, Runnable runnable, int delay) {
        scheduleThread(name, runnable, delay, TimeUnit.SECONDS);
    }

    public static synchronized void scheduleThread(String name, Runnable runnable, int delay, TimeUnit timeUnit) {
        if (scheduledThreadPool != null) {
            throw new IllegalStateException("Executor service already started, no new jobs accepted");
        }
        if (! Nxt.getBooleanProperty("nxt.disable" + name + "Thread")) {
            backgroundJobs.put(runnable, timeUnit.toMillis(delay));
        } else {
            Logger.logMessage("Will not run " + name + " thread");
        }
    }

    public static synchronized void start(int timeMultiplier) {
        if (scheduledThreadPool != null) {
            throw new IllegalStateException("Executor service already started");
        }

        Logger.logDebugMessage("Running " + beforeStartJobs.size() + " tasks...");
        runAll(beforeStartJobs);
        beforeStartJobs = null;

        Logger.logDebugMessage("Running " + lastBeforeStartJobs.size() + " final tasks...");
        runAll(lastBeforeStartJobs);
        lastBeforeStartJobs = null;

        Logger.logDebugMessage("Starting " + backgroundJobs.size() + " background jobs");
        scheduledThreadPool = Executors.newScheduledThreadPool(backgroundJobs.size());
        for (Map.Entry<Runnable,Long> entry : backgroundJobs.entrySet()) {
            scheduledThreadPool.scheduleWithFixedDelay(entry.getKey(), 0, Math.max(entry.getValue() / timeMultiplier, 1), TimeUnit.MILLISECONDS);
        }
        backgroundJobs = null;

        Logger.logDebugMessage("Starting " + afterStartJobs.size() + " delayed tasks");
        Thread thread = new Thread() {
            @Override
            public void run() {
                runAll(afterStartJobs);
                afterStartJobs = null;
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    public static <T> Future<T> submit(Callable<T> callable) {
        return executorService.submit(callable);
    }

    public static Future<?> submit(Runnable runnable) {
        return executorService.submit(runnable);
    }

    public static void shutdown() {
        if (scheduledThreadPool != null) {
	        Logger.logShutdownMessage("Stopping background jobs...");
            shutdownExecutor(scheduledThreadPool);
            shutdownExecutor(executorService);
            scheduledThreadPool = null;
        	Logger.logShutdownMessage("...Done");
        }
    }

    public static void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (! executor.isTerminated()) {
            Logger.logShutdownMessage("some threads didn't terminate, forcing shutdown");
            executor.shutdownNow();
        }
    }

    private static void runAll(List<Runnable> jobs) {
        List<Thread> threads = new ArrayList<>();
        final StringBuffer errors = new StringBuffer();
        for (final Runnable runnable : jobs) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        runnable.run();
                    } catch (Throwable t) {
                        errors.append(t.getMessage()).append('\n');
                        throw t;
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (errors.length() > 0) {
            throw new RuntimeException("Errors running startup tasks:\n" + errors.toString());
        }
    }

    private ThreadPool() {} //never

}

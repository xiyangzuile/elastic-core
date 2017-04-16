/******************************************************************************
 * Copyright © 2013-2016 The XEL Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.util;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * QueuedThreadPool creates threads to process requests until the maximum pool
 * size is reached. Additional requests are queued until a thread becomes
 * available. Threads that are idle for 60 seconds are terminated if the pool
 * size is greater than the core size.
 */
public class QueuedThreadPool extends ThreadPoolExecutor {

	/** Core pool size */
	private int coreSize;

	/** Maximum pool size */
	private int maxSize;

	/** Pending task queue */
	private final LinkedBlockingQueue<Runnable> pendingQueue = new LinkedBlockingQueue<>();

	/**
	 * Create the queued thread pool
	 *
	 * @param coreSize
	 *            Core pool size
	 * @param maxSize
	 *            Maximum pool size
	 */
	public QueuedThreadPool(final int coreSize, final int maxSize) {
		super(coreSize, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
		this.coreSize = coreSize;
		this.maxSize = maxSize;
	}

	/**
	 * Process task completion
	 *
	 * @param task
	 *            Runnable task
	 * @param exc
	 *            Thrown exception
	 */
	@Override
	protected void afterExecute(final Runnable task, final Throwable exc) {
		super.afterExecute(task, exc);
		final Runnable newTask = this.pendingQueue.poll();
		if (newTask != null) super.execute(newTask);
	}

	/**
	 * Execute a task
	 *
	 * @param task
	 *            Task
	 * @throws RejectedExecutionException
	 *             Unable to execute task
	 */
	@Override
	public void execute(final Runnable task) throws RejectedExecutionException {
		if (task == null) throw new NullPointerException("Null runnable passed to execute()");
		try {
			if (this.getActiveCount() >= this.maxSize) this.pendingQueue.put(task);
            else super.execute(task);
		} catch (final InterruptedException exc) {
			throw new RejectedExecutionException("Unable to queue task", exc);
		}
	}

	/**
	 * Return the core pool size
	 *
	 * @return Core pool size
	 */
	@Override
	public int getCorePoolSize() {
		return this.coreSize;
	}

	/**
	 * Return the maximum pool size
	 *
	 * @return Maximum pool size
	 */
	@Override
	public int getMaximumPoolSize() {
		return this.maxSize;
	}

	/**
	 * Set the core pool size
	 *
	 * @param coreSize
	 *            Core pool size
	 */
	@Override
	public void setCorePoolSize(final int coreSize) {
		super.setCorePoolSize(coreSize);
		this.coreSize = coreSize;
	}

	/**
	 * Set the maximum pool size
	 *
	 * @param maxSize
	 *            Maximum pool size
	 */
	@Override
	public void setMaximumPoolSize(final int maxSize) {
		this.maxSize = maxSize;
	}

	/**
	 * Submit a task for execution
	 *
	 * @param <T>
	 *            Result type
	 * @param callable
	 *            Callable task
	 * @return Future representing the task
	 * @throws RejectedExecutionException
	 *             Unable to execute task
	 */
	@Override
	public <T> Future<T> submit(final Callable<T> callable) throws RejectedExecutionException {
		if (callable == null) throw new NullPointerException("Null callable passed to submit()");
		final FutureTask<T> futureTask = new FutureTask<>(callable);
		this.execute(futureTask);
		return futureTask;
	}

	/**
	 * Submit a task for execution
	 *
	 * @param task
	 *            Runnable task
	 * @return Future representing the task
	 * @throws RejectedExecutionException
	 *             Unable to execute task
	 */
	@Override
	public Future<?> submit(final Runnable task) throws RejectedExecutionException {
		if (task == null) throw new NullPointerException("Null runnable passed to submit()");
		final FutureTask<Void> futureTask = new FutureTask<>(task, null);
		this.execute(futureTask);
		return futureTask;
	}

	/**
	 * Submit a task for execution
	 *
	 * @param <T>
	 *            Result type
	 * @param task
	 *            Runnable task
	 * @param result
	 *            Result returned when task completes
	 * @return Future representing the task result
	 * @throws RejectedExecutionException
	 *             Unable to execute task
	 */
	@Override
	public <T> Future<T> submit(final Runnable task, final T result) throws RejectedExecutionException {
		if (task == null) throw new NullPointerException("Null runnable passed to submit()");
		final FutureTask<T> futureTask = new FutureTask<>(task, result);
		this.execute(futureTask);
		return futureTask;
	}
}

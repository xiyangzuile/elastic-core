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

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>
 * A read or update lock allows shared access while a write lock enforces exclusive access.  Multiple
 * threads can hold the read lock but only one thread can hold the update or write lock.  If a thread
 * obtains a lock that it already holds, it must release the lock the same number of times that it
 * obtained the lock.
 * </p>
 * <ul>
 * <li>An attempt to obtain the read lock while another thread holds the write lock will cause
 * the thread to be suspended until the write lock is released.</li>
 * <li>An attempt to obtain the update lock while another thread holds the update or write lock
 * will cause the thread to be suspended until the blocking lock is released.  A thread
 * holding the update lock can subsequently obtain the write lock to gain exclusive access.
 * An attempt to obtain the update lock while holding either the read lock or the write lock
 * will result in an exception.</li>
 * <li>An attempt to obtain the write lock while another thread holds the read, update or write lock
 * will cause the thread to be suspended until the blocking lock is released.
 * An attempt to obtain the write lock while holding the read lock will result in an exception.</li>
 * </ul>
 */
public class ReadWriteUpdateLock {

	/**
	 * Lock interface
	 */
	public interface Lock {

		/**
		 * Check if the thread holds the lock
		 *
		 * @return                  TRUE if the thread holds the lock
		 */
		boolean hasLock();

		/**
		 * Obtain the lock
		 */
		void lock();

		/**
		 * Release the lock
		 */
		void unlock();
	}

	/**
	 * Lock counts
	 */
	private class LockCount {

		/** Read lock count */
		private int readCount;

		/** Update lock count */
		private int updateCount;

		/** Write lock count */
		private int writeCount;
	}

	/**
	 * Read lock
	 */
	private class ReadLock implements Lock {

		/**
		 * Check if the thread holds the lock
		 *
		 * @return                  TRUE if the thread holds the lock
		 */
		@Override
		public boolean hasLock() {
			return ReadWriteUpdateLock.this.lockCount.get().readCount != 0;
		}

		/**
		 * Obtain the lock
		 */
		@Override
		public void lock() {
			ReadWriteUpdateLock.this.sharedLock.readLock().lock();
			ReadWriteUpdateLock.this.lockCount.get().readCount++;
		}

		/**
		 * Release the lock
		 */
		@Override
		public void unlock() {
			ReadWriteUpdateLock.this.sharedLock.readLock().unlock();
			ReadWriteUpdateLock.this.lockCount.get().readCount--;
		}
	}

	/**
	 * Update lock
	 */
	private class UpdateLock implements Lock {

		/**
		 * Check if the thread holds the lock
		 *
		 * @return                  TRUE if the thread holds the lock
		 */
		@Override
		public boolean hasLock() {
			return ReadWriteUpdateLock.this.lockCount.get().updateCount != 0;
		}

		/**
		 * Obtain the lock
		 *
		 * Caller must not hold the read or write lock
		 */
		@Override
		public void lock() {
			final LockCount counts = ReadWriteUpdateLock.this.lockCount.get();
			if (counts.readCount != 0) {
				throw new IllegalStateException("Update lock cannot be obtained while holding the read lock");
			}
			if (counts.writeCount != 0) {
				throw new IllegalStateException("Update lock cannot be obtained while holding the write lock");
			}
			ReadWriteUpdateLock.this.mutexLock.lock();
			counts.updateCount++;
		}

		/**
		 * Release the lock
		 */
		@Override
		public void unlock() {
			ReadWriteUpdateLock.this.mutexLock.unlock();
			ReadWriteUpdateLock.this.lockCount.get().updateCount--;
		}
	}

	/**
	 * Write lock
	 */
	private class WriteLock implements Lock {

		/**
		 * Check if the thread holds the lock
		 *
		 * @return                  TRUE if the thread holds the lock
		 */
		@Override
		public boolean hasLock() {
			return ReadWriteUpdateLock.this.lockCount.get().writeCount != 0;
		}

		/**
		 * Obtain the lock
		 *
		 * Caller must not hold the read lock
		 */
		@Override
		public void lock() {
			final LockCount counts = ReadWriteUpdateLock.this.lockCount.get();
			if (counts.readCount != 0) {
				throw new IllegalStateException("Write lock cannot be obtained while holding the read lock");
			}
			boolean lockObtained = false;
			try {
				ReadWriteUpdateLock.this.mutexLock.lock();
				counts.updateCount++;
				lockObtained = true;
				ReadWriteUpdateLock.this.sharedLock.writeLock().lock();
				counts.writeCount++;
			} catch (final Exception exc) {
				if (lockObtained) {
					ReadWriteUpdateLock.this.mutexLock.unlock();
					counts.updateCount--;
				}
				throw exc;
			}
		}

		/**
		 * Release the lock
		 */
		@Override
		public void unlock() {
			final LockCount counts = ReadWriteUpdateLock.this.lockCount.get();
			ReadWriteUpdateLock.this.sharedLock.writeLock().unlock();
			counts.writeCount--;
			ReadWriteUpdateLock.this.mutexLock.unlock();
			counts.updateCount--;
		}
	}

	/** Lock shared by the read and write locks */
	private final ReentrantReadWriteLock sharedLock = new ReentrantReadWriteLock();

	/** Lock used by the update lock */
	private final ReentrantLock mutexLock = new ReentrantLock();

	/** Lock counts */
	private final ThreadLocal<LockCount> lockCount = ThreadLocal.withInitial(LockCount::new);

	/** Read lock */
	private final ReadLock readLock = new ReadLock();

	/** Update lock */
	private final UpdateLock updateLock = new UpdateLock();

	/** Write lock */
	private final WriteLock writeLock = new WriteLock();

	/**
	 * Return the read lock
	 *
	 * @return                      Read lock
	 */
	public Lock readLock() {
		return this.readLock;
	}

	/**
	 * Return the update lock
	 *
	 * @return                      Update lock
	 */
	public Lock updateLock() {
		return this.updateLock;
	}

	/**
	 * Return the write lock
	 *
	 * @return                      Write lock
	 */
	public Lock writeLock() {
		return this.writeLock;
	}
}

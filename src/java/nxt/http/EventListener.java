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

package nxt.http;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import nxt.AccountLedger;
import nxt.AccountLedger.LedgerEntry;
import nxt.Block;
import nxt.BlockchainProcessor;
import nxt.Db;
import nxt.Nxt;
import nxt.Transaction;
import nxt.TransactionProcessor;
import nxt.db.TransactionalDb;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Logger;

/**
 * EventListener listens for peer, block, transaction and account ledger events as
 * specified by the EventRegister API.  Events are held until
 * an EventWait API request is received.  All pending events
 * are then returned to the application.
 *
 * Event registrations are discarded if an EventWait API request
 * has not been received within nxt.apiEventTimeout seconds.
 *
 * The maximum number of event users is specified by nxt.apiMaxEventUsers.
 */
class EventListener implements Runnable, AsyncListener, TransactionalDb.TransactionCallback {

	/**
	 * Event exception
	 */
	static class EventListenerException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = -702690051067893984L;

		/**
		 * Create an event exception with a message
		 *
		 * @param   message         Exception message
		 */
		public EventListenerException(final String message) {
			super(message);
		}

		/**
		 * Create an event exception with a message and a cause
		 *
		 * @param   message         Exception message
		 * @param   cause           Exception cause
		 */
		public EventListenerException(final String message, final Exception cause) {
			super(message, cause);
		}
	}

	/**
	 * Event registration
	 */
	static class EventRegistration {

		/** Nxt listener event */
		private final Enum<? extends Enum> event;

		/** Account identifier */
		private final long accountId;

		/**
		 * Create the event registration
		 *
		 * @param   event           Nxt listener event
		 * @param   accountId       Account identifier
		 */
		EventRegistration(final Enum<? extends Enum> event, final long accountId) {
			this.event = event;
			this.accountId = accountId;
		}

		/**
		 * Return the account identifier
		 *
		 * @return                  Account identifier
		 */
		public long getAccountId() {
			return this.accountId;
		}

		/**
		 * Return the Nxt listener event
		 *
		 * @return                  Nxt listener event
		 */
		public Enum<? extends Enum> getEvent() {
			return this.event;
		}
	}

	/**
	 * Nxt event listener
	 */
	private class NxtEventListener {

		/**
		 * Blockchain processor event handler
		 */
		private class BlockEventHandler extends NxtEventHandler implements Listener<Block> {

			/**
			 * Create the blockchain processor event handler
			 *
			 * @param   eventRegistration   Event registration
			 */
			public BlockEventHandler(final EventRegistration eventRegistration) {
				super(eventRegistration);
			}

			/**
			 * Add the Nxt listener for this event
			 */
			@Override
			public void addListener() {
				EventListener.blockchainProcessor.addListener(this, (BlockchainProcessor.Event)this.event);
			}

			/**
			 * Event notification
			 *
			 * @param   block       Block
			 */
			@Override
			public void notify(final Block block) {
				this.dispatch(new PendingEvent("Block." + this.event.name(), block.getStringId()));
			}

			/**
			 * Remove the Nxt listener for this event
			 */
			@Override
			public void removeListener() {
				EventListener.blockchainProcessor.removeListener(this, (BlockchainProcessor.Event)this.event);
			}
		}

		/**
		 * Account ledger event handler
		 */
		private class LedgerEventHandler extends NxtEventHandler implements Listener<LedgerEntry> {

			/**
			 * Create the account ledger event handler
			 *
			 * @param   eventRegistration   Event registration
			 */
			public LedgerEventHandler(final EventRegistration eventRegistration) {
				super(eventRegistration);
			}

			/**
			 * Add the Nxt listener for this event
			 */
			@Override
			public void addListener() {
				AccountLedger.addListener(this, (AccountLedger.Event)this.event);
			}

			/**
			 * Event notification
			 *
			 * @param   entry       Ledger entry
			 */
			@Override
			public void notify(final LedgerEntry entry) {
				if ((entry.getAccountId() == this.accountId) || (this.accountId == 0)) {
					this.dispatch(new PendingEvent(String.format("Ledger.%s.%s",
							this.event.name(), Convert.rsAccount(entry.getAccountId())),
							Long.toUnsignedString(entry.getLedgerId())));
				}
			}

			/**
			 * Remove the Nxt listener for this event
			 */
			@Override
			public void removeListener() {
				AccountLedger.removeListener(this, (AccountLedger.Event)this.event);
			}
		}

		/**
		 * Nxt listener event handler
		 */
		private abstract class NxtEventHandler {

			/** Owning event listener */
			protected final EventListener owner;

			/** Account identifier */
			protected final long accountId;

			/** Nxt listener event */
			protected final Enum<? extends Enum> event;

			/**
			 * Create the Nxt event handler
			 *
			 * @param   eventRegistration   Event registration
			 */
			public NxtEventHandler(final EventRegistration eventRegistration) {
				this.owner = EventListener.this;
				this.accountId = eventRegistration.getAccountId();
				this.event = eventRegistration.getEvent();
			}

			/**
			 * Add the Nxt listener for this event
			 */
			public abstract void addListener();

			/**
			 * Dispatch the event
			 */
			protected void dispatch(final PendingEvent pendingEvent) {
				EventListener.this.lock.lock();
				try {
					if (this.waitTransaction() && Db.db.isInTransaction()) {
						pendingEvent.setThread(Thread.currentThread());
						EventListener.this.dbEvents.add(pendingEvent);
						Db.db.registerCallback(this.owner);
					} else {
						EventListener.this.pendingEvents.add(pendingEvent);
						if (!EventListener.this.pendingWaits.isEmpty() && !EventListener.this.dispatched) {
							EventListener.this.dispatched = true;
							EventListener.threadPool.submit(this.owner);
						}
					}
				} finally {
					EventListener.this.lock.unlock();
				}
			}

			/**
			 * Check if two events listeners are equal
			 *
			 * @param   obj             Comparison listener
			 * @return                  TRUE if the listeners are equal
			 */
			@Override
			public boolean equals(final Object obj) {
				return ((obj != null) && (obj instanceof NxtEventHandler) &&
						(this.owner == ((NxtEventHandler)obj).owner) &&
						(this.accountId == ((NxtEventHandler)obj).accountId) &&
						(this.event == ((NxtEventHandler)obj).event));
			}

			/**
			 * Return the account identifier
			 *
			 * @return                  Account identifier
			 */
			public long getAccountId() {
				return this.accountId;
			}

			/**
			 * Return the Nxt event
			 *
			 * @return                  Nxt event
			 */
			public Enum<? extends Enum> getEvent() {
				return this.event;
			}

			/**
			 * Return the hash code for this event listener
			 *
			 * @return                  Hash code
			 */
			@Override
			public int hashCode() {
				return this.event.hashCode();
			}

			/**
			 * Remove the Nxt listener for this event
			 */
			public abstract void removeListener();

			/**
			 * Check if need to wait for end of transaction
			 *
			 * @return                  TRUE if need to wait for transaction to commit/rollback
			 */
			protected boolean waitTransaction() {
				return true;
			}
		}

		/**
		 * Peer event handler
		 */
		private class PeerEventHandler extends NxtEventHandler implements Listener<Peer> {

			/**
			 * Create the peer event handler
			 *
			 * @param   eventRegistration   Event registration
			 */
			public PeerEventHandler(final EventRegistration eventRegistration) {
				super(eventRegistration);
			}

			/**
			 * Add the Nxt listener for this event
			 */
			@Override
			public void addListener() {
				Peers.addListener(this, (Peers.Event)this.event);
			}

			/**
			 * Event notification
			 *
			 * @param   peer        Peer
			 */
			@Override
			public void notify(final Peer peer) {
				this.dispatch(new PendingEvent("Peer." + this.event.name(), peer.getHost()));
			}

			/**
			 * Remove the Nxt listener for this event
			 */
			@Override
			public void removeListener() {
				Peers.removeListener(this, (Peers.Event)this.event);
			}

			/**
			 * Check if need to wait for end of transaction
			 *
			 * @return                  TRUE if need to wait for transaction to commit/rollback
			 */
			@Override
			protected boolean waitTransaction() {
				return false;
			}
		}

		/**
		 * Transaction processor event handler
		 */
		private class TransactionEventHandler extends NxtEventHandler implements Listener<List<? extends Transaction>> {

			/**
			 * Create the transaction processor event handler
			 *
			 * @param   eventRegistration   Event registration
			 */
			public TransactionEventHandler(final EventRegistration eventRegistration) {
				super(eventRegistration);
			}

			/**
			 * Add the Nxt listener for this event
			 */
			@Override
			public void addListener() {
				EventListener.transactionProcessor.addListener(this, (TransactionProcessor.Event)this.event);
			}

			/**
			 * Event notification
			 *
			 * @param   txList      Transaction list
			 */
			@Override
			public void notify(final List<? extends Transaction> txList) {
				final List<String> idList = new ArrayList<>();
				txList.forEach((tx) -> idList.add(tx.getStringId()));
				this.dispatch(new PendingEvent("Transaction." + this.event.name(), idList));
			}

			/**
			 * Remove the Nxt listener for this event
			 */
			@Override
			public void removeListener() {
				EventListener.transactionProcessor.removeListener(this, (TransactionProcessor.Event)this.event);
			}
		}

		/** Event handler */
		private final NxtEventHandler eventHandler;

		/**
		 * Create the Nxt event listener
		 *
		 * @param   eventRegistration           Event registration
		 * @throws  EventListenerException      Invalid event
		 */
		public NxtEventListener(final EventRegistration eventRegistration) throws EventListenerException {
			final Enum<? extends Enum> event = eventRegistration.getEvent();
			if (event instanceof Peers.Event) {
				this.eventHandler = new PeerEventHandler(eventRegistration);
			} else if (event instanceof BlockchainProcessor.Event) {
				this.eventHandler = new BlockEventHandler(eventRegistration);
			} else if (event instanceof TransactionProcessor.Event) {
				this.eventHandler = new TransactionEventHandler(eventRegistration);
			} else if (event instanceof AccountLedger.Event) {
				this.eventHandler = new LedgerEventHandler(eventRegistration);
			} else {
				throw new EventListenerException("Unsupported listener event");
			}
		}

		/**
		 * Add the Nxt listener for this event
		 */
		public void addListener() {
			this.eventHandler.addListener();
		}

		/**
		 * Check if two Nxt events listeners are equal
		 *
		 * @param   obj             Comparison listener
		 * @return                  TRUE if the listeners are equal
		 */
		@Override
		public boolean equals(final Object obj) {
			return ((obj != null) && (obj instanceof NxtEventListener) &&
					this.eventHandler.equals(((NxtEventListener)obj).eventHandler));
		}

		/**
		 * Return the account identifier
		 *
		 * @return                  Account identifier
		 */
		public long getAccountId() {
			return this.eventHandler.getAccountId();
		}

		/**
		 * Return the Nxt event
		 *
		 * @return                  Nxt event
		 */
		public Enum<? extends Enum> getEvent() {
			return this.eventHandler.getEvent();
		}

		/**
		 * Return the hash code for this Nxt event listener
		 *
		 * @return                  Hash code
		 */
		@Override
		public int hashCode() {
			return this.eventHandler.hashCode();
		}

		/**
		 * Remove the Nxt listener for this event
		 */
		public void removeListener() {
			this.eventHandler.removeListener();
		}
	}

	/**
	 * Pending event
	 */
	static class PendingEvent {

		/** Event name */
		private final String name;

		/** Event identifier */
		private final String id;

		/** Event identifier list */
		private final List<String> idList;

		/** Database thread */
		private Thread thread;

		/**
		 * Create a pending event
		 *
		 * @param   name            Event name
		 * @param   idList          Event identifier list
		 */
		public PendingEvent(final String name, final List<String> idList) {
			this.name = name;
			this.idList = idList;
			this.id = null;
		}

		/**
		 * Create a pending event
		 *
		 * @param   name            Event name
		 * @param   id              Event identifier
		 */
		public PendingEvent(final String name, final String id) {
			this.name = name;
			this.id = id;
			this.idList = null;
		}

		/**
		 * Return the event identifier
		 *
		 * @return                  Event identifier
		 */
		public String getId() {
			return this.id;
		}

		/**
		 * Return the event identifier list
		 *
		 * @return                  Event identifier list
		 */
		public List<String> getIdList() {
			return this.idList;
		}

		/**
		 * Return the event name
		 *
		 * @return                  Event name
		 */
		public String getName() {
			return this.name;
		}

		/**
		 * Return the database thread
		 *
		 * @return                  Database thread
		 */
		public Thread getThread() {
			return this.thread;
		}

		/**
		 * Check if the identifier is a list
		 *
		 * @return                  TRUE if the identifier is a list
		 */
		public boolean isList() {
			return (this.idList != null);
		}

		/**
		 * Set the database thread
		 *
		 * @param   thread          Database thread
		 */
		public void setThread(final Thread thread) {
			this.thread = thread;
		}
	}

	/** Maximum event users */
	static final int maxEventUsers = Nxt.getIntProperty("nxt.apiMaxEventUsers");

	/** Event registration timeout (seconds) */
	static final int eventTimeout = Math.max(Nxt.getIntProperty("nxt.apiEventTimeout"), 15);
	/** Blockchain processor */
	static final BlockchainProcessor blockchainProcessor = Nxt.getBlockchainProcessor();

	/** Transaction processor */
	static final TransactionProcessor transactionProcessor = Nxt.getTransactionProcessor();

	/** Active event users */
	static final Map<String, EventListener> eventListeners = new ConcurrentHashMap<>();
	/** Thread to clean up inactive event registrations */
	private static final Timer eventTimer = new Timer();

	static {
		EventListener.eventTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				final long oldestTime = System.currentTimeMillis() - (EventListener.eventTimeout*1000);
				EventListener.eventListeners.values().forEach(listener -> {
					if (listener.getTimestamp() < oldestTime) {
						listener.deactivateListener();
					}
				});
			}
		}, (EventListener.eventTimeout*1000)/2, (EventListener.eventTimeout*1000)/2);
	}
	/** Thread pool for asynchronous completions */
	private static final ExecutorService threadPool = Executors.newCachedThreadPool();

	/** Peer events - update API comments for EventRegister and EventWait if changed */
	static final List<Peers.Event> peerEvents = new ArrayList<>();
	static {
		EventListener.peerEvents.add(Peers.Event.ADD_INBOUND);
		EventListener.peerEvents.add(Peers.Event.ADDED_ACTIVE_PEER);
		EventListener.peerEvents.add(Peers.Event.BLACKLIST);
		EventListener.peerEvents.add(Peers.Event.CHANGED_ACTIVE_PEER);
		EventListener.peerEvents.add(Peers.Event.DEACTIVATE);
		EventListener.peerEvents.add(Peers.Event.NEW_PEER);
		EventListener.peerEvents.add(Peers.Event.REMOVE);
		EventListener.peerEvents.add(Peers.Event.REMOVE_INBOUND);
		EventListener.peerEvents.add(Peers.Event.UNBLACKLIST);
	}

	/** Block events - update API comments for EventRegister and EventWait if changed */
	static final List<BlockchainProcessor.Event> blockEvents = new ArrayList<>();
	static {
		EventListener.blockEvents.add(BlockchainProcessor.Event.BLOCK_GENERATED);
		EventListener.blockEvents.add(BlockchainProcessor.Event.BLOCK_POPPED);
		EventListener.blockEvents.add(BlockchainProcessor.Event.BLOCK_PUSHED);
	}

	/** Transaction events - update API comments for EventRegister and EventWait if changed */
	static final List<TransactionProcessor.Event> txEvents = new ArrayList<>();

	static {
		EventListener.txEvents.add(TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);
		EventListener.txEvents.add(TransactionProcessor.Event.ADDED_UNCONFIRMED_TRANSACTIONS);
		EventListener.txEvents.add(TransactionProcessor.Event.REJECT_PHASED_TRANSACTION);
		EventListener.txEvents.add(TransactionProcessor.Event.RELEASE_PHASED_TRANSACTION);
		EventListener.txEvents.add(TransactionProcessor.Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
	}

	/** Account ledger events - update API comments for EventRegister and EventWait if changed */
	static final List<AccountLedger.Event> ledgerEvents = new ArrayList<>();

	static {
		EventListener.ledgerEvents.add(AccountLedger.Event.ADD_ENTRY);
	}

	/** Application IP address */
	private final String address;

	/** Activity timestamp */
	private long timestamp;

	/** Activity lock */
	private final ReentrantLock lock = new ReentrantLock();

	/** Event listener has been deactivated */
	private volatile boolean deactivated;

	/** Event wait aborted */
	private boolean aborted;

	/** Event thread dispatched */
	private boolean dispatched;

	/** Nxt event listeners */
	private final List<NxtEventListener> nxtEventListeners = new ArrayList<>();

	/** Pending events */
	private final List<PendingEvent> pendingEvents = new ArrayList<>();

	/** Database events */
	private final List<PendingEvent> dbEvents = new ArrayList<>();

	/** Pending waits */
	private final List<AsyncContext> pendingWaits = new ArrayList<>();

	/**
	 * Create an event listener
	 *
	 * @param   address             Application IP address
	 */
	EventListener(final String address) {
		this.address = address;
	}

	/**
	 * Activate the event listener
	 *
	 * Nxt event listeners will be added for the specified events
	 *
	 * @param   eventRegistrations      List of Nxt event registrations
	 * @throws  EventListenerException  Unable to activate event listeners
	 */
	void activateListener(final List<EventRegistration> eventRegistrations) throws EventListenerException {
		if (this.deactivated) {
			throw new EventListenerException("Event listener deactivated");
		}
		if ((EventListener.eventListeners.size() >= EventListener.maxEventUsers) && (EventListener.eventListeners.get(this.address) == null)) {
			throw new EventListenerException(String.format("Too many API event users: Maximum %d", EventListener.maxEventUsers));
		}
		//
		// Start listening for events
		//
		this.addEvents(eventRegistrations);
		//
		// Add this event listener to the active list
		//
		final EventListener oldListener = EventListener.eventListeners.put(this.address, this);
		if (oldListener != null) {
			oldListener.deactivateListener();
		}
		Logger.logDebugMessage(String.format("Event listener activated for %s", this.address));
	}

	/**
	 * Add events to the event list
	 *
	 * @param   eventRegistrations      Nxt event registrations
	 * @throws  EventListenerException  Invalid Nxt event
	 */
	void addEvents(final List<EventRegistration> eventRegistrations) throws EventListenerException {
		this.lock.lock();
		try {
			if (this.deactivated) {
				return;
			}
			//
			// A listener with account identifier 0 accepts events for all accounts.
			// This listener supersedes  listeners for a single account.
			//
			for (final EventRegistration event : eventRegistrations) {
				boolean addListener = true;
				final Iterator<NxtEventListener> it = this.nxtEventListeners.iterator();
				while (it.hasNext()) {
					final NxtEventListener listener = it.next();
					if (listener.getEvent() == event.getEvent()) {
						final long accountId = listener.getAccountId();
						if ((accountId == event.getAccountId()) || (accountId == 0)) {
							addListener = false;
							break;
						}
						if (event.getAccountId() == 0) {
							listener.removeListener();
							it.remove();
						}
					}
				}
				if (addListener) {
					final NxtEventListener listener = new NxtEventListener(event);
					listener.addListener();
					this.nxtEventListeners.add(listener);
				}
			}
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Transaction has been committed
	 *
	 * Dispatch the pending events for this database transaction
	 */
	@Override
	public void commit() {
		final Thread thread = Thread.currentThread();
		this.lock.lock();
		try {
			final Iterator<PendingEvent> it = this.dbEvents.iterator();
			while (it.hasNext()) {
				final PendingEvent pendingEvent = it.next();
				if (pendingEvent.getThread() == thread) {
					it.remove();
					this.pendingEvents.add(pendingEvent);
					if (!this.pendingWaits.isEmpty() && !this.dispatched) {
						this.dispatched = true;
						EventListener.threadPool.submit(EventListener.this);
					}
				}
			}
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Deactivate the event listener
	 */
	void deactivateListener() {
		this.lock.lock();
		try {
			if (this.deactivated) {
				return;
			}
			this.deactivated = true;
			//
			// Cancel all pending wait requests
			//
			if (!this.pendingWaits.isEmpty() && !this.dispatched) {
				this.dispatched = true;
				EventListener.threadPool.submit(this);
			}
			//
			// Remove this event listener from the active list
			//
			EventListener.eventListeners.remove(this.address);
			//
			// Stop listening for events
			//
			this.nxtEventListeners.forEach(NxtEventListener::removeListener);
		} finally {
			this.lock.unlock();
		}
		Logger.logDebugMessage(String.format("Event listener deactivated for %s", this.address));
	}

	/**
	 * Wait for an event
	 *
	 * @param   req                     HTTP request
	 * @param   timeout                 Wait timeout in seconds
	 * @return                          List of pending events or null if wait incomplete
	 * @throws  EventListenerException  Unable to wait for an event
	 */
	List<PendingEvent> eventWait(final HttpServletRequest req, final long timeout) throws EventListenerException {
		List<PendingEvent> events = null;
		this.lock.lock();
		try {
			if (this.deactivated) {
				throw new EventListenerException("Event listener deactivated");
			}
			if (!this.pendingWaits.isEmpty()) {
				//
				// We want only one waiter at a time.  This can happen if the
				// application issues an event wait while it already has an event
				// wait outstanding.  In this case, we will cancel the current wait
				// and replace it with the new wait.
				//
				this.aborted = true;
				if (!this.dispatched) {
					this.dispatched = true;
					EventListener.threadPool.submit(this);
				}
				final AsyncContext context = req.startAsync();
				context.addListener(this);
				context.setTimeout(timeout*1000);
				this.pendingWaits.add(context);
			} else if (!this.pendingEvents.isEmpty()) {
				//
				// Return immediately if we have a pending event
				//
				events = new ArrayList<>();
				events.addAll(this.pendingEvents);
				this.pendingEvents.clear();
				this.timestamp = System.currentTimeMillis();
			} else {
				//
				// Wait for an event
				//
				this.aborted = false;
				final AsyncContext context = req.startAsync();
				context.addListener(this);
				context.setTimeout(timeout*1000);
				this.pendingWaits.add(context);
				this.timestamp = System.currentTimeMillis();
			}
		} finally {
			this.lock.unlock();
		}
		return events;
	}

	/**
	 * Get the activity timestamp
	 *
	 * @return                      Activity timestamp (milliseconds)
	 */
	long getTimestamp() {
		long t;
		this.lock.lock();
		t = this.timestamp;
		this.lock.unlock();
		return t;
	}

	/**
	 * Async operation completed (AsyncListener interface)
	 *
	 * @param   event               Async event
	 */
	@Override
	public void onComplete(final AsyncEvent event) {
	}

	/**
	 * Async error detected (AsyncListener interface)
	 *
	 * @param   event               Async event
	 */
	@Override
	public void onError(final AsyncEvent event) {
		final AsyncContext context = event.getAsyncContext();
		this.lock.lock();
		try {
			this.pendingWaits.remove(context);
			context.complete();
			this.timestamp = System.currentTimeMillis();
			Logger.logDebugMessage("Error detected during event wait for "+this.address, event.getThrowable());
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Async operation started (AsyncListener interface)
	 *
	 * @param   event               Async event
	 */
	@Override
	public void onStartAsync(final AsyncEvent event) {
	}

	/**
	 * Async operation timeout (AsyncListener interface)
	 *
	 * @param   event               Async event
	 */
	@Override
	public void onTimeout(final AsyncEvent event) {
		final AsyncContext context = event.getAsyncContext();
		this.lock.lock();
		try {
			this.pendingWaits.remove(context);
			final JSONObject response = new JSONObject();
			response.put("events", new JSONArray());
			response.put("requestProcessingTime", System.currentTimeMillis()-this.timestamp);
			try (Writer writer = context.getResponse().getWriter()) {
				response.writeJSONString(writer);
			} catch (final IOException exc) {
				Logger.logDebugMessage(String.format("Unable to return API response to %s: %s",
						this.address, exc.toString()));
			}
			context.complete();
			this.timestamp = System.currentTimeMillis();
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Remove events from the event list
	 *
	 * @param   eventRegistrations      Nxt event registrations
	 */
	void removeEvents(final List<EventRegistration> eventRegistrations) {
		this.lock.lock();
		try {
			if (this.deactivated) {
				return;
			}
			//
			// Specifying an account identifier of 0 results in removing all
			// listeners for the specified event.  Otherwise, only the listener
			// for the specified account is removed.
			//
			for (final EventRegistration event : eventRegistrations) {
				final Iterator<NxtEventListener> it = this.nxtEventListeners.iterator();
				while (it.hasNext()) {
					final NxtEventListener listener = it.next();
					if ((listener.getEvent() == event.getEvent()) &&
							((listener.getAccountId() == event.getAccountId()) || (event.getAccountId() == 0))) {
						listener.removeListener();
						it.remove();
					}
				}
			}
			//
			// Deactivate the listeners if there are no events remaining
			//
			if (this.nxtEventListeners.isEmpty()) {
				this.deactivateListener();
			}
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Transaction has been rolled back
	 *
	 * Discard the pending events for this database transaction
	 */
	@Override
	public void rollback() {
		final Thread thread = Thread.currentThread();
		this.lock.lock();
		try {
			final Iterator<PendingEvent> it = this.dbEvents.iterator();
			while (it.hasNext()) {
				if (it.next().getThread() == thread) {
					it.remove();
				}
			}
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Complete the current event wait (Runnable interface)
	 */
	@Override
	public void run() {
		this.lock.lock();
		try {
			this.dispatched = false;
			while (!this.pendingWaits.isEmpty() && (this.aborted || this.deactivated || !this.pendingEvents.isEmpty())) {
				final AsyncContext context = this.pendingWaits.remove(0);
				final List<PendingEvent> events = new ArrayList<>();
				if (!this.aborted && !this.deactivated) {
					events.addAll(this.pendingEvents);
					this.pendingEvents.clear();
				}
				final HttpServletResponse resp = (HttpServletResponse)context.getResponse();
				final JSONObject response = EventWait.formatResponse(events);
				response.put("requestProcessingTime", System.currentTimeMillis()-this.timestamp);
				try (Writer writer = resp.getWriter()) {
					response.writeJSONString(writer);
				} catch (final IOException exc) {
					Logger.logDebugMessage(String.format("Unable to return API response to %s: %s",
							this.address, exc.toString()));
				}
				context.complete();
				this.aborted = false;
				this.timestamp = System.currentTimeMillis();
			}
		} finally {
			this.lock.unlock();
		}
	}
}

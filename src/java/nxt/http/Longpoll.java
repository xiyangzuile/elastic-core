package nxt.http;

import static java.lang.Integer.parseInt;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.BlockchainProcessorImpl;
import nxt.Generator;
import nxt.NxtException;
import nxt.TransactionProcessorImpl;
import nxt.util.Listener;

// TODO, FIXME fix to forbid a memory overload DOS attack. Maybe hard-limit number of longpolls per IP?
// Otherwise user can create INT_MAX number of objects of type ExpiringListPointer in memory
// Another bad effect is that the longpoll causes to hang for 5 seconds even if user clicks cancel in the browser. Examine that please!

// CHECK FOR RACECONDITIONS (synchronized keywords) !!!!!!!!

final class ExpiringListPointer {
	static int lastPosition = 0;
	static int expireTime = 0;
	Date lastUpdated = null;

	public ExpiringListPointer(int latestPosition, int expireTimeLocal) {
		lastUpdated = new Date();
		lastPosition = latestPosition;
		expireTime = expireTimeLocal;
	}

	public boolean expired() {
		// ListPointers expire after 25 seconds
		long seconds = ((new Date()).getTime() - lastUpdated.getTime()) / 1000;
		return seconds > expireTime / 1000;
	}

	public void reuse(int idx) {
		lastUpdated = new Date();
		lastPosition = idx;
	}

	public void normalizeIndex(int removed) {
		lastPosition = lastPosition - removed;
		if (lastPosition < 0)
			lastPosition = 0;
	}
}

class ClearTask extends TimerTask {
	private HashMap<Integer, ExpiringListPointer> toClear = null;
	private ArrayList<String> events = null;

	public ClearTask(HashMap<Integer, ExpiringListPointer> h,
			ArrayList<String> e) {
		this.toClear = h;
		this.events = e;
	}

	@SuppressWarnings("rawtypes")
	public void run() {
		if (toClear != null) {
			int minimalIndex = Integer.MAX_VALUE;
			Iterator it = this.toClear.entrySet().iterator();
			while (it.hasNext()) {
				@SuppressWarnings("unchecked")
				HashMap.Entry<Integer, ExpiringListPointer> ptr = (HashMap.Entry<Integer, ExpiringListPointer>) it
						.next();
				if (ptr.getValue().expired()) {
					//System.out.println("Clearing inactive listener: "
					//		+ ptr.getKey());
					it.remove(); // avoids a ConcurrentModificationException
				} else {
					if (ptr.getValue().lastPosition < minimalIndex) {
						minimalIndex = ptr.getValue().lastPosition;
					}
				}
			}

			// strip events below minimalIndex, if applicable
			if (minimalIndex > 0 && minimalIndex != Integer.MAX_VALUE)
				this.events.subList(0, minimalIndex).clear();

			// run again through iterator and adjust minimal indized
			it = this.toClear.entrySet().iterator();
			while (it.hasNext()) {
				HashMap.Entry<Integer, ExpiringListPointer> ptr = (HashMap.Entry<Integer, ExpiringListPointer>) it
						.next();
				if (minimalIndex > 0 && minimalIndex != Integer.MAX_VALUE)
					ptr.getValue().normalizeIndex(minimalIndex);
			}
		}

	}
}

public final class Longpoll extends APIServlet.APIRequestHandler {

	static final int waitTimeValue = 5000;
	static final int garbageTimeout = 10000;
	static final int expireTime = 25000;
	static final Longpoll instance = new Longpoll();
	static final HashMap<Integer, ExpiringListPointer> setListings = new HashMap<Integer, ExpiringListPointer>();
	static final ArrayList<String> eventQueue = new ArrayList<String>();
	static final ClearTask clearTask = new ClearTask(setListings, eventQueue);
	static final Timer timer = new Timer();
	static boolean timerInitialized = false;

	private Longpoll() {
		super(new APITag[] { APITag.AE }, "nil");
		BlockchainProcessorImpl.getInstance().blockListeners.addListener(new Listener<Block>() {
            @Override
            public void notify(Block block) {
            	String event = "block " + block.getHeight();
            	ArrayList<String> list = new ArrayList<String>();
            	list.add(event);
        		Longpoll.instance.addEvents(list);
            }
        }, nxt.BlockchainProcessor.Event.BLOCK_SCANNED);

		BlockchainProcessorImpl.getInstance().blockListeners.addListener(new Listener<Block>() {
            @Override
            public void notify(Block block) {
            	String event = "new block (" + block.getHeight() + ")";
            	ArrayList<String> list = new ArrayList<String>();
            	list.add(event);
        		Longpoll.instance.addEvents(list);
            }
        }, nxt.BlockchainProcessor.Event.BLOCK_PUSHED);
		
		Generator.addListener(new Listener<Generator>() {
            @Override
			public void notify(Generator t) {
				String event = "generator updated";
				ArrayList<String> list = new ArrayList<String>();
            	list.add(event);
        		Longpoll.instance.addEvents(list);
			}
        }, nxt.Generator.Event.GENERATION_DEADLINE);
		
		TransactionProcessorImpl.getInstance().addListener(new Listener<List<? extends nxt.Transaction>>() {
			@Override
			public void notify(List<? extends nxt.Transaction> t) {
				String event = "broadcast transaction";
				ArrayList<String> list = new ArrayList<String>();
            	list.add(event);
        		Longpoll.instance.addEvents(list);
			}
        }, nxt.TransactionProcessor.Event.BROADCASTED_OWN_TRANSACTION);
	}

	public void addEvents(List<String> l) {
		for (String x : l) {
			Longpoll.eventQueue.add(x);
			//System.out.println("Adding: " + x);
		}

		synchronized (Longpoll.instance) {
			Longpoll.instance.notify();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

		JSONObject response = new JSONObject();

		String randomIdStr = ParameterParser.getParameterMultipart(req,
				"randomId");
		int randomId;
		try {
			randomId = parseInt(randomIdStr);
		} catch (NumberFormatException e) {
			response.put("error",
					"please provide a randomId (within the integer range)");
			return response;
		}

		ExpiringListPointer p = null;
		if (setListings.containsKey(randomId)) {
			//System.out.println("Reusing Linstener: " + randomId);
			p = setListings.get(randomId);
		} else {
			//System.out.println("Creating new Listener: " + randomId);
			p = new ExpiringListPointer(Longpoll.eventQueue.size(), expireTime);
			setListings.put(randomId, p);
		}

		// Schedule timer if not done yet
		if (!timerInitialized) {
			// Schedule to run after every 3 second (3000 millisecond)
			try{
				timer.scheduleAtFixedRate(clearTask, 0, garbageTimeout);
				timerInitialized = true;
			}catch(java.lang.IllegalStateException e){
				timerInitialized = true; // TODO FIXME (WHY SOMETIMES ITS ALREADY INITIALIZED)
			}
			
		}

		synchronized (this) {
			try {
				if (ExpiringListPointer.lastPosition == Longpoll.eventQueue.size()) {
					wait(waitTimeValue);
				}
				
				JSONArray arr = new JSONArray();
				if (ExpiringListPointer.lastPosition >= Longpoll.eventQueue.size()) {
					// Timeout, nothing new, no notification
					response.put("event", "timeout");
					return response;
				}
				for (int i = ExpiringListPointer.lastPosition; i < Longpoll.eventQueue.size(); ++i) {
					arr.add(Longpoll.eventQueue.get(i));
				}
				//System.out.println(p.lastPosition);

				p.reuse(Longpoll.eventQueue.size());

				response.put("event", arr);
				return response;

			} catch (InterruptedException e) {
				// Timeout, no notification
				response.put("event", "timeout");
				return response;
			}
		}
	}

}
package it.unibo.deis.lia.ramp.util;

import java.lang.reflect.Constructor;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Luca Iannario
 */
public class ThreadPool<T extends ThreadPool.IThreadPoolCallback<Q>, Q> {

	/*
	 * TODO tune pool parameters
	 */
	private static final int POOL_SIZE_MIN = 5;
	private static final int POOL_SIZE_MAX = 20;
	private static final int QUEUE_CONTROL_PERIOD = 1000; // millis
	private static final int QUEUE_SIZE_BLOCKING_TIMEOUT = 10000; // millis
	private static final int QUEUE_SIZE_THRESHOLD = POOL_SIZE_MIN;
	private static final int QUEUE_REQUESTS_RATE_THRESHOLD = POOL_SIZE_MIN * 4;
	private static final int HYSTERESIS_THRESHOLD = POOL_SIZE_MIN;
	
	private Vector<PoolWorker> pool;
	private Object enclosingInstance;
	private Class<T> runnableClass;
	private DispatchingQueue dispatchingQueue;
	private String poolName;
	private boolean debug;
	
	public ThreadPool(Class<T> runnableClass, String poolName){
		this(null, runnableClass, poolName, false);
	}
	
	public ThreadPool(Class<T> runnableClass, String poolName, boolean debug){
		this(null, runnableClass, poolName, debug);
	}
	
	public ThreadPool(Object enclosingInstance, Class<T> runnableClass, String poolName){
		this(enclosingInstance, runnableClass, poolName, false);
	}
	
	public ThreadPool(Object enclosingInstance, Class<T> runnableClass, String poolName, boolean debug){
		pool = new Vector<PoolWorker>(POOL_SIZE_MIN);
		this.enclosingInstance = enclosingInstance;
		this.runnableClass = runnableClass;
		this.dispatchingQueue = new DispatchingQueue();
		this.poolName = poolName;
		this.debug = debug;
		if(debug) System.out.println("ThreadPool[" + poolName + "] created");
	}
	
	public ThreadPool<T, Q> init(){
		for(int i = 0; i < POOL_SIZE_MIN; i++){
			addWorker();
		}
		if(debug) System.out.println("ThreadPool[" + poolName + "] initialized");
		return this;
	}
	
	private void addWorker(){
		synchronized (pool) {
			if(pool.size() < POOL_SIZE_MAX){
				if(debug) System.out.println("ThreadPool[" + poolName + "].addPoolThread adding new worker to the pool");
				IThreadPoolCallback<Q> runnable = null;
				try {
					if(enclosingInstance == null){
						runnable = runnableClass.newInstance();
					}else{
						Constructor<T> constructor = runnableClass.getConstructor(enclosingInstance.getClass());
						runnable = constructor.newInstance(enclosingInstance);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				if(runnable != null){
					PoolWorker worker = new PoolWorker(runnable);
					pool.add(worker);
					worker.start();
				}
			}else{
				if(debug) System.out.println("ThreadPool[" + poolName + "].addPoolThread thread pool is already at max capacity");
			}
			if(debug) System.out.println("ThreadPool[" + poolName + "].addPoolThread pool size: " + pool.size());
		}
	}
	
	private boolean removeWorker(PoolWorker worker){
		synchronized (pool) {
			if(pool.size() > POOL_SIZE_MIN){
				if(debug) System.out.println("ThreadPool[" + poolName + "].removePoolThread " + worker + " removing worker from the pool");
				boolean found = pool.remove(worker);
				if(debug) System.out.println("ThreadPool[" + poolName + "].removePoolThread " + worker + " pool size: " + pool.size());
				return found;
			}else{
				if(debug) System.out.println("ThreadPool[" + poolName + "].removePoolThread " + worker + " thread pool is already at min capacity (size=" + pool.size() + ")");
				return false;
			}
		}
	}
	
	public void stopThreadPool(){
		this.dispatchingQueue.stop();
		synchronized (pool) {
			for (int i = 0; i < pool.size(); i++) {
				PoolWorker worker = pool.get(i);
				worker.resetActive();
				worker.interrupt();
			}
		}
		if(debug) System.out.println("ThreadPool[" + poolName + "] stopped");
	}
	
	public void enqueueItem(Q item){
		dispatchingQueue.offer(item);
	}
	
	private Q dequeueItem() throws InterruptedException{
		return dispatchingQueue.poll();
	}
	
	public interface IThreadPoolCallback<Q> extends Runnable {
		
		public void onBeforeExecute(Q item);
		public void onPostExecute();
		
	}
	
	private class PoolWorker extends Thread {
		
		private IThreadPoolCallback<Q> runnable;
		private int hysteresisCounter;
		private boolean active;
		
		public PoolWorker(IThreadPoolCallback<Q> runnable){
			this.runnable = runnable;
			this.hysteresisCounter = 0;
			this.active = true;
		}
		
		@Override
		public void run() {
			
			while(active){
				
				try {
					
					// dequeue and dispatch
					//System.out.println("PoolWorker polling item to dispatch");
					Q packetToDispatch = dequeueItem(); // blocking for at most QUEUE_SIZE_BLOCKING_TIMEOUT
					if(packetToDispatch == null){ // queue is empty
						hysteresisCounter++;
						if(hysteresisCounter == HYSTERESIS_THRESHOLD){
							if(debug) System.out.println("ThreadPool[" + poolName + "]: " + this + " is being stopped due to inactivity");
							active = !removeWorker(this); // if removed stop this thread (active = false)
							if(debug) System.out.println("ThreadPool[" + poolName + "]: " + this + " active=" + active);
							hysteresisCounter = 0;
						}
						continue;
					}
					
					runnable.onBeforeExecute(packetToDispatch);
					
					runnable.run();
					
					runnable.onPostExecute();
					
				} catch (InterruptedException ie){ 
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
			
		}
		
		public void resetActive(){
			this.active = false;
		}
		
	}
	
	private class DispatchingQueue {
		
		private ConcurrentLinkedQueue<Q> queue;
		private Timer timer;
		private int requestsNumber;
		private int queueSize;

		public DispatchingQueue(){
			this.queue = new ConcurrentLinkedQueue<Q>();
			this.timer = new Timer();
			this.timer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					int queueSize = getSize();
					float requestsRate = getRequestsNumberAndReset(); // / (float) QUEUE_CONTROL_PERIOD * 1000;
					if(queueSize > QUEUE_SIZE_THRESHOLD){
						if(debug) System.out.println("ThreadPool[" + poolName + "]: dispatchingQueue.timer: queue size (" + queueSize + ") is above threshold");
						addWorker();
					}else if(requestsRate > QUEUE_REQUESTS_RATE_THRESHOLD){
						if(debug) System.out.println("ThreadPool[" + poolName + "]: dispatchingQueue.timer: requestsNumber/s (" + requestsRate + ") is above threshold");
						addWorker();
					}else{
						//if(debug) System.out.println("ThreadPool[" + poolName + "]: dispatchingQueue.timer: queue is acceptable");
					}
				}
				
			}, QUEUE_CONTROL_PERIOD, QUEUE_CONTROL_PERIOD);
			this.requestsNumber = 0;
			this.queueSize = 0;
		}
		
		public void stop(){
			if(debug) System.out.println("ThreadPool[" + poolName + "]: dispatchingQueue.timer: stop");
			this.timer.cancel();
		}
		
		public synchronized int getRequestsNumberAndReset() {
			int result = requestsNumber;
			requestsNumber = 0;
			return result;
		}
		
		public synchronized int getSize() {
			//return queue.size();
			return queueSize; // more efficient (see queue.size() description)
		}

		public synchronized Q poll() throws InterruptedException{
			if(queue.isEmpty()){
				wait(QUEUE_SIZE_BLOCKING_TIMEOUT);
			}
			Q item = queue.poll();
			if(item != null)
				queueSize--;
			return item;
		}

		public synchronized boolean offer(Q item){
			notify();
			queueSize++;
			requestsNumber++;
			return queue.offer(item);
		}
		
	}

}

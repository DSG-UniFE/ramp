package it.unibo.deis.lia.ramp.service.application;


import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;


public class DistribuitedActuatorServiceNoGUI extends Thread {

	private boolean open;
	
	private int protocol = E2EComm.TCP;
	private static BoundReceiveSocket serviceSocket;
	
	private static DistribuitedActuatorServiceNoGUI distribuitedActuator = null;
	private static Heartbeater heartbeater = null;
	
	// <app_name, <node_id, ClientDescriptor>
	BiHashtable<String, Integer, ClientDescriptor> appDB  = new BiHashtable<String, Integer, ClientDescriptor>();
	
	
	protected DistribuitedActuatorServiceNoGUI(boolean gui) throws Exception {
		open = true;
		
	    serviceSocket = E2EComm.bindPreReceive(protocol);

	    ServiceManager.getInstance(false).registerService("" +
	    		"DistribuitedActuator",
	    		serviceSocket.getLocalPort(),
	    		protocol
			);
	}
	
	public static synchronized DistribuitedActuatorServiceNoGUI getInstance() {
	    try {
	        if (DistribuitedActuatorServiceNoGUI.distribuitedActuator == null) {
	        	// DistribuitedActuatorService senza GUI
	        	DistribuitedActuatorServiceNoGUI.distribuitedActuator = new DistribuitedActuatorServiceNoGUI(false); 
	        	DistribuitedActuatorServiceNoGUI.distribuitedActuator.start();
	        }
	    }
	    catch (Exception e) {
	        e.printStackTrace();
	    }
	    return DistribuitedActuatorServiceNoGUI.distribuitedActuator;
	}
	
	public static boolean isActive(){
	    return DistribuitedActuatorServiceNoGUI.distribuitedActuator != null;
	}
	
	public void stopService(){
	    System.out.println("DistribuitedActuatorService close");
	    ServiceManager.getInstance(false).removeService("DistribuitedActuator");
	    open = false;
	    DistribuitedActuatorServiceNoGUI.heartbeater.stopHeartbeater();
	    try {
	        serviceSocket.close();
	    } catch (IOException ex) {
	        ex.printStackTrace();
	    }
	}

    public void addApp(String appName) {
    	appDB.putK1(appName);
    }
    
    public void removeApp(String appName) {
    	appDB.removeK1(appName);
    }
    
    public void sendCommand(String appName, String command, int threshold) {
    	// TODO
    }
    
	@Override
	public void run() {
	    try {
	        System.out.println("DistribuitedActuatorService START");
	        System.out.println("DistribuitedActuatorService START " + serviceSocket.getLocalPort() + " " + protocol);
	        DistribuitedActuatorServiceNoGUI.heartbeater = new Heartbeater(15, TimeUnit.SECONDS);
	        while (open) {
	            try {
	                // receive
	                GenericPacket gp = E2EComm.receive(serviceSocket, 5*1000);
	                System.out.println("DistribuitedActuatorService new request");
	                new Handler(gp).start();
	            } catch(SocketTimeoutException ste) {
	                System.out.println("DistribuitedActuatorService SocketTimeoutException");
	            }
	        }
	        serviceSocket.close();
	    } catch (SocketException se) {

	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    DistribuitedActuatorServiceNoGUI.distribuitedActuator = null;
	    System.out.println("DistribuitedActuatorService FINISHED");
	}

	
	private class Handler extends Thread {
	    private GenericPacket gp;

	    
	    private Handler(GenericPacket gp) {
	        this.gp = gp;
	    }
	    
	    @Override
	    public void run() {
	        try {
	            if (gp instanceof UnicastPacket) {
	                // 1) payload
	                UnicastPacket up = (UnicastPacket) gp;
	                Object payload = E2EComm.deserialize(up.getBytePayload());
	                if (payload instanceof DistribuitedActuatorRequest) {
	                    System.out.println("DistribuitedActuatorHandler DistribuitedActuatorRequest");
	                    DistribuitedActuatorRequest request = (DistribuitedActuatorRequest) payload;
	                    switch (request.getType()) {
		                    case AVAILABLE_APPS:
		                    	Set<String> appNames = appDB.getApps(); 
		                    	// TODO rispondere le applicaizoni disponibili
		                    	break;
		                    case PRE_COMMAND:
		                    	// TODO
								
		                    	break;
		                    case COMMAND:
		                    	// TODO
		                    	
								break;
		                    case HERE_I_AM:
		                    	// TODO
		                    	
		                    	break;
		                    case JOIN:
		                    	appDB.put(request.getAppName(), up.getSourceNodeId(),
		                    			new ClientDescriptor(request.getPort(), 
		                    					Instant.now().getEpochSecond()));
		                    	break;
		                    case LEAVE:
		                    	appDB.removeK2(request.getAppName(), up.getSourceNodeId());
		                    	break;
		                    case WHICH_APP:
		                    	// TODO
		                    	
								break;
	
							default:
								// received wrong type of request: do nothing...
		                        System.out.println("DistribuitedActuatorHandler wrong type of request: " + 
		                        		request.getType());
								
								break;
						}
	                } else {
	                    // received payload is not DistribuitedActuatorHandler: do nothing...
	                    System.out.println("DistribuitedActuatorService wrong payload: " + payload);
	                }
	            }
	            else{
	                // received packet is not UnicastPacket: do nothing...
	                System.out.println("DistribuitedActuatorHandler wrong packet: " + 
	                		gp.getClass().getName());
	            }

	        } catch(Exception e) {
	            e.printStackTrace();
	        }
	    }
	}
	
	
	
	private class Heartbeater extends Thread {

		private boolean open;
		private final Object monitor = new Object();
		private long millisToSleep = TimeUnit.SECONDS.toMillis(15); // default time
		private  Heartbeater heartbeater = null;
		
		
		private Heartbeater(int millisToSleep, TimeUnit timeUnit) {
			this.open = true;
	        setMillisToSleep(millisToSleep, timeUnit);
	        start();
	    }
	    
		
		protected boolean isActive(){
		    return heartbeater != null;
		}
		
		public void stopHeartbeater(){
		    open = false;
		    synchronized (monitor) {
		    	monitor.notify();
	        }
		}
		
	    @Override
	    public void run() {
	    	System.out.println("DistribuitedActuatorServiceHeartbeater START");
	    	while (open) {
	    		Set<String> appNames = appDB.getApps();
	    		// TODO inviare pre-commad a tutti
	    		synchronized (monitor) {
	    			try {
						monitor.wait(millisToSleep);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
	    		}
	    	}
	    	System.out.println("DistribuitedActuatorServiceHeartbeater FINISHED");
	    }

		protected void setMillisToSleep(long millisToSleep, TimeUnit timeUnit) {
			synchronized (monitor) {
	            this.millisToSleep = timeUnit.toMillis(millisToSleep);
//	            monitor.notify();
	        }
		}
	    
	}

	
	
	public class BiHashtable<K1, K2, V> {
		/*
		 * http://stackoverflow.com/questions/28362502/can-we-put-hash-tables-inside-a-hash-table
		 */
		
		private final Hashtable<K1, Hashtable<K2, V>> tTable;

		
		public BiHashtable() {
			tTable = new Hashtable<K1, Hashtable<K2, V>>();
		}

		/*
		 * Associates the specified value with the specified keys in this map (optional operation). If the map previously
		 * contained a mapping for the key, the old value is replaced by the specified value.
		 * 
		 * @param key1
		 *            the first key
		 * @param key2
		 *            the second key
		 * @param object
		 *            the object to be set
		 * @return the object previously associated with (key1,key2), or <code>null</code> if none
		 * @see Hashtable#put(key, Object)
		 */
		public V put(K1 key1, K2 key2, V object) {
		    Hashtable<K2, V> table;
		    if (tTable.containsKey(key1)) {
		    	table = tTable.get(key1);
		    } else {
		        table = new Hashtable<K2, V>();
		        tTable.put(key1, table);
		    }

		    return table.put(key2, object);
		}
		
		public void putK1(K1 key1) {
		    if (!tTable.containsKey(key1)) {
		    	tTable.put(key1, new Hashtable());
		    }    
		}
		
		/**
		 * Returns the object to which the specified key is mapped, or <code>null</code> if this map contains no mapping for
		 * the key.
		 * 
		 * @param key1
		 *            the first key whose associated value is to be returned
		 * @param key2
		 *            the second key whose associated value is to be returned
		 * @return the object to which the specified key is mapped, or <code>null</code> if this map contains no mapping for
		 *         the key
		 * @see Hashtable#get(Object)
		 */
		public V get(K1 key1, K2 key2) {
		    if (tTable.containsKey(key1)) {
		        return tTable.get(key1).get(key2);
		    } else {
		        return null;
		    }
		}
		
		public Set<K1> getApps() {
		    if (tTable.size() > 0) {
		        return tTable.keySet();
		    } else {
		        return null;
		    }
		}
		
		public Hashtable<K2, V> getNodes(String appName) {
		    if (tTable.containsKey(appName)) {
		        return tTable.get(appName);
		    } else {
		        return null;
		    }
		}
		
		public Hashtable<K2, V> removeK1(K1 key1) {
		    if (tTable.containsKey(key1)) {
		        return tTable.remove(key1);
		    } else {
		        return null;
		    }
		}
		
		public V removeK2(K1 key1, K2 key2) {
			Hashtable<K2, V> table;
		    if (tTable.containsKey(key1) && tTable.get(key1).containsKey(key2)) {
		        return tTable.get(key1).remove(key2);
		    } else {
		        return null;
		    }
		}

		/**
		 * Returns <code>true</code> if this map contains a mapping for the specified key
		 * 
		 * @param key1
		 *            the first key whose presence in this map is to be tested
		 * @param key2
		 *            the second key whose presence in this map is to be tested
		 * @return Returns true if this map contains a mapping for the specified key
		 * @see Map#containsKey(Object)
		 */
		public boolean containsKeys(K1 key1, K2 key2) {
		    return tTable.containsKey(key1) && tTable.get(key1).containsKey(key2);
		}

		public void clear() {
		    tTable.clear();
		}
	}
	
	
	private class ClientDescriptor {
		private int port;
		private long lastUpdate; // unix epoch
		
		
		protected ClientDescriptor(int port, long lastUpdate) {
			setPort(port);
			setLastUpdate(lastUpdate);
		}
		
		protected int getPort() {
			return port;
		}
		
		protected void setPort(int port) {
			this.port = port;
		}
		
		protected long getLastUpdate() {
			return lastUpdate;
		}
		
		protected void setLastUpdate(long lastUpdate) {
			this.lastUpdate = lastUpdate;
		}
	}

}


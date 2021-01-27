package it.unibo.deis.lia.ramp.core.internode;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorRequest.Type;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.util.GeneralUtils;


public class DistributedActuatorService extends Thread {

	private boolean open;

	private static final int PROTOCOL = E2EComm.TCP;
	private static BoundReceiveSocket serviceSocket;

	private static DistributedActuatorService distribuitedActuator = null;
	private static Heartbeater heartbeater = null;

	// <app_name, node_id, ClientDescriptor>
	private BiHashtable<String, Integer, ClientDescriptor> appDB  = new BiHashtable<String, Integer, ClientDescriptor>();


	public DistributedActuatorService(boolean gui) throws Exception {
		open = true;

	    serviceSocket = E2EComm.bindPreReceive(PROTOCOL);

	    ServiceManager.getInstance(false).registerService(
				"DistributedActuator",
	    		serviceSocket.getLocalPort(),
	    		PROTOCOL
			);
	}

	public static synchronized DistributedActuatorService getInstance() {
	    try {
	        if (DistributedActuatorService.distribuitedActuator == null) {
	        	// DistributedActuatorService senza GUI
	        	DistributedActuatorService.distribuitedActuator = new DistributedActuatorService(false);
	        	DistributedActuatorService.distribuitedActuator.start();
	        }
	    }
	    catch (Exception e) {
	        e.printStackTrace();
	    }
	    return DistributedActuatorService.distribuitedActuator;
	}

	public static boolean isActive(){
	    return DistributedActuatorService.distribuitedActuator != null;
	}

	public void stopService(){
		System.out.println("DistributedActuatorService STOP");
	    ServiceManager.getInstance(false).removeService("DistribuitedActuator");
	    open = false;
	    DistributedActuatorService.heartbeater.stopHeartbeater();
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

    /**
	 * @param threshold value from 0 to 1
	 */
	public void sendCommand(String appName, String command, int secondsToWait, float threshold) {
		int millisToWait = secondsToWait * 1000;
		// "primaryValue=xxx,resilience=yyy"
//		System.out.println("DistributedActuatorService.sendCommand: appName=" + appName + " command=" + command);
    	Hashtable<Integer, ClientDescriptor> nodes = appDB.getK2(appName);
    	for(int nodeID : nodes.keySet()) {
			ClientDescriptor node = nodes.get(nodeID);
			Vector<ResolverPath> paths = Resolver.getInstance(true).resolveBlocking(nodeID, 5*1000);
			try {
				E2EComm.sendUnicast(
						paths.firstElement().getPath(),
						nodeID,
						node.getPort(),
						PROTOCOL,
						false,
						GenericPacket.UNUSED_FIELD,
						E2EComm.DEFAULT_BUFFERSIZE,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						E2EComm.serialize(
								new DistributedActuatorRequest(
										DistributedActuatorRequest.Type.PRE_COMMAND,
										serviceSocket.getLocalPort(),
										appName))
						);
//				System.out.println("DistributedActuatorService.sendCommand: sent packet PRE_COMMAND");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

    	try {
			System.out
					.println("DistributedActuatorService.sendCommand: waiting for " + millisToWait + " milliseconds");
			sleep(millisToWait);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

    	float nActiveNodes = 0;
    	ArrayList<Integer> activeNodes = new ArrayList<Integer>();
    	for(int nodeID : nodes.keySet()) {
    		ClientDescriptor node = nodes.get(nodeID);
			if (node.lastUpdate > (System.currentTimeMillis() - millisToWait)) {
    			nActiveNodes++;
    			activeNodes.add(nodeID);
    		}
    	}
//    	System.out.println("DistributedActuatorService.sendCommand: founded n " + nActiveNodes + " active nodes");
//    	System.out.println("DistributedActuatorService.sendCommand: list of active nodes " + Arrays.toString(activeNodes.toArray()));
		if ((nActiveNodes / nodes.size()) >= threshold) {
//    		System.out.println("DistributedActuatorService.sendCommand: outdated threshold");
    		for(int nodeID : activeNodes) {
    			ClientDescriptor node = nodes.get(nodeID);
    			Vector<ResolverPath> paths = Resolver.getInstance(true).resolveBlocking(nodeID, 5*1000);
    			try {
    				E2EComm.sendUnicast(
    						paths.firstElement().getPath(),
    						nodeID, node.getPort(),
    						PROTOCOL,
    						false,
    						GenericPacket.UNUSED_FIELD,
    						E2EComm.DEFAULT_BUFFERSIZE,
    						GenericPacket.UNUSED_FIELD,
    						GenericPacket.UNUSED_FIELD,
    						GenericPacket.UNUSED_FIELD,
    						E2EComm.serialize(
    								new DistributedActuatorRequest(
    										DistributedActuatorRequest.Type.COMMAND,
    										serviceSocket.getLocalPort(),
    										appName,
    										command))
    						);
//    				System.out.println("DistributedActuatorService.sendCommand: sent packet COMMAD=" + command);
    			} catch (Exception e) {
    				e.printStackTrace();
    			}
    		}
		}
    }

	@Override
	public void run() {
	    try {
	        System.out.println("DistributedActuatorService START");
	        GeneralUtils.appendLog("DistributedActuatorService START " +
	        		serviceSocket.getLocalPort() + " " + PROTOCOL);
	        DistributedActuatorService.heartbeater = new Heartbeater(5, TimeUnit.SECONDS);
	        while (open) {
	            try {
	                // receive
	                GenericPacket gp = E2EComm.receive(serviceSocket, 5*1000);
//	                System.out.println("DistributedActuatorService new request");
	                new PacketHandler(gp).start();
	            } catch(SocketTimeoutException ste) {
	                //System.out.println("DistributedActuatorService SocketTimeoutException");
	            }
	        }
	        serviceSocket.close();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    DistributedActuatorService.distribuitedActuator = null;
	    System.out.println("DistributedActuatorService FINISHED");
	    GeneralUtils.appendLog("DistributedActuatorService FINISHED");
	}


	private class PacketHandler extends Thread {
	    private GenericPacket gp;


	    private PacketHandler(GenericPacket gp) {
	        this.gp = gp;
	    }

	    @Override
	    public void run() {
	        try {
	            if (gp instanceof UnicastPacket) {
	                // 1) payload
	                UnicastPacket up = (UnicastPacket) gp;
	                Object payload = E2EComm.deserialize(up.getBytePayload());
	                if (payload instanceof DistributedActuatorRequest) {
//	                    System.out.println("DistributedActuatorService PacketHandler DistributedActuatorRequest");
	                    DistributedActuatorRequest request = (DistributedActuatorRequest) payload;
	                    switch (request.getType()) {
		                    case HERE_I_AM:
//		                    	System.out.println("DistributedActuatorService PacketHandler DistributedActuatorRequest HERE_I_AM");
		                    	ClientDescriptor cd = appDB.get(request.getAppName(), up.getSourceNodeId());
		                    	cd.setLastUpdate(System.currentTimeMillis());
		                    	break;
		                    case JOIN:
//		                    	System.out.println("DistributedActuatorService PacketHandler DistributedActuatorRequest JOIN");
		                    	appDB.put(request.getAppName(), up.getSourceNodeId(),
		                    			new ClientDescriptor(request.getPort(),
		                    					System.currentTimeMillis()));
		                    	break;
		                    case LEAVE:
//		                    	System.out.println("DistributedActuatorService PacketHandler DistributedActuatorRequest LEAVE");
		                    	appDB.removeK2(request.getAppName(), up.getSourceNodeId());
		                    	break;
		                    case WHICH_APP:
//		                    	System.out.println(
//									"DistributedActuatorService PacketHandler DistributedActuatorRequest WHICH_APP");
		                    	Set<String> sAppNames = appDB.getK1();
		                    	String[] aAppNames = sAppNames.toArray(new String[sAppNames.size()]);
//		                    	System.out.println(
//									"DistributedActuatorService PacketHandler DistributedActuatorRequest AppNames:"
//											+ Arrays.toString(aAppNames));
		                    	DistributedActuatorRequest dar = new DistributedActuatorRequest(
		                    			Type.AVAILABLE_APPS,
		                    			serviceSocket.getLocalPort(),
		                    			aAppNames);
		                    	try {
			                    	E2EComm.sendUnicast(
			                    			E2EComm.ipReverse(up.getSource()),
			                    			up.getSourceNodeId(),
		                        			request.getPort(),
			            					E2EComm.TCP,
			            					false,
			            					GenericPacket.UNUSED_FIELD,
			            					E2EComm.DEFAULT_BUFFERSIZE,
			            					GenericPacket.UNUSED_FIELD,
			            					GenericPacket.UNUSED_FIELD,
			            					GenericPacket.UNUSED_FIELD,
			            					E2EComm.serialize(dar)
			            					);
//			                    	System.out.println("DistributedActuatorService PacketHandler DistributedActuatorRequest sent packet AVAILABLE_APPS");
		                    	} catch (Exception e) {
		            	            e.printStackTrace();
		            	        }
		                    	break;
							default:
								// received wrong type of request: do nothing...
//		                        System.out.println("DistribuitedActuatorService PacketHandler wrong type of request: " +
//		                        		request.getType());
								break;
						}
	                } else {
	                    // received payload is not DistribuitedActuatorHandler: do nothing...
//	                    System.out.println("DistributedActuatorService PacketHandler wrong payload: " + payload);
	                }
	            }
	            else{
	                // received packet is not UnicastPacket: do nothing...
//	                System.out.println("DistributedActuatorService PacketHandler wrong packet: " +
//	                		gp.getClass().getName());
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
		private Heartbeater heartbeater = null;


		private Heartbeater(int timeToSleep, TimeUnit timeUnit) {
			this.open = true;
	        setMillisToSleep(timeToSleep, timeUnit);
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
	    	System.out.println("DistributedActuatorServiceHeartbeater START");
	    	while (open) {
	    		synchronized (monitor) {
	    			try {
//	    		    	System.out.println("DistributedActuatorServiceHeartbeater: waiting...");
						monitor.wait(millisToSleep);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
	    		}
	    		Set<String> appNames = appDB.getK1();
//		    	System.out.println("DistributedActuatorServiceHeartbeater: appNames "+appNames);
	    		for (String appName: appNames) {
	    			Hashtable<Integer, ClientDescriptor> nodes = appDB.getK2(appName);
	    			for(int nodeID : nodes.keySet()) {
	    				ClientDescriptor node = nodes.get(nodeID);
	    				Vector<ResolverPath> paths = Resolver.getInstance(true).resolveBlocking(nodeID, 5*1000);
						if (paths != null) {
							try {
								E2EComm.sendUnicast(paths.firstElement().getPath(), nodeID, node.getPort(), PROTOCOL,
										false, GenericPacket.UNUSED_FIELD, E2EComm.DEFAULT_BUFFERSIZE,
										GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD,
										GenericPacket.UNUSED_FIELD,
										E2EComm.serialize(new DistributedActuatorRequest(
												DistributedActuatorRequest.Type.PRE_COMMAND,
												serviceSocket.getLocalPort(), appName)));
							} catch (Exception e) {
								e.printStackTrace();
							}
						} else {
							// TODO Rimuovere i nodeID che non hanno più un
							// percorso
							// appDB.removeK2(appName, nodeID);
						}
	    			}
	    		}
	    	}
	    	System.out.println("DistributedActuatorServiceHeartbeater FINISHED");
	    }

		protected void setMillisToSleep(long timeToSleep, TimeUnit timeUnit) {
			synchronized (monitor) {
	            this.millisToSleep = timeUnit.toMillis(timeToSleep);
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
		    	tTable.put(key1, new Hashtable<K2, V>());
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

		public Set<K1> getK1() {
	        return tTable.keySet();
		}

		public Hashtable<K2, V> getK2(K1 key1) {
		    if (tTable.containsKey(key1)) {
		        return tTable.get(key1);
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
			this.port = port;
			this.lastUpdate = lastUpdate;
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


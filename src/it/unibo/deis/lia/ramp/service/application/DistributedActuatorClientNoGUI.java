package it.unibo.deis.lia.ramp.service.application;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.Resolver;
import it.unibo.deis.lia.ramp.core.internode.ResolverPath;
import it.unibo.deis.lia.ramp.service.application.DistributedActuatorRequest.Type;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;


public class DistributedActuatorClientNoGUI extends Thread{

    private boolean open;
    
	private static DistributedActuatorClientNoGUI distributedActuatorClient = null;

    private static BoundReceiveSocket clientSocket;
    private int protocol = E2EComm.TCP;
    
    // key: app name
 	private Hashtable<String, ClientActuatorAppDescriptor> clientActuatorAppDb = new Hashtable<String, ClientActuatorAppDescriptor>();
    
 	
	public static synchronized DistributedActuatorClientNoGUI getInstance() {
    	try {
        	if (distributedActuatorClient == null) {
				distributedActuatorClient = new DistributedActuatorClientNoGUI(false);
	        	distributedActuatorClient.start();
        	}
		} catch (Exception e) {
			e.printStackTrace();
		}
        return distributedActuatorClient;
    }
	
	private DistributedActuatorClientNoGUI(boolean gui) throws Exception {
		open = true;
        clientSocket = E2EComm.bindPreReceive(protocol);
	}
	
    public void stopClient(){
        System.out.println("DistributedActuatorClientNoGUI stop");
        open = false;
        try {
            clientSocket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
	@Override
    public void run(){
		try{
            System.out.println("DistributedActuatorClientNoGUI START");
            System.out.println("DistributedActuatorClientNoGUI START " + clientSocket.getLocalPort() + " " + protocol);
            new DistributedActuatorClientResiliencyHandler().start();
            while(open){
                try{
                    // receive
                    GenericPacket gp = E2EComm.receive(clientSocket, 5*1000);
                    //System.out.println("DistributedActuatorClientNoGUI new request");
                    new DistributedActuatorClientPacketHandler(gp).start();
                }
                catch(SocketTimeoutException ste){
                    //System.out.println("DistributedActuatorClientNoGUI SocketTimeoutException");
                }
            }
            clientSocket.close();
        }
        catch(SocketException se){

        }
        catch(Exception e){
            e.printStackTrace();
        }
        distributedActuatorClient = null;
        System.out.println("DistributedActuatorClientNoGUI FINISHED");
	}
	
	public void registerNewApp(String appName, DistributedClientListener dcl) {
		Vector<ServiceResponse> services = findDistributedActuatorService(5, 5*1000, 5);
		ArrayList<DistributedActuatorRequest> servicesReceived = new ArrayList<DistributedActuatorRequest>();
		for(ServiceResponse service : services) {
			try {
				BoundReceiveSocket newAppSocket = E2EComm.bindPreReceive(service.getProtocol());
				// TODO check sendUnicast
				E2EComm.sendUnicast(
		                service.getServerDest(),
		                newAppSocket.getLocalPort(),
		                service.getProtocol(),
		                E2EComm.serialize(new DistributedActuatorRequest(
		                		DistributedActuatorRequest.Type.WHICH_APP, 
		                		newAppSocket.getLocalPort())
		                )
		        );
		        System.out.println("DistributedActuatorClient registerNewApp service.getServerDest(): " + 
		        		Arrays.toString(service.getServerDest()) + ", service.getServerPort: " + 
		        		service.getServerPort() + ", service.getProtocol: " + service.getProtocol());
		        
		        GenericPacket gp = null;
				while (open) {
		            try {
		                // receive
		                 gp = E2EComm.receive(newAppSocket, 5*1000);
		                System.out.println("DistributedActuatorClient registerNewApp: received packet");
		                break;
		            } catch(SocketTimeoutException ste) {
		                System.out.println("DistributedActuatorClient registerNewApp: SocketTimeoutException");
		            }
		        }
				if (gp instanceof UnicastPacket) {
	                // 1) payload
	                UnicastPacket up = (UnicastPacket) gp;
	                Object payload = E2EComm.deserialize(up.getBytePayload());
	                if (payload instanceof DistributedActuatorRequest) {
	                    System.out.println("DistributedActuatorClient DistributedActuatorRequest");
	                    DistributedActuatorRequest request = (DistributedActuatorRequest) payload;
	                    switch (request.getType()) {
		                    case AVAILABLE_APPS:
		                    	servicesReceived.add(request);
		                    	break;
		                    default:
								// received wrong type of request: do nothing...
		                        System.out.println("DistributedActuatorClient wrong type of request: " + 
		                        		request.getType());
								
								break;
	                    }
	                }
				}
		        newAppSocket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		int bestControllerNodeID = -1;
		int bestControllerPort = -1;
		int bestControllerNearest = Integer.MAX_VALUE;
		for(DistributedActuatorRequest service : servicesReceived) {
			for(String ser : service.getAppNames()) {
				if (ser.equals(appName)) {
					Vector<ResolverPath> paths = Resolver.getInstance(true).resolveBlocking(service.getNodeID(), 5*1000);
					if (bestControllerNearest > paths.size()) {
						bestControllerNearest = paths.size();
						bestControllerNodeID = service.getNodeID();
						bestControllerPort = service.getPort();
					}
				}
			}
		}
		
		if (bestControllerPort > 0) {
			try {
				// If the join triggers an error, 'clientActuatorAppDb.put' is not executed
				join(appName, bestControllerNodeID, bestControllerPort);
				clientActuatorAppDb.put(appName, new ClientActuatorAppDescriptor(
						bestControllerNodeID, 
						bestControllerPort, 
						System.currentTimeMillis()));
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void leave(String appName) {
		ClientActuatorAppDescriptor controller = clientActuatorAppDb.get(appName);
		Vector<ResolverPath> paths = Resolver.getInstance(true).resolveBlocking(controller.getControllerNodeId(), 5*1000);
		try {
			// TODO check sendUnicast
			E2EComm.sendUnicast(
					paths.firstElement().getPath(),
					controller.getControllerNodeId(), 
					controller.getControllerPort(), 
					E2EComm.TCP,
					false, 
					GenericPacket.UNUSED_FIELD,
					E2EComm.DEFAULT_BUFFERSIZE,
					GenericPacket.UNUSED_FIELD,
					GenericPacket.UNUSED_FIELD,
					GenericPacket.UNUSED_FIELD,
					E2EComm.serialize(new DistributedActuatorRequest(
							DistributedActuatorRequest.Type.LEAVE,
							appName))
					);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private Vector<ServiceResponse> findDistributedActuatorService(int ttl, int timeout, int serviceAmount) {
        long pre = System.currentTimeMillis();
        Vector<ServiceResponse> services = null;
		try {
			services = ServiceDiscovery.findServices(
			        ttl,
			        "DistributedActuator",
			        timeout,
			        serviceAmount,
			        null
			);
		} catch (Exception e) {
			e.printStackTrace();
		}
        long post = System.currentTimeMillis();
        float elapsed = (post-pre)/(float)1000;
        System.out.println("DistributedActuatorClient findDistributedActuatorService elapsed: " + 
        		elapsed + ", services: " + services);
        return services;
    }
	
	private void join(String appName, int nodeID, int port) throws Exception {
		Vector<ResolverPath> paths = Resolver.getInstance(true).resolveBlocking(nodeID, 5*1000);
		// TODO check sendUnicast
		E2EComm.sendUnicast(
				paths.firstElement().getPath(),
				nodeID,
				port, 
				E2EComm.TCP,
				false, 
				GenericPacket.UNUSED_FIELD,
				E2EComm.DEFAULT_BUFFERSIZE,
				GenericPacket.UNUSED_FIELD,
				GenericPacket.UNUSED_FIELD,
				GenericPacket.UNUSED_FIELD,
				E2EComm.serialize(new DistributedActuatorRequest(
						DistributedActuatorRequest.Type.JOIN,
						appName,
						clientSocket.getLocalPort()))
				);
	}

	
	private class DistributedActuatorClientPacketHandler extends Thread {
		
		private GenericPacket gp;
		
		DistributedActuatorClientPacketHandler(GenericPacket gp){
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
	                    System.out.println("DistributedActuatorClientPacketHandler DistributedActuatorRequest");
	                    DistributedActuatorRequest request = (DistributedActuatorRequest) payload;
	                    switch (request.getType()) {
		                    case PRE_COMMAND:
		                    	DistributedActuatorRequest dar = new DistributedActuatorRequest(
		                    			Type.HERE_I_AM,
		                    			request.getAppName());
		                    	try {
		                    		// TODO check sendUnicast
			                    	E2EComm.sendUnicast(
			                    			E2EComm.ipReverse(up.getSource()),
		                        			clientActuatorAppDb.get(request.getAppName()).getControllerPort(),
		                        			E2EComm.TCP,
		                        			E2EComm.serialize(dar));
		                    	} catch (Exception e) {
		            	            e.printStackTrace();
		            	        }
		                    	break;
		                    case COMMAND:
		                    	clientActuatorAppDb.get(request.getAppName()).getDistributedClientListener().receivedCommand(request);
								break;
							default:
								// received wrong type of request: do nothing...
		                        System.out.println("DistributedActuatorClientPacketHandler wrong type of request: " + 
		                        		request.getType());
								break;
						}
	                } else {
	                    // received payload is not DistributedActuatorClientPacketHandler: do nothing...
	                    System.out.println("DistributedActuatorClientPacketHandler wrong payload: " + 
	                    		payload);
	                }
	            }
	            else{
	                // received packet is not UnicastPacket: do nothing...
	                System.out.println("DistributedActuatorClientPacketHandler wrong packet: " + 
	                		gp.getClass().getName());
	            }

	        } catch(Exception e) {
	            e.printStackTrace();
	        }
		}
	}
	
	
	private class ClientActuatorAppDescriptor{
		
		private int controllerNodeId;
		private int controllerPort;
		private long timestamp; // unix epoch
		private DistributedClientListener distributedClientListener;
		
		protected ClientActuatorAppDescriptor(int nodeID, int port, long timestamp) {
			this.controllerNodeId = nodeID;
			this.controllerPort = port;
			this.timestamp = timestamp;
		}
		
		public DistributedClientListener getDistributedClientListener() {
			return distributedClientListener;
		}
		public void setDistributedClientListener(DistributedClientListener distributedClientListener) {
			this.distributedClientListener = distributedClientListener;
		}
		public int getControllerNodeId() {
			return controllerNodeId;
		}
		public void setControllerNodeId(int controllerNodeId) {
			this.controllerNodeId = controllerNodeId;
		}
		public int getControllerPort() {
			return controllerPort;
		}
		public void setControllerPort(int controllerPort) {
			this.controllerPort = controllerPort;
		}
		public long getTimestamp() {
			return timestamp;
		}
		public void setTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}
		
	}
	
	
	private class DistributedActuatorClientResiliencyHandler extends Thread {
		
		@Override
	    public void run() {
			while (open) {
				try {
					sleep(10*1000);
					for (String appName: clientActuatorAppDb.keySet()) {
						ClientActuatorAppDescriptor clieantActuatorAppDescriptor = clientActuatorAppDb.get(appName);
						long lastTimeout = clieantActuatorAppDescriptor.getTimestamp();
						if ((System.currentTimeMillis()-lastTimeout) > (60*1000)) {
							clieantActuatorAppDescriptor.getDistributedClientListener().activateResilience();

							// TODO look for another service whith the same app name
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
}

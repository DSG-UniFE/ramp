package it.unibo.deis.lia.ramp.core.internode;

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
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorRequest.Type;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;


public class DistributedActuatorClient extends Thread{

    private boolean open;

	private static DistributedActuatorClient distributedActuatorClient = null;

    private static BoundReceiveSocket clientSocket;
    private int protocol = E2EComm.TCP;

    // key: app name
 	private Hashtable<String, AppDescriptor> appDB = new Hashtable<String, AppDescriptor>();


	public static synchronized DistributedActuatorClient getInstance() {
    	try {
        	if (distributedActuatorClient == null) {
				distributedActuatorClient = new DistributedActuatorClient(false);
	        	distributedActuatorClient.start();
        	}
		} catch (Exception e) {
			e.printStackTrace();
		}
        return distributedActuatorClient;
    }

	private DistributedActuatorClient(boolean gui) throws Exception {
		open = true;
        clientSocket = E2EComm.bindPreReceive(protocol);
	}

    public void stopClient(){
        System.out.println("DistributedActuatorClient STOP");
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
            System.out.println("DistributedActuatorClient START");
            GeneralUtils.appendLog("DistributedActuatorClient START " +
            		clientSocket.getLocalPort() + " " + protocol);
            new ResilienceHandler().start();
            while(open){
                try{
                    // receive
                    GenericPacket gp = E2EComm.receive(clientSocket, 5*1000);
                    // System.out.println("DistributedActuatorClient new request");
                    new PacketHandler(gp).start();
                }
                catch(SocketTimeoutException ste){
                    // System.out.println("DistributedActuatorClient SocketTimeoutException");
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
        System.out.println("DistributedActuatorClient FINISHED");
        GeneralUtils.appendLog("DistributedActuatorClient FINISHED");
	}

	public boolean registerNewApp(String appName, DistributedActuatorClientListener dcl) {
		Vector<ServiceResponse> services = findDistributedActuatorService(5, 5*1000, 5);
		ArrayList<UnicastPacket> servicesReceived = new ArrayList<UnicastPacket>();
		for(ServiceResponse service : services) {
			try {
				BoundReceiveSocket newAppSocket = E2EComm.bindPreReceive(service.getProtocol());
				E2EComm.sendUnicast(service.getServerDest(), service.getServerPort(), service.getProtocol(),
						E2EComm.serialize(new DistributedActuatorRequest(DistributedActuatorRequest.Type.WHICH_APP,
								newAppSocket.getLocalPort())));
		        System.out.println("DistributedActuatorClient registerNewApp service.getServerDest(): " +
		        		Arrays.toString(service.getServerDest()) + ", service.getServerPort: " +
		        		service.getServerPort() + ", service.getProtocol: " + service.getProtocol());

		        GenericPacket gp = null;
	            try {
	                // receive
	                 gp = E2EComm.receive(newAppSocket, 10*1000);
					// System.out.println("DistributedActuatorClient registerNewApp: received packet");
	            } catch(SocketTimeoutException ste) {
	                // System.out.println("DistributedActuatorClient registerNewApp: SocketTimeoutException");
	            }

				if (gp instanceof UnicastPacket) {
	                // 1) payload
	                UnicastPacket up = (UnicastPacket) gp;
	                Object payload = E2EComm.deserialize(up.getBytePayload());
	                if (payload instanceof DistributedActuatorRequest) {
	                    DistributedActuatorRequest request = (DistributedActuatorRequest) payload;
//						System.out.println(
//								"DistributedActuatorClient registerNewApp DistributedActuatorRequest: " + request);
	                    switch (request.getType()) {
						case AVAILABLE_APPS:
							servicesReceived.add(up);
							break;
						default:
							// received wrong type of request: do nothing...
//							System.out.println("DistributedActuatorClient registerNewApp wrong type of request: "
//									+ request.getType());
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
		for(UnicastPacket up : servicesReceived) {
            Object payload;
			try {
				payload = E2EComm.deserialize(up.getBytePayload());
	            DistributedActuatorRequest request = (DistributedActuatorRequest) payload;

				for(String ser : request.getAppNames()) {
					if (ser.equals(appName)) {
						Vector<ResolverPath> paths = Resolver.getInstance(true).resolveBlocking(up.getSourceNodeId(), 5*1000);
						if (bestControllerNearest > paths.size()) {
							bestControllerNearest = paths.size();
							bestControllerNodeID = up.getSourceNodeId();
							bestControllerPort = request.getPort();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (bestControllerPort > 0) {
			try {
				// If the join triggers an error, 'clientActuatorAppDb.put' is not executed
				join(appName, bestControllerNodeID, bestControllerPort);
				appDB.put(appName,
						new AppDescriptor(bestControllerNodeID, bestControllerPort, System.currentTimeMillis(), dcl));
//				System.out.println("DistributedActuatorClient registerNewApp: added '" +
//						appName + "', nodeID " + bestControllerNodeID);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public void leave(String appName) {
//		System.out.println("DistributedActuatorClient leave appName: '" + appName + "'");
		try {
			AppDescriptor controller = appDB.get(appName);
//			System.out.println("DistributedActuatorClient.leave controller: " + controller);
			Vector<ResolverPath> paths = Resolver.getInstance(true).resolveBlocking(controller.getControllerNodeId(), 5*1000);
			if (paths != null) {
				E2EComm.sendUnicast(paths.firstElement().getPath(), controller.getControllerNodeId(),
						controller.getControllerPort(), protocol, false, GenericPacket.UNUSED_FIELD,
						E2EComm.DEFAULT_BUFFERSIZE, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD, E2EComm.serialize(
								new DistributedActuatorRequest(DistributedActuatorRequest.Type.LEAVE, appName)));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Vector<ServiceResponse> findDistributedActuatorService(int ttl, int timeout, int serviceAmount) {
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
//		System.out.println("DistributedActuatorClient findDistributedActuatorService services: " + services);
        return services;
    }

	private void join(String appName, int nodeID, int port) throws Exception {
		try {
			Vector<ResolverPath> paths = Resolver.getInstance(true).resolveBlocking(nodeID, 5 * 1000);
			if (paths != null) {
				E2EComm.sendUnicast(paths.firstElement().getPath(), nodeID, port, protocol, false,
						GenericPacket.UNUSED_FIELD, E2EComm.DEFAULT_BUFFERSIZE, GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD,
						E2EComm.serialize(new DistributedActuatorRequest(DistributedActuatorRequest.Type.JOIN,
								clientSocket.getLocalPort(), appName)));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private class PacketHandler extends Thread {

		private GenericPacket gp;

		PacketHandler(GenericPacket gp){
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
//	                    System.out.println("DistributedActuatorClientPacketHandler DistributedActuatorRequest");
	                    DistributedActuatorRequest request = (DistributedActuatorRequest) payload;
	                    switch (request.getType()) {
						case PRE_COMMAND:
							try {
	                    		appDB.get(request.getAppName()).setTimestamp(System.currentTimeMillis());
								E2EComm.sendUnicast(E2EComm.ipReverse(up.getSource()),
										appDB.get(request.getAppName()).getControllerPort(), protocol,
										E2EComm.serialize(
												new DistributedActuatorRequest(Type.HERE_I_AM, request.getAppName())));
							} catch (Exception e) {
								e.printStackTrace();
							}
							break;
						case COMMAND:
							appDB.get(request.getAppName()).setTimestamp(System.currentTimeMillis());
							appDB.get(request.getAppName()).getDistributedActuatorClientListener()
									.receivedCommand(request);
							break;
						default:
							// received wrong type of request: do nothing...
//							System.out.println("DistributedActuatorClientPacketHandler wrong type of request: "
//									+ request.getType());
							break;
						}
	                } else {
	                    // received payload is not DistributedActuatorClientPacketHandler: do nothing...
//	                    System.out.println("DistributedActuatorClientPacketHandler wrong payload: " + payload);
	                }
	            }
	            else{
	                // received packet is not UnicastPacket: do nothing...
//	                System.out.println("DistributedActuatorClientPacketHandler wrong packet: " +
//	                		gp.getClass().getName());
	            }
	        } catch(Exception e) {
	            e.printStackTrace();
	        }
		}
	}


	private class AppDescriptor{

		private Integer controllerNodeID = null;
		private Integer controllerPort = null;
		private Long timestamp = null; // unix epoch
		private DistributedActuatorClientListener distributedActuatorClientListener;

		protected AppDescriptor(int nodeID, int port, long timestamp, DistributedActuatorClientListener distributedActuatorClientListener) {
			this.controllerNodeID = nodeID;
			this.controllerPort = port;
			this.timestamp = timestamp;
			this.distributedActuatorClientListener = distributedActuatorClientListener;
		}

		public DistributedActuatorClientListener getDistributedActuatorClientListener() {
			return distributedActuatorClientListener;
		}
		public void setDistributedActuatorClientListener(DistributedActuatorClientListener distributedActuatorClientListener) {
			this.distributedActuatorClientListener = distributedActuatorClientListener;
		}
		public Integer getControllerNodeId() {
			return controllerNodeID;
		}
		public void setControllerNodeId(Integer controllerNodeId) {
			this.controllerNodeID = controllerNodeId;
		}
		public Integer getControllerPort() {
			return controllerPort;
		}
		public void setControllerPort(Integer controllerPort) {
			this.controllerPort = controllerPort;
		}
		public Long getTimestamp() {
			return timestamp;
		}
		public void setTimestamp(Long timestamp) {
			this.timestamp = timestamp;
		}

		@Override
		public String toString() {
			return "AppDescriptor [controllerNodeID=" + controllerNodeID + ", controllerPort=" + controllerPort
					+ ", timestamp=" + timestamp + "]";
		}

	}


	private class ResilienceHandler extends Thread {

		@Override
	    public void run() {
			while (open) {
				try {
					sleep(5*1000);
					// System.out.println("DistributedActuatorClientPacketHandler.ResilienceHandler");
					for (String appName: appDB.keySet()) {
						AppDescriptor appDescriptor = appDB.get(appName);
						Long lastTimeout = appDescriptor.getTimestamp();
						if ( lastTimeout == null || (System.currentTimeMillis()-lastTimeout) > (20*1000) ) {

							// look for another service with the same app name
							boolean ok = registerNewApp(appName, appDescriptor.getDistributedActuatorClientListener());

							if(!ok){
								appDescriptor.setControllerNodeId(null);
								appDescriptor.setControllerPort(null);
								appDescriptor.setTimestamp(null);
								appDescriptor.getDistributedActuatorClientListener().activateResilience();
							}
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

}

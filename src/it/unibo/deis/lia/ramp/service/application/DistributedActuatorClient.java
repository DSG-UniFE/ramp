package it.unibo.deis.lia.ramp.service.application;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Hashtable;
import java.util.Set;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.application.DistributedActuatorRequest.Type;
import it.unibo.deis.lia.ramp.util.GeneralUtils;


public class DistributedActuatorClient extends Thread{

    private boolean open;
    
	private static DistributedActuatorClient distributedActuatorClient = null;

    private static BoundReceiveSocket clientSocket;
    private int protocol = E2EComm.TCP;
    
    // key: app name
 	private Hashtable<String, ClientActuatorAppDescriptor> clientActuatorAppDb = new Hashtable<String, ClientActuatorAppDescriptor>();
    
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
        System.out.println("DistributedActuatorClient stop");
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
            System.out.println("DistributedActuatorClient START "+clientSocket.getLocalPort()+" "+protocol);
            new DistributedActuatorClientResiliencyHandler().start();
            while(open){
                try{
                    // receive
                    GenericPacket gp = E2EComm.receive(clientSocket, 5*1000);
                    //System.out.println("DistributedActuatorClient new request");
                    new DistributedActuatorClientPacketHandler(gp).start();
                }
                catch(SocketTimeoutException ste){
                    //System.out.println("DistributedActuatorClient SocketTimeoutException");
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
	}
	
	
	public void registerNewApp(String appName, DistributedClientListener dcl) {
		//TODO
	}
	
	public void leave(String appName) {
		//TODO
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
		                    	// TODO
		                    	DistributedActuatorRequest dar = new DistributedActuatorRequest(
		                    			request.getAppName(), 
		                    			Type.HERE_I_AM);
	                        	
		                    	E2EComm.sendUnicast(
	                        			up.getSource(),//TODO reverse
	                        			up.getSourcePortAck(),// TODO porta del service in hashtable
	                        			E2EComm.TCP,
	                        			E2EComm.serialize(dar));
		                    	break;
		                    case COMMAND:
		                    	// TODO
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
	                    System.out.println("DistributedActuatorClientPacketHandler wrong payload: " + payload);
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
		
		 // TODO private ClientActuatorCommandListener commandListener;
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
						if (System.currentTimeMillis()-lastTimeout > 60*1000) {
							// TODO clieantActuatorAppDescriptor.getClientActuatorCommandListener().activateResiliency();
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

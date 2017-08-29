
package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;

import java.util.Arrays;
import java.util.Vector;


/**
 *
 * @author Luca Iannario
 */
public class CBRClient extends Thread{
	
	private boolean active;
	public static final int SENDING_RATE = 50; // 50ms => 20 packets/s
	public static final short PACKET_TIMEOUT_CONNECT = 150; // ms

    private static CBRClient cbrClient=null;
    
    private CBRClient(){
    	active = true;
    }
    
    public static synchronized CBRClient getInstance(){
        if(cbrClient==null){
        	cbrClient=new CBRClient();
        	cbrClient.start();
        }
        return cbrClient;
    }

    public void stopClient(){
    	active = false;
    	cbrClient=null;
    }
    
    private Vector<ServiceResponse> findCBRServices() throws Exception{
        long pre = System.currentTimeMillis();
        Vector<ServiceResponse> retrievedServices = ServiceDiscovery.findServices(
                5,
                "CBR",
                5000,
                1
        );
        long post = System.currentTimeMillis();
        float elapsed = (post-pre)/(float)1000;
        System.out.println("CBRClient findInternetService elapsed="+elapsed+"    services="+retrievedServices);
        
        return retrievedServices;
    }
    
    private ServiceResponse findClosestCBRService(Vector<ServiceResponse> retrievedServices){
    	if(retrievedServices == null) return null;
    	int minPathLength = Integer.MAX_VALUE;
    	ServiceResponse closestCBRService = null;
    	for (ServiceResponse serviceResponse : retrievedServices) {
			if(serviceResponse.getServerDest().length < minPathLength)
				closestCBRService = serviceResponse;
		}
    	return closestCBRService;
    }

    @Override
    public void run(){
        try{
            System.out.println("CBRClient START");

        	Vector<ServiceResponse> foundServices = findCBRServices();
        	ServiceResponse closestService = findClosestCBRService(foundServices);
        	if(closestService == null){
        		System.out.println("CBRClient: no CBRServices found");
        		active = false;
        	}else{
        		System.out.println("CBRClient using CBRService reachable trough " + Arrays.toString(closestService.getServerDest()));
        	}

            while(active){
            	CBRPacket cbrPacket = new CBRPacket();
            	byte[] cbrPacketbytes = E2EComm.serialize(cbrPacket);
            	
            	E2EComm.sendUnicast(closestService.getServerDest(), 
            						closestService.getServerNodeId(), 
            						closestService.getServerPort(), 
            						E2EComm.TCP, 
            						false, 							// ack
            						GenericPacket.UNUSED_FIELD, 	// timeoutAck
            						E2EComm.DEFAULT_BUFFERSIZE,		// bufferSize
            						GenericPacket.UNUSED_FIELD,		// packetDeliveryTimeout
            						PACKET_TIMEOUT_CONNECT, 		// packetTimeoutConnect
            						cbrPacketbytes);
            	
            	System.out.println(cbrPacket.toString() + " sent (" + cbrPacketbytes.length/1024 + " KB)");
            	
            	Thread.sleep(SENDING_RATE);
            }
            
            System.out.println("CBRClient FINISHED");
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

}

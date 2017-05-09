package it.unibo.deis.lia.ramp.service.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.util.GeneralUtils;


public class DistribuitedActuatorServiceNoGUI extends Thread {

	private boolean open;
	
	private int protocol = E2EComm.TCP;
	
	private static BoundReceiveSocket serviceSocket;
	private static DistribuitedActuatorServiceNoGUI distribuitedActuator = null;
	
	
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
	    try {
	        serviceSocket.close();
	    } catch (IOException ex) {
	        ex.printStackTrace();
	    }
	}

	
	@Override
	public void run() {
	    try {
	        System.out.println("DistribuitedActuatorService START");
	        System.out.println("DistribuitedActuatorService START " + serviceSocket.getLocalPort() + " " + protocol);
	        new DistribuitedActuatorHeartbeater(15*1000).start();
	        while (open) {
	            try {
	                // receive
	                GenericPacket gp = E2EComm.receive(serviceSocket, 5*1000);
	                System.out.println("DistribuitedActuatorService new request");
	                new DistribuitedActuatorHandler(gp).start();
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


	private class DistribuitedActuatorHeartbeater extends Thread {
		private int timeToWait;
		
		private DistribuitedActuatorHeartbeater(int timeToWait) {
	        this.timeToWait = timeToWait;
	    }
	    
	    
	    @Override
	    public void run() {
	    	// TODO*)*)*)*)
	    	
	    }
		
	}
	
	
	private class DistribuitedActuatorHandler extends Thread {
	    private GenericPacket gp;

	    
	    private DistribuitedActuatorHandler(GenericPacket gp) {
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
	                    System.out.println("DistribuitedActuator DistribuitedActuatorRequest");
	                    DistribuitedActuatorRequest request = (DistribuitedActuatorRequest) payload;
	                    switch (request.getType()) {
		                    case S_AVAILABLE_APPS:
								break;
		                    case S_PRE_COMMAND:
								break;
		                    case S_COMMAND:
								break;
		                    case C_HERE_I_AM:
								break;
		                    case C_JOIN:
								break;
		                    case C_LEAVE:
		                    	break;
		                    case C_WHICH_APP:
								break;
	
							default:
								break;
						}
	                } else {
	                    // received payload is not DistribuitedActuatorRequest: do nothing...
	                    System.out.println("FDistribuitedActuatorService wrong payload: " + payload);
	                }
	            }
	            else{
	                // received packet is not UnicastPacket: do nothing...
	                System.out.println("FDistribuitedActuatorService wrong packet: " + gp.getClass().getName());
	            }

	        } catch(Exception e) {
	            e.printStackTrace();
	        }
	    }
	}
}




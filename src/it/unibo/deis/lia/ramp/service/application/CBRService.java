
package it.unibo.deis.lia.ramp.service.application;

import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.PriorityBlockingQueue;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;


/**
 *
 * @author Carlo Giannelli
 */
public class CBRService extends Thread{

    private boolean active = true;
    private int protocol = E2EComm.TCP;

    private final BoundReceiveSocket serviceSocket;
    private long lastReceivedPacketTimestamp;
    private int lastReceivedPacketId;
    private PriorityBlockingQueue<CBRPacket> cbrPacketsQueue;
    
    private BufferedWriter fileWriter;

    private static CBRService cbrService=null;
    
    public static synchronized CBRService getInstance(){
        try{
            if(cbrService==null){
            	cbrService=new CBRService();
            	cbrService.start();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return cbrService;
    }
    
    private CBRService() throws Exception{
        serviceSocket = E2EComm.bindPreReceive(protocol);
        lastReceivedPacketTimestamp = 0;
        lastReceivedPacketId = -1;
        cbrPacketsQueue = new PriorityBlockingQueue<CBRPacket>(50);
        File file = new File("./test/CBR_" + CBRPacket.PAYLOAD_DEFAULT_SIZE/1024 + "KB_" + CBRClient.SENDING_RATE + "ms_" + CBRClient.PACKET_TIMEOUT_CONNECT + "ms.txt");
        if(file.exists()) file.delete();
        fileWriter = new BufferedWriter(new FileWriter(file));
        fileWriter.write("CBRPacket.PAYLOAD_DEFAULT_SIZE: " + CBRPacket.PAYLOAD_DEFAULT_SIZE/1024 + " KB");
        fileWriter.newLine();
        fileWriter.write("CBRClient.SENDING_RATE: " + CBRClient.SENDING_RATE + " ms");
        fileWriter.newLine();
        fileWriter.write("CBRClient.PACKET_TIMEOUT_CONNECT: " + CBRClient.PACKET_TIMEOUT_CONNECT + " ms");
        fileWriter.newLine();
        fileWriter.newLine();
        fileWriter.write("id, receivedTS, inter-packetDelay, transmissionTime, throughput (KB/s)");
        fileWriter.newLine();
        fileWriter.flush();
        ServiceManager.getInstance(false).registerService(
                "CBR",
                serviceSocket.getLocalPort(),
                protocol
        );
    }

    public void stopService(){
        active = false;
        this.interrupt();
        ServiceManager.getInstance(false).removeService("CBR");
        cbrService = null;
    }

    @Override
    public void run(){
        try{
            System.out.println("CBRService START");
            
            CBRServicePacketConsumer consumer = new CBRServicePacketConsumer();
            consumer.start();
            
            while(active){
                try{
                    // receive
                    GenericPacket gp=E2EComm.receive(
                            serviceSocket,
                            10*1000
                    );
                    System.out.println("CBRService new request");
                    new CBRServiceHandler(gp).start();
                }
                catch(SocketTimeoutException ste){
                    //System.out.println("CBRService SocketTimeoutException");
                }
                catch(SocketException se){
                    //System.out.println("CBRService SocketTimeoutException");
                }
                catch(EOFException eofe){

                }
            }
            consumer.interrupt();
            serviceSocket.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("CBRService FINISHED");

    }

    private class CBRServiceHandler extends Thread{
        private GenericPacket gp;
        private CBRServiceHandler(GenericPacket gp){
            this.gp=gp;
        }
        @Override
        public void run(){
            try{
                if( gp instanceof UnicastPacket){
                    // 1) payload
                    UnicastPacket up=(UnicastPacket)gp;
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if(payload instanceof CBRPacket){
                    	CBRPacket cbrPacket = (CBRPacket) payload;
                    	cbrPacketsQueue.add(cbrPacket); // fill the queue in order
                    	int expectedPacketId = lastReceivedPacketId == -1 ? 0 : lastReceivedPacketId + 1;
                    	if(cbrPacket.getId() <= expectedPacketId){
                    		synchronized (cbrPacketsQueue) {
                    			cbrPacketsQueue.notifyAll(); // and wake up the consumer
                    		}
                    	}
                    }
                    else{
                        // received payload is not CBRPacket: do nothing...
                        System.out.println("CBRService wrong payload: "+payload);
                    }
                }
                else{
                     // received packet is not UnicastPacket: do nothing...
                    System.out.println("CBRService wrong packet: "+gp.getClass().getName());
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
    private class CBRServicePacketConsumer extends Thread {
    	
    	@Override
    	public void run() {

    		try {
				
    			while(active){
    				try {
    					CBRPacket cbrPacket = null;
    					cbrPacket = cbrPacketsQueue.peek();
    					int expectedPacketId = lastReceivedPacketId == -1 ? 0 : lastReceivedPacketId + 1;
    					boolean packetLost = false;
    					while(cbrPacket == null || cbrPacket.getId() > expectedPacketId){ // wait for the right next (in order) packet
    						if(cbrPacket != null){
    							if(packetLost){
    								System.out.println("CBRServicePacketConsumer: packet lost (id=" + expectedPacketId + ")");
    								fileWriter.write(Integer.toString(expectedPacketId) + ", " + 0 + ", " + 0 + ", " + 0 + ", " + 0);
    		                        fileWriter.newLine();
    								break;
    							}else{
    								System.out.println("CBRServicePacketConsumer: headCbrPacketId=" + cbrPacket.getId() + " (expectedId=" + expectedPacketId + ")");
    							}
    						}
    						synchronized (cbrPacketsQueue) {
    							cbrPacketsQueue.wait(1000);
    							packetLost = true;
							}
    						cbrPacket = cbrPacketsQueue.peek();
    					}
    					// the head of the queue has the right packet to be extracted
    					cbrPacket = cbrPacketsQueue.poll();
    					lastReceivedPacketId = cbrPacket.getId();
    					
    					// get statistics
                    	long receivedTimestamp = System.currentTimeMillis();
                    	long interpacketDelay = receivedTimestamp - lastReceivedPacketTimestamp;
                    	long transmissionTime = receivedTimestamp - cbrPacket.getCreationTimestamp();
                    	long throughput = (long) ((cbrPacket.getSize()/1024) / (transmissionTime/(float)1000)); // KB/s
                    	lastReceivedPacketTimestamp = receivedTimestamp;
                        System.out.println("CBRServicePacketConsumer CBRPacket " + "(id=" + cbrPacket.getId() + ") received at " + 
                        					receivedTimestamp + ": inter-packet delay=" + interpacketDelay + ", transmissionTime=" + transmissionTime +
                        					", throughput=" + throughput + ", size=" + cbrPacket.getSize());
                        //fileWriter.write("id, receivedTS, inter-packetDelay, transmissionTime, throughput (KB/s)");
                        fileWriter.write(Integer.toString(cbrPacket.getId()) + ", " + receivedTimestamp + ", " + interpacketDelay + ", " + transmissionTime + ", " + throughput);
                        fileWriter.newLine();
                        fileWriter.flush();
					} catch (Exception e) {
						e.printStackTrace();
					}
    			}
    			
			} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    }
    
}

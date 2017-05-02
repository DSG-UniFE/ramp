
package it.unibo.deis.lia.ramp.service.application;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;


/**
 *
 * @author Carlo Giannelli
 */
public class InternetService extends Thread{

    private boolean open=true;

    //private int protocol = E2EComm.UDP;
    private int protocol = E2EComm.TCP;

    private final BoundReceiveSocket serviceSocket;

    private static InternetService internetService=null;
    private static InternetServiceJFrame isjf;
    public static synchronized InternetService getInstance(){
        try{
            if(internetService==null){
                internetService=new InternetService();
                internetService.start();
            }
            isjf.setVisible(true);
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return internetService;
    }
    private InternetService() throws Exception{
        serviceSocket = E2EComm.bindPreReceive(protocol);
        ServiceManager.getInstance(false).registerService(
                "Internet",
                serviceSocket.getLocalPort(),
                protocol
        );
        isjf = new InternetServiceJFrame(this);
    }

    public void stopService(){
        System.out.println("InternetService close");
        open=false;
        try {
            this.serviceSocket.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        ServiceManager.getInstance(false).removeService("Internet");
        internetService = null;
    }

    @Override
    public void run(){
        try{
            System.out.println("InternetService START");
            while(open){
                try{
                    // receive
                    GenericPacket gp=E2EComm.receive(
                            serviceSocket,
                            10*1000
                    );
                    System.out.println("InternetService new request");
                    new InternetServiceHandler(gp).start();
                }
                catch(SocketTimeoutException ste){
                    //System.out.println("InternetService SocketTimeoutException");
                }
                catch(SocketException se){
                    //System.out.println("InternetService SocketTimeoutException");
                }
                catch(EOFException eofe){

                }
            }
            serviceSocket.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("InternetService FINISHED");

    }

    private class InternetServiceHandler extends Thread{
        private GenericPacket gp;
        private InternetServiceHandler(GenericPacket gp){
            this.gp=gp;
        }
        @Override
        public void run(){
            try{
                if( gp instanceof UnicastPacket){
                    // 1) payload
                    UnicastPacket up=(UnicastPacket)gp;
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if(payload instanceof InternetRequest){
                        //System.out.println("InternetService InternetRequest");
                        InternetRequest internetRequest=(InternetRequest)payload;

                        byte[] responseToTheClient = InternetUtil.performInternetConnection(internetRequest);

                        String[] destToClient = E2EComm.ipReverse(up.getSource());

                        // send unicast with default bufferSize
                        System.out.println("InternetService sendUnicast internetRequest.getClientPort()="+internetRequest.getClientPort());
                        E2EComm.sendUnicast(
                                destToClient,
                                internetRequest.getClientPort(),
                                protocol,
                                //0, // default bufferSize
                                responseToTheClient
                        );
                    }
                    else{
                        // received payload is not InternetRequest: do nothing...
                        System.out.println("InternetService wrong payload: "+payload);
                    }
                }
                else{
                     // received packet is not UnicastPacket: do nothing...
                    System.out.println("InternetService wrong packet: "+gp.getClass().getName());
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
}

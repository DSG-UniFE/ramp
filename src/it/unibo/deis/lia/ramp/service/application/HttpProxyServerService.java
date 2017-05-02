package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.application.ResourceProvider.HttpProxyServerMonitoredWebServers;
import it.unibo.deis.lia.ramp.service.application.ResourceProvider.MonitoredWebServer;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;

import java.io.EOFException;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;


/**
 *
 * @author Luca Iannario
 */
public class HttpProxyServerService extends Thread{

	private static int HTTP_PROXY_SERVER_PORT_MIN_PORT = 6006;
	private static int HTTP_PROXY_SERVER_PORT_MAX_PORT = 6008;
    private static boolean active = false;
    private int protocol = E2EComm.TCP;
    
    private ResourceProvider resourceProvider;

    private BoundReceiveSocket serviceSocket;

    private static HttpProxyServerService httpProxyServer = null;
    
    
    public static synchronized HttpProxyServerService getInstance(){
        try{
            if(httpProxyServer == null){
            	active = true;
            	httpProxyServer = new HttpProxyServerService();
            	httpProxyServer.start();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return httpProxyServer;
    }
    private HttpProxyServerService() throws Exception {
    	System.out.println("HttpProxyServerService.activate");
    	for(int port = HTTP_PROXY_SERVER_PORT_MIN_PORT; port <= HTTP_PROXY_SERVER_PORT_MAX_PORT; port++){
    		try {
    			serviceSocket = E2EComm.bindPreReceive(port, protocol);
    			break;
    		} catch (Exception e) {}
    	}
    	if(serviceSocket == null)
    		throw new Exception("HttpProxyServerService cannot bind serviceSocket socket");
        ServiceManager.getInstance(false).registerService(
                "HttpProxyServer",
                serviceSocket.getLocalPort(),
                protocol
        );
    }
    
    public static boolean isActive(){
    	return active;
    }

    public void stopService(){
    	System.out.println("HttpProxyServerService.deactivate");
    	active = false;
    	ServiceManager.getInstance(false).removeService("HttpProxyServer");
    	this.resourceProvider = null;
    	httpProxyServer = null;
    }
    
    void setResourceProvider(ResourceProvider resourceProvider){
    	this.resourceProvider = resourceProvider;
    }

    @Override
    public void run(){
        try{
        	System.out.println("HttpProxyServerService START");
            while(active){
                try{
                    // receive
                    GenericPacket gp = E2EComm.receive(
                            serviceSocket,
                            10*1000
                    );
                    System.out.println("HttpProxyServerService new request");
                    new HttpProxyServerHandler(gp).start();
                }
                catch(SocketTimeoutException ste){
                    //System.out.println("HttpProxyServerService SocketTimeoutException");
                }
                catch(SocketException se){
                    //System.out.println("HttpProxyServerService SocketTimeoutException");
                }
                catch(EOFException eofe){

                }
            }
            System.out.println("HttpProxyServerService END");
        }
        catch(Exception e){
            e.printStackTrace();
        }finally{
        	try {
				serviceSocket.close();
			} catch (IOException e) {}
        }
    }
    
    private class HttpProxyServerHandler extends Thread{
        private GenericPacket gp;
        private HttpProxyServerHandler(GenericPacket gp){
            this.gp = gp;
        }
        @Override
        public void run(){
            try{
            	byte[] responseToTheClient = null;
            	String[] destToClient = null;
                if( gp instanceof UnicastPacket){
                    // 1) payload
                    UnicastPacket up = (UnicastPacket) gp;
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    destToClient = E2EComm.ipReverse(up.getSource());
                    if(payload instanceof InternetRequest){
                        //System.out.println("HttpProxyServerService InternetRequest");
                        final InternetRequest internetRequest = (InternetRequest) payload;
                        String request = new String(internetRequest.getInternetPayload());
                        System.out.println("HttpProxyServerService: internetRequest.getClientPort() " + internetRequest.getClientPort());
                        System.out.println(request.substring(0, request.indexOf(0x0A, request.indexOf(0x0A) + 1) - 1)); // print only first two lines
                        PipedInputStream pis = new PipedInputStream();
                        final PipedOutputStream pos = new PipedOutputStream(pis);
                        Thread fetchingThread = new Thread(new Runnable() {
							@Override
							public void run() {
								try {
									InternetUtil.performInternetConnection(internetRequest, pos);
									pos.flush();
									pos.close();
								} catch (Exception e) {
									//e.printStackTrace();
								}								
							}
						});
                        fetchingThread.start();

                        // send unicast with default bufferSize
                        E2EComm.sendUnicast(
                                destToClient,
                                internetRequest.getClientPort(),
                                protocol,
                                //0, // default bufferSize
                                pis
                        );
                    	
                        // mandatory otherwise this thread would end before than fetchingThread
                        // (sendUnicast is not blocking), pis would be closed, the pipe pos/pis
                        // would be broken, and fetchingThread would fail
                        fetchingThread.join(); // wait for fetchingThread to finish before proceed
                        int attempts = 0;
                        while(pis.available() > 0 && attempts++ < 100){ // allow sendUnicast to finish to send pis contents
                        	//System.out.println("HttpProxyServerService pis.available() = " + pis.available());
                        	Thread.sleep(250);
                        }
                        pis.close(); // sendUnicast can terminate correctly
                        System.out.println("HttpProxyServerService sendUnicast response finished");
                    }
                    else if(payload instanceof HttpProxyServerMonitoredWebServers){
                    	// HttpProxyclient is requesting the list of monitored web servers
                    	System.out.println("HttpProxyServerService: requested the list of monitored web servers");
                    	
                    	HttpProxyServerMonitoredWebServers response = (HttpProxyServerMonitoredWebServers) payload;
                    	if(resourceProvider != null){
                    		response.setMonitoredWebServers(resourceProvider.getMonitoredWebServers());
                    	}else{
                    		response.setMonitoredWebServers(Collections.<MonitoredWebServer>emptySet());
                    	}
                    	responseToTheClient = E2EComm.serialize(response);
                    	
                    	// send unicast with default bufferSize
                        System.out.println("HttpProxyServerService sendUnicast: httpProxyServerMonitoredWebServers.getClientPort() = " + response.getClientPort());
                        E2EComm.sendUnicast(
                                destToClient,
                                response.getClientPort(),
                                response.getProtocol(),
                                //0, // default bufferSize
                                responseToTheClient
                        );
                    }
                    else{
                        // received payload is not valid: do nothing...
                        System.out.println("HttpProxyServerService wrong payload: "+payload);
                    }
                }
                else{
                     // received packet is not UnicastPacket: do nothing...
                    System.out.println("HttpProxyServerService wrong packet: "+gp.getClass().getName());
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
}


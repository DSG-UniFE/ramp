
package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;


/**
 *
 * @author Carlo Giannelli
 */
public class InternetClient extends Thread{

    private final Object lock=new Object();

    private static int INTERNET_CLIENT_PORT = 8080;

    private Vector<ServiceResponse> availableServices;
    private Vector<Float> availableServicesWeights;
    private HashMap<ServiceResponse, Vector<Float>> throughputHistory;
    
    // only for logging
    private HashMap<ServiceResponse, BufferedWriter> availableServicesFiles;
    private long lastServiceSearch;

    private static InternetClient internetClient=null;
    private static InternetClientJFrame icj;
    private InternetClient(){
        availableServices = new Vector<ServiceResponse>();
        availableServicesWeights = new Vector<Float>();
        throughputHistory = new HashMap<ServiceResponse, Vector<Float>>();
        icj = new InternetClientJFrame(this);
    }
    public static synchronized InternetClient getInstance(){
        if(internetClient==null){
            internetClient=new InternetClient();
            internetClient.start();
        }
        icj.setVisible(true);
        return internetClient;
    }

    public void stopClient(){
        internetClient=null;
    }
    
    public Vector<Float> getAvailableServicesWeights() {
        return availableServicesWeights;
    }
    public Vector<ServiceResponse> findInternetService(int ttl, int timeout, int serviceAmount) throws Exception{

        // 1) retrieve services
        long pre = System.currentTimeMillis();
        Vector<ServiceResponse> retrievedServices = ServiceDiscovery.findServices(
                ttl,
                "Internet",
                timeout,
                serviceAmount,
                null
        );
        long post = System.currentTimeMillis();
        float elapsed = (post-pre)/(float)1000;
        System.out.println("InternetClient findInternetService elapsed="+elapsed+"    services="+retrievedServices);
        
        float averagePathLengthCounter = 0;
        for(int i=0; i<retrievedServices.size(); i++){
            averagePathLengthCounter += retrievedServices.elementAt(i).getServerDest().length;
        }
        float averagePathLength = averagePathLengthCounter / retrievedServices.size();

        // 2) compute weights (PathLength metric)
        synchronized(lock){
            availableServices = new Vector<ServiceResponse>();
            availableServicesWeights = new Vector<Float>();
            throughputHistory = new HashMap<ServiceResponse, Vector<Float>>();
            if( RampEntryPoint.isLogging() ){
                availableServicesFiles = new HashMap<ServiceResponse, BufferedWriter>();
            }
            if(retrievedServices.size()==1){
                ServiceResponse service = retrievedServices.elementAt(0);
                availableServices.addElement(service);
                availableServicesWeights.addElement(new Float(1));
            }
            else{
                for(int i=0; i<retrievedServices.size(); i++){
                    ServiceResponse service = retrievedServices.elementAt(i);
                    float weight = ( 1 - ( service.getServerDest().length / averagePathLength / retrievedServices.size() ) ) / ( retrievedServices.size() - 1 ) ;
                    availableServices.addElement(service);
                    availableServicesWeights.addElement(weight);
                    if( RampEntryPoint.isLogging() ){
                        BufferedWriter bw = new BufferedWriter(new FileWriter(new File("./temp/"+service.getServerDest()[service.getServerDest().length-1]+".csv")));
                        bw.write(
                                "timestamp, payload (KB), throughput (KB/s), weight"
                        );
                        bw.newLine();
                        bw.flush();
                        availableServicesFiles.put(service, bw);
                        lastServiceSearch=System.currentTimeMillis();
                    }
                }
            }
        }
        
        return availableServices;
    }

    String[] layer3Path = null;
    public boolean activateLayer3Path(ServiceResponse internetService){
        long start = System.currentTimeMillis();
        boolean res=false;
        try{
            it.unibo.deis.lia.ramp.core.internode.Layer3RoutingRequest l3rr = new it.unibo.deis.lia.ramp.core.internode.Layer3RoutingRequest();
            res = E2EComm.sendUnicast(
                    internetService.getServerDest(),
                    "".hashCode(),
                    internetService.getServerPort(),
                    internetService.getProtocol(),
                    true, // ack
                    20 * 1000 * internetService.getServerDest().length, // timeout ack: 10s per-hop
                    GenericPacket.UNUSED_FIELD, // bufferSize
                    GenericPacket.UNUSED_FIELD,
                    GenericPacket.UNUSED_FIELD,
                    E2EComm.serialize(l3rr)
            );
            layer3Path = internetService.getServerDest();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        long end = System.currentTimeMillis();
        long elapsed = end-start;
        System.out.println("InternetClient.activateLayer3Path elapsed="+elapsed);
        return res;
    }

    @Override
    public void run(){
        try{
            System.out.println("InternetClient START");
            ServerSocket ssFromLocalBrowser = new ServerSocket(InternetClient.INTERNET_CLIENT_PORT);
            ssFromLocalBrowser.setReuseAddress(true);
            ssFromLocalBrowser.setSoTimeout(5*1000);
            while(internetClient!=null){
                try{
                    Socket s = ssFromLocalBrowser.accept();
                    //System.out.println("InternetClient new request");
                    new InternetClientHandler(s).start();
                }
                catch(SocketTimeoutException ste){
                    //System.out.println("InternetService SocketTimeoutException");
                }
                catch(SocketException se){
                    //System.out.println("InternetService SocketTimeoutException");
                }
            }
            ssFromLocalBrowser.close();
            System.out.println("InternetClient FINISHED");
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private class InternetClientHandler extends Thread{
        private Socket sToTheLocalApplication;
        private InternetClientHandler(Socket s){
            this.sToTheLocalApplication=s;
        }
        @Override
        public void run(){
            try{
                if( availableServices==null || availableServices.size()==0 ){
                    System.out.println("InternetClient: NO AVAILABLE SERVICES; should look for services before");
                }
                else{
                	// START HTTP PARSING
                	
                    // receive data from local browser
                    InputStream is = sToTheLocalApplication.getInputStream();

                    long requestArrivalTime = System.currentTimeMillis();
                    int portRemoteEndpoint = 80;
                    int layer4Protocol = InternetRequest.TCP;
                    String ipAddressRemoteEndpoint = "";
                    String text="";
                    int contentLength=-1;
                    boolean connectionClose=false;
                    boolean chunked=false;

                    String line = InternetUtil.readLine(is); // first line
                    
                    // parsing header HTTP
                    while( line!=null && !line.equals("") ){
                        //System.out.println("InternetClient line "+line);
                        
                        if( line.toLowerCase().contains("host") ){
                            String[] tokensHost = line.split(" ")[1].split(":");
                            ipAddressRemoteEndpoint = tokensHost[0];
                            //System.out.println("\t\tipAddressRemoteEndpoint: "+ipAddressRemoteEndpoint);
                            if(tokensHost.length>1){
                                portRemoteEndpoint=Integer.parseInt(tokensHost[1]);
                                //System.out.println("\t\tportRemoteEndpoint: "+portRemoteEndpoint);
                            }
                        }
                        else if( line.toLowerCase().startsWith("layer4protocol") ){
                            String stringLayer4Protocol = line.split(" ")[1];
                            if(stringLayer4Protocol.toLowerCase().equals("tcp")){
                                layer4Protocol = InternetRequest.TCP;
                            }
                            else if(stringLayer4Protocol.toLowerCase().equals("udp")){
                                layer4Protocol = InternetRequest.UDP;
                            }
                            else{
                                System.out.println("InternetClient: unsupported layer-4 protocol: "+layer4Protocol+" (using the default: TCP)");
                            }
                        }
                        
                        if( line.toLowerCase().contains("keep-alive") ){
                        	// removing "Connection: keep-alive" header
                            //System.out.println("\t\tdeleted: "+line);
                        }
                        else{
                            text+=line+(char)0x0D+(char)0x0A;
                        }

                        if(line.contains("Content-Length")){
                            String length=line.split(" ")[1];
                            //System.out.println("InternetClient length "+length);
                            contentLength = Integer.parseInt(length);
                        }
                        else if(line.contains("Connection: close")){
                            connectionClose = true;
                        }
                        else if(line.contains("Transfer-Encoding: chunked")){
                            chunked=true;
                        }
                        
                        line = InternetUtil.readLine(is);
                    }
                    if( ! connectionClose ){
                    	// manually adding "Connection: close" header
                    	text+="Connection: close"+(char)0x0D+(char)0x0A;
                    }
                    text += "" + (char)0x0D + (char)0x0A;
                    
                    // parsing payload HTTP
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    if(chunked){
                        line = InternetUtil.readLine(is);
                        //System.out.println("InternetUtil chunked line "+line);
                        int lineLength = Integer.decode("0x"+line);
                        //System.out.println("InterneInternetUtiltService chunked lineLength "+lineLength);
                        for(int i=0; i<line.length(); i++){
                            baos.write(line.charAt(i));
                        }
                        baos.write(0x0D); // CR
                        baos.write(0x0A); // LF
                        while( ! line.equals("0") ){
                            for(int i=0; i<lineLength; i++){
                                int temp=is.read();
                                //System.out.print((char)temp);
                                baos.write(temp);
                            }
                            //System.out.println();
                            //System.out.println("InternetService chunked buf "+new String(buf));
                            baos.write(is.read()); // (char)0x0D
                            baos.write(is.read()); // (char)0x0A

                            line = InternetUtil.readLine(is);
                            //System.out.println("InternetService chunked line "+line);
                            lineLength = Integer.decode("0x"+line);
                            //System.out.println("InternetService chunked lineLength "+lineLength);
                            for(int i=0; i<line.length(); i++){
                                baos.write(line.charAt(i));
                            }
                            baos.write(0x0D); // (char)0x0D
                            baos.write(0x0A); // (char)0x0A
                        }
                        baos.write(0x0D); // (char)0x0D
                        baos.write(0x0A); // (char)0x0A
                    }
                    byte[] chunkedArray = baos.toByteArray();
                    byte[] sending=null;
                    if(contentLength==-1 && chunkedArray.length==0){
                        sending=text.getBytes();
                    }
                    else if( chunkedArray.length != 0 ){
                        sending=new byte[text.length()+chunkedArray.length];
                        System.arraycopy(text.getBytes(), 0, sending, 0, text.length());
                        System.arraycopy(chunkedArray, 0, sending, text.length(), chunkedArray.length);
                    }
                    else{
                        sending = new byte[text.length()+contentLength];
                        System.arraycopy(text.getBytes(), 0, sending, 0, text.length());

                        int temp = 0;
                        for(int i=text.length(); temp!=-1 && i<sending.length; i++){
                            temp = is.read();
                            //System.out.print(""+(char)temp);
                            sending[i] = (byte)temp;
                        }
                    }
                    float requestPayloadSize = sending.length; // byte
                    
                    // END HTTP PARSING

                    BoundReceiveSocket receiveSocket = E2EComm.bindPreReceive(layer4Protocol);
                    InternetRequest ir = new InternetRequest(
                            ipAddressRemoteEndpoint,
                            portRemoteEndpoint,
                            receiveSocket.getLocalPort(),
                            layer4Protocol,
                            sending
                    );

                    // previous trivial service selection
                    //int selectedService = serviceCounter % availableServices.size();
                    //serviceCounter++;

                    // weight-based service selection
                    int selectedServiceNumber=-1;
                    float randomValue = RampEntryPoint.nextRandomFloat();
                    float weigthsCounter=0;
                    float currentServiceWeight=-1;
                    for(int i=0; selectedServiceNumber==-1 && i<availableServicesWeights.size(); i++){
                        currentServiceWeight = availableServicesWeights.elementAt(i);
                        weigthsCounter += currentServiceWeight;
                        //System.out.println("\tInternetClient  i="+i+"  weigthsCounter="+weigthsCounter+"  randomValue="+randomValue);
                        if(randomValue<=weigthsCounter){
                            selectedServiceNumber=i;
                        }
                    }
                    ServiceResponse selectedService = availableServices.elementAt(selectedServiceNumber);
                    //System.out.println("\tInternetClient selectedService="+selectedService+" weight="+availableServicesWeights.elementAt(selectedServiceNumber));

                    long pre = System.currentTimeMillis();
                    byte [] responseToTheApplication = null;

                    boolean samePath = true;
                    if(layer3Path==null){
                        samePath = false;
                    }
                    else if(layer3Path.length != selectedService.getServerDest().length){
                        samePath = false;
                    }
                    else{
                        for(int i=0; i<layer3Path.length && samePath==true; i++ ){
                            if(!layer3Path[i].equals(selectedService.getServerDest()[i])){
                                samePath = false;
                            }
                        }
                    }
                    
                    if( samePath ){
                        // exploit the OS-based layer-3 multi-hop path
                        System.out.println("\tInternetClient OS-based layer-3 multi-hop path to "+Arrays.toString(selectedService.getServerDest()));
                        responseToTheApplication = InternetUtil.performInternetConnection(ir);
                    }
                    else{
                        // exploit the RAMP-based layer-7 multi-hop path
                        System.out.println("\tInternetClient RAMP-based layer-7 multi-hop path to "+Arrays.toString(selectedService.getServerDest()));

                        // send unicast with default bufferSize
                        //System.out.println("InternetClient sendUnicast");
                        E2EComm.sendUnicast(
                                selectedService.getServerDest(),
                                selectedService.getServerPort(),
                                selectedService.getProtocol(),
                                //0, // default bufferSize
                                E2EComm.serialize(ir)
                        );

                        // receive from the server
                        //System.out.println("InternetClient receive and close");
                        // XXX tune timeout value...
                        UnicastPacket up = (UnicastPacket)E2EComm.receive(
                                receiveSocket,
                                30*1000
                        );
                        receiveSocket.close();
                        responseToTheApplication = up.getBytePayload();

                    }
                    long post = System.currentTimeMillis();
                    float elapsed = post-pre; // milliseconds

                    // send data to the local application
                    OutputStream os = sToTheLocalApplication.getOutputStream();
                    os.write(responseToTheApplication);
                    os.flush();

                    sToTheLocalApplication.close();
                    // communication with application finished!!!
                    
                    

                    // calculate and save throughput to update weights
                    float responsePayloadSize = responseToTheApplication.length; // byte
                    float throughput = ( (requestPayloadSize+responsePayloadSize)/1024) / (elapsed/1000); // KB/s
                    //System.out.println("\tInternetClient throughput "+throughput);
                    //System.out.println("InternetClient receive: selectedService="+selectedService+"  throughput(KB/s)="+throughput+"  elapsed(ms)="+elapsed+"  payloadSize(byte)="+(requestPayloadSize+responsePayloadSize));
                    
                    synchronized(lock){
                        Vector<Float> historyVector = throughputHistory.get(selectedService);
                        if(historyVector==null){
                            historyVector = new Vector<Float>();
                            throughputHistory.put(selectedService, historyVector);
                        }
                        historyVector.addElement(throughput);

                        if( RampEntryPoint.isLogging() ){
                            BufferedWriter bw = availableServicesFiles.get(selectedService);
                            bw.write(
                                    (requestArrivalTime-lastServiceSearch)/1000.0+", "+
                                    ((requestPayloadSize+responsePayloadSize)/1024)+", "+
                                    throughput+", "+
                                    currentServiceWeight
                            );
                            bw.newLine();
                            bw.flush();
                        }



                        // use throughput history to update weights
                        //System.out.println("\tInternetClient selectedService="+selectedService+"  historyVector(KB/s)="+historyVector);
                        if(historyVector.size()>20){
                            System.out.println("InternetClient weight update");

                            Vector<Float> currentThroughputs = new Vector<Float>();
                            for(int i=0; i<availableServices.size(); i++){
                                ServiceResponse aService = availableServices.elementAt(i);
                                Vector<Float> aHistoryVector = throughputHistory.get(aService);
                                //System.out.println("\tInternetClient aService "+aService+"   aHistoryVector "+aHistoryVector);

                                float averagePerServiceThroughput = 0;
                                if(aHistoryVector==null || aHistoryVector.size()==0){
                                    // never used this path
                                }
                                else{
                                    float throughputCounter = 0;
                                    for(int j=0; j<aHistoryVector.size(); j++){
                                        throughputCounter += aHistoryVector.elementAt(j);
                                    }
                                    averagePerServiceThroughput = throughputCounter / aHistoryVector.size();
                                }
                                currentThroughputs.addElement(averagePerServiceThroughput);

                                aHistoryVector = new Vector<Float>();
                                throughputHistory.put(aService, aHistoryVector);
                            }

                            float throughputCounter = 0;
                            for(int i=0; i<currentThroughputs.size(); i++){
                                throughputCounter += currentThroughputs.elementAt(i);
                            }
                            float averageTotalThroughput = throughputCounter / currentThroughputs.size();



                            availableServicesWeights = new Vector<Float>();
                            if(availableServices.size()==1){
                                availableServicesWeights.addElement(new Float(1));
                            }
                            else{
                                for(int i=0; i<availableServices.size(); i++){
                                    float weight = currentThroughputs.elementAt(i) / averageTotalThroughput / availableServices.size() ;
                                    availableServicesWeights.addElement(weight);
                                }
                            }
                            System.out.println("\tInternetClient update weights: availableServices="+availableServices);
                            System.out.println("\tInternetClient update weights: availableServicesWeights="+availableServicesWeights);

                        }
                   }// end synchronized

                    //System.out.println("InternetClient received textPayload SENT");
                }
            }
            catch(Exception e){
                System.out.println("InternetClient: "+e.getMessage());
                e.printStackTrace();
            }
        }
    }

}

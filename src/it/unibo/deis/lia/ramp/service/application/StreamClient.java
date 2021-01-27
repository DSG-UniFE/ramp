/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.service.management.*;
import it.unibo.deis.lia.ramp.core.e2e.*;
import it.unibo.deis.lia.ramp.*;

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author useruser
 */
public class StreamClient{

    private String vlcDirectory;
    private String vlc;

    private static StreamClient streamClient=null;
    private static StreamClientJFrame scjf;
    private StreamClient(){
        vlc="vlc";
        if(RampEntryPoint.os.startsWith("linux")){
            vlcDirectory="";
            if(RampEntryPoint.os.startsWith("linux")){
                try {
                    Process pId = Runtime.getRuntime().exec("id -ru");
                    BufferedReader brId = new BufferedReader(new InputStreamReader(pId.getInputStream()));
                    String lineId;
                    lineId = brId.readLine();
                    if (lineId.equals("0")) {
                        vlc = "vlc-wrapper";
                    }
                } catch (IOException ex) {
                    Logger.getLogger(StreamClient.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        else if(RampEntryPoint.os.startsWith("mac")){
            vlcDirectory="/Applications/VLC.app/Contents/MacOS/";
        }
        else if(RampEntryPoint.os.startsWith("windows")){
            vlcDirectory="C:/VLC-1.1.12/";
        }
        else{
            vlcDirectory="???";
        }
        scjf = new StreamClientJFrame(this);
    }
    public static synchronized StreamClient getInstance(){
        if(streamClient==null){
            streamClient=new StreamClient();
        }
        scjf.setVisible(true);
        return streamClient;
    }

    public void stopClient(){
        streamClient=null;
    }

    public String getVlcDirectory() {
        System.out.println("StreamClient getVlcDirectory "+vlcDirectory);
        return vlcDirectory;
    }
    public void setVlcDirectory(String vlcDirectory) {
        System.out.println("StreamClient setVlcDirectory "+ vlcDirectory);
        if(RampEntryPoint.os.startsWith("windows")) {
            String charToReplace = "";
            if(vlcDirectory.contains("\\")) {
                charToReplace = "\\";
            } else if (vlcDirectory.contains("/")) {
                charToReplace = "/";
            }
            vlcDirectory = (vlcDirectory.replace(charToReplace,"\\\\"));
            this.vlcDirectory = vlcDirectory + "\\\\";
        } else {
            this.vlcDirectory = vlcDirectory + "/";
        }
    }
    
    public Vector<ServiceResponse> findStreamService(int ttl, int timeout, int serviceAmount) throws Exception{
        long pre = System.currentTimeMillis();
        Vector<ServiceResponse> services = ServiceDiscovery.findServices(
                ttl,
                "Stream",
                timeout,
                serviceAmount,
                null
        );
        long post = System.currentTimeMillis();
        float elapsed = (post-pre)/(float)1000;
        System.out.println("StreamClient findStreamService elapsed="+elapsed+"    services="+services);
        return services;
    }

    public String[] streamList(ServiceResponse service) throws Exception{
        BoundReceiveSocket receiveSocket = E2EComm.bindPreReceive(service.getProtocol());
        int clientPort = receiveSocket.getLocalPort();

        StreamRequest sr=new StreamRequest("list", clientPort);
        E2EComm.sendUnicast(
        		service.getServerDest(), 
        		service.getServerPort(), 
        		service.getProtocol(),
                E2EComm.serialize(sr)
    		);
        
        // receive the stream list
        UnicastPacket up = (UnicastPacket)E2EComm.receive(receiveSocket, 3*1000);
        receiveSocket.close();
        String[] availableStreams= (String[])E2EComm.deserialize(up.getBytePayload());
        return availableStreams;
    }

    public void getStream(ServiceResponse service, String stream, String streamProtocol, String rampProtocol) throws Exception{
        int sendProtocol;
        if(rampProtocol.equals("udp")){
            sendProtocol=E2EComm.UDP;
        }
        else{
            sendProtocol=E2EComm.TCP;
        }
        BoundReceiveSocket receiveSocket = E2EComm.bindPreReceive(sendProtocol);
        int clientPort = receiveSocket.getLocalPort();

        StreamRequest sr=new StreamRequest(stream, clientPort, streamProtocol, rampProtocol);
        System.out.println("StreamClient requiring "+stream);
        E2EComm.sendUnicast(
                service.getServerDest(),
                service.getServerPort(),
                service.getProtocol(),
                E2EComm.serialize(sr)
        );

        // prepare receiving packets and sending them to the local vlc player

        // vlc in a protable manner
        DatagramSocket vlcDs = new DatagramSocket();
        vlcDs.setReuseAddress(true);
        int vlcPlayerPort = vlcDs.getLocalPort();
        vlcDs.close();

        String access = null;
        if(streamProtocol.contains("RTP")){
            access = "rtp";
        }
        else{
            access = "udp";
        }

        String[] comArray={
            vlcDirectory+vlc,
            access+"://@:"+vlcPlayerPort,
            "vlc://quit"
        };

       System.out.print("StreamClient comArray ");
       for(String s : comArray){
           System.out.print(s+" ");
       }
       System.out.println();
       Runtime.getRuntime().exec(comArray);
        
        byte[] received=new byte[1];
        DatagramSocket dsToLocalVLC = new DatagramSocket();
        System.out.println("StreamClient starting to receive packets");
        int timeout = 25*1000;

        Vector<Float> intervals=new Vector<Float>();
        Vector<Integer> payloads=new Vector<Integer>();
        Vector<Integer> packets=new Vector<Integer>();

        //try{
            boolean active = true;
            do{
                // a) receive a piece of stream
                long pre = System.currentTimeMillis();
                try{
                    UnicastPacket up = (UnicastPacket)E2EComm.receive(
                            receiveSocket,
                            timeout
                    );

                    if( RampEntryPoint.isLogging() ){
                        long post = System.currentTimeMillis();
                        float elapsed = post-pre;
                        int payloadSize = up.getBytePayload().length;
                        //System.out.println("StreamClient payloadSize="+payloadSize);
                        int packetSize = E2EComm.objectSizePacket(up);
                        //System.out.println("StreamClient packetSize="+packetSize);
                        intervals.addElement(elapsed);
                        payloads.addElement(payloadSize);
                        packets.addElement(packetSize);
                    }

                    timeout = 5*1000;

                    // b) send the piece of stream to the local vlc client
                    received = up.getBytePayload();
                    DatagramPacket dp = new DatagramPacket(
                            received,
                            received.length,
                            InetAddress.getLocalHost(),
                            vlcPlayerPort
                    );
                    dsToLocalVLC.send(dp);

                }
                catch(SocketTimeoutException ste){
                    //throw ste;
                    System.out.println("StreamClient timeout");
                    active=false;
                }
                catch(SocketException se){
                    se.printStackTrace();
                    receiveSocket = E2EComm.bindPreReceive(clientPort, sendProtocol);
                }
                catch(EOFException eofe){
                    System.out.println("StreamClient EOF");
                    active=false;
                    //e.printStackTrace();
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }while(received.length>0 && active); // until End Of Stream
        /*}
        catch(SocketTimeoutException ste){
            System.out.println("StreamClient timeout");
        }*/
        dsToLocalVLC.close();
        receiveSocket.close();
        System.out.println("StreamClient FINISHED");

        if( RampEntryPoint.isLogging() ){
            float count=0;
            int i;
            FileWriter file = new FileWriter("./temp/packet_interval.csv");
            for(i=0; i<intervals.size(); i++){
                count+=intervals.elementAt(i);
                file.write(intervals.elementAt(i)+"\n");
            }
            file.close();
            float totElapsed=count;
            float average=totElapsed/(float)i;
            count=0;
            for(i=0; i<intervals.size(); i++){
                count+=(intervals.elementAt(i)-average)*(intervals.elementAt(i)-average);
            }
            double stdDeviation=Math.sqrt(count/(double)i);
            count=0;
            for(i=0; i<payloads.size(); i++){
                count+=payloads.elementAt(i);
            }
            int byteAmountPayload=(int)count;
            count=0;
            for(i=0; i<packets.size(); i++){
                count+=packets.elementAt(i);
            }
            int byteAmountPackets=(int)count;
            float bitratePayload=(byteAmountPayload*8)/(totElapsed/1000)/1024; // kbit/s
            float bitratePackets=(byteAmountPackets*8)/(totElapsed/1000)/1024; // kbit/s
            System.out.println("\n\nStreamClient \n" +
                    "totElapsed="+totElapsed/1000+" (s)   \n" +
                    "average="+average+" (ms)   \n" +
                    "stdDeviation="+stdDeviation+" (ms)   \n" +
                    "byteAmountPayload="+byteAmountPayload/1024+" (kbyte)   \n" +
                    "byteAmountPackets="+byteAmountPackets/1024+" (kbyte)    \n" +
                    "bitratePayload="+bitratePayload+" (kbit/s)    \n" +
                    "bitratePackets="+bitratePackets+" (kbit/s)    \n");
        }
    }
}


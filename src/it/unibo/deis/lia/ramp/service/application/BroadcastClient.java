/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.service.application.mpeg.*;

import it.unibo.deis.lia.ramp.service.management.*;
import it.unibo.deis.lia.ramp.core.e2e.*;
import it.unibo.deis.lia.ramp.*;

import java.util.*;
import java.net.*;
import java.io.*;

/**
 *
 * @author useruser
 */
public class BroadcastClient extends Thread{

    //private static final int CLIENT_REFRESH = 5000;

    // simple program name (without "SA=X" prefix) ==> service program handler
    private Hashtable<String,BroadcastClientProgramHandler> activeProgramsDB = new Hashtable<String,BroadcastClientProgramHandler>();

    private String vlcDirectory = null;
    private String vlcCommand = null;

    private static BroadcastClient broadcastClient = null;
    private static BroadcastClientJFrame bcjf = null;
    
    private BroadcastClient(){
		if( RampEntryPoint.getAndroidContext()==null ){
			// NOT Android
	        vlcCommand = "vlc";
	        if(RampEntryPoint.os.startsWith("linux")){
	            vlcDirectory = "";
	            try{
	                Process pId = Runtime.getRuntime().exec("id -ru");
	                BufferedReader brId = new BufferedReader(new InputStreamReader(pId.getInputStream()));
	                String lineId;
	                lineId = brId.readLine();
	                if(lineId.equals("0")){
	                    vlcCommand = "vlc-wrapper";
	                }
	            }
	            catch(Exception e){
	                e.printStackTrace();
	            }
	        }
	        else if(RampEntryPoint.os.startsWith("mac")){
	            vlcDirectory = "/Applications/VLC.app/Contents/MacOS/";
	        }
	        else if(RampEntryPoint.os.startsWith("windows")){
	            vlcDirectory = "C:/VLC-1.1.12/";
	        }
	        else{
	            vlcDirectory = "???";
	        }
			bcjf = new BroadcastClientJFrame(this);
        }
        else{
            if( RampEntryPoint.getAndroidContext() != null ){
            	// Android
            	//vlcDirectory =  android.os.Environment.getExternalStorageDirectory()+"/ramp";
            }
        }
    }
    public static synchronized BroadcastClient getInstance(){
        if( broadcastClient == null ){
        	broadcastClient = new BroadcastClient();
        	broadcastClient.start();
        }
        if(bcjf != null){
        	bcjf.setVisible(true);
        }
        return broadcastClient;
    }
    public static synchronized BroadcastClient getInstanceNoShow(boolean force){
        if( force && broadcastClient == null ){
        	broadcastClient = new BroadcastClient();
        	broadcastClient.start();
        }
        return broadcastClient;
    }
    public static BroadcastClient getInstanceNoForce(){
    	return broadcastClient;
    }
    
    public void run(){
    	System.out.println("BroadcastClient.run() START");
    	while( broadcastClient != null ){
    		try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		if( ! RampEntryPoint.isActive() ){
    			broadcastClient.stopClient();
    		}
    	}
    	System.out.println("BroadcastClient.run() FINISHED");
    }
    
	/*private BroadcastClient(){
        vlcCommand = "vlc";
        if(RampEntryPoint.os.startsWith("linux")){
            vlcDirectory="";
            try{
                Process pId = Runtime.getRuntime().exec("id -ru");
                BufferedReader brId = new BufferedReader(new InputStreamReader(pId.getInputStream()));
                String lineId;
                lineId=brId.readLine();
                if(lineId.equals("0")){
                    vlcCommand = "vlc-wrapper";
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        else if(RampEntryPoint.os.startsWith("mac")){
            vlcDirectory="/Applications/VLC.app/Contents/MacOS/";
        }
        else if(RampEntryPoint.os.startsWith("windows")){
            vlcDirectory="C:/Program Files/VideoLAN/VLC/";
        }
        else{
            vlcDirectory="???";
        }
        bcjf = new BroadcastClientJFrame(this);
    }
    public static synchronized BroadcastClient getInstance(){
        if(broadcastClient==null){
            broadcastClient=new BroadcastClient();
        }
        bcjf.setVisible(true);
        return broadcastClient;
    }
    public static synchronized BroadcastClient getInstanceNoShow(){
        if(broadcastClient==null){
            broadcastClient=new BroadcastClient();
        }
        return broadcastClient;
    }*/

    public void stopClient(){
        if(bcjf!=null)
        	bcjf.setVisible(false);
        // stop every program handler
        String[] programList = getActivePrograms();
        for(int i=0; i<programList.length; i++){
            BroadcastClientProgramHandler ph = activeProgramsDB.get(programList[i]);
            ph.stopProgramHandler();
            activeProgramsDB.remove(programList[i]);
        }
    	broadcastClient = null;
    }
    public static BroadcastClientJFrame getBcjf() {
		return bcjf;
	}

    public String getVlcDirectory() {
        //System.out.println("BroadcastClient getVlcDirectory "+vlcDirectory);
        return vlcDirectory;
    }
    public void setVlcDirectory(String vlcDirectory) {
        System.out.println("BroadcastClient setVlcDirectory "+vlcDirectory);
        this.vlcDirectory = vlcDirectory+"/";
    }
    
    public Vector<ServiceResponse> findBroadcastService(int ttl, int timeout, int serviceAmount) throws Exception{
        //long pre = System.currentTimeMillis();
        Vector<ServiceResponse> services = ServiceDiscovery.findServices(
                ttl,
                "Broadcast",
                timeout,
                serviceAmount,
                null);
        //long post = System.currentTimeMillis();
        //float elapsed = (post-pre)/(float)1000;
        //System.out.println("BroadcastClient findBroadcastService elapsed="+elapsed+"    services="+services);
        return services;
    }

    public String[] programList(ServiceResponse service) throws Exception{
        BoundReceiveSocket clientSocket = E2EComm.bindPreReceive(service.getProtocol());
        int clientPort = clientSocket.getLocalPort();
        BroadcastRequest sr=new BroadcastRequest(null, clientPort, "list");
        E2EComm.sendUnicast(
                service.getServerDest(),
                service.getServerPort(),
                service.getProtocol(),
                E2EComm.serialize(sr)
            );
        
        // receive the program list
        String[] availablePrograms = null;
        try{
            //long pre = System.currentTimeMillis();
            UnicastPacket up = (UnicastPacket)E2EComm.receive(
                    clientSocket,
                    3*1000
            );
            //long post = System.currentTimeMillis();
            clientSocket.close();
            //availablePrograms = (String[])ApplicationUtil.byte2Object(up.getBytePayload());
            availablePrograms = (String[])E2EComm.deserialize(up.getBytePayload());
            //long elapsed = post - pre;
            //System.out.println("BroadcastClient programList service="+service+": elapsed="+elapsed+" "+Arrays.toString(availablePrograms));
        }
        catch(Exception e){
            //e.printStackTrace();
            System.out.println("BroadcastClient programList service="+service+": "+e.getMessage());
        }
        return availablePrograms;
    }

    public int getProgram(final ServiceResponse service, final String programName) throws Exception{
        BroadcastClientProgramHandler oldPh = activeProgramsDB.remove(BroadcastClient.getSimpleProgramName(programName));
        if(oldPh!=null){
            oldPh.stopProgramHandler();
        }
        BroadcastClientProgramHandler ph = new BroadcastClientProgramHandler(
                service,
                BroadcastClient.getSimpleProgramName(programName),
                BroadcastClient.getSplitterAmount(programName)
        );
        ph.start();
        activeProgramsDB.put(BroadcastClient.getSimpleProgramName(programName), ph);
        while(ph.getMediaPlayerPort() == -1){
        	Thread.sleep(50);
        }
        return ph.getMediaPlayerPort();
    }
    
    public void stopProgram(String programName){
        BroadcastClientProgramHandler ph = activeProgramsDB.remove(programName);
        if(ph!=null){
            ph.stopProgramHandler();
        }
    }

    public void showProgramConfig(String simpleProgramName){
        BroadcastClientProgramHandler ph = activeProgramsDB.get(simpleProgramName);
        if(ph!=null){
            ph.showProgramConfig();
        }
    }

    public String[] getActivePrograms(){
        Set<String> nameSet = activeProgramsDB.keySet();
        String[] programNames = nameSet.toArray(new String[0]);
        for(int i=0; i<programNames.length; i++){
            BroadcastClientProgramHandler ph = activeProgramsDB.get(programNames[i]);
            if(!ph.isActive()){
                activeProgramsDB.remove(programNames[i]);
            }
        }
        nameSet = activeProgramsDB.keySet();
        String[] res = nameSet.toArray(new String[0]);
        return res;
    }

    protected class BroadcastClientProgramHandler extends Thread{
        private String simpleProgramName; // without "SA=X" prefix
        private byte splitterAmount;
        private boolean active = true;
        private boolean manualQualityMonitorEnabled = false;
        private BroadcastClientProgramConfigJFrame programConfig = null;
        private ProgramRediscovery programRediscovery = null;
        private int clientLocalPort;
        private int mediaPlayerPort = -1;
        
        private ServiceResponse service;
        private ServiceResponse previousService = null;
        
        // quality tailor parameters
        protected float rtpIn = 5.0F;   // %
        protected float rtpDe = 15.0F;  // 7.5F;   // %
        protected float pdvIn = 0.4F; // 0.5F;   // ratio
        protected float pdvDe = 1.0F; //1.0F;   // 1.5F;   // ratio

        private int monitorWindowSize;
        private int consecutiveSocketTimeoutExceptions = 0;

        private boolean fakeClient;
        
        protected BroadcastClientProgramHandler(ServiceResponse service, String simpleProgramName, byte splitterAmount) {
            this.simpleProgramName = simpleProgramName;
            this.splitterAmount = splitterAmount;
            this.service = service;
            if( bcjf != null ){
            	this.programConfig = new BroadcastClientProgramConfigJFrame(this);
                this.programConfig.setTitle("BroadcastClient: "+simpleProgramName);
            }
            this.fakeClient = false;
        }
        protected BroadcastClientProgramHandler(ServiceResponse service, String simpleProgramName, byte splitterAmount, boolean fakeClient) {
            this.simpleProgramName = simpleProgramName;
            this.splitterAmount = splitterAmount;
            this.service = service;
            this.fakeClient = fakeClient;
            if(fakeClient){
                System.out.println("BroadcastClient fakeClient this.service: "+this.service);
            }
            else{
            	if( bcjf != null ){
	                this.programConfig = new BroadcastClientProgramConfigJFrame(this);
	                this.programConfig.setTitle("BroadcastClient: "+simpleProgramName);
            	}
            }
        }
        
        protected void stopProgramHandler(){
        	System.out.println("BroadcastClientProgramHandler stopProgramHandler "+this.simpleProgramName);
            //this.active = false;
            try{
                sendMessageToServiceHandler("stop");
            }
            catch(Exception e){
                e.printStackTrace();
            }
            this.active = false;
        }
        
        public int getMediaPlayerPort() {
			return mediaPlayerPort;
		}
        
		public boolean isActive() {
            return active;
        }
        
        protected void showProgramConfig(){
            programConfig.setVisible(true);
        }

        protected String getProgramName() {
            return simpleProgramName;
        }

        protected byte getSplitterAmount(){
            return splitterAmount;
        }

        synchronized protected void enableDynamicClient() {
            System.out.println("BroadcastClientProgramHandler enableDynamicClient "+this.simpleProgramName);
            if(programRediscovery==null){
                programRediscovery = new ProgramRediscovery(this);
                programRediscovery.start();
            }
        }
        synchronized protected void disableDynamicClient() {
            System.out.println("BroadcastClientProgramHandler disableDynamicClient "+this.simpleProgramName);
            if(programRediscovery!=null){
                programRediscovery.stopProgramRediscover();
                programRediscovery = null;
            }
        }

        protected void enableManualQualityMonitor(){
            manualQualityMonitorEnabled = true;
        }
        protected void disableManualQualityMonitor(){
            manualQualityMonitorEnabled = false;
        }
        protected void sendMessageToServiceHandler(String message) throws Exception{
            //System.out.println("BroadcastClient sendMessage message="+message+" programName="+simpleProgramName);
            sendMessageToServiceHandler(message, this.service);
        }
        protected void sendMessageToServiceHandler(String message, ServiceResponse aService) throws Exception{
            //System.out.println("BroadcastClient sendMessage message="+message+" programName="+simpleProgramName+" aService="+aService+" "+System.currentTimeMillis());
            if(aService!=null){
                BroadcastRequest sendingRequest = new BroadcastRequest(
                        simpleProgramName,
                        clientLocalPort,
                        message
                );
                E2EComm.sendUnicast(
                        aService.getServerDest(),
                        aService.getServerPort(),
                        aService.getProtocol(),
                        E2EComm.serialize(sendingRequest)
                );
            }
        }


        private FileWriter broadcastClientQualityLogFile = null;
        @Override
        public void run(){

            // only for logging
            Vector<Integer> interPacketIntervalVector = new Vector<Integer>();
            long startArrival = System.nanoTime()/1000; // micros
            Vector<Float> arrivals = new Vector<Float>();
            Vector<Long> currentTimes = new Vector<Long>();
            Vector<Short> rtpSequenceNumbers = new Vector<Short>();
            //Vector<Integer> rtpTimestamps = new Vector<Integer>();
            Vector<Integer> tsPacketTypes = new Vector<Integer>();
            int pidAudio = -1;
            int pidVideo = -1;
            //String currentVideoType = "";
            int currentVideoType = TSPacket.UNDEFINED;
            //int currentType = -1;

            try{
                System.out.println("BroadcastClient requiring "+simpleProgramName);

                if( RampEntryPoint.isLogging() ){
                    broadcastClientQualityLogFile = new FileWriter("./temp/BroadcastClientQuality.csv");
                    broadcastClientQualityLogFile.write("current time (ms),Stream Jitter,Lost Packets (%),consecutiveSocketTimeoutExceptions,message\n");
                }

                BoundReceiveSocket clientSocket = E2EComm.bindPreReceive(service.getProtocol());
                clientLocalPort = clientSocket.getLocalPort();

                // register
                this.sendMessageToServiceHandler(null);

                // check quality
                monitorWindowSize = 25; // starting value, refined in relation to the monitoring period
                Vector<BroadcastClientQualityData> qualityRampPacketVector = new Vector<BroadcastClientQualityData>();
                QualityMonitor qm = new QualityMonitor(qualityRampPacketVector, this);
                qm.start();

                DatagramSocket dsToLocalVLC = null;
                
                if( !fakeClient ){
                    // prepare receiving packets and sending them to the local media player
                    DatagramSocket dsMediaClient = new DatagramSocket();
                    dsMediaClient.setReuseAddress(true);
                    mediaPlayerPort = dsMediaClient.getLocalPort();
                    dsMediaClient.close();
                    
                    if( RampEntryPoint.getAndroidContext() == null ){
	                    String[] comArray = null;
	                    comArray = new String[]{
	                        vlcDirectory+vlcCommand
	                        //,"-vvv"
	                        ,"--rtp-caching","500" //"250"  // millis
	                        //,"--rtp-timeout","15"    // seconds
	                        //,"--rtp-max-dropout","1"
	                        //,"--rtp-max-misorder","1"
	                        ,"--rtp-max-src","3"
	                        ,"rtp://@:"+mediaPlayerPort
	                        //,"vlc://quit"
	                    };
	
	                    System.out.print("BroadcastClient comArray: ");
	                    for(String s : comArray){
	                        System.out.print(s+" ");
	                    }
	                    System.out.println();
	
	                    List<String> comList = Arrays.asList(comArray);
	                    ProcessBuilder pbVlcClient = new ProcessBuilder(comList);
	                    pbVlcClient.start();
                    }
                    else{
                        System.out.println("BroadcastClient MediaPlayer Android");
                    	/*android.media.MediaPlayer mp = new android.media.MediaPlayer();
                    	mp.setDataSource(
                			RampEntryPoint.getAndroidContext(), 
                			android.net.Uri.parse("udp://127.0.0.1:"+mediaPlayerPort+"")
            			);
                    	//mp.prepare();
                    	mp.start();*/
                        /*
                        android.widget.VideoView videoView = (android.widget.VideoView) findViewById(R.id.VideoView);
                        android.widget.MediaController mediaController = new android.widget.MediaController(RampEntryPoint.getAndroidContext());
                        mediaController.setAnchorView(videoView);
                        // Set video link (mp4 format )
                        Uri video = Uri.parse("udp://127.0.0.1:"+mediaPlayerPort+"");
                        videoView.setMediaController(mediaController);
                        videoView.setVideoURI(video);
                        videoView.start();*/
                    }

                    dsToLocalVLC = new DatagramSocket();
                }
                System.out.println("BroadcastClient starting to receive packets");
                int timeout = 500; // timeout BroadcastClient (ms)

                long previousRampArrival = System.nanoTime()/1000;
                int previousRampInterPacketInterval = -1;
                long lastAck = -1;
                int serviceTimeout = 5; // seconds
                
                int previousRtpTimestamp = -1;
                int previousRtpInterTimestamp = -1;

                while(active){
                    try{
                        if( consecutiveSocketTimeoutExceptions*timeout > 30*1000 ){
                            // stop the client
                            System.out.println("BroadcastClient: stop the client");
                            this.active = false;
                        }
                        else if( programRediscovery!=null
                        		&& service!=null
                                && System.nanoTime()/1000-previousRampArrival > serviceTimeout*1000*1000 ){
                            // invalidate the current service
                            System.out.println("BroadcastClient: not receive the program "+this.simpleProgramName+" from "+this.service);
                            if( RampEntryPoint.isLogging() ){
                                System.out.println("BroadcastClient: not receive the program "+this.simpleProgramName+" from "+this.service+" "+System.currentTimeMillis());
                            }
                            this.service = null;
                            // start a program rediscovery immediately
                            //Thread t = new Thread(
                            //    new Runnable(){
                            //        @Override
                             //       public void run() {
                                        try {
                                        	System.out.println("BroadcastClient: TIMEOUT start a program rediscovery immediately for "+simpleProgramName+" "+System.currentTimeMillis());
                                        	this.service = null;
                                            programRediscovery.discover(5,1000,1);
                                            if( this.service == null ){
                                                programRediscovery.discover(5,1000,2);
                                            }
                                        }
                                        catch (Exception ex) {
                                            ex.printStackTrace();
                                        }
                             //       }
                            //    }
                            //);
                            //t.start();
                        }

                        // 1) receive packet
                        UnicastPacket up = (UnicastPacket)E2EComm.receive(
                                clientSocket,
                                timeout
                        );
                        consecutiveSocketTimeoutExceptions = 0;
                        serviceTimeout = 2;

                        // check if the packet sender is actually the current stream source
                        if( this.service==null ){
                            System.out.println("BroadcastClient: no active service");
                        }
                        else if( !Arrays.equals(E2EComm.ipReverse(up.getSource()), this.service.getServerDest())
                                && !(previousService!=null && Arrays.equals(E2EComm.ipReverse(up.getSource()), previousService.getServerDest()) ) ){
                            System.out.println("BroadcastClient: packet received from a different streamer");
                            System.out.print("up.getSource(): ");
                            for(String s : up.getSource()){
                                System.out.print(s+" ");
                            }
                            System.out.println();
                            System.out.print("this.service.getServerDest(): ");
                            for(String s : this.service.getServerDest()){
                                System.out.print(s+" ");
                            }
                            System.out.println();
                        }
                        else{
                            if(previousService!=null && Arrays.equals(up.getSource(), this.service.getServerDest()) ){
                                System.out.println("BroadcastClient: first packet of the new streamer "+System.currentTimeMillis());
                                /*if(RampProperties.logging){
                                    System.out.println("BroadcastClient: first packet of the new streamer "+System.currentTimeMillis());
                                }*/
                                // send stop to the previous service
                                this.sendMessageToServiceHandler("stop", previousService);
                                previousService = null;
                            }
                            long currentRampArrival = System.nanoTime()/1000; // micros

                            //System.out.println("BroadcastClient: up.getBytePayload().length "+up.getBytePayload().length);
                            if( up.getBytePayload().length == 0 ){
                            	// FINISHED STREAM
                            	//Thread t = new Thread(
                            	//	new Runnable(){
                            	//        public void run(){
                            	        	try {
                                            	System.out.println("BroadcastClient: FINISHED SOURCE (byte[0]) start a program rediscovery immediately for "+simpleProgramName+" "+System.currentTimeMillis());
                                            	this.service = null;
                                                programRediscovery.discover(5,1000,1);
                                                if( this.service == null ){
                                                    programRediscovery.discover(5,1000,2);
                                                }
											} catch (Exception e) {
												e.printStackTrace();
											}
                            	//        }
                            	//	}
                            	//);
                            	//t.start();
                            }
                            else{
                            	// receive either RTP objects or raw bytes
                                byte[] receivedBytes;
                                Object received = null;
                                
                                try{
	                            	received = E2EComm.deserialize(up.getBytePayload());
		                            //if(received instanceof RTP){
		                                RTP rtp = (RTP)received;
		                                receivedBytes = rtp.getBytes();
		                                byte newSplitterAmount = rtp.getSplitterAmount();
		
		                                if(previousService!=null && Arrays.equals(up.getSource(), previousService.getServerDest())){
		                                    System.out.println("BroadcastClient: inter-service transition");
		                                }
		                                else{
		                                    if(splitterAmount != newSplitterAmount){
		                                        System.out.println("BroadcastClient: new splitterAmount from "+splitterAmount+" to "+newSplitterAmount);
		                                        splitterAmount = newSplitterAmount;
		                                    }
		                                }
		                            //}
	                            }
	                            catch(java.io.StreamCorruptedException sce){
	                            	receivedBytes = up.getBytePayload();
	                            }
	                            catch(Exception e){
	                            	e.printStackTrace();
	                            	receivedBytes = null;
	                            	//Thread t = new Thread(
	                            	//	new Runnable(){
	                            	//        public void run(){
	                            	        	try {
	                                            	System.out.println("BroadcastClient: FINISHED SOURCE (null) start a program rediscovery immediately for "+simpleProgramName+" "+System.currentTimeMillis());
	                                            	this.service = null;
	                                                programRediscovery.discover(5,1000,1);
	                                                if( this.service == null ){
	                                                    programRediscovery.discover(5,1000,2);
	                                                }
												} catch (Exception e2) {
													e2.printStackTrace();
												}
	                            	//        }
	                            	//	}
	                            	//);
	                            	//t.start();
	                            }
	                            //else{
	                                //System.out.println("BroadcastClient received RTP as byte[]");
	                            //	receivedBytes = up.getBytePayload();
	                                // ??? receivedBytes = (byte[])E2EComm.deserialize(receivedBytes);
	                            //}
	
	                            // 2) send packet to local vlc
	                            if( receivedBytes!=null && !fakeClient ){
	                            	//System.out.println("BroadcastClient sending received RTP "+receivedBytes.length+" towards "+mediaPlayerPort);
		                            DatagramPacket dp = new DatagramPacket(
	                                        receivedBytes,
	                                        receivedBytes.length,
	                                        InetAddress.getLocalHost(),
	                                        mediaPlayerPort
	                                );
	                                dsToLocalVLC.send(dp);
	                            }
	
	                            // 3) update raw quality data
	                            if(receivedBytes==null){
	                                System.out.println("BroadcastClient: invalid receivedBytes "+receivedBytes);
	                            }
	                            else if(receivedBytes.length<4){
	                                System.out.println("BroadcastClient: invalid receivedBytes.length "+receivedBytes.length);
	                            }
	                            else{
	                                // 3a) parameters related to RAMP packets
	                                int sequenceNumber = (((receivedBytes[2])+256)%256) * 256
	                                        + ((receivedBytes[3])+256)%256;
	                                int pdvRamp = -1;
	                                int interRampPacketInterval = -1;
	                                if(previousRampArrival!=-1){
	                                    interRampPacketInterval = (int)(currentRampArrival - previousRampArrival);
	                                    //System.out.println("BroadcastClient interPacketInterval = "+(interPacketInterval/1000.0)+" (millis)");
	                                    if(previousRampInterPacketInterval!=-1){
	                                        pdvRamp = interRampPacketInterval - previousRampInterPacketInterval;
	                                    }
	                                    previousRampInterPacketInterval = interRampPacketInterval;
	                                }
	                                previousRampArrival = currentRampArrival;
	                                BroadcastClientQualityData qd = null;
	                                if(pdvRamp!=-1 && interRampPacketInterval!=-1){
	                                    qd = new BroadcastClientQualityData(
	                                    		pdvRamp, 
	                                    		sequenceNumber, 
	                                    		interRampPacketInterval
	                                		);
	                                }
	                                
	                                // 3b) parameters related to RTP packets 
	                                //	   (useful for VBR video coding)
	                                if( qd!=null && received!=null && received instanceof RTP){
	                                    RTP rtp = (RTP)E2EComm.deserialize(up.getBytePayload());
	                                    int currentRtpTimestamp = rtp.getTimestamp();
	                                    //System.out.println("BroadcastClient: currentRtpTimestamp "+currentRtpTimestamp+" at "+System.currentTimeMillis());
	                                    int pdvRtp = -1;
	                                    int interRtpTimestampInterval = -1;
	                                    if(previousRtpTimestamp!=-1){
	                                        interRtpTimestampInterval = (int)(currentRtpTimestamp - previousRtpTimestamp);
	                                        //System.out.println("BroadcastClient interRtpTimestampInterval = "+(interRtpTimestampInterval/1.0)+" (Hz?)");
	                                        if(previousRtpInterTimestamp!=-1){
	                                            pdvRtp = interRtpTimestampInterval - previousRtpInterTimestamp;
	                                        }
	                                        previousRtpInterTimestamp = interRtpTimestampInterval;
	                                    }
	                                    previousRtpTimestamp = currentRtpTimestamp;
	                                    if( pdvRtp!=-1 && interRtpTimestampInterval!=-1 && interRtpTimestampInterval!=-1 ){
	                                    	qd.setTimestampIntervalRtp(interRtpTimestampInterval);
	                                        qd.setPdvRtp(pdvRtp);
	                                    }
	                                }
	                                // 3c) update the set of quality-related context information
	                                if( qd != null ){
	                                    while(qualityRampPacketVector.size() >= this.monitorWindowSize){
	                                        // remove the oldest element from the head
	                                        if(qualityRampPacketVector.size()>0){
	                                            try{
	                                            	qualityRampPacketVector.remove(0);
	                                            }
	                                            catch(Exception e){
	                                            	//
	                                            }
	                                        }
	                                    }
	                                    // add the new element to the tail
	                                    qualityRampPacketVector.addElement(qd);
	                                    if( RampEntryPoint.isLogging() ){
	                                        interPacketIntervalVector.addElement(interRampPacketInterval);
	                                    }
	                                }
	                            }
	                            

	                            // 4) send ack
	                            if(System.currentTimeMillis()-lastAck > 1000){
	                                lastAck = System.currentTimeMillis();
	                                sendMessageToServiceHandler("ack");
	                            }
	                            
	
	                            // 5) logging
	                            if( RampEntryPoint.isLogging() ){
	                                //long post = System.currentTimeMillis();
	                                float elapsed = currentRampArrival - startArrival;
	                                RTP rtp = (RTP)E2EComm.deserialize(up.getBytePayload());
	                                TSPacket[] tsPackets = rtp.getTsPackets();
	                                for(int i=0; i<tsPackets.length; i++){
	                                    //tsPacketTypes.addElement(tsPackets[i].getFrameType()+" "+tsPackets[i].isPayloadUnitStart());
	                                	//System.out.println("BroadcastClient logging: tsPackets[i].isPayloadUnitStart() = "+tsPackets[i].isPayloadUnitStart());
	                                    if(tsPackets[i].isPayloadUnitStart()){
	                                    	byte frameType = tsPackets[i].getFrameType();
	                                    	//System.out.println("BroadcastClient logging: tsPackets[i].getFrameType() = "+tsPackets[i].getFrameType());
	                                        if( frameType == TSPacket.UNDEFINED ){//null){
	                                            System.out.println("BroadcastClient logging1: getFrameType() is NULL!!!");
	                                        }
	                                        else if( TSPacket.isAudio(frameType) ){//frameType.equals("Audio")){
	                                        	//System.out.println("BroadcastClient logging1: getFrameType() audio");
	                                            pidAudio = tsPackets[i].getPid();
	                                        }
	                                        //else if(frameType.startsWith("Video")){
	                                        else if( TSPacket.isVideo(frameType) ){
	                                        	//System.out.println("BroadcastClient logging1: getFrameType() video");
	                                            pidVideo = tsPackets[i].getPid();
	                                            currentVideoType = frameType;
	                                            //if(frameType.equals("VideoI")){
	                                            /*if( frameType==TSPacket.I_FRAME ){
	                                                currentVideoType = "VideoI";
	                                            }
	                                            //else if(frameType.equals("VideoP")
	                                            //        || frameType.equals("VideoB") ){
	                                            else if( frameType==TSPacket.P_FRAME
	                                                    || frameType==TSPacket.B_FRAME ){
	                                                currentVideoType = "VideoPB";
	                                            }
	                                            else{
	                                                System.out.println("BroadcastClient logging1: currentVideoType = " + currentVideoType);
	                                                System.exit(-1);
	                                            }*/
	                                        }
	                                    }
	                                    
	                                    arrivals.addElement(elapsed);
	                                    currentTimes.addElement(System.currentTimeMillis());
	                                    rtpSequenceNumbers.addElement(rtp.getSequenceNumber());
	                                    //rtpTimestamps.addElement(rtp.getTimestamp());
	                                    if(tsPackets[i].getPid() == pidAudio){
	                                        tsPacketTypes.addElement(1);
	                                    }
	                                    else if( tsPackets[i].getPid() == pidVideo ){
	                                    	//System.out.println("BroadcastClient logging: currentVideoType = "+currentVideoType);
	                                        if( currentVideoType == TSPacket.UNDEFINED ){
	                                            tsPacketTypes.addElement(-1);
	                                        }
	                                        else if( currentVideoType == TSPacket.I_FRAME 
	                                        		|| currentVideoType == TSPacket.IDR_FRAME ){
	                                            tsPacketTypes.addElement(2);
	                                        }
	                                        else if( currentVideoType == TSPacket.P_FRAME 
	                                        		|| currentVideoType == TSPacket.B_FRAME 
	                                        		|| currentVideoType == TSPacket.NON_IDR_FRAME ){
	                                            tsPacketTypes.addElement(3);
	                                        }
	                                        else{
	                                            System.out.println("BroadcastClient logging2: currentVideoType = "+currentVideoType);
	                                            //System.exit(-1);
	                                            tsPacketTypes.addElement(-2);
	                                        }
	                                    }
	                                }
	                            }
                            }
                        }
                    }
                    catch(SocketTimeoutException ste){
                        //ste.printStackTrace();
                        this.sendMessageToServiceHandler(null);
                        consecutiveSocketTimeoutExceptions++;
                        System.out.println("BroadcastClient SocketTimeoutException "+ste.getMessage()+": consecutiveSocketTimeoutExceptions = " + consecutiveSocketTimeoutExceptions+" "+this.service+" "+System.currentTimeMillis());
                    }
                }// end while

                if(!fakeClient){
                    dsToLocalVLC.close();
                }
                clientSocket.close();
            }
            catch(Exception e){
                e.printStackTrace();
            }

            try{
                if( RampEntryPoint.isLogging() ){
                	System.out.println("BroadcastClient program handler LOGGING");
                    java.io.FileWriter bcLogPackets = new java.io.FileWriter("./temp/BroadcastClientPackets.csv");
                    bcLogPackets.write(
                            "current time (ms), "+
                            "rtpSequenceNumber, "+
                            //"rtpTimestamp, "+
                            "audio, "+
                            "video SD, "+
                            "video non-SD "+
                            "\n"
                    );
                    long previousTime = -1;
                    int lastRtpSequenceNumber = -1;
                    int audioPackets = 0;
                    int videoSdPackets = 0;
                    int videoNsdPackets = 0;
                    System.out.println("BroadcastClient program handler LOGGING: tsPacketTypes.size() = "+tsPacketTypes.size());
                    for(int i=0; i<tsPacketTypes.size(); i++){
                        long currentTime = Math.round(arrivals.elementAt(i)/1000);
                        int currentRtpSequenceNumber = rtpSequenceNumbers.elementAt(i);
                        //int currentRtpTimestamp = rtpTimestamps.elementAt(i);
                        if(previousTime != currentTime){
                            if(previousTime != 1){
                                for(int j=lastRtpSequenceNumber+1; lastRtpSequenceNumber!=-1 && j<currentRtpSequenceNumber; j++){
                                    bcLogPackets.write("0,0,0,\n" + "" + "," + j + ",");
                                }
                                bcLogPackets.write(
                                    audioPackets + ","
                                    + videoSdPackets + ","
                                    + videoNsdPackets + ","
                                    + "\n"
                                    + currentTimes.elementAt(i) + ","
                                    + currentRtpSequenceNumber + ","
                                    //+ currentRtpTimestamp + ", "
                                );
                            }
                            previousTime = currentTime;
                            lastRtpSequenceNumber = rtpSequenceNumbers.elementAt(i);
                            audioPackets = 0;
                            videoSdPackets = 0;
                            videoNsdPackets = 0;
                        }
                        if(tsPacketTypes.elementAt(i) == 1){
                            audioPackets++;
                        }
                        else if(tsPacketTypes.elementAt(i) == 2){
                            videoSdPackets++;
                        }
                        else if(tsPacketTypes.elementAt(i) == 3){
                            videoNsdPackets++;
                        }
                    }
                    bcLogPackets.close();

                    if(broadcastClientQualityLogFile!=null){
                        broadcastClientQualityLogFile.close();
                    }
                    broadcastClientQualityLogFile = null;

                    Integer[] interPacketIntervalArray = interPacketIntervalVector.toArray(new Integer[0]);
                    float countMean = 0;
                    for(int i=0; i<interPacketIntervalArray.length; i++){
                        countMean += interPacketIntervalArray[i];
                        //System.out.println( "BroadcastClient interPacket["+i+"] (ms): " + interPacketIntervalArray[i] );
                    }
                    float mean = countMean/((float)interPacketIntervalArray.length);

                    float countStddev = 0;
                    for(int i=0; i<interPacketIntervalArray.length; i++){
                        float x = interPacketIntervalArray[i] - mean;
                        countStddev += ( x * x );
                    }
                    float y = countStddev/((float)interPacketIntervalArray.length);
                    float stddev = (float)Math.sqrt(y);
                    System.out.println("BroadcastClient interPacket   mean (ms): "+ (mean/1000.0F) +"   stddev (ms): "+ (stddev/1000.0F) +"   interPacket.length "+interPacketIntervalArray.length);
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }

            if( programConfig != null ){
                programConfig.setVisible(false);
            }
            this.stopProgramHandler();
            /*try{
                sendMessageToServiceHandler("stop");
            }
            catch(Exception e){
                e.printStackTrace();
            }
            this.active = false;*/

            System.out.println("BroadcastClient FINISHED " + simpleProgramName);
        }// end BroadcastClientProgramHandler.run()

    }// end class BroadcastClientProgramHandler
    

    private class ProgramRediscovery extends Thread{
        private BroadcastClientProgramHandler programHandler;
        private boolean active = true;
        private long programRediscoverPeriod = 3000;
        private int serviceDiscoveryTimeout = 1500;
        private ProgramRediscovery(BroadcastClientProgramHandler programHandler){
            this.programHandler = programHandler;
        }
        @Override
        public void run(){
            //System.out.println("ProgramRediscover START "+BroadcastClient.getSimpleProgramName(programHandler.simpleProgramName));
            System.out.println("ProgramRediscover START "+programHandler.simpleProgramName);
            while(this.active && this.programHandler.active){
                try{
                	long pre = System.currentTimeMillis();
                	
                    this.discover(5, serviceDiscoveryTimeout, 5);
                    
                    long post = System.currentTimeMillis();
                    long elapsed = post - pre;
                    long sleepPeriod = programRediscoverPeriod - elapsed;
                    if( sleepPeriod > 0 ){
                        Thread.sleep(sleepPeriod);
                    }
                    else{
                        Thread.sleep(10);
                    }
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }// end while
            System.out.println("ProgramRediscover FINISHED");
        }
        protected void discover(int ttl, int serviceDiscoveryTimeout, int serviceAmount) throws Exception{
            // look for an alternative source of the same program
            // 1) discovery
            //System.out.println("ProgramRediscover looking for alternative services... "+programHandler.simpleProgramName);
            // XXX which dynamic client discovery parameters?
            Vector<ServiceResponse> services = findBroadcastService(
            		ttl,
                    serviceDiscoveryTimeout,
                    serviceAmount
            );
            //System.out.println("ProgramRediscover found services "+services);
            // 2) from closest to farthest, require program list
            for(int i=0; i<services.size(); i++){
                //System.out.println("ProgramRediscover service #"+i+" "+services.elementAt(i));
                String[] programs = programList(services.elementAt(i));
                boolean found = false;
                if(programs!=null){
                    for(int j=0; !found && j<programs.length; j++){
                        //System.out.println("ProgramRediscover program #"+j+" "+programs[j]);
                        try{
                            if(BroadcastClient.getSimpleProgramName(programs[j]).equals(programHandler.simpleProgramName)){
                                System.out.println("ProgramRediscover found the same program at "+services.elementAt(i)+" "+System.currentTimeMillis());
                                found = true;
                                // 3) check if the sender is a Dispenser or a SmartSplitter
                                boolean eitherDispenserOrSmartSplitter = false;
                                if(BroadcastClient.getSplitterAmount(programs[j]) == 0){
                                    // Dispenser
                                    eitherDispenserOrSmartSplitter = true;
                                }
                                else if(services.elementAt(i).getQos().equals(
                                        "broadcastSplitter=true smartBroadcastSplitter=true")){
                                    eitherDispenserOrSmartSplitter = true;
                                }
                                
                                // 4) check if the found service is better than the current one, if any
                                //System.out.println("ProgramRediscover programHandler.service "+programHandler.service);
                                if(programHandler.service==null){
                                    // the previous streamer is not available anymore
                                    // 5) register to the newly found program
                                    programHandler.service = services.elementAt(i);
                                    programHandler.sendMessageToServiceHandler(null);
                                    programHandler.splitterAmount = BroadcastClient.getSplitterAmount(programs[j]);
                                    System.out.println("ProgramRediscover CHANGE SERVICE (previous is unavailable) "+programHandler.service+" "+System.currentTimeMillis());
                                    if( RampEntryPoint.isLogging() ){
                                        System.out.println("ProgramRediscover CHANGE SERVICE (previous is unavailable) "+programHandler.service+" "+System.currentTimeMillis());
                                    }
                                }
                                else if( ! eitherDispenserOrSmartSplitter ){
                                    System.out.println("ProgramRediscover eitherDispenserOrSmartSplitter "+eitherDispenserOrSmartSplitter);
                                    //System.out.println("ProgramRediscover services.elementAt(i).getQos() "+services.elementAt(i).getQos());
                                }
                                else if(services.elementAt(i).getServerDest().length < programHandler.service.getServerDest().length){
                                    // 5) register to the newly found program
                                    programHandler.previousService = programHandler.service;
                                    programHandler.service = services.elementAt(i);
                                    programHandler.sendMessageToServiceHandler(null);
                                    programHandler.splitterAmount = BroadcastClient.getSplitterAmount(programs[j]);
                                    System.out.println("ProgramRediscover CHANGE SERVICE (closer) "+programHandler.service+" "+System.currentTimeMillis());
                                    if( RampEntryPoint.isLogging() ){
                                        System.out.println("ProgramRediscover CHANGE SERVICE (closer) "+programHandler.service+" "+System.currentTimeMillis());
                                    }
                                }
                                else if( services.elementAt(i).getServerDest()[0].equals(InetAddress.getLocalHost().getHostAddress().replaceAll("/", "")) ){
                                    // new service in loopback
                                    if(!programHandler.service.equals(services.elementAt(i)) ){
                                        // 5) register to the newly found program
                                        programHandler.previousService = programHandler.service;
                                        programHandler.service = services.elementAt(i);
                                        programHandler.sendMessageToServiceHandler(null);
                                        programHandler.splitterAmount = BroadcastClient.getSplitterAmount(programs[j]);
                                        System.out.println("ProgramRediscover CHANGE SERVICE (local) "+programHandler.service+" "+System.currentTimeMillis());
                                        if( RampEntryPoint.isLogging() ){
                                            System.out.println("ProgramRediscover CHANGE SERVICE (local) "+programHandler.service+" "+System.currentTimeMillis());
                                        }
                                    }
                                }
                                else if( !programHandler.service.getServerDest()[0].equals(InetAddress.getLocalHost().getHostAddress().replaceAll("/", "")) // current service not loopback
                                        && services.elementAt(i).getServerDest().length == programHandler.service.getServerDest().length
                                        &&  BroadcastClient.getSplitterAmount(programs[j]) < programHandler.splitterAmount ){
                                    // 5) register to the newly found program
                                    programHandler.previousService = programHandler.service;
                                    programHandler.service = services.elementAt(i);
                                    programHandler.sendMessageToServiceHandler(null);
                                    programHandler.splitterAmount = BroadcastClient.getSplitterAmount(programs[j]);
                                    System.out.println("ProgramRediscover CHANGE SERVICE (same path length but lower SplitterAmount) "+programHandler.service+" "+System.currentTimeMillis());
                                    if( RampEntryPoint.isLogging() ){
                                        System.out.println("ProgramRediscover CHANGE SERVICE (same path length but lower SplitterAmount) "+programHandler.service+" "+System.currentTimeMillis());
                                    }
                                }
                            }
                        }
                        catch(Exception e){
                            e.printStackTrace();
                        }
                    } // end for
                }
            }
        }
        private void stopProgramRediscover(){
            this.active = false;
        }
    }

    private class QualityMonitor extends Thread{
        private Vector<BroadcastClientQualityData> qualityRampPacketVector;
        private BroadcastClientProgramHandler programHandler;
        
        private QualityMonitor(
                Vector<BroadcastClientQualityData> qualityRampPacketVector,
                BroadcastClientProgramHandler programHandler) {
            this.qualityRampPacketVector = qualityRampPacketVector;
            this.programHandler = programHandler;
        }

        @Override
        public void run(){
            //System.out.println("QualityMonitor START "+programHandler.simpleProgramName);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                //
            }
            long qualityMonitorPeriod = 1000; // XXX qualityMonitorPeriod
            while(programHandler.active){
                try{
                    float streamJitter;
                    float lostPacketsPerc;
                    String streamJitterString = null;
                    if( qualityRampPacketVector.size() == 0 ){
                        // no received packets in the last period
                        streamJitterString = "";
                        streamJitter = 0;
                        lostPacketsPerc = 100;
                        System.out.println("BroadcastClient lostPacketsPerc = "+lostPacketsPerc+" (%): NO PACKETS RECEIVED!");
                    }
                    else if( qualityRampPacketVector.size() == 1 ){
                        // received just 1 packet in the last period
                        streamJitterString = "";
                        streamJitter = 0;
                        // fix rtp to 90% in case of only one packet received
                        lostPacketsPerc = 90;
                    }
                    else{
                    	// there is enough data to assess quality
                        BroadcastClientQualityData[] qdArray = qualityRampPacketVector.toArray(new BroadcastClientQualityData[0]);
                        qualityRampPacketVector.removeAllElements();
                        
                        // 1) unordered RTP
                        int unorderedRtp = 0;
                        for(int i=1; i<qdArray.length; i++){
                        	//System.out.println("BroadcastClient QualityMonitor.run() qdArray[i].getSerialNumber() = "+qdArray[i].getSerialNumber());
                        	if(qdArray[i].getSerialNumber() != (qdArray[i-1].getSerialNumber()+1)){
                                unorderedRtp++;
                            }
                        }
                        lostPacketsPerc = ((float)unorderedRtp)/qdArray.length * 100;
                        
                        // 2) RAMP packets
                        float countRampPacketIntervalMean = 0;
                        for(int i=0; i<qdArray.length; i++){
                            countRampPacketIntervalMean += ((float)qdArray[i].getPacketIntervalRamp());
                        }
                        float rampPacketIntervalMean = countRampPacketIntervalMean/((float)qdArray.length);
                        float countRampPdvMean = 0;
                        for(int i=0; i<qdArray.length; i++){
                            countRampPdvMean += ((float)qdArray[i].getPdvRamp());
                        }
                        float pdvRampMean = countRampPdvMean/((float)qdArray.length);
                        float countRampPdvStddev = 0;
                        for(int i=0; i<qdArray.length; i++){
                            float x = ((float)qdArray[i].getPdvRamp()) - pdvRampMean;
                            countRampPdvStddev += ( x * x );
                        }
                        float rampY = countRampPdvStddev/((float)qdArray.length);
                        float pdvRampStddev = (float)Math.sqrt(rampY);
                        float pdvRampRatio = pdvRampStddev/rampPacketIntervalMean;
                        
                        // 3) RTP timestamps
                        float countRtpPacketIntervalMean = 0;
                        for(int i=0; i<qdArray.length; i++){
                        	countRtpPacketIntervalMean += ((float)qdArray[i].getTimestampIntervalRtp());
                        }
                        float timestampRtpIntervalMean = countRtpPacketIntervalMean/((float)qdArray.length);
                        //System.out.println("BroadcastClient timestampRtpIntervalMean = "+timestampRtpIntervalMean);
                        float countRtpPdvMean = 0;
                        for(int i=0; i<qdArray.length; i++){
                        	countRtpPdvMean += ((float)qdArray[i].getPdvRtp());
                        }
                        float pdvTimestampRtpMean = countRtpPdvMean/((float)qdArray.length);
                        float countRtpPdvStddev = 0;
                        for(int i=0; i<qdArray.length; i++){
                            float x = ((float)qdArray[i].getPdvRtp()) - pdvTimestampRtpMean;
                            countRtpPdvStddev += ( x * x );
                        }
                        float rtpY = countRtpPdvStddev/((float)qdArray.length);
                        float pdvTimestampRtpStddev = (float)Math.sqrt(rtpY);
                        //System.out.println("BroadcastClient pdvRtpStddev = "+pdvRtpStddev);
                        float pdvTimestampRtpRatio = pdvTimestampRtpStddev/timestampRtpIntervalMean;
                        if( pdvTimestampRtpRatio == Float.NaN ){
                        	pdvTimestampRtpRatio = 0;
                        }
                        
                        // 4) RAMP packets + RTP timestamps
                        streamJitter = pdvRampRatio - pdvTimestampRtpRatio;
                        
                        // final
                        System.out.println("BroadcastClient programHandler.monitorWindowSize = "+programHandler.monitorWindowSize+" (#)");
                        programHandler.monitorWindowSize = Math.round( qualityMonitorPeriod/(rampPacketIntervalMean/1000.0F) );
                        if( programHandler.monitorWindowSize < 5 ){
                            programHandler.monitorWindowSize = 5;
                        }
                        streamJitterString = ""+streamJitter;
                        
                        System.out.println();
                        //System.out.println("BroadcastClient unorderedRtp = "+unorderedRtp+" (#)");
                        System.out.println("BroadcastClient lostPacketsPerc = "+lostPacketsPerc+" (%)");
                        //System.out.println();
                        //System.out.println("BroadcastClient packetIntervalMean = "+rampPacketIntervalMean/1000.0+" (millis)");
                        //System.out.println("BroadcastClient pdvRampMean = "+pdvRampMean/1000.0+" (millis)");
                        //System.out.println("BroadcastClient pdvRampStddev = "+pdvRampStddev/1000.0+" (millis)");
                        System.out.println("BroadcastClient pdvRampRatio = "+pdvRampRatio+" (ratio)");
                        //System.out.println();
                        //System.out.println("BroadcastClient timestampRtpIntervalMean = "+timestampRtpIntervalMean+" (Hz?)");
                        //System.out.println("BroadcastClient pdvTimestampRtpMean = "+pdvTimestampRtpMean+" (Hz?)");
                        //System.out.println("BroadcastClient pdvTimestampRtpStddev = "+pdvTimestampRtpStddev+" (Hz?)");
                        System.out.println("BroadcastClient pdvTimestampRtpRatio = "+pdvTimestampRtpRatio+" (ratio)");
                        //System.out.println();
                        System.out.println("BroadcastClient streamJitter = "+streamJitter+" (ratio difference)");
                        //System.out.println();
                        //System.out.println();
                        
                    }

                    /*String quality = null;
                    if( programHandler.consecutiveSocketTimeoutExceptions == 0
                            && streamJitter < programHandler.pdvIn
                            && lostPacketsPerc < programHandler.rtpIn ){
                        quality = "increase";
                    }
                    else if( programHandler.consecutiveSocketTimeoutExceptions >= 2
                            || streamJitter > programHandler.pdvDe
                            || lostPacketsPerc > programHandler.rtpDe ){
                        quality = "decrease";
                    }

                    if(quality!=null && !this.programHandler.manualQualityMonitorEnabled){
                        this.programHandler.sendMessageToServiceHandler(quality);
                    }
                    System.out.println();*/
                    
                    float deltaQuality;
                	if( programHandler.consecutiveSocketTimeoutExceptions >= 2 ){
                        deltaQuality = -2;
                    }
                	else{
                		// XXX deltaLP & deltaSJ
                        if(streamJitter<0) streamJitter = 0;
                		float deltaLP = (float)( ( 1.0F - Math.tanh((lostPacketsPerc-5F)*0.5F) ) / 2.0F );
                		float deltaSJ = (float) ( (1.0F - Math.tanh((streamJitter-0.5F)*5)) / 2.0F );
                		deltaQuality = ( deltaLP * deltaSJ * 1.5F ) - 1.0F;
                	}
                	System.out.println("BroadcastClient deltaQuality = "+deltaQuality);
                	System.out.println();
                    
                    if( /*quality!=null &&*/ ! this.programHandler.manualQualityMonitorEnabled ){
                        this.programHandler.sendMessageToServiceHandler(""+deltaQuality);
                    }
                    System.out.println();

                    try{
                        if( RampEntryPoint.isLogging() && this.programHandler.broadcastClientQualityLogFile!=null ){
                            /*if(quality == null){
                                this.programHandler.broadcastClientQualityLogFile.write(
                                        System.currentTimeMillis() + ","
                                        + streamJitterString + ", "
                                        + lostPacketsPerc + ","
                                        + programHandler.consecutiveSocketTimeoutExceptions
                                        + ","
                                        + "" + "\n");
                            }
                            else if(quality.equals("increase")){
                                int qualityInt = +1;*/
                                this.programHandler.broadcastClientQualityLogFile.write(
                                        System.currentTimeMillis() + ","
                                        + streamJitterString + ","
                                        + lostPacketsPerc + ","
                                        + programHandler.consecutiveSocketTimeoutExceptions
                                        + ","
                                        //+ qualityInt + "\n");
                                        + deltaQuality + "\n");
                            /*}
                            else if(quality.equals("decrease")){
                                int qualityInt = -1;
                                this.programHandler.broadcastClientQualityLogFile.write(
                                        System.currentTimeMillis() + ","
                                        + streamJitterString + ", "
                                        + lostPacketsPerc + ","
                                        + programHandler.consecutiveSocketTimeoutExceptions + ","
                                        + qualityInt + "\n");
                            }*/
                                this.programHandler.broadcastClientQualityLogFile.flush();

                        }
                    }
                    catch(Exception e){
                        e.printStackTrace();
                        //this.bs.removeProgram(programName);
                    }
                    Thread.sleep(qualityMonitorPeriod);
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
            System.out.println("QualityMonitor FINISHED");
        }
    }

    protected static byte getSplitterAmount(String programName){
        return (byte)Integer.parseInt(programName.split(" ",2)[0].split("=")[1]);
    }
    protected static String getSimpleProgramName(String programName){
        return programName.split(" ",2)[1];
    }
    
}

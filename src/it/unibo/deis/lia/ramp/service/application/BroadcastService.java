package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.service.application.mpeg.*;

import it.unibo.deis.lia.ramp.service.management.*;
import it.unibo.deis.lia.ramp.core.e2e.*;
import it.unibo.deis.lia.ramp.*;
import it.unibo.deis.lia.ramp.util.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 *
 * @author Carlo Giannelli
 */
public class BroadcastService extends Thread{

    // clients must refresh their interest at least every 11s
    public static final int CLIENT_TIMEOUT = 6000;

    // simple program name (without "SA=X" prefix) ==> service program handler
    private final Hashtable<String,BroadcastServiceProgramHandler> programsDB = new Hashtable<String,BroadcastServiceProgramHandler>();

    
    // quality related variables
    public final static float MAX_QUALITY = 10.0F;
    public final static float MIN_QUALITY = 0.0F;
    //public final static float START_QUALITY = BroadcastService.MAX_QUALITY;
    //public final static float START_QUALITY = (BroadcastService.MAX_QUALITY+BroadcastService.MIN_QUALITY)/2;
    public final static float START_QUALITY = BroadcastService.MAX_QUALITY;

    private final float MAX_DROP_RATE = 1.0F; //0.95F;

    // sampling related variables
    String videoTranscoder = "no transcode";
    //String audioTranscoder = "mpga"; 	// MPEG 1 Layer 1
    String audioTranscoder = "mp3"; 	// MPEG 1 Layer 3
    //String audioTranscoder = "mp4a"; 	// MPEG-4 Audio (is AAC)
    //String audioTranscoder = "a52"; 	// Vorbis
    // http://wiki.videolan.org/Codec
    // http://developer.android.com/guide/appendix/media-formats.html
    
    private int videoBitrate = 768;     // Kbit/s
    private float videoScale = 1.0F; //0.5F;       // ratio
    private int audioBirate = 64;           // Kbit/s
    private int mtu = 1024*5;               // bytes

    private boolean rawBytes = false;

    // ffmpeg parameter
    private int gopSize = 4;                // keyint (GOP size)
    
    private String repositoryDirectory="./temp";
    private String vlcDirectory;
    private String vlc;
    private String webcam;
    private String param = "dshow"; // Fabio Pascucci

    private BroadcastSplitter broadcastSplitter;
    private boolean smartSplitter;

    private boolean open = true;
    private final BoundReceiveSocket broadcastServiceSocket;
    private int protocol = E2EComm.UDP;
    //private int protocol = E2EComm.TCP;

    private static BroadcastService broadcastService=null;
    private static BroadcastServiceJFrame bsjf;
    

	public static synchronized BroadcastService getInstance(){
        try {
            if(broadcastService==null){
                broadcastService = new BroadcastService();
                broadcastService.start();
            }
            bsjf.setVisible(true);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return broadcastService;
    }
	public static synchronized BroadcastService getInstanceNoShow(){
        try {
            if(broadcastService==null){
                broadcastService = new BroadcastService();
                broadcastService.start();
            }
           
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return broadcastService;
    }

    private BroadcastService() throws Exception{
        broadcastServiceSocket = E2EComm.bindPreReceive(protocol);

        registerService();
        
        vlc = "vlc";
        if(RampEntryPoint.os.startsWith("linux")){
            vlcDirectory="";
            //webcam="/dev/video0";
            webcam="default webcam";
            param = "v4l2";
            try{
                Process pId = Runtime.getRuntime().exec("id -ru");
                BufferedReader brId = new BufferedReader(new InputStreamReader(pId.getInputStream()));
                String lineId;
                lineId=brId.readLine();
                if(lineId.equals("0")){
                    vlc = "vlc-wrapper";
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        else if(RampEntryPoint.os.startsWith("mac")){
            vlcDirectory="/Applications/VLC.app/Contents/MacOS/";
            webcam="default webcam";
            param = "dshow"; // TODO corretto?
        }
        else if(RampEntryPoint.os.startsWith("windows")){
            //vlcDirectory="C:/Program Files/VideoLAN/VLC/";
        	vlcDirectory="C:/VLC-1.1.12/";
            //webcam="Creative WebCam (WDM)";
            //webcam="Logitech QuickCam Communicate STX";
            //webcam="USB 2.0 UVC 0.3M Webcam";
            webcam = "default webcam";
            param = "dshow";
        }
        else{
            vlcDirectory="???";
            webcam="???";
            param = "???";
        }

        bsjf = new BroadcastServiceJFrame(this);
    }
    
    public static BroadcastServiceJFrame getBsjf() {
		return bsjf;
	}

    private void registerService(){
        String qos = "";
        if(broadcastSplitter==null){
            qos += "broadcastSplitter=false";
        }
        else{
            qos += "broadcastSplitter=true";
            qos += " ";
            qos += "smartBroadcastSplitter="+this.isSmartSplitter();
        }
        ServiceManager.getInstance(false).registerService(
                "Broadcast",
                broadcastServiceSocket.getLocalPort(),
                protocol,
                qos
        );
    }

    synchronized public void startBroadcastSplitter(){
        System.out.println("BroadcastService startBroadcastSplitter");
        if(broadcastSplitter==null){
            broadcastSplitter = BroadcastSplitter.getInstance(this);
            registerService();
        }
    }
    synchronized public void stopBroadcastSplitter(){
        System.out.println("BroadcastService stopBroadcastSplitter");
        if( broadcastSplitter != null ){
            //this.disableSmartSplitter();
            broadcastSplitter.stopBroadcastSplitter();
            broadcastSplitter = null;
            registerService();
        }
    }
    
    /*public int getProtocol() {
		return protocol;
	}
	public void setProtocol(int protocol) {
        System.out.println("BroadcastService setProtocol "+protocol);
		if( protocol==E2EComm.UDP || protocol==E2EComm.TCP ){
			if( this.protocol!=protocol ){
				this.protocol = protocol;
				
				try {
		            broadcastServiceSocket.close();
		        } catch (IOException ex) {
		            ex.printStackTrace();
		        }
		        this.stopBroadcastSplitter();
		        String[] programList = getProgramList();
		        for(int i=0; i<programList.length; i++){
		            BroadcastServiceProgramHandler ph = programsDB.get(programList[i]);
		            ph.stopProgramHandler();
		        }
		        ServiceManager.getInstance(false).removeService("Broadcast");
		        BroadcastService bs = new BroadcastService();
		        bs.start();
			}
		}
	}*/
	
	public boolean isRawBytes() {
        return rawBytes;
    }
    public void setRawBytes(boolean rawBytes) {
        this.rawBytes = rawBytes;
    }
    
    public boolean isSmartSplitter(){
        return smartSplitter;
    }
    synchronized public void enableSmartSplitter() throws Exception{
        System.out.println("BroadcastService enableSmartSplitter");
        //if( broadcastSplitter == null ){
        //    throw new Exception("Cannot enable SmartSplitter feature if BroadcastSplitter is not active");
        //}
        this.smartSplitter = true;
        registerService();
    }
    synchronized public void disableSmartSplitter() {
        System.out.println("BroadcastService disableSmartSplitter");
        this.smartSplitter = false;
        registerService();
    }
    
    public void showProgramConfig(String simpleProgramName){
        BroadcastServiceProgramHandler ph = programsDB.get(simpleProgramName);
        if(ph!=null){
            ph.showProgramConfig();
        }
    }
    
    public String getVlcDirectory() {
        //System.out.println("BroadcastService getVlcDirectory "+vlcDirectory);
        return vlcDirectory;
    }
    public void setVlcDirectory(String vlcDirectory) {
        //System.out.println("BroadcastService setVlcDirectory "+vlcDirectory);
        this.vlcDirectory = vlcDirectory+"/";
    }

    public String getWebcam() {
        //System.out.println("BroadcastService getWebcam: "+webcam);
        return webcam;
    }
    public void setWebcam(String webcam) {
        //System.out.println("BroadcastService setWebcam: "+webcam);
        this.webcam = webcam;
    }

    public String getRepositoryDirectory(){
        return repositoryDirectory;
    }
    public void setRepositoryDirectory(String repositoryDirectory){
    	this.repositoryDirectory = repositoryDirectory;
    }

    public String[] getProgramList(){
        String[] res = programsDB.keySet().toArray(new String[0]);
        return res;
    }

    public String[] getSourceList(){
        File dir = new File(repositoryDirectory);
        String[] list = dir.list();
        // filter the list of returned files
        // to not return any files that start with '.'.
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !(name.startsWith("."))
                        &&
                        (
                            name.endsWith(".mpg")
                            || name.endsWith(".mp3")
                            || name.endsWith(".mp4")
                            || name.endsWith(".avi")
                            || name.endsWith(".ts")
                            || name.endsWith(".ps")
                            || name.endsWith(".mkv")
                            || name.endsWith(".wmv")
                            || name.endsWith(".3gp")
                        );
            }
        };
        /*System.out.println("StreamService.getStreamList pre-filter list: "+list);
        for(String s : list){
            System.out.println("\t"+s);
        }*/
        list = dir.list(filter);
        /*System.out.println("StreamService.getStreamList post-filter list: "+list);
        for(String s : list){
            System.out.println("\t"+s);
        }*/
        String[] res=new String[list.length+1];
        System.arraycopy(list, 0, res, 0, list.length);
        res[res.length-1] = webcam;
        return res;
    }

    public void addProgram(String simpleProgramName, AbstractRtpParser rtpMpegParser, byte splitterAmount, String[] serviceSource) throws Exception{
        synchronized(programsDB){
            // start streaming a traversing program (from BroadcastSplitter)
            BroadcastServiceProgramHandler ph = programsDB.get(simpleProgramName);
            if( ph != null ){
                if(ph.getSplitterAmount()==0){
                    throw new Exception("Program already exists and is not splitter-based: "+ph.simpleProgramName);
                }
                else if(ph.getSplitterAmount()<=splitterAmount){
                    throw new Exception("Program already with fewer or the same splitters: "+ph.simpleProgramName);
                }
                else{
                    // the new source is either local or with less splitters:
                    // substitute the previous source with this new source
                    ph.splitterAmount = splitterAmount;
                    ph.serviceSource = serviceSource;
                    ph.rtpMpegParser = rtpMpegParser;
                }
            }
            else{
                // the source is new, i.e., it does not exist locally
                ph = new BroadcastServiceProgramHandler(this, simpleProgramName, rtpMpegParser, splitterAmount, serviceSource);
                ph.start();
                programsDB.put(simpleProgramName, ph);
                System.out.println("BroadcastService: addProgram "+simpleProgramName+" with RtpMpegParser ADDED");
            }
        }
    }
    public void addProgram(String simpleProgramName) throws Exception{
        System.out.println("BroadcastService: addProgram "+simpleProgramName);
        synchronized(programsDB){
            // start streaming something (file, dvb, webcam...)
            BroadcastServiceProgramHandler ph = programsDB.remove(simpleProgramName);
            if( ph != null ){
                ph.stopProgramHandler();
            }
            ph = new BroadcastServiceProgramHandler(this, simpleProgramName);
            ph.start();
            programsDB.put(simpleProgramName, ph);
        }
    }
    public void removeProgram(String simpleProgramName){
        // stop stream a program
        synchronized(programsDB){
            BroadcastServiceProgramHandler ph = programsDB.get(simpleProgramName);
            if( ph != null ){
                if(ph.getSplitterAmount()==0){
                    ph.stopProgramHandler();
                    programsDB.remove(simpleProgramName);
                }
                else{
                    System.out.println("BroadcastService.removeProgram: cannot stop listener-based program "+simpleProgramName);
                }
            }
        }
    }

    public void stopService(){
        this.stopBroadcastSplitter();
        open = false;
        if(bsjf != null) bsjf.setVisible(false);
        /*try {
            broadcastServiceSocket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }*/
        String[] programList = getProgramList();
        for(int i=0; i<programList.length; i++){
            BroadcastServiceProgramHandler ph = programsDB.get(programList[i]);
            ph.stopProgramHandler();
        }
        ServiceManager.getInstance(false).removeService("Broadcast");
        broadcastService=null;
    }

    public String getVideoTranscoder() {
        return videoTranscoder;
    }

    public void setVideoTranscoder(String transcoder) {
    	System.out.println("BroadcastService setTranscoder "+transcoder);
        this.videoTranscoder = transcoder;
    }
	
    public int getAudioBirate() {
        return audioBirate;
    }
    public void setAudioBirate(int audioBirate) {
        this.audioBirate = audioBirate;
    }

    public int getGopSize() {
        return gopSize;
    }
    public void setGopSize(int gopSize) {
        this.gopSize = gopSize;
    }

    public int getMtu() {
        return mtu;
    }
    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public int getVideoBitrate() {
        return videoBitrate;
    }
    public void setVideoBitrate(int videoBitrate) {
        this.videoBitrate = videoBitrate;
    }

    public float getVideoScale() {
        return videoScale;
    }
    public void setVideoScale(float videoScale) {
        this.videoScale = videoScale;
    }


    @Override
    public void run(){
        System.out.println("BroadcastService START");
        while(open){
            try{
                GenericPacket gp = E2EComm.receive(broadcastServiceSocket, 5*1000);
                //System.out.println("BroadcastService new request");
                new BroadcastServiceHandler(gp).start();
            }
            catch(SocketTimeoutException ste){
                //
            }
            catch(Exception e){
            	//System.out.println("BroadcastService e = "+e.getMessage());
            	e.printStackTrace();
            }
        }

        try {
            broadcastServiceSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("BroadcastService FINISHED");
    }

    private class BroadcastServiceHandler extends Thread{
        private GenericPacket gp;
        private BroadcastServiceHandler(GenericPacket gp){
            this.gp=gp;
        }
        @Override
        public void run(){
            try{
                // check packet type
                if( gp instanceof UnicastPacket){
                    // check payload
                    UnicastPacket up=(UnicastPacket)gp;
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if(payload instanceof BroadcastRequest){
                        //System.out.println("BroadcastService BroadcastRequest");
                        BroadcastRequest request = (BroadcastRequest)payload;
                        String simpleProgramName = request.getSimpleProgramName();
                        String message = request.getMessage();
                        String[] newDest = E2EComm.ipReverse(up.getSource());
                        int newDestNodeId = up.getSourceNodeId();
                        if(message!=null && message.equals("list")){
                            // send broadcasting program list
                            //System.out.println("BroadcastService list");
                            String[] list = getProgramList();
                            for(int i=0; i<list.length; i++){
                                list[i] = "SA="+programsDB.get(list[i]).getSplitterAmount()+" "+list[i];
                            }
                            E2EComm.sendUnicast(
                                newDest,
                                request.getClientPort(),
                                protocol,  
                                E2EComm.serialize(list)
                            );
                        }
                        else{
                            // manage receiver request
                            EndPoint receiver = new EndPoint(
                                    request.getClientPort(),
                                    newDestNodeId,
                                    newDest
                            );
                            //System.out.println("BroadcastService requested program="+simpleProgramName+" receiver="+receiver);
                            BroadcastServiceProgramHandler ph = programsDB.get(simpleProgramName);
                            if(ph!=null){
                                if(message!=null && message.equals("stop")){
                                    // stop client
                                    System.out.println("BroadcastService stop for "+simpleProgramName+" from "+receiver);
                                    ph.removeReceiver(receiver);
                                }
                                else{
                                    ph.addReceiver(receiver);
                                    if(message==null){
                                        //System.out.println("BroadcastService refresh for "+simpleProgramName+" from "+receiver);
                                    }
                                    else if(message.equals("ack")){
                                        ph.receivedAck(receiver);
                                    }
                                    else{
                                    	try{
	                                        float deltaQuality = Float.parseFloat(message);
	                                    	ph.changeQuality(receiver, deltaQuality);
                                    	}
                                    	catch(NumberFormatException nfe){
                                    		System.out.println("BroadcastService: unknown message for "+simpleProgramName+" from "+receiver+": message="+message );
                                    	}
                                    }
                                }
                            }
                            else{
                                System.out.println("BroadcastService unknown requested program: "+simpleProgramName+" receiver="+receiver);
                            }
                        }
                        //System.out.println("BroadcastService FINISHED BroadcastRequest res "+res);
                    }
                    else{
                        // received payload is not BroadcastService: do nothing...
                        System.out.println("BroadcastService wrong payload: "+payload);
                    }
                }
                else{
                     // received packet is not UnicastPacket: do nothing...
                    System.out.println("BroadcastService wrong packet: "+gp.getClass().getName());
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    protected class BroadcastServiceProgramHandler extends Thread{

        //private Random r = new Random();
        private boolean active = true;

        // per-client registration DB
        private final Hashtable<EndPoint, Long> receiversDB;

        // per-client required quality
        private final Hashtable<EndPoint, BroadcastReceiverQuality> qualityDB;

        // per-client last ack
        private final Hashtable<EndPoint, Long> ackDB;

        private BroadcastClient.BroadcastClientProgramHandler fakeClient = null;
        private String[] serviceSource = null;
        
        private BroadcastService bs;
        private AbstractRtpParser rtpMpegParser = null;
        private String simpleProgramName;
        private byte splitterAmount;
        private BroadcastServiceProgramConfigJFrame programConfig;
        private boolean qualityTailoring = true;
        private boolean streamParsing = true;

        protected boolean isQualityTailoring() {
            return qualityTailoring;
        }
        protected void setQualityTailoring(boolean qualityTailoring) {
            System.out.println("BroadcastService setQualityTailoring: "+qualityTailoring);
            this.qualityTailoring = qualityTailoring;
        }
        
        protected boolean isStreamParsing() {
			return streamParsing;
		}
        protected void setStreamParsing(boolean streamParsing) {
        	System.out.println("BroadcastService setStreamParsing: "+streamParsing);
            this.streamParsing = streamParsing;
	        //if( rtpMpegParser != null ){
	        	rtpMpegParser.setParsePayload(this.isStreamParsing());
	        //}
	            System.out.println("BroadcastService setStreamParsing: rtpMpegParser.isParsePayload()="+rtpMpegParser.isParsePayload());
		}
		
		protected byte getSplitterAmount() {
            return splitterAmount;
        }

        protected void showProgramConfig(){
            programConfig.setVisible(true);
        }

        private void receivedAck(EndPoint receiver){
            //System.out.println("BroadcastService receivedAck from "+receiver+" for "+simpleProgramName);
            ackDB.put(receiver, System.currentTimeMillis());
        }

        private BroadcastServiceProgramHandler(BroadcastService bs, String simpleProgramName, AbstractRtpParser rtpMpegParser, byte splitterAmount, String[] serviceSource) throws Exception{
            this.receiversDB = new Hashtable<EndPoint, Long>();
            this.qualityDB = new Hashtable<EndPoint, BroadcastReceiverQuality>();
            this.ackDB = new Hashtable<EndPoint, Long>();
            this.bs = bs;
            this.simpleProgramName = simpleProgramName;
            this.rtpMpegParser = rtpMpegParser;
            this.splitterAmount = splitterAmount;
            this.programConfig = new BroadcastServiceProgramConfigJFrame(this);
            this.serviceSource = serviceSource;
            this.programConfig.setTitle("BroadcastService: "+simpleProgramName);
        }

        private BroadcastServiceProgramHandler(BroadcastService bs, String simpleProgramName) throws Exception{
            this.receiversDB = new Hashtable<EndPoint, Long>();
            this.qualityDB = new Hashtable<EndPoint, BroadcastReceiverQuality>();
            this.ackDB = new Hashtable<EndPoint, Long>();
            this.bs = bs;
            this.simpleProgramName = simpleProgramName;
            this.splitterAmount = 0;
            this.programConfig = new BroadcastServiceProgramConfigJFrame(this);
            this.programConfig.setTitle("BroadcastService: "+simpleProgramName);
            
            DatagramSocket dsVlc = new DatagramSocket();
            int vlcPort = dsVlc.getLocalPort();

            // create MPEG-TS-Parser
            String mux = "ts";

            // timeouts
            int startingTimeout = 15;   // seconds
            int workingTimeout = 3;     // seconds
            
            String transcodeString = "";
            if(videoTranscoder.equals("mp2v")){
            	transcodeString += 
                    //":sout=" +
                    "#transcode{"+
                        "vcodec=mp2v,vb="+videoBitrate+",fps=25,scale="+videoScale+"" +
                        ",acodec="+audioTranscoder+",ab="+audioBirate+""+
                        ",venc=ffmpeg{" +
                            "keyint="+gopSize +
                            //+"," + "hurry-up" +
                        "}"+
                    "}";
            }
            else if(videoTranscoder.equals("x264")){
// #transcode{vb=800,venc=x264{subme=5,ref=3,bframes=3,deblock=0:0},scale=1.0,vcodec=h264,acodec=mp3,ab=64,threads=4}
// http://www.veetle.com/index.php/article/view/x264            	
            	transcodeString += 
                    //":sout=" +
                    "#transcode{"+
                        "vcodec=h264,vb="+videoBitrate+",fps=25,scale="+videoScale+"" +
                        ",acodec="+audioTranscoder+
                        //",acodec=mp3" +
                        ",ab="+audioBirate+""+
                        /**/
                        ",venc=x264{" +
                        	//"bframes=1" + // (1) (comment)
                            "keyint="+gopSize+"" + 
                            ",idrint="+gopSize+"" +
                            //",profile=baseline" + // only I and P slices (un-comment)
                            //",profile=extended" + // I/P/B slices (comment)
                            ",profile=high" + // I/P/B slices (comment)
                            /* // http://doom10.org/index.php?topic=733.0
                             +
                            "constrained-intra," +*/
                            ",level=5.1" +
                            
                            // http://www.veetle.com/index.php/article/view/x264
                            ",subme=9" +
                            ",ref=2" + // (1)
                            ",me=hex" +
                            ",merange=24" +
                            ",subme=9" +
                            ",threads=2" +
                            
                            
                            //"slice-max-mbs=2," + // (1?) NO!
                            //"slice-max-size=120," + // NO!!!
                            //"intra-refresh," + // NO!!
                            
                            /*
                            "level=3.0," +
                            "nocabac," +
                            "qpmax=36," +
                            "qpmin=10," +
                            "me=hex," +
                            "merange=24," +
                            "subme=9," +
                            "qcomp=0.6" +
                            /**/
                        "}" +
                        /**/
                    "}";
            }
            else{
            	transcodeString += 
                    //":sout=" +
                    "#transcode{"+
                        "acodec="+audioTranscoder+
                    "}";
            }
            
            String[] comArray;
            if(simpleProgramName.startsWith("http")){
                // source from remote host
                startingTimeout = 45;
                workingTimeout = 10;
                String[] temp = {
                    vlcDirectory+vlc,
                    "http://"+simpleProgramName.split(" ")[1],
                    "--http-caching",""+500, // ms, default is 1200
                    "--mtu",""+mtu,
                    //"--ts-out-mtu",""+mtu,
                    //transcodeString  +
                    "--sout","\""+transcodeString+
                    ":duplicate{"+
                        // the following line is optional
                        //"dst=display,"+
                        // the following line is optional
                        //comArray[5]+="dst=std{access=file,mux=ts,dst="+repositoryDirectory+"/output_server.mpg},";
                        "dst=rtp{mux=ts,dst=127.0.0.1,port="+vlcPort+"}"+
                    "}"+"\""
                    //,"vlc://quit"
                };
                comArray = temp;
            }
            else if(simpleProgramName.startsWith("dvb-t")){
                // source from a dvb-t device
                String[] temp = {
                    vlcDirectory+vlc,
                    "dvb-t://",
                    ":dvb-frequency="+simpleProgramName.split(" ")[1]+"000",
                    ":program="+simpleProgramName.split(" ")[2],
                    "--mtu",""+mtu,
                    //"--ts-out-mtu",""+mtu,
                    //transcodeString  +
                    "--sout","\""+transcodeString+
                    ":duplicate{"+
                        // the following line is optional
                        //"dst=display,"+
                        // the following line is optional
                        //comArray[5]+="dst=std{access=file,mux=ts,dst="+repositoryDirectory+"/output_server.mpg},";
                        "dst=rtp{" +
                        	"mux=ts,dst=127.0.0.1,port="+vlcPort+"" +
                		"}"+
                    "}"+"\"",
                    "vlc://quit"
                };
                comArray = temp;
            }
            else if(simpleProgramName.equals(webcam)){
                // source from a webcam
                String webcamName = webcam;
                if(webcam.equals("default webcam")){
                    webcamName="";
                }
                String[] temp = {
                    vlcDirectory+vlc,
                    ""+param+"://", // "dshow://",
                    ":"+param+"-vdev="+webcamName, // ":dshow-vdev="+webcamName,
                    ":"+param+"-adev=", // ":dshow-adev=",
                    "--mtu",""+mtu,
                    //"--ts-out-mtu",""+mtu,
                    //transcodeString  +
                    "--sout","\""+transcodeString+
                    ":duplicate{"+
                        // the following line is optional
                        //comArray[5]+="dst=display,";
                        // the following line is optional
                        //comArray[5]+="dst=std{access=file,mux=ts,dst="+repositoryDirectory+"/output_server.mpg},";
                        ",dst=rtp{mux=ts,dst=127.0.0.1,port="+vlcPort+"}"+
                    "}"+"\"",
                    "vlc://quit"
                };
                comArray = temp;
            }
            else{
                // source from a file
                String[] temp = {
                    vlcDirectory+vlc,
                    repositoryDirectory+"/"+simpleProgramName,
                    "--mtu",""+mtu,
                    //"--ts-out-mtu",""+mtu,
                    //transcodeString  +
                    "--sout","\""+transcodeString+
                    ":duplicate{"+
	                    // the following line is optional
	                    //comArray[2]+="dst=display,";
	                    // the following line is optional
	                    //comArray[2]+="dst=std{access=file,mux=ts,dst="+repositoryDirectory+"/output_server.mpg},";
                        "dst=rtp{" +
                        	"mux=ts" + // XXX UNcomment this line
//                    		"mux=mp4" + // XXX comment this line
                        	",dst=127.0.0.1,port="+vlcPort+
                        	//",name=nolescam.sdp", // XXX comment this line
                        	//",sdp=rtsp://127.0.0.1:554/"+"ciao"+".sdp" + // XXX comment this line
                        "}"+
                    "}"+"\""
                    //,"vlc://quit"
                };
                comArray = temp;
                
                if(videoTranscoder.equals("Andr")){ // TODO remove
                    //android 
                	String[] temp2 = {
                			// TODO http://stackoverflow.com/questions/2947369/streaming-video-using-non-standard-protocols
                			// TODO http://forum.videolan.org/viewtopic.php?f=4&t=60335
                			// TODO http://forum.xda-developers.com/archive/index.php/t-527451.html
                			
                			vlcDirectory+vlc,
                            repositoryDirectory+"/"+simpleProgramName,
                            "--mtu",""+mtu,
                            ":sout=#transcode{" +
                            		"vcodec=h264,venc=x264{" +
                            			"no-cabac,level=12,vbv-maxrate=384,vbv-bufsize=1000,keyint=4,ref=3,bframes=0" +
                        			"},width=320,height=180,acodec=mp3,ab=64,vb=384" +
                    			"}" +
                    			":" +
                    			"rtp{" +
                    				//"mux=ts," +
                    				"dst=127.0.0.1" +
                    				",port="+vlcPort+
//                    				",sdp=rtsp://127.0.0.1:"+vlcPort+"/stream.sdp"+
                    				//",mp4a-latm" +
        						"}",
                            /*"--sout","'#transcode{" +
                            	"soverlay,ab=42,samplerate=44100,channels=1,acodec=mp4a,vcodec=h264,width=320,height=180," +
                            	"vfilter=\"canvas{width=320,height=180,aspect=16:9}\",fps=25,vb=200," +
                            	"venc=x264{" +
                            		"vbv-bufsize=500,partitions=all,level=12,no-cabac,subme=7,threads=4,ref=2,mixed-refs=1," +
                            		"bframes=0,min-keyint=1,keyint=50,trellis=2,direct=auto,qcomp=0.0,qpmax=51" +
                        		"}" +
                    		"}" +
                    		":gather:rtp{" +
                    			"mp4a-latm," +
                    			//"sdp=rtsp://127.0.0.1:"+vlcPort+"/"+this.simpleProgramName.split("[ )]")[0]+".sdp" +
                    			//"sdp=rtsp://127.0.0.1:"+vlcPort+"/ramp.sdp" +
                            	",dst=127.0.0.1,port="+vlcPort+
                			"}'",*/
                            "vlc://quit"
                        };
                    //",level=1.2" +
                	//",nocabac," +
                	//",bframes=0" + 
                    comArray = temp2;
                }
            }

            System.out.print("BroadcastService comArray: ");
            for(String s : comArray){
                System.out.print(s+" ");
            }
            System.out.println();

            List<String> comList = Arrays.asList(comArray);
            ProcessBuilder pbVlcSever = new ProcessBuilder(comList);
            pbVlcSever.start();

            /*InputStream is;
            UDPInputStream udpis = new UDPInputStream(dsVlc, startingTimeout, workingTimeout);
            new Thread(udpis).start();
            is = udpis;*/

            if(videoTranscoder.equals("Andr")){ // TODO remove
            	// TODO RtpParser!!!
            	this.rtpMpegParser = new RtpParser(dsVlc, startingTimeout, workingTimeout);
			}
            else if(mux.equals("ts")){
            	UDPInputStream udpis = new UDPInputStream(dsVlc, startingTimeout, workingTimeout);
                new Thread(udpis).start();
                this.rtpMpegParser = new RtpMpegTsParser(udpis, startingTimeout, workingTimeout);
            }
            else{
                throw new Exception("mux = "+mux);
            }
            this.rtpMpegParser.start();
        }

        private FileWriter broadcastServiceLogFileQuality = null;
        @Override
        public void run(){
            System.out.println("BroadcastService: ProgramHandler START "+simpleProgramName);
            try{
                if( RampEntryPoint.isLogging() ){
                    broadcastServiceLogFileQuality = new FileWriter("./temp/BroadcastServiceQuality.csv");
                    broadcastServiceLogFileQuality.write("current time (ms)" + "," + "receiver quality" + "," + "quality variation" + "\n");
                    broadcastServiceLogFileQuality.flush();
                }
                
                rtpMpegParser.setParsePayload(this.isStreamParsing());
                while(active){
                    // 1) wait for RTP packets
                    RTP rtp = rtpMpegParser.getRtp();
                    if(rtp==null){
                        rtp = new RTP(new byte[0]);
                    }
                    rtp.setSimpleProgramName(simpleProgramName);
                    //System.out.println("BroadcastServiceProgramHandler "+this.simpleProgramName+": TS packets "+rtp.getTsPackets().length+", bytes "+rtp.getBytes().length);
                    
                    // 2) send this RTP packet to registered clients
                    EndPoint[] bcArray = receiversDB.keySet().toArray(new EndPoint[0]);
                    for(int i=0; i<bcArray.length; i++){
                    	
                        Long lastAck = ackDB.get(bcArray[i]);
                        if(lastAck!=null && System.currentTimeMillis()-lastAck > 3*1000){
                            // fast decrease
                            System.out.println("BroadcastService.ProgramHandler FAST decrease "+bcArray[i]);
                            //this.changeQuality(bcArray[i], "decrease");
                            //this.changeQuality(bcArray[i], "decrease");
                            this.changeQuality(bcArray[i], -2.0F);
                            ackDB.put(bcArray[i],System.currentTimeMillis());
                        }
                        
                        Long lastContact = receiversDB.get(bcArray[i]);
                        if( lastContact==null || System.currentTimeMillis() - lastContact > BroadcastService.CLIENT_TIMEOUT ){
                            //System.out.println("BroadcastService.ProgramHandler receiversDB.get(bcArray[i]) "+receiversDB.get(bcArray[i]));
                            System.out.println("BroadcastService.ProgramHandler REMOVING "+bcArray[i]);
                            removeReceiver(bcArray[i]);
                        }
                        else{
                        	byte[] sendingRtpBytes;
                        	if( rtp.getRtpHeader() == null ){
                        		System.out.println("BroadcastService FINISHING "+simpleProgramName+": sending ClosingPacket to "+bcArray[i]);
            					sendingRtpBytes = new byte[0];
                        	}
                        	else{
	                            RTP tailoredRtpObject;
	                            if(this.isQualityTailoring()){
	                            	//int pre = rtp.getTsPackets().length;
	                                tailoredRtpObject = qualityTailor(rtp, bcArray[i]);
	                            	//System.out.println("BroadcastService.ProgramHandler sendingRtpObject.getTsPackets().length       from "+pre+" to "+((RTP)sendingRtpObject).getTsPackets().length);
	                            }
	                            else{
	                            	tailoredRtpObject = rtp;
	                            }
	                            
	                            if( isRawBytes() ){
	                            	sendingRtpBytes = tailoredRtpObject.getBytes();
	                            }
	                            else{
	                            	sendingRtpBytes = E2EComm.serialize(tailoredRtpObject);
	                            }
                        	}

                            //if(sendingRtpObject!=null){
                                E2EComm.sendUnicast(
                                    bcArray[i].getAddress(),
                                    bcArray[i].getPort(),
                                    protocol,
                                    sendingRtpBytes
                                );
                            //}
                            //else{
                            //    System.out.println("BroadcastService.ProgramHandler sendingRtp NULL");
                            //}
                        }
                    }

                    // 3) check smart splitter
                    if(splitterAmount>0){
                        // split stream
                        if( !isSmartSplitter() && fakeClient!=null ){
                            System.out.println("BroadcastService stopping FakeClient "+System.currentTimeMillis());
                            this.stopProgramHandler();
                            fakeClient.stopProgramHandler();
                            fakeClient = null;
                        }
                    }

                    if(rtp.getBytes()==null || rtp.getBytes().length==0){
                        throw new EndOfStreamException();
                    }
                }
            }
            catch(EndOfStreamException eose){
                //eose.printStackTrace();
                System.out.println("BroadcastService EndOfStreamException "+simpleProgramName);
            }
            catch(Exception e){
                e.printStackTrace();
            }
            
            /*EndPoint[] bcArray = receiversDB.keySet().toArray(new EndPoint[0]);
            byte[] closingPacket = new byte[0];
            for(int i=0; i<bcArray.length; i++){
            	try {
                    System.out.println("BroadcastService FINISHING "+simpleProgramName+": sending ClosingPacket to "+bcArray[i]);
					E2EComm.sendUnicast(
				        bcArray[i].getAddress(),
				        bcArray[i].getClientPort(),
				        protocol,
				        closingPacket
				    );
				} catch (Exception e) {
					e.printStackTrace();
				}
            }*/
            
            programConfig.setVisible(false);
            bs.programsDB.remove(simpleProgramName);
            rtpMpegParser.stopRtpMpegParser();
            
            if(fakeClient!=null){
                fakeClient.stopProgramHandler();
                fakeClient = null;
            }

            try{
                if( RampEntryPoint.isLogging() && broadcastServiceLogFileQuality!=null ){
                    broadcastServiceLogFileQuality.close();
                    broadcastServiceLogFileQuality = null;
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }

            if( RampEntryPoint.isLogging() ){
                tailoringTimes.removeElementAt(0);
                tailoringTimes.removeElementAt(0);
                tailoringTimes.removeElementAt(0);
                Collections.sort(tailoringTimes);

                int maxValue = Math.round(tailoringTimes.size()*0.95F);

                Long[] elapsedArray = tailoringTimes.toArray(new Long[0]);
                float countMean = 0;
                for(int i=0; i<maxValue; i++){
                    countMean += elapsedArray[i];
                    //System.out.println("BroadcastService tailoringTimes["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
                }
                float mean = countMean/((float)maxValue);

                //for(int i=maxValue; i<elapsedArray.length; i++){
                //    System.out.println("DISCARDING BroadcastService tailoringTimes["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
                //}

                float countStddev = 0;
                for(int i=0; i<maxValue; i++){
                    float x = elapsedArray[i] - mean;
                    countStddev += ( x * x );
                }
                float y = countStddev/((float)maxValue);
                float stddev = (float)Math.sqrt(y);
                System.out.println(
                        "95% BroadcastService tailoringTimes   " +
                        "mean (ms): "+mean/(1000.0F)/(1000.0F)+"   " +
                        "stddev (ms): "+stddev/(1000.0F)/(1000.0F)+"   " +
                        "elapsedArray.length "+maxValue
                );


                maxValue = Math.round(tailoringTimes.size()*0.90F);

                elapsedArray = tailoringTimes.toArray(new Long[0]);
                countMean = 0;
                for(int i=0; i<maxValue; i++){
                    countMean += elapsedArray[i];
                    //System.out.println("BroadcastService tailoringTimes["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
                }
                mean = countMean/((float)maxValue);

                //for(int i=maxValue; i<elapsedArray.length; i++){
                //    System.out.println("DISCARDING BroadcastService tailoringTimes["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
                //}

                countStddev = 0;
                for(int i=0; i<maxValue; i++){
                    float x = elapsedArray[i] - mean;
                    countStddev += ( x * x );
                }
                y = countStddev/((float)maxValue);
                stddev = (float)Math.sqrt(y);
                System.out.println(
                        "90% BroadcastService tailoringTimes   " +
                        "mean (ms): "+mean/(1000.0F)/(1000.0F)+"   " +
                        "stddev (ms): "+stddev/(1000.0F)/(1000.0F)+"   " +
                        "elapsedArray.length "+maxValue
                );
            }
            
            System.out.println("BroadcastService: ProgramHandler FINISHED "+simpleProgramName);
        }

        private void stopProgramHandler(){
            System.out.println("BroadcastService: ProgramHandler.stopProgramHandler "+simpleProgramName+" "+System.currentTimeMillis());
            rtpMpegParser.stopRtpMpegParser();
            this.active = false;
            //rtpMpegParser.
        }

        private synchronized void addReceiver(EndPoint receiver){
            //System.out.println("BroadcastService addReceiver "+receiver+" for "+this.simpleProgramName);
            //System.out.println("BroadcastService addReceiver pre: receiversDB "+receiversDB);
            if(receiversDB.containsKey(receiver)){
                receiversDB.put(receiver, System.currentTimeMillis());
            }
            else{
                // new receiver
                System.out.println("BroadcastService addReceiver: new receiver "+receiver+" for "+simpleProgramName+" "+System.currentTimeMillis());
                ackDB.put(receiver,System.currentTimeMillis());

                qualityDB.put(receiver, new BroadcastReceiverQuality(Math.round(BroadcastService.START_QUALITY)));

                // add the receiver after quality and ack
                receiversDB.put(receiver, System.currentTimeMillis());

                /*System.out.println("BroadcastService addReceiver: " +
                        "splitterAmount="+splitterAmount + " " +
                        "bs.isSmartSplitter()="+bs.isSmartSplitter() + " " +
                        "receiversDB.size()="+receiversDB.size() + " " +
                        "fakeClient="+fakeClient);*/
                // if first client of a split program with smart splitter enabled
                // then create a fake local client with rediscover to the source of the program
                if(splitterAmount>0 && bs.isSmartSplitter() && receiversDB.size()>0 && fakeClient==null){
                    try{
                        System.out.println("BroadcastService CREATE fakeClient for "+simpleProgramName);
                        // look for the service sending this source
                        String[] serviceDest = E2EComm.ipReverse(serviceSource);
                        ServiceResponse service = ServiceDiscovery.findService(serviceDest, "Broadcast", 3*1000);
                        System.out.println("BroadcastService fakeClient service: "+service);

                        // create the fake client
                        if(service!=null){
                            byte newSplitterAmount = (byte)(this.splitterAmount - 1);
                            this.fakeClient = BroadcastClient.getInstanceNoShow(true).new BroadcastClientProgramHandler(service, this.simpleProgramName, newSplitterAmount, true);
                            this.fakeClient.start();
                            System.out.println("BroadcastService fakeClient activated");
                        }
                        else{
                            System.out.println("BroadcastService fakeClient service not found");
                        }
                    }
                    catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
        private synchronized void removeReceiver(EndPoint receiver){
            System.out.println("BroadcastService REMOVING receiver "+receiver+" for "+simpleProgramName+" "+System.currentTimeMillis());
            receiversDB.remove(receiver);
            qualityDB.remove(receiver);
            ackDB.remove(receiver);
            // if removing the only client of a split program with smart splitter enabled,
            // then disable the fake local client with rediscover to the source of the program
            if(receiversDB.isEmpty() && fakeClient!=null){
                // stop the fake client
                fakeClient.stopProgramHandler();
                fakeClient = null;
                System.out.println("BroadcastService REMOVING fakeClient for "+simpleProgramName+" "+System.currentTimeMillis());
            }
        }

        //private void changeQuality(EndPoint receiver, String quality){
        private void changeQuality(EndPoint receiver, float deltaQuality){
            //System.out.println("BroadcastService: ProgramHandler.changeQuality "+programName+" "+quality);
            //System.out.println("BroadcastService receiver="+receiver+" deltaQuality="+deltaQuality);
            //System.out.println("BroadcastService old quality level: "+qualityDB.get(receiver));

            BroadcastReceiverQuality receiverQuality = qualityDB.get(receiver);
            float currentQuality = receiverQuality.getCurrentQuality();
            receiverQuality.setCurrentQuality(currentQuality + deltaQuality);
            if( receiverQuality.getCurrentQuality() > MAX_QUALITY ){
                receiverQuality.setCurrentQuality(MAX_QUALITY);
            }
            else if( receiverQuality.getCurrentQuality() < MIN_QUALITY ){
                receiverQuality.setCurrentQuality(MIN_QUALITY);
            }

            try{
                if( RampEntryPoint.isLogging() && broadcastServiceLogFileQuality!=null ){
                    /*int qualityInt = -1000; //
                    if(deltaQuality.equals("increase")){
                        qualityInt = +1;
                    }
                    else if(deltaQuality.equals("decrease")){
                        qualityInt = -1;
                    }*/
                    //broadcastServiceLogFileQuality.write(System.currentTimeMillis() + "," + qualityDB.get(receiver).getCurrentQuality() + "," + qualityInt + "\n");
                	broadcastServiceLogFileQuality.write(System.currentTimeMillis() + "," + qualityDB.get(receiver).getCurrentQuality() + "," + deltaQuality + "\n");
                    broadcastServiceLogFileQuality.flush();
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }

            receiverQuality = qualityDB.get(receiver);
            currentQuality = receiverQuality.getCurrentQuality();
            float mean = (MAX_QUALITY+MIN_QUALITY)/2.0F;
            /*if( currentQuality == MAX_QUALITY ){
                // remove nothing
                receiverQuality.setPBDropRate(0.0F);
                receiverQuality.setIDropRate(0.0F);
            }
            else if( currentQuality == MIN_QUALITY ){
                // remove everything (but not PES header, PSI and audio)
                receiverQuality.setPBDropRate(MAX_DROP_RATE);
                receiverQuality.setIDropRate(MAX_DROP_RATE/10.0F*9.0F);
            }
            else */
        	if( currentQuality >= mean ){
                // do not remove I frames
        		float newNsdDropRate = MAX_DROP_RATE - MAX_DROP_RATE*(currentQuality-mean)/(MAX_QUALITY-mean);
        		if( newNsdDropRate > 1.0F ){
        			newNsdDropRate = 1.0F;
        		}
                receiverQuality.setNsdDropRate(newNsdDropRate);
                receiverQuality.setSdDropRate(0.0F);
            }
            else {//if(currentQuality<mean){
                // remove every P/B frame and some I frames
                receiverQuality.setNsdDropRate(MAX_DROP_RATE);
                float newSdDropRate = MAX_DROP_RATE - MAX_DROP_RATE*(currentQuality-mean)/(MAX_QUALITY-mean);
        		if( newSdDropRate > 0.9F ){
        			newSdDropRate = 0.9F;
        		}
                receiverQuality.setSdDropRate(newSdDropRate);
            }
        	
        	
        	
            //System.out.println("BroadcastService iDropRate="+receiverQuality.getIDropRate()+" pbDropRate="+receiverQuality.getPBDropRate());
            System.out.println("BroadcastService new quality level: "+qualityDB.get(receiver));
        }
        
        private Vector<Long> tailoringTimes = new Vector<Long>();
        private int gopFrame = 0;
        private RTP qualityTailor(final RTP rtp, EndPoint receiver) throws Exception{

            /**/long startTailoring = -1;
            if( RampEntryPoint.isLogging() ){
                startTailoring = System.nanoTime();
            }/**/

            RTP tailoredRTP = (RTP)(rtp.clone());
            BroadcastReceiverQuality receiverQuality = qualityDB.get(receiver);
            
            for(int i=0; i<tailoredRTP.getTsPackets().length; i++){
                TSPacket tsPacket = tailoredRTP.getTsPackets()[i];
                byte frameType = tsPacket.getFrameType();
                //System.out.println("BroadcastService frameType="+frameType+" pid="+tsPacket.getPid());
                
                if(tsPacket.isPayloadUnitStart()){
                    //System.out.println("BroadcastService isPayloadUnitStart "+tsPacket.getPid());
                    if( frameType==TSPacket.UNDEFINED ){
                        //System.out.println("BroadcastService.qualityTailor: null frameType???");
                    }
                    else if( frameType==TSPacket.AUDIO ){
                        receiverQuality.setCurrentAudioPid(tsPacket.getPid());
                    }
                    else if( TSPacket.isVideo(frameType) ){
                		// MPEG2 & h264
                        //System.out.println("BroadcastService.qualityTailor: frameType = "+frameType);
                        
                    	receiverQuality.setCurrentVideoPid(tsPacket.getPid());
                        receiverQuality.setDropCurrentVideoFrame(false);
                        
                        float random = RampEntryPoint.nextRandomFloat();
                        if( frameType == TSPacket.I_FRAME // mpeg2
                        		|| frameType == TSPacket.IDR_FRAME ){ // h264 Instantaneous Decoder Refresh (IDR)
                        	gopFrame = 0;
                            if( random < receiverQuality.getSdDropRate() ){
                                receiverQuality.setDropCurrentVideoFrame(true);
                            }
                        }
                        else if( frameType==TSPacket.P_FRAME 
                                || frameType==TSPacket.B_FRAME ){ // mpeg2
                        	gopFrame++;
                        	if( random < receiverQuality.getNsdDropRate() ){
                                receiverQuality.setDropCurrentVideoFrame(true);
                            }
                        }
                        else if( frameType==TSPacket.NON_IDR_FRAME ){ // h264
                        	gopFrame++;
                        	float gopProb = 1-((float)gopFrame/(float)(gopSize-1)); // # P frames = gopSize -1
                        	//System.out.println("BroadcastService.qualityTailor: gopFrame="+gopFrame+" gopSize="+gopSize+" gopProb="+gopProb);
                        	// start dropping frames from the last of the GoP
                            if( receiverQuality.getNsdDropRate() > gopProb ){ 
                                receiverQuality.setDropCurrentVideoFrame(true);
                            }
                        }

                    }
                } // END isPayloadUnitStart
            	
            	if( tsPacket.getPid() == receiverQuality.getCurrentVideoPid() ){
                    if( receiverQuality.isDropCurrentVideoFrame() ){
                        //System.out.println("BroadcastService.qualityTailor: dropping TS Packet "+receiverQuality.getCurrentVideoPid());
                        tailoredRTP.removeTsPacket(i);
                        i--;
                    }
                }
                
            } // end for

            /**/
            if( RampEntryPoint.isLogging() && startTailoring!=-1 ){
                long endTailoring = System.nanoTime();
                long elapsedTailoring = endTailoring - startTailoring;
                //System.out.println("BroadcastService elapsedTailoring: "+elapsedTailoring);
                tailoringTimes.addElement(elapsedTailoring);
            }
            /**/

            return tailoredRTP;
        }
    }
    
}

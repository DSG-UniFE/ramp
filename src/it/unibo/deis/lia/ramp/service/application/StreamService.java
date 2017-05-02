/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.core.e2e.*;
import it.unibo.deis.lia.ramp.*;

import java.io.*;
import java.net.*;

/**
 *
 * @author useruser
 */
public class StreamService extends Thread{

    private boolean open = true;

    private String repositoryDirectory="./temp";
    private String vlcDirectory;
    private String vlc;

    private int protocol = E2EComm.UDP;
    //private int protocol = E2EComm.TCP;
    private short timeoutConnect = 500; // ms
    
    private int bitrate; // kbits/s (used only for raw-UDP)

    private String webcam;
    private String param; // Fabio Pascucci

    private final BoundReceiveSocket localServiceSocket;
    private static StreamService streamService=null;
    private static StreamServiceJFrame ssjf;
    public static synchronized StreamService getInstance(){
        try{
            if(streamService==null){
                streamService=new StreamService();
                streamService.start();
            }
            ssjf.setVisible(true);
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return streamService;
    }
    private StreamService() throws Exception{
        localServiceSocket = E2EComm.bindPreReceive(protocol);
        ServiceManager.getInstance(false).registerService(
        		"Stream", 
        		localServiceSocket.getLocalPort(), 
        		protocol);
        bitrate=860;

        vlc="vlc";
        if(RampEntryPoint.os.startsWith("linux")){
            vlcDirectory="";
            webcam="default webcam";
            //webcam="/dev/video0";
            param="v4l2";
            try{
                Process pId = Runtime.getRuntime().exec("id -ru");
                BufferedReader brId = new BufferedReader(new InputStreamReader(pId.getInputStream()));
                String lineId;
                lineId=brId.readLine();
                if(lineId.equals("0")){
                    vlc="vlc-wrapper";
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        else if(RampEntryPoint.os.startsWith("mac")){
            vlcDirectory="/Applications/VLC.app/Contents/MacOS/";
            //webcam="webcam ???";
            webcam="default webcam";
            param = "dshow"; // TODO corretto?
        }
        else if(RampEntryPoint.os.startsWith("windows")){
            vlcDirectory="C:/VLC-1.1.12/";
            //webcam="Creative WebCam (WDM)";
            //webcam="Logitech QuickCam Communicate STX";
            webcam="USB 2.0 UVC 0.3M Webcam";
            //webcam="default webcam";
            param = "dshow";
        }
        else{
            vlcDirectory="???";
            webcam="???";
            param = "???";
        }

        ssjf = new StreamServiceJFrame(this);
    }

    public String getVlcDirectory() {
        System.out.println("StreamService getVlcDirectory "+vlcDirectory);
        return vlcDirectory;
    }
    public void setVlcDirectory(String vlcDirectory) {
        System.out.println("StreamService setVlcDirectory "+vlcDirectory);
        this.vlcDirectory = vlcDirectory+"/";
    }

    public int getBitrate() {
        System.out.println("StreamService getBitrate: "+bitrate);
        return bitrate;
    }
    public void setBitrate(int bitrate) {
        System.out.println("StreamService setBitrate: "+bitrate);
        this.bitrate = bitrate;
    }

    public short getConnectTimeout() {
		return timeoutConnect;
	}
	public void setTimeoutConnect(short timeoutConnect) {
		this.timeoutConnect = timeoutConnect;
	}
	
	public String getWebcam() {
        System.out.println("StreamService getWebcam: "+webcam);
        return webcam;
    }
    public void setWebcam(String webcam) {
        System.out.println("StreamService setWebcam: "+webcam);
        this.webcam = webcam;
    }

    public String getRepositoryDirectory(){
        return repositoryDirectory;
    }
    public void setRepositoryDirectory(String repositoryDirectory){
        this.repositoryDirectory=repositoryDirectory;
    }

    public String[] getStreamList(){
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
                            || name.endsWith(".avi")
                            || name.endsWith(".ts")
                            || name.endsWith(".wmv")
                            || name.endsWith(".3gp")
                        );
            }
        };
        list = dir.list(filter);
        String[] res=new String[list.length+1];
        System.arraycopy(list, 0, res, 0, list.length);
        res[res.length-1] = webcam;
        return res;
    }
    
    public void stopService(){
        open = false;
        /*try {
            localServiceSocket.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }*/
        ServiceManager.getInstance(false).removeService("Stream");
        streamService=null;
    }

    @Override
    public void run(){
        try{
            System.out.println("StreamService START");
            while(open){
                try{
                    GenericPacket gp = E2EComm.receive(localServiceSocket, 5*1000);
                    System.out.println("StreamService new request");
                    new StreamHandler(gp).start();
                }
                catch(SocketTimeoutException ste){
                    //
                }
                catch(SocketException se){
                    se.printStackTrace();
                }
            }
            localServiceSocket.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("StreamService FINISHED");
    }

    private class StreamHandler extends Thread{
        private GenericPacket gp;
        private StreamHandler(GenericPacket gp){
            this.gp = gp;
        }
        @Override
        public void run(){
            try{
            	
                // check packet type
                if( gp instanceof UnicastPacket){
                    // check payload
                    UnicastPacket up = (UnicastPacket)gp;
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    
                    if(payload instanceof StreamRequest){
                        System.out.println("StreamService StreamRequest");
                        StreamRequest request = (StreamRequest)payload;
                        String fileName = request.getStreamName();
                        String[] newDest = E2EComm.ipReverse(up.getSource());
                        int newDestNodeId = up.getSourceNodeId();
                        byte[] res = null;

                        if(fileName.equals("list")){
                            System.out.println("StreamService list");
                            //send file list
                            res = E2EComm.serialize(getStreamList());
                            E2EComm.sendUnicast(
                                newDest,
                                request.getClientPort(),
                                protocol,
                                res
                            );
                        }
                        else{
                            System.out.println("StreamService fileName: "+fileName);

                            int sendProtocol;
                            if(request.getRampProtocol().equals("udp")){
                                sendProtocol = E2EComm.UDP;
                            }
                            else{
                                sendProtocol = E2EComm.TCP;
                            }

                            if(request.getStreamProtocol().equals("raw-UDP")
                                    && (!fileName.equals(webcam)) ){
                                // send raw bytes of a file via UDP
                                File f = new File(repositoryDirectory+"/"+fileName);
                                    res = new byte[1024*10]; // XXX which is the optimal size?
                                    FileInputStream fis = new FileInputStream(f);
                                    int readBytes;
                                    
                                    // send unicast until End Of File
                                    //while( (readBytes=fis.read((byte[])res))!=-1 ){
                                    while( (readBytes = fis.read(res,0,res.length))!=-1 ){
                                        if(readBytes>0){
                                            //byte[] sending = new byte[readBytes];
                                            //System.arraycopy(res, 0, sending, 0, readBytes);
                                            
                                            E2EComm.sendUnicast(
                                                newDest,
                                                newDestNodeId,
                                                request.getClientPort(),
                                                sendProtocol,
                                                false,
                                                GenericPacket.UNUSED_FIELD,
                                                GenericPacket.UNUSED_FIELD,
                                                GenericPacket.UNUSED_FIELD,
                                                timeoutConnect, // millis
                                                res
                                            );
                                            long sleep = readBytes/bitrate*8;
                                            //System.out.println("sleep "+sleep);
                                            Thread.sleep(sleep);
                                        }
                                    }
                                    fis.close();
                            }
                            else{
                                DatagramSocket dsVlc = new DatagramSocket();
                                dsVlc.setReuseAddress(true);
                                int localVlcOutputPort = dsVlc.getLocalPort();
                                dsVlc.close();

                                String vcodec = "mp2v"; // MPEG-2 Video

                                // eventually adjust video bitrate
                                String video_birate = "40"; // kbit/s
                                String fps = "25";
                                int mtu = 1500; // 1500;

                                String[] comArray = null;
                                
                                if(fileName.equals(webcam)){
                                    String webcamName = webcam;
                                    if(webcam.equals("default webcam")){
                                        webcamName="";
                                    }
                                    // source from a webcam
                                    /*source+="dshow:// " +
                                        ":dshow-vdev=\""+fileName+"\" " +
                                        ":dshow-adev=\"none\" " + // no audio
                                        ":dshow-size=\"\"";*/
                                    
                                    comArray = new String[8];
                                    comArray[0]=vlcDirectory+vlc;
                                    comArray[1]=""+param+"://"; // comArray[1]="dshow://";
                                    comArray[2]=":"+param+"-vdev="+webcamName; // comArray[2]=":dshow-vdev="+webcamName;
                                    //comArray[3]=":"+param+"-adev=\"none\""; // comArray[3]=":dshow-adev=\"none\"";
                                    comArray[3]=":"+param+"-size=\"\""; // comArray[3]=":dshow-size=\"\"";
                                    //comString+="--mtu=5000 :sout=#transcode{vcodec="+vcodec+",vb="+video_birate+",fps="+fps+",acodec=mp3,ab=128}";
                comArray[4]="--mtu"; comArray[5]=""+mtu;
                //comArray[6]="--ts-out-mtu"; comArray[7]=""+mtu;
                                    comArray[6]=":sout=#transcode{vcodec="+vcodec+",vb="+video_birate+",fps="+fps+",acodec=mp3,ab=64}";
                                    comArray[6]+=":duplicate{";

                                    // the following line is optional
                                    //comString+="dst=display,";

                                    // the following line is optional
                                    //comString+="dst=std{access=file,mux=ts,dst="+repositoryDirectory+"/output_server.mpg},";

                                    if(request.getStreamProtocol().equals("ts-UDP")){
                                        comArray[6]+="dst=std{access=udp,mux=ts,dst=127.0.0.1:"+localVlcOutputPort+"}";
                                    }
                                    else if(request.getStreamProtocol().equals("ts-RTP")){
                                        comArray[6]+="dst=rtp{dst=127.0.0.1,port="+localVlcOutputPort+",mux=ts}";
                                    }
                                    else{
                                        throw new Exception("Protocol unsupported with this kind of stream: request.getUdp_rtp() = " + request.getStreamProtocol());
                                    }
                                    comArray[6]+="}";
                                    comArray[7]="vlc://quit";
                                }
                                else{
                                    // source from a file

                                    comArray = new String[6];
                                    comArray[0]=vlcDirectory+vlc;
                                    comArray[1]=repositoryDirectory+"/"+fileName;
                                    //comString+="--mtu=5000 :sout=#transcode{vcodec="+vcodec+",vb="+video_birate+",fps="+fps+",acodec=mp3,ab=128}";
                comArray[2]="--mtu"; comArray[3]=""+mtu;
                //comArray[4]="--ts-out-mtu"; comArray[5]=""+mtu;
                                    comArray[4]=":sout=#transcode{vcodec="+vcodec+",vb="+video_birate+",fps="+fps+",acodec=mp3,ab=64}";
                                    comArray[4]+=":duplicate{";

                                    // the following line is optional
                                    //comString+="dst=display,";

                                    // the following line is optional
                                    //comString+="dst=std{access=file,mux=ts,dst="+repositoryDirectory+"/output_server.mpg},";

                                    if(request.getStreamProtocol().equals("ts-UDP")){
                                        comArray[4]+="dst=std{access=udp,mux=ts,dst=127.0.0.1:"+localVlcOutputPort+"}";
                                    }
                                    else if(request.getStreamProtocol().equals("ts-RTP")){
                                        comArray[4]+="dst=rtp{dst=127.0.0.1,port="+localVlcOutputPort+",mux=ts}";
                                    }
                                    else{
                                        throw new Exception("Protocol unsupported with this kind of stream: request.getUdp_rtp() = " + request.getStreamProtocol());
                                    }
                                    comArray[4]+="}";
                                    comArray[5]="vlc://quit";
                                }
                                
                               System.out.print("StreamService comArray ");
                               for(String s : comArray){
                                   System.out.print(s+" ");
                               }
                               System.out.println();
                               Runtime.getRuntime().exec(comArray);

                                // read the UDP stream from the local vlc
                                DatagramSocket dsFromVlc = new DatagramSocket(localVlcOutputPort);
                                dsFromVlc.setReuseAddress(true);
                                dsFromVlc.setSoTimeout(10*1000);
                                //byte[] receiveBuffer = new byte[2*1024]; // at least 1400, i.e., MTU
                                byte[] receiveBuffer = new byte[(int)(mtu*1.20)]; // at least 1400, i.e., MTU
                                //InetAddress localAddress = InetAddress.getByName("127.0.0.1");
                                InetAddress localAddress = InetAddress.getLocalHost();

                                // send the stream
                                try{
                                    while(true){// read from local vlc
                                        DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length, localAddress, localVlcOutputPort);
                                        //System.out.println("StreamService receiving...");
                                        dsFromVlc.receive(packet);
                                        dsFromVlc.setSoTimeout(2*1000);
                                        int readBytes = packet.getLength();
                                        //System.out.println("StreamService readBytes = "+readBytes);
                                        
                                        // write to local Dispatcher
                                        byte[] sending=new byte[readBytes];
                                        System.arraycopy(receiveBuffer, 0, sending, 0, readBytes);

                                        try{
                                            E2EComm.sendUnicast(
                                                newDest,
                                                newDestNodeId,
                                                request.getClientPort(),
                                                sendProtocol,
                                                false,
                                                GenericPacket.UNUSED_FIELD,
                                                GenericPacket.UNUSED_FIELD,
                                                GenericPacket.UNUSED_FIELD,
                                                timeoutConnect, // millis
                                                sending
                                            );
                                        }
                                        catch(Exception e){
                                            e.printStackTrace();
                                        }
                                        
                                    }
                                }
                                catch(SocketTimeoutException ste){
                                    //System.out.println("StreamService webcam finished");
                                }
                                dsFromVlc.setSoTimeout(100);
                                dsFromVlc.close();
                            }

                            // send End Of Stream (packet with 0 bytes in the payload)
                            try{
                                E2EComm.sendUnicast(
                                    newDest,
                                    newDestNodeId,
                                    request.getClientPort(),
                                    sendProtocol,
                                    false,
                                    GenericPacket.UNUSED_FIELD,
                                    GenericPacket.UNUSED_FIELD,
                                    GenericPacket.UNUSED_FIELD,
                                    timeoutConnect, // millis
                                    new byte[0]
                                );
                            }
                            catch(Exception e){
                                e.printStackTrace();
                            }
                            
                        }
                        
                        System.out.println("StreamService FINISHED StreamRequest res "+res);
                    }
                    else{
                        // received payload is not StreamRequest: do nothing...
                        System.out.println("StreamService wrong payload: "+payload);
                    }
                }
                else{
                     // received packet is not UnicastPacket: do nothing...
                    System.out.println("StreamService wrong packet: "+gp.getClass().getName());
                }
                
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}

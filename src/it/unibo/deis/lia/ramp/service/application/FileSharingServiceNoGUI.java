
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

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.application.FileSharingRequest.FileSharingList;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

/**
 *
 * @author useruser
 */
public class FileSharingServiceNoGUI extends Thread{

    private boolean open=true;

    private String sharedDirectory = "./temp/fsService";
    private int bufferSize = 0;
    private boolean bestBufferSize = false;

    private int protocol = E2EComm.TCP;

    private static BoundReceiveSocket serviceSocket;
    private static FileSharingServiceNoGUI fileSharing=null;


	public static boolean isActive(){
        return FileSharingServiceNoGUI.fileSharing != null;
    }
    public static synchronized FileSharingServiceNoGUI getInstance(){
        try{

            if(FileSharingServiceNoGUI.fileSharing==null){
                FileSharingServiceNoGUI.fileSharing = new FileSharingServiceNoGUI(false); // FileSharingService senza GUI
                FileSharingServiceNoGUI.fileSharing.start();

            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return FileSharingServiceNoGUI.fileSharing;
    }
    public static synchronized FileSharingServiceNoGUI getInstanceNoShow(){
        try{
            if(FileSharingServiceNoGUI.fileSharing==null){
                FileSharingServiceNoGUI.fileSharing = new FileSharingServiceNoGUI(false);
                FileSharingServiceNoGUI.fileSharing.start();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return FileSharingServiceNoGUI.fileSharing;
    }
    protected FileSharingServiceNoGUI(boolean gui) throws Exception{
        serviceSocket = E2EComm.bindPreReceive(protocol);

        ServiceManager.getInstance(false).registerService("" +
        		"FileSharing",
        		serviceSocket.getLocalPort(),
        		protocol
    		);

        if(RampEntryPoint.getAndroidContext() != null){
        	sharedDirectory = RampEntryPoint.getAndroidSharedDirectory().getAbsolutePath() + "/fsService";
        	File dir = new File(sharedDirectory);
        	if(!dir.exists())
        		dir.mkdir();
        }
    }

    public void setBestBufferSize(boolean bestBufferSize) {
        this.bestBufferSize = bestBufferSize;
    }
    public int getBufferSize() {
        return bufferSize;
    }
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public String getSharedDirectory(){
        return sharedDirectory;
    }
    public void setSharedDirectory(String sharedDirectory){
        this.sharedDirectory=sharedDirectory;
    }

    public String[] getFileList(){
    	String[] res = new String[0];
    	try{
	        File dir = new File(sharedDirectory);
	        res = dir.list();
	        // filter the list of returned files
	        // to not return any files that start with '.'.
	        FilenameFilter filter = new FilenameFilter() {
	            @Override
	            public boolean accept(File dir, String name) {
	                return !name.startsWith(".");
	            }
	        };
	        res = dir.list(filter);
        }
        catch(Exception e){
        	e.printStackTrace();
        }
        return res;
    }

    public void stopService(){
        System.out.println("FileSharingService close");
        ServiceManager.getInstance(false).removeService("FileSharing");
        open=false;
        try {
            serviceSocket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run(){
        try{
            System.out.println("FileSharingService START");
            System.out.println("FileSharingService START "+serviceSocket.getLocalPort()+" "+protocol);
            while(open){
                try{
                    // receive
                    GenericPacket gp = E2EComm.receive(serviceSocket, 5*1000);
                    //System.out.println("FileSharingService new request");
                    new FileSharingHandler(gp).start();
                }
                catch(SocketTimeoutException ste){
                    //System.out.println("FileSharingService SocketTimeoutException");
                }
            }
            serviceSocket.close();
        }
        catch(SocketException se){

        }
        catch(Exception e){
            e.printStackTrace();
        }
        FileSharingServiceNoGUI.fileSharing = null;
        System.out.println("FileSharingService FINISHED");
    }

    private class FileSharingHandler extends Thread{
        private GenericPacket gp;

        private FileSharingHandler(GenericPacket gp){
            this.gp=gp;
        }
        @Override
        public void run(){
            try{
                if( gp instanceof UnicastPacket){
                    // 1) payload
                    UnicastPacket up = (UnicastPacket)gp;
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if(payload instanceof FileSharingRequest){
                        System.out.println("FileSharingService FileSharingRequest");
                        FileSharingRequest request=(FileSharingRequest)payload;
                        String fileName = request.getFileName();
                        int expiry = request.getExpiry();
                        long startReceiving=System.currentTimeMillis();
                        if( ! request.isGet() ){
                            // receiving a file
                            System.out.println("FileSharingService: receiving "+fileName+"...");

                            BoundReceiveSocket receiveFileSocket = E2EComm.bindPreReceive(
                            		E2EComm.TCP //protocol
                            		);

                            // sending local port
                            String[] newDest=E2EComm.ipReverse(up.getSource());
                            E2EComm.sendUnicast(
                                    newDest,
                                    request.getClientPort(),
                                    protocol,
                                    //0, // default buffer size
                                    E2EComm.serialize(receiveFileSocket.getLocalPort())
                            );

//                            //Stefano Lanzone - New sendUnicast with hardcoded values (es. expiry = 10 * 60)
//                            E2EComm.sendUnicast(
//                                    newDest,
//                                    up.getSourceNodeId(),
//                                    request.getClientPort(),
//                                    protocol,
//                                    false, GenericPacket.UNUSED_FIELD,
//                                    E2EComm.DEFAULT_BUFFERSIZE,
//                                    GenericPacket.UNUSED_FIELD,
//                                    86400,
//                                    GenericPacket.UNUSED_FIELD,
//                                    E2EComm.serialize(receiveFileSocket.getLocalPort())
//                            );

                            File f = new File(sharedDirectory+"/"+fileName);
                            FileOutputStream fos = new FileOutputStream(f);

                            int timeout = 10*1000;
                            if(expiry != GenericPacket.UNUSED_FIELD)
                            	timeout = expiry * 1000;
                            // waiting for file
                            E2EComm.receive(
                            		receiveFileSocket,
                            		timeout,
                            		fos
                        		);
                            long endReceiving=System.currentTimeMillis();
                            fos.close();
                            float receivingTime = (endReceiving-startReceiving) / 1000.0F;

                            String notifyText =  "Received file "+fileName +" from "+up.getSourceNodeId();
                            notify(notifyText);
                            System.out.println("FileSharingService: "+fileName+" received in "+receivingTime+"s" );
                            GeneralUtils.appendLog("FileSharingService: "+fileName+" received in "+receivingTime+"s" +" from "+newDest);
                        }
                        else{
                            String[] newDest = E2EComm.ipReverse(up.getSource());
                            FileSharingList res=null;
                            int sendingBufferSize = bufferSize;
                            if(fileName.equals("")){
                                System.out.println("FileSharingService list");
                                // 2a) send file list
                                res = new FileSharingList(getFileList());

                                sendingBufferSize = E2EComm.DEFAULT_BUFFERSIZE;

                                // send unicast with bufferSize
                                E2EComm.sendUnicast(
                                        newDest,
                                        up.getSourceNodeId(), //"".hashCode(),
                                        request.getClientPort(),
                                        protocol,
                                        false,
                                        GenericPacket.UNUSED_FIELD,
                                        sendingBufferSize,
                                        GenericPacket.UNUSED_FIELD, // packetDeliveryTimeout
                                        (short)GenericPacket.UNUSED_FIELD, // packetTimeoutConnect
                                        E2EComm.serialize(res)
                                );

//                                //Stefano Lanzone - New sendUnicast with hardcoded values (es. expiry = 10 * 60)
//                                E2EComm.sendUnicast(
//                                        newDest,
//                                        up.getSourceNodeId(),
//                                        request.getClientPort(),
//                                        protocol,
//                                        false,
//                                        GenericPacket.UNUSED_FIELD,
//                                        sendingBufferSize,
//                                        GenericPacket.UNUSED_FIELD,
//                                        86400,
//                                        GenericPacket.UNUSED_FIELD, // packetTimeoutConnect
//                                        E2EComm.serialize(res)
//                                );
                            }
                            else{
                                // 2b) send a specific file
                                System.out.println("FileSharingService fileName: "+fileName);
                                File f = new File(sharedDirectory+"/"+fileName);
                                FileInputStream fis = new FileInputStream(f);
                                if(bestBufferSize==true){
                                    sendingBufferSize = E2EComm.bestBufferSize(newDest.length, f.length());
                                }


                                // send unicast with bufferSize
//                                E2EComm.sendUnicast(
//                                        newDest,
//                                        up.getSourceNodeId(), //"".hashCode(),
//                                        request.getClientPort(),
//                                        E2EComm.TCP, //.protocol,
//                                        false,
//                                        GenericPacket.UNUSED_FIELD,
//                                        sendingBufferSize,
//                                        GenericPacket.UNUSED_FIELD,
//                                        fis
//                                );

                                //Stefano Lanzone
                                E2EComm.sendUnicast(
                                        newDest,
                                        up.getSourceNodeId(), //"".hashCode(),
                                        request.getClientPort(),
                                        E2EComm.TCP, //.protocol,
                                        false,
                                        GenericPacket.UNUSED_FIELD, //impostare il timewait?
                                        sendingBufferSize,
                                        GenericPacket.UNUSED_FIELD,
                                        expiry,
                                        GenericPacket.UNUSED_FIELD,
                                        fis
                                );

                                String notifyText =  "Sent file "+fileName +" to "+up.getSourceNodeId();
                                notify(notifyText);
                                GeneralUtils.appendLog("FileSharingService: "+fileName+" sent to "+up.getSourceNodeId());
                                //fis.close();
                            }
                        }
                    }
                    else{
                        // received payload is not FileSharingRequest: do nothing...
                        System.out.println("FileSharingService wrong payload: "+payload);
                    }
                }
                else{
                    // received packet is not UnicastPacket: do nothing...
                    System.out.println("FileSharingService wrong packet: "+gp.getClass().getName());
                }

            }
            catch(Exception e){
                e.printStackTrace();
            }
        }

		private void notify(String text) {
			if (RampEntryPoint.getAndroidContext() != null){
				try {
			         Class<?> activityFS = Class.forName("it.unife.dsg.ramp_android.service.application.FileSharingServiceActivity");

			         Method mI=activityFS.getMethod("createNotification", String.class);
			         Method aMI = activityFS.getMethod("getInstance");
			         if(mI!=null){

			          	mI.invoke(aMI.invoke(null, new Object[]{}), text);
			          }

			     } catch (IllegalAccessException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (IllegalArgumentException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (InvocationTargetException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (NoSuchMethodException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (SecurityException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (ClassNotFoundException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     }
			}
		}
    }
}

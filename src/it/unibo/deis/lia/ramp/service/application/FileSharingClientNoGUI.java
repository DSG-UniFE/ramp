
package it.unibo.deis.lia.ramp.service.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.application.FileSharingRequest.FileSharingList;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

/**
 *
 * @author useruser
 */
public class FileSharingClientNoGUI{

    private String sharedDirectory="./temp/fsClient";

    protected static FileSharingClientNoGUI fileSharingClientNoGUI = null;

	protected FileSharingClientNoGUI(){
    	if( RampEntryPoint.getAndroidContext() != null ){
    		sharedDirectory = RampEntryPoint.getAndroidSharedDirectory().getAbsolutePath() + "/fsClient";
    		File dir = new File(sharedDirectory);
    		if(!dir.exists())
    			dir.mkdir();
    	}
    }
    public static synchronized FileSharingClientNoGUI getInstance(){
        if(fileSharingClientNoGUI == null){
            fileSharingClientNoGUI = new FileSharingClientNoGUI();
        }
        return fileSharingClientNoGUI;
    }

    public void stopClient(){
        fileSharingClientNoGUI = null;
    }

    public static boolean isActive() {
		return (fileSharingClientNoGUI != null);
	}

    public Vector<ServiceResponse> findFileSharingService(int ttl, int timeout, int serviceAmount) throws Exception{
        long pre = System.currentTimeMillis();
        Vector<ServiceResponse> services = ServiceDiscovery.findServices(
                ttl,
                "FileSharing",
                timeout,
                serviceAmount,
                null
        );
        long post = System.currentTimeMillis();
        float elapsed = (post-pre)/(float)1000;
        System.out.println("FileSharingClient findFileSharingService elapsed="+elapsed+"    services="+services);
        return services;
    }

    public String[] getRemoteFileList(ServiceResponse service) throws Exception{
        BoundReceiveSocket clientSocket = E2EComm.bindPreReceive(service.getProtocol());
        FileSharingRequest fsr=new FileSharingRequest(true, "fileList", "", clientSocket.getLocalPort(), GenericPacket.UNUSED_FIELD);
        E2EComm.sendUnicast(
                service.getServerDest(),
                service.getServerPort(),
                service.getProtocol(),
                E2EComm.serialize(fsr)
        );

//      //Stefano Lanzone - New sendUnicast with expiry value
//      E2EComm.sendUnicast(service.getServerDest(),
//		  service.getServerNodeId(), service.getServerPort(), service.getProtocol(),
//		  false, GenericPacket.UNUSED_FIELD,
//        E2EComm.DEFAULT_BUFFERSIZE,
//        GenericPacket.UNUSED_FIELD,
//        86400,
//        GenericPacket.UNUSED_FIELD,
//        E2EComm.serialize(fsr)
//		  );

        System.out.println("FileSharingClient fileList service.getServerDest()="+Arrays.toString(service.getServerDest())+" service.getServerPort="+service.getServerPort()+" service.getProtocol="+service.getProtocol());

        // receive the file list
        UnicastPacket up = (UnicastPacket)E2EComm.receive(
                clientSocket,
                8*1000 // timeout
        );
        clientSocket.close();
        FileSharingList availableFiles = (FileSharingList) E2EComm.deserialize(up.getBytePayload());
        return availableFiles.getFileNames().toArray(new String[0]);
    }

    public void getRemoteFile(ServiceResponse service, String fileName, int expiry) throws Exception{
        BoundReceiveSocket socketClient = E2EComm.bindPreReceive(
        		E2EComm.TCP // service.getProtocol()
    		);
        FileSharingRequest fsr = new FileSharingRequest(true, "getFile", fileName, socketClient.getLocalPort(), expiry);
        System.out.println("FileSharingClient requiring "+fileName );
        GeneralUtils.appendLog("FileSharingClient getFile "+fileName +" from "+service.getServerDest());

        if(expiry == GenericPacket.UNUSED_FIELD)
        {
        	E2EComm.sendUnicast(
        			service.getServerDest(),
        			service.getServerPort(),
        			service.getProtocol(),
        			E2EComm.serialize(fsr)
        			);
        }
        else
        {
        	//Stefano Lanzone - New sendUnicast with expiry value
        	E2EComm.sendUnicast(service.getServerDest(),
        			service.getServerNodeId(), service.getServerPort(), service.getProtocol(),
        			false, GenericPacket.UNUSED_FIELD,
        			E2EComm.DEFAULT_BUFFERSIZE,
        			GenericPacket.UNUSED_FIELD,
        			expiry,
        			GenericPacket.UNUSED_FIELD,
        			E2EComm.serialize(fsr)
        			);
        }

        // receive the requested file and write it in the local filesystem
        FileOutputStream fos = new FileOutputStream(sharedDirectory+"/"+fileName);
        long pre = System.currentTimeMillis();

        int timeout = 5*1000;
        if(expiry != GenericPacket.UNUSED_FIELD)
        	timeout = expiry * 1000;
        UnicastHeader uh = (UnicastHeader)E2EComm.receive(
                socketClient,
                timeout, // timeout
                fos
        );
        socketClient.close();

        long post = System.currentTimeMillis();
        float elapsed = (post-pre)/(float)1000;
        System.out.println("FileSharingClient getFile elapsed(s)="+elapsed+" bufferSize(B)="+uh.getBufferSize()+" size(KB)="+(fos.getChannel().position()/1024.0F)+" throughput(Kbit/s)="+(fos.getChannel().position()*8.0F/1024.0F/elapsed));
        GeneralUtils.appendLog("FileSharingClient getFile elapsed(s)="+elapsed+" bufferSize(B)="+uh.getBufferSize()+" size(KB)="+(fos.getChannel().position()/1024.0F)+" throughput(Kbit/s)="+(fos.getChannel().position()*8.0F/1024.0F/elapsed));

        //byte[] fileByte= (byte[])up.getObjectPayload();
        //FileOutputStream fos=new FileOutputStream(sharedDirectory+"/"+file);
        //fos.write(fileByte);
        fos.close();

        String notifyText = "Received remote file "+fileName;
        notify(notifyText);
        GeneralUtils.appendLog("FileSharingClient received remote file "+fileName+" from "+service.getServerDest());
        System.out.println("FileSharingClient received "+fileName);
    }

    public void sendLocalFile(ServiceResponse service, String fileName, int expiry) throws Exception{
        BoundReceiveSocket socketClient = E2EComm.bindPreReceive(
        		service.getProtocol()
    		);
        FileSharingRequest fsr=new FileSharingRequest(false, "sendFile", fileName, socketClient.getLocalPort(), expiry);
        System.out.println("FileSharingClient: request to send "+fileName);
        GeneralUtils.appendLog("FileSharingClient: request to send "+fileName +" to "+service.getServerDest());

        if(expiry == GenericPacket.UNUSED_FIELD)
        {
        	E2EComm.sendUnicast(
        			service.getServerDest(),
        			service.getServerPort(),
        			service.getProtocol(),
        			E2EComm.serialize(fsr)
        			);
        }
        else
        {
        	//Stefano Lanzone - New sendUnicast with expiry value
        	E2EComm.sendUnicast(service.getServerDest(),
        			service.getServerNodeId(), service.getServerPort(), service.getProtocol(),
        			false, GenericPacket.UNUSED_FIELD,
        			E2EComm.DEFAULT_BUFFERSIZE,
        			GenericPacket.UNUSED_FIELD,
        			expiry,
        			GenericPacket.UNUSED_FIELD,
        			E2EComm.serialize(fsr)
        			);
        }

        int timeout = 5*1000;
        if(expiry != GenericPacket.UNUSED_FIELD)
        	timeout = expiry * 1000;
    	UnicastPacket upServerPort = (UnicastPacket) E2EComm.receive(socketClient, timeout);
    	int port = (Integer)(E2EComm.deserialize(upServerPort.getBytePayload()));
        System.out.println("FileSharingClient: sending "+fileName+" to "+port);
        GeneralUtils.appendLog("FileSharingClient: sending "+fileName+" to "+port);

        File f = new File(sharedDirectory+"/"+fileName);
        FileInputStream fis = new FileInputStream(f);

        if(expiry == GenericPacket.UNUSED_FIELD)
        {
        	E2EComm.sendUnicast(
        			service.getServerDest(),
        			port,
        			E2EComm.TCP, //service.getProtocol(),
        			fis
        			);
        }
        else
        {

        	//Stefano Lanzone - New sendUnicast with expiry value
        	E2EComm.sendUnicast(service.getServerDest(),
        			service.getServerNodeId(), port, E2EComm.TCP,
        			false, GenericPacket.UNUSED_FIELD,
        			E2EComm.DEFAULT_BUFFERSIZE,
        			GenericPacket.UNUSED_FIELD,
        			expiry,
        			GenericPacket.UNUSED_FIELD,
        			fis
        			);
        }

        //fis.close();
        GeneralUtils.appendLog("FileSharingClient: "+fileName+" sent to "+service.getServerDest());
        System.out.println("FileSharingClient: "+fileName+" sent");
    }

    public String[] getLocalFileList(){
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

    private void notify(String text) {
		if (RampEntryPoint.getAndroidContext() != null){
			try {
		         Class<?> activityFS = Class.forName("it.unife.dsg.ramp.android.service.application.FileSharingServiceActivity");

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

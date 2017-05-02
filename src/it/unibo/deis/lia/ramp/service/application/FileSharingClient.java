
package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;

/**
 *
 * @author useruser
 */
public class FileSharingClient extends FileSharingClientNoGUI {

    private static FileSharingClient fileSharingClient = null;
    private static FileSharingClientJFrame fscj = null;
    
	private FileSharingClient(boolean gui){
		if( gui && RampEntryPoint.getAndroidContext()==null ){
        	fscj = new FileSharingClientJFrame(this);
        }
    }
	
    public static synchronized FileSharingClient getInstance() {
        if(fileSharingClient == null){
            fileSharingClient = new FileSharingClient(true);
        }
        if(fscj != null){
            fscj.setVisible(true);
        }
        FileSharingClientNoGUI.getInstance();
        return fileSharingClient;
    }
    
    public static synchronized FileSharingClient getInstanceNoShow(){
        if(fileSharingClient==null){
            fileSharingClient=new FileSharingClient(false);
        }
        FileSharingClientNoGUI.getInstance();
        return fileSharingClient;
    }

    public void stopClient(){
    	if(fscj!=null)
    		fscj.setVisible(false);
        fileSharingClient=null;
        super.stopClient();
    }
}


package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;

/**
 *
 * @author useruser
 */
public class FileSharingService extends FileSharingServiceNoGUI {

    private static FileSharingService fileSharing = null;
    private static FileSharingServiceJFrame fssjf = null;
    
    public static synchronized FileSharingService getInstance(boolean gui){
    	try{
            if(FileSharingService.fileSharing==null){
                FileSharingService.fileSharing = new FileSharingService(gui);
                fileSharing.start(); // Added by Dmitrij
                //FileSharingServiceNoGUI.getInstance();
            }
            if(fssjf!=null){
                fssjf.setVisible(true);
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    	return FileSharingService.fileSharing;
    }
	
    public static synchronized FileSharingService getInstance(){
        return FileSharingService.getInstance(true); // FileSharingService senza GUI Dmitrij
        
    }
    /*public static synchronized FileSharingService getInstanceNoShow(){
        try {
            if(FileSharingService.fileSharing==null){
                FileSharingService.fileSharing = new FileSharingService(false);
                //FileSharingServiceNoGUI.getInstance();
            }
            if(fssjf!=null) {
                
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return FileSharingService.fileSharing;
    }*/
    
    private FileSharingService(boolean gui) throws Exception{
    	super(gui);
        
        if(gui && RampEntryPoint.getAndroidContext() == null){
        	fssjf = new FileSharingServiceJFrame(this);
        }
    }

    @Override
    public void stopService() {
        super.stopService();
        fileSharing = null;
    }
}

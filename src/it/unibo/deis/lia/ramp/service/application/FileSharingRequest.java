
package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;

/**
 *
 * @author useruser
 */
public class FileSharingRequest implements Serializable {
	
    private static final long serialVersionUID = 1328809791835230307L;
	
	private String fileName;
    private int clientPort = -1;
    private boolean get;
    private String action;
    private int expiry = GenericPacket.UNUSED_FIELD;
    
    public FileSharingRequest(boolean get, String action, String fileName, int clientPort, int expiry) {
    	this.get = get;
    	this.action = action;
        this.fileName = fileName;
        this.clientPort = clientPort;
        this.expiry = expiry;
    }

    public int getClientPort() {
        return clientPort;
    }
    public String getFileName() {
        return fileName;
    }
	public boolean isGet() {
		return get;
	}
	
	public String getAction() {
		return action;
	}
	
	public int getExpiry() {
		return expiry;
	}

	public static class FileSharingList implements Serializable {

		private static final long serialVersionUID = -2942214714449356065L;
		
		private Set<String> fileNames;
		
		public FileSharingList(String[] fileNames){
			this.fileNames = new HashSet<String>();
			this.fileNames.addAll(Arrays.asList(fileNames));
		}
		
		public void removeFile(String fileName){
			fileNames.remove(fileName);
		}
		
		public Set<String> getFileNames(){
			return fileNames;
		}
		
	}
    
}

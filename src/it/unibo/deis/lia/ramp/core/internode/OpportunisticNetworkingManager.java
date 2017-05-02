package it.unibo.deis.lia.ramp.core.internode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.OpportunisticNetworkingManager.ReplacePackets;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;

/**
 * 
 * @author Stefano Lanzone
 */
public class OpportunisticNetworkingManager extends Thread {

	private String savedPacketDirectory = "./temp/savedPacket";
	public static final String FILE_SAVEDPACKET_EXT = ".sp";
	public static final String FILEPACKET_EXT = ".packet";
	
	private static OpportunisticNetworkingManager opportunisticNetworkingManager = null; 
	private OpportunisticNetworkingSettings opportunisticNetworkingSettings;
	
	private Map<SavedPacket, GenericPacket> savedPackets;
	private Map<Integer, Long> managedPackets;   //Table with the managed packets
	
	//Getters and Setters
	public int getSendPacketsPeriod() {
		return opportunisticNetworkingSettings.getSendPacketsPeriod();
	}

	public void setSendPacketsPeriod(int sendPacketsPeriod) {
		this.opportunisticNetworkingSettings.setSendPacketsPeriod(sendPacketsPeriod);
	}

	public int getExpirationTimeManagedPackets() {
		return opportunisticNetworkingSettings.getExpirationTimeManagedPackets();
	}

	public void setExpirationTimeManagedPackets(int expirationTimeManagedPackets) {
		this.opportunisticNetworkingSettings.setExpirationTimeManagedPackets(expirationTimeManagedPackets);
	}

	public boolean isPersistPackets() {
		return this.opportunisticNetworkingSettings.isPersistPackets();
	}

	public void setPersistPackets(boolean persistPackets) {
		this.opportunisticNetworkingSettings.setPersistPackets(persistPackets);
	}
	
	public boolean isRemovePacketAfterSend() {
		return this.opportunisticNetworkingSettings.isRemovePacketAfterSend();
	}

	public void setRemovePacketAfterSend(boolean removePacketAfterSend) {
		this.opportunisticNetworkingSettings.setRemovePacketAfterSend(removePacketAfterSend);
	}

	public int getAvailableStorage() {
		return this.opportunisticNetworkingSettings.getAvailableStorage();
	}

	public void setAvailableStorage(int availableStorage) {
		this.opportunisticNetworkingSettings.setAvailableStorage(availableStorage);
	}

	public int getNumberOfOneHopDestinations() {
		return this.opportunisticNetworkingSettings.getNumberOfOneHopDestinations();
	}

	public void setNumberOfOneHopDestinations(int numberOfOneHopDestinations) {
		this.opportunisticNetworkingSettings.setNumberOfOneHopDestinations(numberOfOneHopDestinations);
	}
	
	public int getMinNumberOfOneHopDestinations() {
		return this.opportunisticNetworkingSettings.getMinNumberOfOneHopDestinations();
	}

	public void setMinNumberOfOneHopDestinations(int minNumberOfOneHopDestination) {
		this.opportunisticNetworkingSettings.setMinNumberOfOneHopDestinations(minNumberOfOneHopDestination);
	}
	
	public int getMaxNumberOfOneHopDestinations() {
		return this.opportunisticNetworkingSettings.getMaxNumberOfOneHopDestinations();
	}

	public void setMaxNumberOfOneHopDestinations(int maxNumberOfOneHopDestination) {
		this.opportunisticNetworkingSettings.setMaxNumberOfOneHopDestinations(maxNumberOfOneHopDestination);
	}
	
	public int getPacketSizeThresholdHigher() {
		return this.opportunisticNetworkingSettings.getPacketSizeThresholdHigher();
	}

	public void setPacketSizeThresholdHigher(int packetSizeThresholdHigher) {
		this.opportunisticNetworkingSettings.setPacketSizeThresholdHigher(packetSizeThresholdHigher);
	}
	
	public int getPacketSizeThresholdLower() {
		return this.opportunisticNetworkingSettings.getPacketSizeThresholdLower();
	}

	public void setPacketSizeThresholdLower(int packetSizeThresholdLower) {
		this.opportunisticNetworkingSettings.setPacketSizeThresholdLower(packetSizeThresholdLower);
	}
	
	public ReplacePackets getReplacePackets() {
		return ReplacePackets.valueOf(this.opportunisticNetworkingSettings.getReplacePackets());
	}

	public void setReplacePackets(ReplacePackets replacePackets) {
		this.opportunisticNetworkingSettings.setReplacePackets(replacePackets.toString());
	}

	//getInstance
	public static synchronized OpportunisticNetworkingManager getInstance(boolean forceStart) {
		if (forceStart && opportunisticNetworkingManager == null) {
			opportunisticNetworkingManager = new OpportunisticNetworkingManager();
			
			opportunisticNetworkingManager.start();
			
			System.out.println("OpportunisticNetworkingManager ENABLED");
		}
		return opportunisticNetworkingManager;
	}
	
	private OpportunisticNetworkingManager()
	{ 
		 this.savedPackets = new HashMap<SavedPacket, GenericPacket>();
		 this.managedPackets = new HashMap<Integer, Long>();
	     
		 if( RampEntryPoint.getAndroidContext() != null ){
			 savedPacketDirectory = RampEntryPoint.getAndroidSharedDirectory().getAbsolutePath() + "/savedPacket";
	    		File dir = new File(savedPacketDirectory);
	    		if(!dir.exists())
	    			dir.mkdir();
	     }
		 
		 this.opportunisticNetworkingSettings = deserializeSettings();
		 
	     //get packet information from persistent storage and restore in savedPackets table
	     String[] savedPacketsFileList = getSavedPacketsFileList();
	     for(String fileName:savedPacketsFileList) {
                    SavedPacket savedPacket = restoreSavedPacket(savedPacketDirectory +"/" +fileName);
                    savedPackets.put(savedPacket, null);
	    	}
	}
	
	private OpportunisticNetworkingSettings deserializeSettings()
	{
		OpportunisticNetworkingSettings ons = new OpportunisticNetworkingSettings();
		String fileName = savedPacketDirectory +"/" +"opportunistic_networking_settings.ser";  
		
		File file = new File(fileName);
		if(file.exists())
		{
			//Deserialization
			try {
				FileInputStream fileIn = new FileInputStream(fileName);
				ObjectInputStream in = new ObjectInputStream(fileIn);
				ons = (OpportunisticNetworkingSettings) in.readObject();
				in.close();
				fileIn.close();
				System.out.println("OpportunisticNetworkingManager: OpportunisticNetworkingSettings data deserialized");
			}catch(IOException i) {
	         i.printStackTrace();
			}catch(ClassNotFoundException c) {
	         System.out.println("OpportunisticNetworkingManager: OpportunisticNetworkingSettings class not found in deserializeSettings");
	         c.printStackTrace();
			}
		}
		
		return ons;
	}
	
	public void serializeSettings()
	{
		String fileName = savedPacketDirectory +"/" +"opportunistic_networking_settings.ser";  
		
		try {
	         FileOutputStream fileOut = new FileOutputStream(fileName);
	         ObjectOutputStream out = new ObjectOutputStream(fileOut);
	         out.writeObject(opportunisticNetworkingSettings);
	         out.close();
	         fileOut.close();
	         System.out.printf("OpportunisticNetworkingManager: OpportunisticNetworkingSettings data is saved in "+fileName);
	      }catch(IOException i) {
	    	 System.out.println("OpportunisticNetworkingManager: OpportunisticNetworkingSettings not found in serializeSettings");
	         i.printStackTrace();
	      }
	}
	
	/*
	 * Metodi usati in fase di inizializzazione per sapere quali pacchetti 
	 * sono salvati in memoria
	 */
	private String[] getSavedPacketsFileList(){
    	String[] res = new String[0];
    	try{
	        File dir = new File(savedPacketDirectory);
	        res = dir.list();
	        // filter the list of returned files
	        // to not return any files that start with '.' and contains "_" (packet)
	        FilenameFilter filter = new FilenameFilter() {
	            @Override
	            public boolean accept(File dir, String name) {
	                return !name.startsWith(".") && name.endsWith(FILE_SAVEDPACKET_EXT);
	            }
	        };
	        res = dir.list(filter);
        }
        catch(Exception e){
        	e.printStackTrace();
        }
        return res;
    }
	
	private SavedPacket restoreSavedPacket(String fileName)
	{
		 SavedPacket savedPacket = null;
		 
		 //Deserialization
		 try {
	         FileInputStream fileIn = new FileInputStream(fileName);
	         ObjectInputStream in = new ObjectInputStream(fileIn);
	         savedPacket = (SavedPacket) in.readObject();
	         in.close();
	         fileIn.close();
	      }catch(IOException i) {
	         i.printStackTrace();
	      }catch(ClassNotFoundException c) {
	         System.out.println("OpportunisticNetworkingManager: SavedPacket class not found");
	         c.printStackTrace();
	      }
		 
		 if(savedPacket != null)
			 System.out.println("OpportunisticNetworkingManager: Deserialized SavedPacket "+savedPacket.getId());
		 
		 return savedPacket;
	}
	
	/*
	 * Metodi usati dal task periodico per inviare i pacchetti ancora validi 
	 * salvati in memoria
	 */
	private GenericPacket restorePacket(SavedPacket savedPacket) throws Exception
	{
		GenericPacket gp = savedPackets.get(savedPacket);
		if(gp == null)
		{
			String fileNamePacket = savedPacketDirectory +"/"+savedPacket.getId() + FILEPACKET_EXT;
			File fp= new File(fileNamePacket);
				if (fp.exists())
				{   
					byte[] fileData = new byte[(int) fp.length()];
//					DataInputStream dis = new DataInputStream(new FileInputStream(fp));
//					dis.readFully(fileData);
//					dis.close();
					
					int totalBytesRead = 0;
					@SuppressWarnings("resource")
					InputStream input = new BufferedInputStream(new FileInputStream(fp));
			        while(totalBytesRead < fileData.length){
			            int bytesRemaining = fileData.length - totalBytesRead;
			            //input.read() returns -1, 0, or more :
			            int bytesRead = input.read(fileData, totalBytesRead, bytesRemaining); 
			            if (bytesRead > 0){
			              totalBytesRead = totalBytesRead + bytesRead;
			            }
			          }
			        
					gp = E2EComm.deserializePacket(fileData);
					savedPackets.put(savedPacket, gp);
				}
		}
				
	    return gp;
	}
	
	private void removePacket(SavedPacket savedPacket)
	{
		GeneralUtils.appendLog("OpportunisticNetworkingManager removePacket "+savedPacket.getId());
		
		//Add packet to menaged packet table
		managedPackets.put(savedPacket.getId(), System.currentTimeMillis());
		
		//Delete packet files
		String fileNameSavedPacket = savedPacketDirectory +"/"+savedPacket.getId() + FILE_SAVEDPACKET_EXT;  
		String fileNamePacket = savedPacketDirectory +"/"+savedPacket.getId() + FILEPACKET_EXT;  
		File fsp= new File(fileNameSavedPacket);
        if (fsp.exists())
        	fsp.delete();
        File fp= new File(fileNamePacket);
        if (fp.exists())
        	fp.delete();
        
        savedPackets.remove(savedPacket);
	}
	
	private void refreshPacketExpiry(SavedPacket savedPacket)
	{
		//Aggiornare il valore della variabile expiry
	    long currentTime=System.currentTimeMillis();
	    long packetSaveTime = savedPacket.getSaveTime();
	    long deltaTime = currentTime - packetSaveTime;
	    int expiry = savedPacket.getExpiry();
	    int newExpiry = (int)(expiry - 
	    		TimeUnit.MILLISECONDS.toSeconds(deltaTime));
	    savedPacket.setExpiry(newExpiry);
	}
	
	private void sendPacket(SavedPacket savedPacket) throws Exception
	{	
		boolean sendOK = false;
		//Get packet with restore packet method
		GenericPacket gp = restorePacket(savedPacket);
        
		//Test if GenericPacket is unicast or broadcast
		if (gp instanceof UnicastPacket) {
			GeneralUtils.appendLog("OpportunisticNetworkingManager: try to send the unicast packet "+savedPacket.getId());
			UnicastPacket up = (UnicastPacket) gp;
			
			//Discovery to found the destination node
			UnicastHeader uh = up.getHeader();
			int destNodeId = uh.getDestNodeId();
			
			System.out.println("OpportunisticNetworkingManager looking for destNodeId = " + destNodeId);
			ResolverPath bestPath = null;
			
			Vector<ResolverPath> availablePaths = Resolver.getInstance(false).resolveBlocking(destNodeId, 2500);
			
			if (availablePaths != null) {
				for (int i = 0; i < availablePaths.size(); i++) {
					ResolverPath aPath = availablePaths.elementAt(i);
//					if (failedNextHop == null || !aPath.getPath()[0].equals(failedNextHop.toString().replaceAll("/", ""))) {
						// the first hop of "aPath" is not the just failed hop
						if (bestPath == null) {
							bestPath = aPath;
						} else if (aPath.getPath().length < bestPath.getPath().length) {
							// XXX hardcoded bestPath based on hop count: should be more flexible...
							bestPath = aPath;
						}
//					}
				}
			}
			
			if(bestPath != null)
			{
				System.out.println("OpportunisticNetworkingManager bestPath = "+Arrays.toString(bestPath.getPath()));
			    
				// 2a) modify UP
				String[] previousDest = uh.getDest();
				if (previousDest == null) {
					previousDest = new String[0];
					uh.setCurrentHop((byte) 0);
				}
				
				String[] newDest = new String[uh.getCurrentHop() + bestPath.getPath().length];
			
				// copy the already performed path from UH
				int i = 0;
				for (; i < uh.getCurrentHop(); i++) {
					newDest[i] = previousDest[i];
				}

				// replace the rest of the path with the newly discovered best path
				for (int j = 0; j < bestPath.getPath().length; j++) {
					newDest[i + j] = bestPath.getPath()[j];
				}
				System.out.println("OpportunisticNetworkingManager newDest = "+Arrays.toString(newDest));
				uh.setDest(newDest);
				
				// 2b) send ResolverAdvice to the sender
				if (uh.getSource().length > 0) {// .getCurrentHop()>0){
				// send ResolverAdvice only if the sender is not the local node
				System.out.println("OpportunisticNetworkingManager send ResolverAdvice: uh.getSource( )= " + Arrays.toString(uh.getSource()));
				ResolverAdvice resolverAdvice = new ResolverAdvice(uh.getDestNodeId(), previousDest, uh.getCurrentHop(), newDest);

				E2EComm.sendUnicast(E2EComm.ipReverse(uh.getSource()), Resolver.RESOLVER_PORT, E2EComm.UDP, E2EComm.serialize(resolverAdvice));
				}
				
				//Send Packet
		        int retry = GenericPacket.UNUSED_FIELD;
		        up.setRetry((byte)retry);
				sendOK = E2EComm.sendUnicast(E2EComm.TCP, up);
				
				GeneralUtils.appendLog("OpportunisticNetworkingManager: sent unicast packet to bestPath = "+Arrays.toString(bestPath.getPath()));
			}
			else
			{
				sendOK = sendPacketToNeighbors(gp);
			}
			
			if(!sendOK)
				GeneralUtils.appendLog("OpportunisticNetworkingManager: unicast packet "+savedPacket.getId() +" not sent to neighbors");
			else
				GeneralUtils.appendLog("OpportunisticNetworkingManager: sent unicast packet "+savedPacket.getId() +" to neighbors");
			
			if(sendOK && isRemovePacketAfterSend())
			{
				GeneralUtils.appendLog("OpportunisticNetworkingManager: remove unicast packet "+savedPacket.getId() +" sent");
				removePacket(savedPacket);
			}
		}
		else if (gp instanceof BroadcastPacket) {
			GeneralUtils.appendLog("OpportunisticNetworkingManager: try to send the broadcast packet "+savedPacket.getId());
			BroadcastPacket bp = (BroadcastPacket) gp;
			Set<Integer> exploredNodeIdList = savedPacket.getExploredNodeIdList();
			
			//Invio il pacchetto in broadcast
			E2EComm.sendBroadcast(E2EComm.TCP, exploredNodeIdList, bp); 
			
			//Aggiornare l'elenco dei vicini
			Vector<InetAddress> neighbors = Heartbeater.getInstance(false).getNeighbors();
			exploredNodeIdList = savedPacket.getExploredNodeIdList();
			for (int i = 0; i < neighbors.size(); i++) {
				final InetAddress aNeighbor = neighbors.elementAt(i);
				Integer destNodeId = Heartbeater.getInstance(false).getNodeId(aNeighbor);
				if(!exploredNodeIdList.contains(destNodeId))
					exploredNodeIdList.add(destNodeId);
			}
			savedPacket.setExploredNodeIdList(exploredNodeIdList);
		}
		else {
			// not UnicastPacket not BroadcastPacket
			GeneralUtils.appendLog("OpportunisticNetworkingManager unknown packet type: " + gp.getClass().getName());
			throw new Exception("OpportunisticNetworkingManager unknown packet type: " + gp.getClass().getName());
		}
	}
	
    /*
     * Metodi privati per gestire i pacchetti in ingresso con il metodo
     * receivedPacket(GenericPacket gp)
     */

	private boolean containsPacket(SavedPacket savedPacket) 
	{
		boolean res = false;
	 
		for(SavedPacket sp : savedPackets.keySet())
	    {
			if(sp.equals(savedPacket))
			{	
				res = true;
				GeneralUtils.appendLog("OpportunisticNetworkingManager savedPackets: contains packet in local memory");
			}
	    }
		
		if(res == false)
		{
			int packetId = savedPacket.getId();
			if(managedPackets.containsKey(packetId))
			{
				GeneralUtils.appendLog("OpportunisticNetworkingManager managedPackets: packet already managed");
				
				/* Il pacchetto è già stato gestito, 
				 * vedere se è stato gestito per un tempo superiore a expirationTimeManagedPackets  */
				long saveTime = managedPackets.get(packetId);
				if(System.currentTimeMillis() - saveTime > (getExpirationTimeManagedPackets() * 60 * 1000))
					managedPackets.remove(packetId);
				else
					res = true;
			}
		}
		
		return res;
	}
	
	private boolean savePacket(SavedPacket savedPacket, GenericPacket gp) throws IOException
	{
		boolean res = false;

		//Aggiungere informazioni di pacchetto relative al salvataggio
		savedPacket.setSaveTime(System.currentTimeMillis());
		
		if(gp instanceof BroadcastPacket)
		{	
			//Salvare i vicini se il pacchetto è di broadcast
			Vector<InetAddress> neighbors = Heartbeater.getInstance(false).getNeighbors();
			Set<Integer> exploredNodeIdList = new HashSet<Integer>();
			for (int i = 0; i < neighbors.size(); i++) {
				final InetAddress aNeighbor = neighbors.elementAt(i);
				Integer destNodeId = Heartbeater.getInstance(false).getNodeId(aNeighbor);
				exploredNodeIdList.add(destNodeId);
			}
			savedPacket.setExploredNodeIdList(exploredNodeIdList);
		}
		
		//Verificare se c'è spazio in memoria per salvare il pacchetto
		long availableStorageByte = getAvailableStorage() * 1024 * 1024; //(1 MB = 1024 KBytes, 1 KB = 1024 Bytes)
		
		//Determinare lo spazio che occuperebbe il pacchetto in memoria
		long savedPacketSize = E2EComm.objectSize(savedPacket);
		long packetSize = E2EComm.objectSizePacket(gp);
		long totalPacketSize = savedPacketSize + packetSize;
	    
		GeneralUtils.appendLog("OpportunisticNetworkingManager: try to save packet "+savedPacket.getId() + " with packetSize = "+packetSize 
				+", savedPacketSize = "+savedPacketSize +", totalPacketSize = "+totalPacketSize);
		
		if(totalPacketSize <= availableStorageByte)
		{   //Il pacchetto può essere memorizzato
			try
			{
				//Liberare la memoria se necessario
				freeStorage(availableStorageByte, totalPacketSize);
				
				//Store packet
				storeSavedPacket(savedPacket); 
				storePacket(gp, savedPacketDirectory +"/"+savedPacket.getId() + FILEPACKET_EXT);
				
				//Add packet to saved packets table
				savedPackets.put(savedPacket, gp);
				
				GeneralUtils.appendLog("OpportunisticNetworkingManager: packet "+savedPacket.getId() +" saved");
				res = true;
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			GeneralUtils.appendLog("OpportunisticNetworkingManager: packet "+savedPacket.getId() +" not saved, because is too big!");
		}

		return res;
	}
	
	private void freeStorage(long maxStorageSize, long totalPacketSize) throws Exception
	{
		File savedPacketFolder = new File(savedPacketDirectory);
		long savedPacketFolderSize = getFolderSize(savedPacketFolder);
		long availableSize = maxStorageSize - savedPacketFolderSize;
        
		if(totalPacketSize > availableSize)
	    {
			GeneralUtils.appendLog("OpportunisticNetworkingManager: free storage because totalPacketSize = "+totalPacketSize +" > "+" availableSize = "+availableSize);
			
			File[] files = savedPacketFolder.listFiles(new FilenameFilter() {
		    public boolean accept(File dir, String name) {
		        return !name.startsWith(".") && name.endsWith(FILEPACKET_EXT);
		    }
			});
		
	    	//Non c'è abbastanza spazio in memoria, eliminare un pacchetto
			boolean delete = deletePacket(files);
			//Chiamata ricorsiva per rifare la verifica, se è stato eliminato qualcosa
			if(delete)
				freeStorage(maxStorageSize, totalPacketSize);
			else
			{
				GeneralUtils.appendLog("OpportunisticNetworkingManager error: No files to free storage!");
				throw new Exception("OpportunisticNetworkingManager: No files to free storage!");	
			}
	    }
	}
	
	private boolean deletePacket(File [] files)
	{
		boolean delete = false;
		
		if(files.length == 0)
			return delete;
		
		//storage unavailable, delete files
    	switch (getReplacePackets()) {
        case OLD: 
            System.out.println("OpportunisticNetworkingManager: Delete old packets");
            GeneralUtils.appendLog("OpportunisticNetworkingManager: delete the older packet");
            
            Arrays.sort(files, new Comparator<File>()
      		{
 				@Override
 				public int compare(File o1, File o2) {
 					if (o1.lastModified() < o2.lastModified()) {
      		            return -1;
      		        } else if (o1.lastModified() > o2.lastModified()) {
      		            return +1;
      		        } else {
      		            return 0;
      		        }
 				}
      		});
            
            break;
        case SMALL: 
        	 System.out.println("OpportunisticNetworkingManager: Delete small packets");
        	 GeneralUtils.appendLog("OpportunisticNetworkingManager: delete the smaller packet"); 
        	 
        	 Arrays.sort(files, new Comparator<File>()
      		{
 				@Override
 				public int compare(File o1, File o2) {
 					if (o1.length() < o2.length()) {
      		            return -1;
      		        } else if (o1.length() > o2.length()) {
      		            return +1;
      		        } else {
      		            return 0;
      		        }
 				}
      		});
             
            break;
        case HUGE:
        	 System.out.println("OpportunisticNetworkingManager: Delete huge packets");
        	 GeneralUtils.appendLog("OpportunisticNetworkingManager: delete the bigger packet");  
        	 
             Arrays.sort(files, new Comparator<File>()
     		{
				@Override
				public int compare(File o1, File o2) {
					if (o1.length() > o2.length()) {
     		            return -1;
     		        } else if (o1.length() < o2.length()) {
     		            return +1;
     		        } else {
     		            return 0;
     		        }
				}
     		});
             
        	 break;
        }
    	
    	File fileToDelete = files[0];
    	String packetToDelete = fileToDelete.getName();

    	int pos = packetToDelete.lastIndexOf(".");
    	if (pos > 0) {
    		packetToDelete = packetToDelete.substring(0, pos);
    	}
    	int savedPacketId = Integer.parseInt(packetToDelete);
    
    	 Set<Entry<SavedPacket, GenericPacket>> set = savedPackets.entrySet();
         Iterator<Entry<SavedPacket, GenericPacket>> iterator = set.iterator();
         SavedPacket savedPacket = null;
         while(iterator.hasNext() && savedPacket == null) {
        	 @SuppressWarnings("rawtypes")
			Map.Entry mentry = (Map.Entry)iterator.next();
        	 
        	 if(((SavedPacket)(mentry.getKey())).getId() == savedPacketId)
        	 {	 
        		 savedPacket = (SavedPacket) (mentry.getKey());
        		 removePacket(savedPacket);
        		 delete = true;
        	 }
         }
         
         return delete;  
	}
	
	private long getFolderSize(File folder) {
		long length = 0;
	    File[] files = folder.listFiles();
	 
	    int count = files.length;
	 
	    for (int i = 0; i < count; i++) {
	        if (files[i].isFile()) {
	            length += files[i].length();
	        }
	        else {
	            length += getFolderSize(files[i]);
	        }
	    }
	    return length;
	}
	
	private void storeSavedPacket(SavedPacket savedPacket)
	{
		String fileNameSavedPacket = savedPacketDirectory +"/"+savedPacket.getId() + FILE_SAVEDPACKET_EXT;  
		
		try {
	         FileOutputStream fileOut = new FileOutputStream(fileNameSavedPacket);
	         ObjectOutputStream out = new ObjectOutputStream(fileOut);
	         out.writeObject(savedPacket);
	         out.close();
	         fileOut.close();
	         System.out.printf("OpportunisticNetworkingManager: Serialized data is saved in "+fileNameSavedPacket);
	         GeneralUtils.appendLog("OpportunisticNetworkingManager: savedPacket "+savedPacket.getId() +" serialized");  
	      }catch(IOException i) {
	         i.printStackTrace();
	      }
	}
	
	private void storePacket(GenericPacket gp, String fileNamePacket)
	{
		try {
			OutputStream output = null;
			output = new BufferedOutputStream(new FileOutputStream(fileNamePacket));
	        output.write(E2EComm.serializePacket(gp));
	        output.close();
	        GeneralUtils.appendLog("OpportunisticNetworkingManager: packet serialized in "+fileNamePacket);  
		}
	    catch(IOException i) {
        i.printStackTrace();
        }
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private boolean sendPacketToNeighbors(GenericPacket gp) throws Exception {
		
		boolean res = false;
		UnicastPacket up;
		UnicastHeader uh;
		//Test if GenericPacket is unicast or broadcast
		if (gp instanceof UnicastPacket) {
				up = (UnicastPacket) gp;
                uh = up.getHeader();
		}
		else {
		      // not UnicastPacket
			  throw new Exception("OpportunisticNetworkingManager unknown packet type: " + gp.getClass().getName());
		}
		
		
		String[] currentNeighbors = RampEntryPoint.getInstance(false, null).getCurrentNeighbors();
		String[] neighbors;
		
//		int k = 0;
		if (currentNeighbors.length > 0 && uh.getSource().length > 0)
		{
			//Invio il pacchetto ai vicini, ma no alla sorgente del pacchetto
//			neighbors = new String[currentNeighbors.length - 1];
			
			List<String> temp_neighbors = new ArrayList<String>();
			String source = uh.getSource()[uh.getSource().length - 1]; //???
			
			for(int i=0; i<currentNeighbors.length; i++){
//				String[] n = currentNeighbors[i].split("[.]");
//				String[] s = source.split("[.]");
				
				if(currentNeighbors[i].equals(source))
				{	
					System.out.printf("OpportunisticNetworkingManager: the neighbor "+currentNeighbors[i] +" is the source "+source);
				}
				else
				{
					temp_neighbors.add(currentNeighbors[i]);
//					k++;
				}
			}
			neighbors = new String[temp_neighbors.size()];
			neighbors = temp_neighbors.toArray(neighbors);
		}
		else
			neighbors = currentNeighbors;
		
		int onehopdestinations = neighbors.length; 
		
		if(onehopdestinations > 0)
		{
			double packetSize = E2EComm.objectSizePacket(gp);
			double thresholdLower = getPacketSizeThresholdLower() * 1024; //bytes
			double thresholdHigher = getPacketSizeThresholdHigher() * 1024; //bytes
			
			if(packetSize < thresholdLower)
			{
				//pacchetto catalogato come "piccolo", posso mandare il pacchetto a più destinatari
				onehopdestinations = getMaxNumberOfOneHopDestinations();
			}
			else if(packetSize > thresholdHigher)
			{
				//pacchetto catalogato come "grande", mando a meno destinatari
				onehopdestinations = getMinNumberOfOneHopDestinations();
			}
			else
			{
				//pacchetto di dimensioni "normali"
				onehopdestinations = getNumberOfOneHopDestinations();
			}
			
			// 1) find new paths to the same nodeId
			String failedNextHop = null;
			// if(uh.getDest()!=null){
			if (uh.getDest() != null && uh.getDest().length > uh.getCurrentHop()) {
				failedNextHop = uh.getDest()[uh.getCurrentHop()];
			}

			int destNodeId = uh.getDestNodeId();
			// remove the failed path to nodeId
			Resolver resolver = Resolver.getInstance(false);
			if (resolver != null && failedNextHop != null) {
				resolver.removeEntry(destNodeId, failedNextHop, 0);
			}
			resolver = null;
			
			//Invio il pacchetto a #onehopdestinations
			for(int i=0; i<neighbors.length && i < onehopdestinations; i++){
				GeneralUtils.appendLog("OpportunisticNetworkingManager send packet "+up.getId() +" to neighbor " +neighbors[i]);
				
				String[] previousDest = uh.getDest();
				String[] newDest;
				
				if (previousDest == null) {
					previousDest = new String[0];
					uh.setCurrentHop((byte) 0);
				}
				
				if(previousDest.length == 0 || uh.getCurrentHop() == previousDest.length - 1)
					newDest = new String[previousDest.length + 1];
				else
					newDest = new String[previousDest.length];
				
				//newDest = new String[uh.getCurrentHop() + 1];
				
				// copy the already performed path from UH
				int j = 0;
				for (; j < uh.getCurrentHop(); j++) {
					newDest[j] = previousDest[j];
				}
				
				newDest[j] = neighbors[i];
				
				if(newDest.length == previousDest.length)
				{
					j++;
					for (; j < newDest.length; j++) {
						newDest[j] = previousDest[j];
					}
				}
				else
				{
					for (; j < newDest.length - 1; j++) {
						newDest[j + 1] = previousDest[j];
					}
				}
				
				uh.setDest(newDest);

		        int retry = GenericPacket.UNUSED_FIELD;
		        up.setRetry((byte)retry);
				E2EComm.sendUnicast(E2EComm.TCP, up);
				res = true;
            }	
		}
		
		return res;
	}
	
	//Logica di gestione dei pacchetti che arrivano in input a runtime
	public void receivePacket(GenericPacket gp) throws Exception
	{
		SavedPacket savedPacket = new SavedPacket();
		boolean isPacketUnicast = true;
		
		//Test if GenericPacket is unicast or broadcast
		if (gp instanceof UnicastPacket) {
				UnicastPacket up = (UnicastPacket) gp;
				GeneralUtils.appendLog("OpportunisticNetworkingManager: received unicast packet "+up.getId() +" expiry = "+up.getExpiry());
				
				savedPacket.setId(up.getId());
				savedPacket.setExpiry(up.getExpiry());
		}
		else if (gp instanceof BroadcastPacket) {
				final BroadcastPacket bp = (BroadcastPacket) gp;
				GeneralUtils.appendLog("OpportunisticNetworkingManager: received broadcast packet "+bp.getId() +" expiry = "+bp.getExpiry());
				
				savedPacket.setId(bp.getId());
				savedPacket.setExpiry(bp.getExpiry());
				isPacketUnicast = false;
		}
		else {
		      // not UnicastPacket not BroadcastPacket
			  throw new Exception("Unknown packet type: " + gp.getClass().getName());
		}
		
		/*Verificare se già ho gestito o sto gestendo il pacchetto
		* oppure if expiry == 0 the opportunistic networking is over!
		*/
		if(!containsPacket(savedPacket) && savedPacket.getExpiry() > 0)
		{
			boolean sendPacket = true;
			//Il pacchetto va gestito
			if(isPersistPackets())
			{
				//Save packet
				sendPacket  = savePacket(savedPacket, gp);
			}
			
			if(sendPacket && isPacketUnicast)
			{
				GeneralUtils.appendLog("OpportunisticNetworkingManager: saved packet, sendPacketToNeighbors...");
				//Se il pacchetto è stato salvato può essere inviato ai vicini...
				boolean sendOK = sendPacketToNeighbors(gp);
				
				if(!sendOK)
					GeneralUtils.appendLog("OpportunisticNetworkingManager: unicast packet "+savedPacket.getId() +" not sent to neighbors");
				else
					GeneralUtils.appendLog("OpportunisticNetworkingManager: sent unicast packet "+savedPacket.getId() +" to neighbors");
				
				if(sendOK && isRemovePacketAfterSend())
				{	
					GeneralUtils.appendLog("OpportunisticNetworkingManager: remove unicast packet "+savedPacket.getId() +" sent");
					removePacket(savedPacket);
				}
			}
		}
		else
		{
			GeneralUtils.appendLog("OpportunisticNetworkingManager: dropped packet already managed!");
		}
	}

	public void receivedInterrupt() {
		
	}
		
	//Gestione periodica dei pacchetti in memoria
	@Override
	public void run() {
		try {
			System.out.println("OpportunisticNetworkingManager START");
			GeneralUtils.appendLog("OpportunisticNetworkingManager START");
			
			while (active) {
				try {
					//Periodically send packet not expiry 
					
					List<SavedPacket> listSp = new ArrayList<SavedPacket>(savedPackets.keySet());
					
					for(SavedPacket sp : listSp)
					    {
						    //Refresh expiry
						    refreshPacketExpiry(sp);
							if(sp.getExpiry() == 0) //when expiry == 0 the opportunistic management is over
							{	
								GeneralUtils.appendLog("OpportunisticNetworkingManager: packet "+sp.getId() +" expiry = 0");
								removePacket(sp);
							} else {
								//Send Packet
								sendPacket(sp);
							}
					    }
					//sleep(getSendPacketsPeriod() * 1000);
					//TODO to check
					synchronized (this) {
						wait(getSendPacketsPeriod()*1000);
					}
				} catch (InterruptedException ie) {
					System.out.println("OpportunisticNetworkingManager: this should happen only at exit");
					GeneralUtils.appendLog("OpportunisticNetworkingManager: this should happen only at exit");
				} catch (Exception e) {
					e.printStackTrace();
				}	
			}
			System.out.println("OpportunisticNetworkingManager FINISHED");
			GeneralUtils.appendLog("OpportunisticNetworkingManager FINISHED");
		} catch (Exception e) {
			e.printStackTrace();
		}
		opportunisticNetworkingManager = null;
		System.out.println("OpportunisticNetworkingManager END");
		GeneralUtils.appendLog("OpportunisticNetworkingManager END");
	}
	
	private boolean active = true;
	
	//TODO
	public void sentNotify() {
		System.out.println("OpportunisticNetworkingManager: receivedNotify");
		synchronized (this) {
			notify();
		}
	}
	
	public void deactivate(boolean clear) {
		System.out.println("OpportunisticNetworkingManager DISABLED");
		GeneralUtils.appendLog("OpportunisticNetworkingManager DISABLED");	 
		
		//memory clear
		if(clear)
		{
			List<SavedPacket> listSp = new ArrayList<SavedPacket>(savedPackets.keySet());
			 
			for(SavedPacket sp : listSp)
				removePacket(sp);	

			savedPackets.clear();
			managedPackets.clear();
			
			serializeSettings();
		}
		this.active = false;
		interrupt();
	}
	
	public enum ReplacePackets {
	    OLD, SMALL, HUGE
	}
}

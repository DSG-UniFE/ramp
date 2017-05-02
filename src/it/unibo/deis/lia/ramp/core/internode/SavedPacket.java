package it.unibo.deis.lia.ramp.core.internode;

import java.io.Serializable;
import java.util.Set;

/**
 * 
 * @author Stefano Lanzone
 */
public class SavedPacket implements Serializable {

	private static final long serialVersionUID = 3665750344603083506L;
	
	private int id;			                 //id saved packet
	private long saveTime;                   //timestamp in millis
	private int expiry;                      //expiry of the packet, in seconds				 
	private Set<Integer> exploredNodeIdList; //list of explored node (id)
    
	public int getId() {
    	return id;
    }
    
    public void setId(int id) {
    	this.id = id;
    }
    
    public long getSaveTime() {
		return saveTime;
	}

	public void setSaveTime(long saveTime) {
		this.saveTime = saveTime;
	}

	public int getExpiry() {
		return expiry;
	}

	public void setExpiry(int expiry) {
		if(expiry < 0)
			expiry = 0;
		this.expiry = expiry;
	}
    
	public Set<Integer> getExploredNodeIdList() {
		return exploredNodeIdList;
	}

	public void setExploredNodeIdList(Set<Integer> exploredNodeIdList) {
		this.exploredNodeIdList = exploredNodeIdList;
	}
	
	@Override
	public boolean equals(Object other){
	    if (other == null) return false;
	    if (other == this) return true;
	    if (!(other instanceof SavedPacket))return false;
	    SavedPacket otherSavedPacket = (SavedPacket)other;
	    
	    if(this.getId() == otherSavedPacket.getId())
	    	return true;
	    else
	    	return false;
	}
}

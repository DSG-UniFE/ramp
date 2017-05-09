package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public class DistribuitedActuatorRequest implements Serializable {
	
	/**
	 * 
	 */ 
	private static final long serialVersionUID = 4859525002395755276L;
	
	private Type type;
	
	protected DistribuitedActuatorRequest (Type type) {
    	
    }
	
	public Type getType() {
		return type;
	}


	public void setType(Type type) {
		this.type = type;
	}


	
	public enum Type {
		S_AVAILABLE_APPS, S_PRE_COMMAND, S_COMMAND,
		C_HERE_I_AM, C_JOIN, C_LEAVE, C_WHICH_APP
	}
}

package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public class DistribuitedActuatorRequest implements Serializable {
	/**
	 * 
	 */ 
	private static final long serialVersionUID = 4859525002395755276L;
	
	private Type type;
	private String appName;
	private int port;
	
	protected DistribuitedActuatorRequest (String appName, Type type, int port) {
    	this.appName = appName;
    	this.type = type;
    	this.port = port;
    }
	
	public Type getType() {
		return type;
	}


	public void setType(Type type) {
		this.type = type;
	}

	public String getAppName() {
		return appName;
	}

	public void setAppName(String appName) {
		this.appName = appName;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
	
	public enum Type {
		AVAILABLE_APPS, PRE_COMMAND, COMMAND,
		HERE_I_AM, JOIN, LEAVE, WHICH_APP
	}
}

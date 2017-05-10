package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public class DistributedActuatorRequest implements Serializable {
	/**
	 * 
	 */ 
	private static final long serialVersionUID = 4859525002395755276L;
	
	private Type type;
	private String appName;
	private int port;
	private long lastUpdate;
	
	protected DistributedActuatorRequest (String appName, Type type, int port, long lastUpdate) {
    	this.appName = appName;
    	this.type = type;
    	this.port = port;
    	this.lastUpdate = lastUpdate;
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

	public long getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(long lastUpdate) {
		this.lastUpdate = lastUpdate;
	}
	
	
	public enum Type {
		AVAILABLE_APPS, PRE_COMMAND, COMMAND,
		HERE_I_AM, JOIN, LEAVE, WHICH_APP
	}
}

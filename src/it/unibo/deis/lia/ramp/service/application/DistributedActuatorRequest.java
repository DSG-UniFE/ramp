package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public class DistributedActuatorRequest implements Serializable {
	/**
	 * 
	 */ 
	private static final long serialVersionUID = 4859525002395755276L;
	
	private Type type;
	private String appName;
	private String[] appNames;
	private String command;
	private int port;


	protected DistributedActuatorRequest (String appName, Type type) {
    	this.appName = appName;
    	this.type = type;
    }
	
	protected DistributedActuatorRequest (String appName, Type type, int port) {
    	this.appName = appName;
    	this.type = type;
    	this.port = port;
    }
	
	protected DistributedActuatorRequest (String appName, Type type, String command) {
    	this.appName = appName;
    	this.type = type;
    	this.command = command;
    }
	
	protected DistributedActuatorRequest (String[] appNames, Type type) {
    	this.appNames = appNames;
    	this.type = type;
    }
	
	public String[] getAppNames() {
		return appNames;
	}

	public void setAppNames(String[] appNames) {
		this.appNames = appNames;
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
	
	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}
	
	public enum Type {
		AVAILABLE_APPS, PRE_COMMAND, COMMAND,
		HERE_I_AM, JOIN, LEAVE, WHICH_APP
	}
}

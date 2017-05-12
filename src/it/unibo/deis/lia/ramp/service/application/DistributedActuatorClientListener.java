package it.unibo.deis.lia.ramp.service.application;

public interface DistributedActuatorClientListener {
	
	public void activateResilience();
	public void receivedCommand(DistributedActuatorRequest dar);
	
}

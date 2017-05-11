package it.unibo.deis.lia.ramp.service.application;

public interface DistributedClientListener {
	
	public void activateResilience();
	public void receivedCommand(DistributedActuatorRequest dar);
	
}

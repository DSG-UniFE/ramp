package test.distributedactuator;

import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClientListener;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorRequest;

public class Sensor extends Thread implements DistributedActuatorClientListener {
	
	private float threshold;
	private float resilience;
	
	public Sensor(){
	}
	
	@Override
	public void run() {
		// periodically retrieve pictures (5 seconds)
		// send alert if images are different more than threshold
	}

	@Override
	public void activateResilience() {
		System.out.println("Sensor.activateResilience");
		// modify the threshold according to the saved resilience
	}

	@Override
	public void receivedCommand(DistributedActuatorRequest dar) {
		System.out.println("Sensor.receivedCommand: "+dar);
		// modify the threshold
		//save the resilience value
	}
	
}
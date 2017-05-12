package test.distributedactuator;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClient;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClientListener;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorService;

public class DistributedActuatorTest {
	

	public static void main(String[] args) throws InterruptedException {
		RampEntryPoint.getInstance(true, null);
    	DistributedActuatorService das = DistributedActuatorService.getInstance();
    	DistributedActuatorClient dac = DistributedActuatorClient.getInstance();
    	DistributedActuatorClientListener dacl = new Sensor();
		
    	String appName = "test app name";
    	das.addApp(appName);
    	dac.registerNewApp(appName, dacl);
    	
    	Thread.sleep(1000);
    	das.sendCommand(appName, "command=c,resilience=r", 5000, 0);
    	
    	Thread.sleep(5000);
    	dac.leave(appName);

    	Thread.sleep(2000);
    	das.removeApp(appName);
	}
	
}

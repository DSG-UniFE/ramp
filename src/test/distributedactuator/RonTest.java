package test.distributedactuator;

import java.net.SocketTimeoutException;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;

public class RonTest {

	public static void main(String[] args) throws Exception {
//		From terminal
		// java -cp './bin:./libs/*' it.unibo.deis.lia.ramp.RampEntryPoint
		boolean open = true;

		RampEntryPoint.getInstance(true, null);

		BoundReceiveSocket serviceSocket = E2EComm.bindPreReceive(E2EComm.TCP);

	    ServiceManager.getInstance(false).registerService(
	    		"RonTest",
	    		serviceSocket.getLocalPort(),
	    		E2EComm.TCP
			);

	    System.out.println("RonTest START on port: " + serviceSocket.getLocalPort() + " " + E2EComm.TCP);

	    while (open) {
            try {
                // receive
                GenericPacket gp = E2EComm.receive(serviceSocket, 5*1000);
                System.out.println("RonTest new request");
            } catch(SocketTimeoutException ste) {
                //System.out.println("DistributedActuatorService SocketTimeoutException");
            }
        }

        serviceSocket.close();
	}

}

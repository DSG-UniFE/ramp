package test.distributedactuator;

import java.net.SocketTimeoutException;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.util.Benchmark;

public class RonTest {

	public static void main(String[] args) throws Exception {
		//		From terminal
		// java -cp './bin:./libs/*' it.unibo.deis.lia.ramp.RampEntryPoint
		boolean open = true;

		Benchmark.createFile();
		Benchmark.append(System.currentTimeMillis(), "ron_test_started", 0, 0, 0);

		RampEntryPoint ramp = RampEntryPoint.getInstance(true, null);

		BoundReceiveSocket serviceSocket = E2EComm.bindPreReceive(E2EComm.TCP);

		ServiceManager.getInstance(false).registerService(
				"RonTest",
				serviceSocket.getLocalPort(),
				E2EComm.TCP
				);

		System.out.println("RonTest, main(): START on port: " + serviceSocket.getLocalPort() + " " + E2EComm.TCP);

		System.out.println("RonTest, main(): registering shutdown hook");
		// Setup signal handling in order to always stop RAMP gracefully
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (ramp != null) {
						System.out.println("ShutdownHook is being executed: gracefully stopping RAMP...");
						ramp.stopRamp();
					}

					if (serviceSocket != null) {
						serviceSocket.close();
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}));

		while (open) {
			try {
				// receive
				GenericPacket gp = E2EComm.receive(serviceSocket);
				System.out.println("RonTest, main(): new request");

				if (gp instanceof UnicastPacket) {
					UnicastPacket up = (UnicastPacket) gp;

					Benchmark.append(System.currentTimeMillis(), "ron_test_received_unicast", up.getId(),
							up.getSourceNodeId(), up.getDestNodeId());
					System.out.println("RonTest, main(): ron_test_received_unicast");
				} else if (gp instanceof BroadcastPacket) {
					BroadcastPacket bp = (BroadcastPacket) gp;

					Benchmark.append(System.currentTimeMillis(), "ron_test_received_broadcast", bp.getId(),
							bp.getSourceNodeId(), bp.getDestPort());
					System.out.println("RonTest, main(): ron_test_received_broadcast");
				}
			} catch(SocketTimeoutException ste) {
				System.out.println("DistributedActuatorService SocketTimeoutException: ");
				ste.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (serviceSocket != null) {
			serviceSocket.close();
		}
	}

}

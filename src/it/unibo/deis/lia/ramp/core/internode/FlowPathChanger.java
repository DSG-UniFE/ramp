package it.unibo.deis.lia.ramp.core.internode;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

/**
 * 
 * @author Alessandro Dolci
 * 
 */
public class FlowPathChanger implements DataPlaneForwarder {
	
	private static FlowPathChanger flowPathChanger = null;
	
	public synchronized static FlowPathChanger getInstance() {
		if (flowPathChanger == null) {
			flowPathChanger = new FlowPathChanger();
			Dispatcher.getInstance(false).addPacketForwardingListener(flowPathChanger);
			System.out.println("FlowPathChanger ENABLED");
		}
		return flowPathChanger;
	}
	
	public void deactivate() {
		if (flowPathChanger != null) {
			Dispatcher.getInstance(false).removePacketForwardingListener(flowPathChanger);
			flowPathChanger = null;
			System.out.println("FlowPathChanger DISABLED");
		}
	}

	@Override
	public void receivedUdpUnicastPacket(UnicastPacket up) {
		receivedTcpUnicastPacket(up);
	}

	@Override
	public void receivedUdpBroadcastPacket(BroadcastPacket bp) {
		
	}

	@Override
	public void receivedTcpUnicastPacket(UnicastPacket up) {
		receivedTcpUnicastHeader(up.getHeader());
	}

	@Override
	public void receivedTcpUnicastHeader(UnicastHeader uh) {
		// Check if the packet header contains a valid flowId and has to be processed according to the SDN paradigm
		if (uh.getFlowId() != GenericPacket.UNUSED_FIELD) {
			String[] newPath = null;
			ControllerClient controllerClient = ControllerClient.getInstance();
			
			// If the current node is the sender, check for a new complete path
			if (uh.getSourceNodeId() == Dispatcher.getLocalRampId() || uh.getCurrentHop() == (byte) 0) {
				newPath = controllerClient.getFlowPath(uh.getDestNodeId(), uh.getFlowId());
				// If the received path is not null and it's different from the current path, replace the current one with it
				if (newPath != null && !newPath.equals(uh.getDest())) {
					uh.setDest(newPath);
					System.out.println("FlowPathChanger: dest path changed to packet " + uh.getId() + " belonging to flow " + uh.getFlowId());
					for (int i = 0; i < newPath.length; i++)
						System.out.println("FlowPathChanger: new flow path address " + i + ", " + newPath[i]);
				}
				else
					System.out.println("FlowPathChanger: no changes made to packet " + uh.getId() + " belonging to flow " + uh.getFlowId());
			}
		}
	}

	@Override
	public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
		
	}

	@Override
	public void receivedTcpBroadcastPacket(BroadcastPacket bp) {
		
	}

	@Override
	public void sendingTcpUnicastPacketException(UnicastPacket up, Exception e) {
		
	}

	@Override
	public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e) {
		
	}

}

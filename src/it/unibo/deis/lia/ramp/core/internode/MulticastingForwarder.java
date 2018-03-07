package it.unibo.deis.lia.ramp.core.internode;

import java.util.List;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

/**
 * 
 * @author Alessandro Dolci
 *
 */
public class MulticastingForwarder implements DataPlaneForwarder {
	
	private static MulticastingForwarder multicastingForwarder = null;
	
	public synchronized static MulticastingForwarder getInstance() {
		if (multicastingForwarder == null) {
			multicastingForwarder = new MulticastingForwarder();
			Dispatcher.getInstance(false).addPacketForwardingListener(multicastingForwarder);
			System.out.println("MulticastingForwarder ENABLED");
		}
		return multicastingForwarder;
	}

	@Override
	public void deactivate() {
		if (multicastingForwarder != null) {
			Dispatcher.getInstance(false).removePacketForwardingListener(multicastingForwarder);
			multicastingForwarder = null;
			System.out.println("MulticastingForwarder DISABLED");
		}
	}

	@Override
	public void receivedUdpUnicastPacket(UnicastPacket up) {
		if (up.getFlowId() != GenericPacket.UNUSED_FIELD && up.getDestNodeId() == Dispatcher.getLocalRampId() && up.getDestPort() == 40000) {
			ControllerClient controllerClient = ControllerClient.getInstance();
			
			List<PathDescriptor> nextHops = controllerClient.getFlowMulticastNextHops(up.getFlowId());
			if (nextHops != null) {
				String[] currentDest = new String[up.getDest().length];
				for (int i = 0; i < up.getDest().length; i++)
					currentDest[i] = up.getDest()[i];
					
				for (PathDescriptor pathDescriptor : nextHops) {
					UnicastPacket duplicatePacket = null;
					MulticastPathDescriptor multicastPathDescriptor = (MulticastPathDescriptor) pathDescriptor;
					if (multicastPathDescriptor.getPathNodeIds().get(0) == Dispatcher.getLocalRampId()) {
						System.out.println("MulticastingForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " is directed to this node, duplicating it and setting the destination port");
						try {
							duplicatePacket = new UnicastPacket(
									up.getDest(),
									multicastPathDescriptor.getDestPort(),
									up.getDestNodeId(),
									up.getSourceNodeId(),
									up.isAck(),
									up.getSourcePortAck(),
									up.getCurrentHop(),
									up.getBufferSize(),
									up.getRetry(),
									up.getTimeWait(),
									up.getExpiry(),
									up.getConnectTimeout(),
									up.getFlowId(),
									up.getBytePayload()
									);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					else {
						String[] duplicatePacketDest = new String[currentDest.length+1];
						for (int i = 0; i < currentDest.length; i++)
							duplicatePacketDest[i] = currentDest[i];
						duplicatePacketDest[currentDest.length] = multicastPathDescriptor.getPath()[0];
						int duplicatePacketDestNodeId = multicastPathDescriptor.getPathNodeIds().get(0);
						System.out.println("MulticastingForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " has to be forwarded to node " + duplicatePacketDestNodeId + ", duplicating and sending it");
						try {
							duplicatePacket = new UnicastPacket(
									duplicatePacketDest,
									up.getDestPort(),
									duplicatePacketDestNodeId,
									up.getSourceNodeId(),
									up.isAck(),
									up.getSourcePortAck(),
									up.getCurrentHop(),
									up.getBufferSize(),
									up.getRetry(),
									up.getTimeWait(),
									up.getExpiry(),
									up.getConnectTimeout(),
									up.getFlowId(),
									up.getBytePayload()
									);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					try {
						E2EComm.sendUnicast(E2EComm.UDP, duplicatePacket);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				up.setDest(null);
				up.setRetry((byte) 0);
			}
		}
	}

	@Override
	public void receivedUdpBroadcastPacket(BroadcastPacket bp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void receivedTcpUnicastPacket(UnicastPacket up) {
		if (up.getFlowId() != GenericPacket.UNUSED_FIELD && up.getDestNodeId() == Dispatcher.getLocalRampId() && up.getDestPort() == 40000) {
			ControllerClient controllerClient = ControllerClient.getInstance();
			
			List<PathDescriptor> nextHops = controllerClient.getFlowMulticastNextHops(up.getFlowId());
			if (nextHops != null) {
				String[] currentDest = new String[up.getDest().length];
				for (int i = 0; i < up.getDest().length; i++)
					currentDest[i] = up.getDest()[i];
					
				for (PathDescriptor pathDescriptor : nextHops) {
					UnicastPacket duplicatePacket = null;
					MulticastPathDescriptor multicastPathDescriptor = (MulticastPathDescriptor) pathDescriptor;
					if (multicastPathDescriptor.getPathNodeIds().get(0) == Dispatcher.getLocalRampId()) {
						System.out.println("MulticastingForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " is directed to this node, duplicating it and setting the destination port");
						try {
							duplicatePacket = new UnicastPacket(
									up.getDest(),
									multicastPathDescriptor.getDestPort(),
									up.getDestNodeId(),
									up.getSourceNodeId(),
									up.isAck(),
									up.getSourcePortAck(),
									up.getCurrentHop(),
									up.getBufferSize(),
									up.getRetry(),
									up.getTimeWait(),
									up.getExpiry(),
									up.getConnectTimeout(),
									up.getFlowId(),
									up.getBytePayload()
									);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					else {
						String[] duplicatePacketDest = new String[currentDest.length+1];
						for (int i = 0; i < currentDest.length; i++)
							duplicatePacketDest[i] = currentDest[i];
						duplicatePacketDest[currentDest.length] = multicastPathDescriptor.getPath()[0];
						int duplicatePacketDestNodeId = multicastPathDescriptor.getPathNodeIds().get(0);
						System.out.println("MulticastingForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " has to be forwarded to node " + duplicatePacketDestNodeId + ", duplicating and sending it");
						try {
							duplicatePacket = new UnicastPacket(
									duplicatePacketDest,
									up.getDestPort(),
									duplicatePacketDestNodeId,
									up.getSourceNodeId(),
									up.isAck(),
									up.getSourcePortAck(),
									up.getCurrentHop(),
									up.getBufferSize(),
									up.getRetry(),
									up.getTimeWait(),
									up.getExpiry(),
									up.getConnectTimeout(),
									up.getFlowId(),
									up.getBytePayload()
									);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					try {
						E2EComm.sendUnicast(E2EComm.TCP, duplicatePacket);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				up.setDest(null);
				up.setRetry((byte) 0);
			}
		}
	}

	@Override
	public void receivedTcpUnicastHeader(UnicastHeader uh) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void receivedTcpBroadcastPacket(BroadcastPacket bp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendingTcpUnicastPacketException(UnicastPacket up, Exception e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e) {
		// TODO Auto-generated method stub
		
	}

}

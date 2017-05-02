
package it.unibo.deis.lia.ramp.core.internode;

import java.util.Arrays;
import java.util.Vector;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.util.GeneralUtils;


/**
 * 
 * @author Carlo Giannelli
 */
public class ContinuityManager implements PacketForwardingListener {

	private static ContinuityManager continuityManager = null;
	private static PeriodicMultihopBeat periodicMultihopBeat = null;
	//Stefano Lanzone
    private static OpportunisticNetworkingManager opportunisticNetworkingManager = null; 
	private boolean opportunisticNetworking = false; 
		
	public static synchronized ContinuityManager getInstance(boolean forceStart) {
		if (forceStart) {
			if (continuityManager == null) {
				continuityManager = new ContinuityManager();
				Dispatcher.getInstance(false).addPacketForwardingListener(continuityManager);
	
				opportunisticNetworkingManager = OpportunisticNetworkingManager.getInstance(true);
				
				GeneralUtils.appendLog("ContinuityManager ENABLED");
				System.out.println("ContinuityManager ENABLED");
			}
		}
		return continuityManager;
	}

	public static void deactivate() {
		if (continuityManager != null) {
			continuityManager.stopPeriodicMultihopBeat();
			Dispatcher.getInstance(false).removePacketForwardingListener(continuityManager);
			
			//Stefano Lanzone
			if(opportunisticNetworkingManager != null)
			{
				opportunisticNetworkingManager.deactivate(true);
				opportunisticNetworkingManager = null;
			}
			
			continuityManager = null;
			GeneralUtils.appendLog("ContinuityManager DISABLED");
			System.out.println("ContinuityManager DISABLED");
		}
	}

	synchronized public void startPeriodicMultihopBeat() {
		if (periodicMultihopBeat == null) {
			periodicMultihopBeat = continuityManager.new PeriodicMultihopBeat();
			periodicMultihopBeat.start();
		}
	}

	synchronized public void stopPeriodicMultihopBeat() {
		if (periodicMultihopBeat != null) {
			periodicMultihopBeat.stopPeriodicMultihopBeat();
			periodicMultihopBeat = null;
		}
	}

	private class PeriodicMultihopBeat extends Thread {
		private boolean active = true;

		private void stopPeriodicMultihopBeat() {
			this.interrupt();
			active = false;
		}

		public void run() {
			System.out.println("ContinuityManager.PeriodicMultihopBeat STARTED");
			do {
				try {
					E2EComm.sendBroadcast(5, // ttl
							0, // destPort (0 => no applications are waiting for this packet)
							E2EComm.UDP, null // no payload
					);
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					sleep(5000);
				} catch (InterruptedException ie) {
					// ie.printStackTrace();
				}
			} while (active);
			System.out.println("ContinuityManager.PeriodicMultihopBeat FINISHED");
		}
	}

	@Override
	public void sendingTcpUnicastPacketException(UnicastPacket up, Exception e) {
		sendingTcpUnicastHeaderException(up.getHeader(), e);
		
		//Stefano Lanzone
		if(opportunisticNetworking)
		{
			//Send packet to Opportunistic Networking Manager
			GeneralUtils.appendLog("ContinuityManager send unicast packet to OpportunisticNetworkingManager");
			
			sendToOpportunisticNetworkingManager(up);
			up.getHeader().setRetry((byte) 0);
		}
	}

	/* Send packet to Opportunistic Networking Manager
	 * Stefano Lanzone */
	private void sendToOpportunisticNetworkingManager(GenericPacket gp)
	{
		try
		{
			opportunisticNetworkingManager.receivePacket(gp);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	@Override
	public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception exception) {
		// System.out.println("ContinuityManager.sendingTCPUnicastHeaderException at "+System.currentTimeMillis()+" exception: "+exception);
		opportunisticNetworking = false;
		
		int destNodeId = uh.getDestNodeId();
		if (destNodeId == "".hashCode()) {// null){
			// a) do nothing... (Continuity Manager disabled without destNodeId)
		}
		// else if(destNodeId.equals(Dispatcher.getLocalId())){
		else if (destNodeId == Dispatcher.getLocalRampId()) {
			// b) the local node is the destination
			int packetRetry = uh.getRetry();
			if (packetRetry == GenericPacket.UNUSED_FIELD) {
				System.out.println("ContinuityManager: DROPPING packet for the local node");
				uh.setRetry((byte) 0);
			} else {
				// System.out.println("ContinuityManager: waiting for local service activation "+(timeDeadline-System.currentTimeMillis()));
				System.out.println("ContinuityManager: waiting for local service activation " + packetRetry);
				// the local service is not ready:
				// just wait a while and then retry...
				if (uh.getDest() == null) {
					try {
						//String[] vIA = { InetAddress.getLocalHost().getHostAddress().replaceAll("/", "") };
						String[] vIA = { GeneralUtils.getLocalHost() };
						uh.setDest(vIA);
						uh.setCurrentHop((byte) 1);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else if (packetRetry > 0) {
					try {
						long sleep = uh.getTimeWait();
						System.out.println("ContinuityManager: local sleep " + sleep);
						Thread.sleep(sleep);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					// missed deadline...
					System.out.println("ContinuityManager: DROPPING Delay-Tolerant packet for the local node");
				}
			}
		} else {
			// c) looking for the destination node

			try {
				// 1) find new paths to the same nodeId
				String failedNextHop = null;
				// if(uh.getDest()!=null){
				if (uh.getDest() != null && uh.getDest().length > uh.getCurrentHop()) {
					failedNextHop = uh.getDest()[uh.getCurrentHop()];
				}

				// remove the failed path to nodeId
				Resolver resolver = Resolver.getInstance(false);
				if (resolver != null && failedNextHop != null) {
					resolver.removeEntry(destNodeId, failedNextHop, 0);
				}
				resolver = null;

				System.out.println("ContinuityManager looking for destNodeId = " + destNodeId);
				ResolverPath bestPath = null;
				long preResolve = System.currentTimeMillis();

				Vector<ResolverPath> availablePaths = Resolver.getInstance(false).resolveBlocking(destNodeId, 2500);

				long elapsedResolve = System.currentTimeMillis() - preResolve;
				if (availablePaths != null) {
					for (int i = 0; i < availablePaths.size(); i++) {
						ResolverPath aPath = availablePaths.elementAt(i);
						if (failedNextHop == null || !aPath.getPath()[0].equals(failedNextHop.toString().replaceAll("/", ""))) {
							// the first hop of "aPath" is not the just failed hop
							if (bestPath == null) {
								bestPath = aPath;
							} else if (aPath.getPath().length < bestPath.getPath().length) {
								// XXX hardcoded bestPath based on hop count: should be more flexible...
								bestPath = aPath;
							}
						}
					}
				}
				
				/* Opportunistic Networking
				 * Stefano Lanzone*/
				if(bestPath == null)
				{
					int expiry = uh.getExpiry();
					if(expiry != GenericPacket.UNUSED_FIELD)
					{	
						opportunisticNetworking = true;
						//uh.setRetry((byte) 0);
					}
				}		
				
				int packetRetry = uh.getRetry();
				if (bestPath != null || opportunisticNetworking) {
					// there is a route to destNodeId or packet is managed in opportunistic way
				} else if (packetRetry == GenericPacket.UNUSED_FIELD) {
					// no delay tolerant
					System.out.println("ContinuityManager: DROPPING packet for " + destNodeId);
					uh.setRetry((byte) 0);
				} else if (packetRetry == 0) {
					// missed deadline
					System.out.println("ContinuityManager: DROPPING Delay-Tolerant packet for " + destNodeId);
				} else {
					// Delay-Tolerant packet
					// sleep a while...
					System.out.println("ContinuityManager: storing delay-tolerant packet for " + destNodeId + " (packetRetry=" + packetRetry + ", timeWait=" + uh.getTimeWait() + ")");
					long sleep = uh.getTimeWait() - elapsedResolve;
					if (sleep > 0) {
						Thread.sleep(sleep);
					}
				}

				// 2) if there is a path,
				// modify the packet header to send it to the new best destination
				if (bestPath != null) {
					// System.out.println("ContinuityManager bestPath = "+Arrays.toString(bestPath.getPath()));
					// 2a) modify UP
					String[] previousDest = uh.getDest();
					if (previousDest == null) {
						previousDest = new String[0];
						uh.setCurrentHop((byte) 0);
					}
					// String[] newDest = new String[previousDest.length+bestPath.getPath().length];
					String[] newDest = new String[uh.getCurrentHop() + bestPath.getPath().length];

					// copy the already performed path from UH
					int i = 0;
					for (; i < uh.getCurrentHop(); i++) {
						newDest[i] = previousDest[i];
					}

					// replace the rest of the path with the newly discovered best path
					for (int j = 0; j < bestPath.getPath().length; j++) {
						newDest[i + j] = bestPath.getPath()[j];
					}
					// System.out.println("ContinuityManager newDest = "+Arrays.toString(newDest));
					uh.setDest(newDest);
					GeneralUtils.appendLog("ContinuityManager send unicast packet to new bestPath = "+newDest);
					
					// 2b) send ResolverAdvice to the sender
					if (uh.getSource().length > 0) {// .getCurrentHop()>0){
						// send ResolverAdvice only if the sender is not the local node
						System.out.println("ContinuityManager send ResolverAdvice: uh.getSource( )= " + Arrays.toString(uh.getSource()));
						ResolverAdvice resolverAdvice = new ResolverAdvice(uh.getDestNodeId(), previousDest, uh.getCurrentHop(), newDest);

						E2EComm.sendUnicast(E2EComm.ipReverse(uh.getSource()), Resolver.RESOLVER_PORT, E2EComm.UDP, E2EComm.serialize(resolverAdvice));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				opportunisticNetworking = false;
			}
		}
	}

	@Override
	public void receivedUdpUnicastPacket(UnicastPacket up) {
	}

	@Override
	public void receivedUdpBroadcastPacket(BroadcastPacket bp) {
	}

	@Override
	public void receivedTcpUnicastPacket(UnicastPacket up) {
		receivedTcpUnicastHeader(up.getHeader());
	}

	@Override
	public void receivedTcpBroadcastPacket(BroadcastPacket bp) {
		
		if(bp.getExpiry() != GenericPacket.UNUSED_FIELD && bp.getTtl() > 0)  
		{
			//Send packet to Opportunistic Networking Manager
			GeneralUtils.appendLog("ContinuityManager send broadcast packet to OpportunisticNetworkingManager");
			sendToOpportunisticNetworkingManager(bp);
		}
	}

	@Override
	public void receivedTcpUnicastHeader(UnicastHeader uh) {
		// System.out.println("ContinuityManager: receivedTCPUnicastHeader");

		// then use Resolver (and in case of delay-tolerant packet retry after a while)
		// String destNodeId = uh.getDestNodeId();
		int destNodeId = uh.getDestNodeId();
		// if(destNodeId!=null){
		if (destNodeId != "".hashCode()) {
			// if(!destNodeId.equals(Dispatcher.getLocalId())){
			if (destNodeId != Dispatcher.getLocalRampId()) {
				// the local node is not the destination
				String[] dest = uh.getDest();
				if (dest == null || dest.length <= uh.getCurrentHop()) {
					// no more values in dest
					sendingTcpUnicastHeaderException(uh, null);
				} else {
					// is the dest path vaild?

					//Stefano Lanzone: questa verifica la faccio solo nel caso No Opportunistic Networking
					int expiry = uh.getExpiry();
//					if(expiry == GenericPacket.UNUSED_FIELD)
//					{	
						// Vector<ResolverPath> availablePaths = resolver.resolveNow(destNodeId);
						Vector<ResolverPath> availablePaths = Resolver.getInstance(false).resolveNow(destNodeId);

						boolean found = false;
						for (int i = 0; (availablePaths != null) && (!found) && (i < availablePaths.size()); i++) {
							String[] aPath = availablePaths.elementAt(i).getPath();
							int currentHop = uh.getCurrentHop();
							boolean ok = true;
							if ((aPath.length + currentHop) != dest.length) {
								ok = false;
							} else {
								for (int j = 0; ok && j < aPath.length; j++) {
										if (!aPath[j].equals(dest[currentHop + j])) {
										/*
									 	* System.out.println( "ContinuityManager: aPath.elementAt(j)="+aPath.elementAt(j)+" " + "dest.elementAt(currentHop+j))="+dest.elementAt(currentHop+j)+" " + "currentHop="+currentHop );
									 	*/
										// this path is NOT the path in dest
										ok = false;
									}
								}
							}
							if (ok) {
								// this path IS the path in dest
								found = true;
							}
							// System.out.println("ContinuityManager: ok="+ok+" dest="+dest+" aPath="+aPath);
						}
						if (!found) {
							// the path in dest seems to be not valid;
							// try to check it via sendingTCPUnicastHeaderException and Resolver!
							
							if(expiry == GenericPacket.UNUSED_FIELD) 
								uh.setDest(null); //Stefano Lanzone: metto la dest a null solo nel caso No Opportunistic Networking
							                      //se sono nel caso Opportunistic consento ugualmente la verifica di un path verso destNodeId
							System.out.println("ContinuityManager receivedTCPUnicastHeader: dest=" + Arrays.toString(dest) + " NOT FOUND");
							sendingTcpUnicastHeaderException(uh, null);
						}
					}
//				}
			}
		}
	}

	@Override
	public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
	}

}

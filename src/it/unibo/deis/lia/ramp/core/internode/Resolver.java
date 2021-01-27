/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.core.internode;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;

/**
 * 
 * @author useruser
 */
public class Resolver extends Thread implements PacketForwardingListener {

	// "protected" since exploited by ContinuityManager
	protected static int RESOLVER_PORT = 5002;
	private final static int RESOLVER_REQUEST_TIMEOUT = 3000;
	
	// key:nodeId
	private HashMap<Integer, Long> lastSearchDatabase = null;
	private HashMap<Integer, Vector<ResolverPath>> resolverDatabase = null;

	// !!! FOLLOWING METHOD ONLY FOR JFRAME RAMPGUI!!!
	/*
	 * public HashMap<String,Long> getLastSearchDatabase(){ return lastSearchDatabase; } public HashMap<String,Vector<ResolverPath>> getResolverDatabase(){ return resolverDatabase; }
	 */

	private Resolver() {
		resolverDatabase = new HashMap<Integer, Vector<ResolverPath>>();
		lastSearchDatabase = new HashMap<Integer, Long>();
	}

	private static Resolver resolver = null;

	public static synchronized Resolver getInstance(boolean forceStart) {
		if (forceStart && resolver == null) {
			resolver = new Resolver();
			resolver.start();
			// register this instance as listener to Dispatcher
			Dispatcher.getInstance(false).addPacketForwardingListener(resolver);
			System.out.println("Resolver ACTIVE");
		}
		return resolver;
	}

	public Vector<ResolverPath> resolveNow(int nodeId) {
		return resolve(nodeId, false, -1);
	}

	public Vector<ResolverPath> resolveBlocking(int nodeId) {
		return resolve(nodeId, true, Resolver.RESOLVER_REQUEST_TIMEOUT);
	}

	public Vector<ResolverPath> resolveBlocking(int nodeId, int timeout) {
		return resolve(nodeId, true, timeout);
	}

	synchronized private Vector<ResolverPath> resolve(int nodeId, boolean blocking, int timeout) {
		// System.out.println("Resolver.resolve nodeId="+nodeId+"  blocking="+blocking+" timeout="+timeout);
		Vector<ResolverPath> res = null;
		
		if(nodeId == Dispatcher.getLocalRampId()){
			// trying to resolve itself
			try {
				res = new Vector<ResolverPath>();
				//String[] pathToLocalhost = new String[]{ InetAddress.getLocalHost().getHostAddress() };
				String[] pathToLocalhost = new String[]{ GeneralUtils.getLocalHost() };
				res.add(new ResolverPath(pathToLocalhost));
				return res;
			} catch (UnknownHostException e) {
				return null;
			}
		}

		// 1) retrieve the required entry
		Vector<ResolverPath> multipleRP = resolverDatabase.get(nodeId);
		// System.out.println("Resolver.resolve multipleRP="+multipleRP);

		// 2) delete old entries
		if (multipleRP != null) {
			for (int i = 0; i < multipleRP.size(); i++) {
				ResolverPath rp = multipleRP.elementAt(i);
				if (System.currentTimeMillis() - rp.getLastUpdate() > 5 * 60 * 1000) { // TODO tune validity time
					multipleRP.remove(i);
					i--;
					System.out.println("Resolver.resolve deleted " + rp);
				}
			}
		}

		// 3a) if exists at least one path
		// return the entry
		if (multipleRP != null && multipleRP.size() > 0) {
		//XXX check if (multipleRP != null){
		//	if(multipleRP.size() > 0) {
				// System.out.println("Resolver.resolve EXISTS! " + multipleRP);
				res = multipleRP;
		//	}
		} 
		else if (blocking) {
			// 3b) if does not exist any entry
			Long lastSearch = lastSearchDatabase.get(nodeId);
			if (lastSearch == null) {
				lastSearch = new Long(0);
			}
			// System.out.println("Resolver.resolve  lastSearch="+lastSearch);
			long elapsed = System.currentTimeMillis() - lastSearch;
			if (lastSearch != null && elapsed < timeout) {
				// another broadcast sent few seconds ago --> no broadcast, just wait
				System.out.println("Resolver.resolve another broadcast for " + nodeId + " " + elapsed + " ms ago");
			} 
			else {
				System.out.println("Resolver.resolve sendBroadcast for " + nodeId + " at " + System.currentTimeMillis());
				lastSearchDatabase.put(nodeId, System.currentTimeMillis());
				ResolverRequest resolverRequest = new ResolverRequest(nodeId);
				try {
					E2EComm.sendBroadcast(5, // TTL (XXX tune ttl value)
							Resolver.RESOLVER_PORT, E2EComm.UDP, E2EComm.serialize(resolverRequest));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			long start = System.currentTimeMillis();
			long now = System.currentTimeMillis();
			while ((res == null || res.size() == 0) && now - start <= timeout) {
				// System.out.println("Resolver.resolve sleeping...");
				try {
					Thread.sleep(100);
				} 
				catch (Exception e) {
					e.printStackTrace();
				}
				res = resolverDatabase.get(nodeId);
				// System.out.println("Resolver.resolve post sleep/get res="+res);
				now = System.currentTimeMillis();
			}
		}
		// System.out.println("Resolver.resolve finished for "+nodeId+" at "+System.currentTimeMillis()+": res="+res);
		return res;
	}

	// must be private (or not?)
	private void addEntry(int nodeId, String[] ipSeq) {
		// invoked by listener methods:
		// add a resolverDatabase entry
		// (or refresh an old one)

		// System.out.println("Resolver.addEntry  nodeId="+nodeId+"  ipSeq="+ipSeq);

		ResolverPath rp = new ResolverPath(ipSeq);

		Vector<ResolverPath> multipleRP = resolverDatabase.get(nodeId);
		if (multipleRP == null) {
			multipleRP = new Vector<ResolverPath>();
			resolverDatabase.put(nodeId, multipleRP);
		}
		if (multipleRP.contains(rp)) {
			multipleRP.remove(rp);
		}
		multipleRP.addElement(rp);
	}

	protected synchronized void removeEntry(int nodeId, String failedHop, int positionFailedHop) {
		// System.out.println("Resolver.removeEntry  nodeId="+nodeId+"  failedHop="+failedHop+"  positionFailedHop="+positionFailedHop);
		// synchronized(resolverDatabase){
		Vector<ResolverPath> multipleRP = resolverDatabase.get(nodeId);
		if (multipleRP != null) {
			for (int i = 0; i < multipleRP.size(); i++) {
				ResolverPath rp = multipleRP.elementAt(i);
				// System.out.println("Resolver.removeEntry  rp="+rp);
				if (rp.getPath().length > positionFailedHop) {
					// System.out.println("Resolver.removeEntry  failedHop="+failedHop+"  rp.getPath().elementAt(positionFailedHop)="+rp.getPath().elementAt(positionFailedHop));
					if (failedHop.equals(rp.getPath()[positionFailedHop])) {
						System.out.println("Resolver.removeEntry removing " + Arrays.toString(multipleRP.elementAt(i).getPath()));
						multipleRP.removeElementAt(i);
						i--;
					}
				}
			}
			if (multipleRP.size() == 0) {
				resolverDatabase.remove(nodeId);
			}
		}
		// }
	}

	private BoundReceiveSocket socketResolver = null;
	private boolean active = true;

	public void stopResolver() {
		System.out.println("Resolver.stopResolver");
		active = false;
		this.interrupt();
		if (socketResolver != null) {
			try {
				socketResolver.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		try {
			System.out.println("Resolver START");
			socketResolver = E2EComm.bindPreReceive(Resolver.RESOLVER_PORT, E2EComm.UDP);
			while (active) {
				// wait for requests/responses from other Resolvers
				GenericPacket gp = E2EComm.receive(socketResolver);
				new ResolverHandler(gp).start();
			}
			System.out.println("Resolver FINISHED");
		} catch (SocketException se) {

		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			try {
				socketResolver.close();
			} catch (IOException e) {}
		}
		System.out.println("Resolver END");
		resolver = null;
	}

	private class ResolverHandler extends Thread {
		private GenericPacket gp;

		private ResolverHandler(GenericPacket gp) {
			this.gp = gp;
		}

		@Override
		public void run() {
			try {
				if (gp instanceof BroadcastPacket) {
					// System.out.println("Resolver.run BroadcastPacket");
					BroadcastPacket bp = (BroadcastPacket) gp;
					Object payload = E2EComm.deserialize(bp.getBytePayload());
					if (payload instanceof ResolverRequest) {
						// System.out.println("Resolver.run ResolverRequest");
						ResolverRequest resolverRequest = (ResolverRequest) payload;
						// System.out.println("Resolver.run ResolverRequest looking for "+resolverRequest.getNodeId());
						// if(resolverRequest.getNodeId().equals(Dispatcher.getLocalId())){
						if (resolverRequest.getNodeId() == Dispatcher.getLocalRampId()) {
							System.out.println("Resolver.run ResolverRequest sending ResolverAdvice");
							ResolverAdvice resolverAdvice = new ResolverAdvice(Dispatcher.getLocalRampId());
							E2EComm.sendUnicast(E2EComm.ipReverse(bp.getSource()), Resolver.RESOLVER_PORT, E2EComm.UDP, E2EComm.serialize(resolverAdvice));
						}
					}
				} 
				else if (gp instanceof UnicastPacket) {
					// System.out.println("Resolver.run UnicastPacket");
					UnicastPacket up = (UnicastPacket) gp;
					Object payload = E2EComm.deserialize(up.getBytePayload());
					if (payload instanceof ResolverAdvice) {
						System.out.println();
						System.out.println("Resolver.run ResolverAdvice");
						ResolverAdvice resolverAdvice = (ResolverAdvice) payload;
						int nodeId = resolverAdvice.getNodeId();
						// System.out.println("Resolver.run ResolverAdvice nodeId="+nodeId);
						String[] brokenPath = resolverAdvice.getBrokenPath();

						if (brokenPath == null) {
							System.out.println("Resolver.run ResolverAdvice: ResolverRequest response from " + nodeId);
						} 
						else {
							// there is a broken path
							// System.out.println("");
							System.out.println("Resolver.run ResolverAdvice brokenPath=" + Arrays.toString(brokenPath) + " from=" + Arrays.toString(up.getSource()));
							String failedNode = brokenPath[resolverAdvice.getFailedHop()];
							System.out.println("Resolver.run ResolverAdvice failedNode=" + failedNode);
							// System.out.println("Resolver.run ResolverAdvice nodeId="+nodeId+"  failedNode="+failedNode+"   position="+up.getSource().size());

							// 1) remove broken path
							System.out.println("Resolver.run ResolverAdvice removing nodeId=" + nodeId + " brokenPath=" + Arrays.toString(brokenPath));
							Resolver.getInstance(false).removeEntry(nodeId, failedNode, up.getSource().length);

							// 2) add new path
							String[] newPath = resolverAdvice.getNewPath();
							System.out.println("Resolver.run ResolverAdvice adding nodeId=" + nodeId + " newPath=" + Arrays.toString(newPath));
							Resolver.getInstance(false).addEntry(nodeId, newPath);
							System.out.println("");
						}
					} else {
						// ignore packet
					}
				} else {
					// ignore packet
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void receivedTcpUnicastPacket(UnicastPacket up) {
		receivedTcpUnicastHeader(up.getHeader());
	}

	@Override
	public void receivedUdpUnicastPacket(UnicastPacket up) {
		receivedTcpUnicastPacket(up);
	}

	@Override
	public void receivedUdpBroadcastPacket(BroadcastPacket bp) {
		receivedTcpBroadcastPacket(bp);
	}

	@Override
	public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
		// do nothing...
	}

	@Override
	public void receivedTcpBroadcastPacket(BroadcastPacket bp) {
		int nodeId = bp.getSourceNodeId();
		//String[] ipSeq = E2EComm.ipReverse(bp.getSource());
		String[] ipSeq = bp.getSource();
		if (ipSeq.length > 0) {
			//addEntry(nodeId, ipSeq);
			addEntry(nodeId, E2EComm.ipReverse(ipSeq));
		}
	}

	@Override
	public void receivedTcpUnicastHeader(UnicastHeader uh) {
		int nodeId = uh.getSourceNodeId();
		//String[] ipSeq = E2EComm.ipReverse(uh.getSource());
		String[] ipSeq = uh.getSource();
		if (ipSeq.length > 0) {
			//addEntry(nodeId, ipSeq);
			addEntry(nodeId, E2EComm.ipReverse(ipSeq));
		}
	}

	@Override
	public void sendingTcpUnicastPacketException(UnicastPacket up, Exception exception) {
		sendingTcpUnicastHeaderException(up.getHeader(), exception);
	}

	@Override
	public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e) {
		// remove the failed path to nodeId
		if (uh.getDest() != null) {
			if (uh.getDest().length > uh.getCurrentHop()) {
				int destNodeId = uh.getDestNodeId();
				String failedNextHop = uh.getDest()[uh.getCurrentHop()];
				removeEntry(destNodeId, failedNextHop, 0);
			}
		}
	}

}

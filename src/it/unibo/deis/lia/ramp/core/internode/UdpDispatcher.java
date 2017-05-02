
package it.unibo.deis.lia.ramp.core.internode;


import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

import it.unibo.deis.lia.ramp.util.GeneralUtils;
import it.unibo.deis.lia.ramp.util.ThreadPool;
import it.unibo.deis.lia.ramp.util.ThreadPool.IThreadPoolCallback;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Vector;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;


/**
 * 
 * @author Carlo Giannelli
 */
public class UdpDispatcher extends Thread {
	
	//private DatagramSocket receiverDS;
	private MulticastSocket receiverDS;
	
	// pooling
	private static ThreadPool<UdpDispatcherHandler, EnqueuedUdpGenericPacket> pool;

	// only for Android
	private static MulticastLock wifiMulticastLock;
	
	public UdpDispatcher() throws Exception {
		// setup pool and start threads
		pool = new ThreadPool<UdpDispatcherHandler, EnqueuedUdpGenericPacket>(this, UdpDispatcherHandler.class, getClass().getSimpleName()).init();
		
		//receiverDS = new DatagramSocket(Dispatcher.DISPATCHER_PORT);
		
		// required to receive even via the multicast heartbeat address
		receiverDS = new MulticastSocket(Dispatcher.DISPATCHER_PORT);
		
		try {
			receiverDS.joinGroup(InetAddress.getByName(Heartbeater.HEARTBEAT_MULTICAST_ADDRESS));
		} catch (Exception e) {
			// Multicast could not be supported (usually on wifi ad-hoc)
		}
		
		receiverDS.setReuseAddress(true);
		receiverDS.setBroadcast(true);
		
		if (RampEntryPoint.getAndroidContext() != null) {
			WifiManager wifi = (WifiManager) RampEntryPoint.getAndroidContext().getSystemService(Context.WIFI_SERVICE);
			if (wifi != null) {
				wifiMulticastLock = wifi.createMulticastLock("UdpDispatcher-MulticastLock");
				wifiMulticastLock.acquire();
			}
		}
	}

	private boolean active = true;

	protected void stopUdpDispatcher() {
		if (RampEntryPoint.getAndroidContext() != null) {
			if (wifiMulticastLock != null && wifiMulticastLock.isHeld())
				wifiMulticastLock.release();
		}
		// stop pool threads
		pool.stopThreadPool();
		pool = null;
		active = false;
		receiverDS.close();
		this.interrupt();
	}
	
	public static void asyncDispatchUdpGenericPacket(GenericPacket gp, InetAddress remoteAddress){
		pool.enqueueItem(new EnqueuedUdpGenericPacket(gp, remoteAddress));
	}
	
	@Override
	public void run() {
		try {
			System.out.println("UdpDispatcher START");
			while (active) {
				byte[] buffer = new byte[GenericPacket.MAX_UDP_PACKET];
				DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
				//System.out.println("UdpDispatcher received "+System.currentTimeMillis());

				// receive
				receiverDS.receive(dp);

				byte[] data = dp.getData();
				// from byte[] to object
				GenericPacket gp = E2EComm.deserializePacket(data, dp.getOffset(), dp.getLength());
				// enqueue
				UdpDispatcher.asyncDispatchUdpGenericPacket(gp, dp.getAddress());
			}
		}
		catch (java.net.BindException be) {
			// be.printStackTrace();
			System.out.println("UdpDispatcher port " + Dispatcher.DISPATCHER_PORT + " already in use; exiting");
		}
		catch (java.net.SocketException se) {
			// se.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("UdpDispatcher END");
	}

	public class UdpDispatcherHandler implements IThreadPoolCallback<EnqueuedUdpGenericPacket> {

		private GenericPacket gp = null;
		private InetAddress remoteAddress;
		
		@Override
		public void run() {
			//System.out.println("UdpDispatcherHandler run start");
			try {

				String remoteAddressString = null;
				boolean firstHop = false;

				//System.out.println("UdpDispatcher.UdpDispatcherHandler.run remoteAddress.toString() "+System.currentTimeMillis()+" from "+remoteAddress.toString());
				remoteAddressString = remoteAddress.toString().replaceAll("/", "").split(":")[0];
				final byte[] ip = remoteAddress.getAddress();
				if (ip != null) {
					final StringBuilder sb = new StringBuilder();
					for (int i = 0; i < ip.length; i++) {
						sb.append((ip[i]) & 0xff); // convert byte to 0..255 by bitwise AND with 0xff
						if (i < (ip.length - 1)) {
							sb.append('.');
						}
					}
					remoteAddressString = sb.toString();
				}
				//System.out.println("UdpDispatcher.UdpDispatcherHandler.run remoteAddressString = "+remoteAddressString);
				if (remoteAddressString.equals("127.0.0.1") || remoteAddressString.equals("127.0.1.1")) {
					firstHop = true;
				}
				// System.out.println("UdpDispatcher.UdpDispatcherHandler.run firstHop = "+firstHop);

				if (!firstHop) {
					Vector<String> localAddresses = Dispatcher.getLocalNetworkAddresses();
					for (int i = 0; (firstHop == false) && (i < localAddresses.size()); i++) {
						if (localAddresses.elementAt(i).equals(remoteAddressString)) {
							firstHop = true;
						}
					}
				}
				// System.out.println("UdpDispatcher firstHop "+firstHop);

				if (gp instanceof HeartbeatRequest) {
					if (!firstHop) {
						HeartbeatRequest heartbeatRequest = (HeartbeatRequest)gp;
						heartbeatRequestHandler(remoteAddress, heartbeatRequest);
					}
				} 
				else if (gp instanceof HeartbeatResponse) {
					if (!firstHop) {
						HeartbeatResponse heartbeatResponse = (HeartbeatResponse)gp;
						heartbeatResponseHandler(remoteAddress, heartbeatResponse);
					}
				} 
				else if (gp instanceof UnicastPacket) {
					UnicastPacket up = (UnicastPacket) gp;
					unicastPacketUdpHandler(firstHop, remoteAddressString, up);
				} 
				else if (gp instanceof BroadcastPacket) {
					BroadcastPacket bp = (BroadcastPacket) gp;
					broadcastPacketUdpHandler(firstHop, remoteAddressString, bp);
				}
//				else if (gp instanceof EncryptedPacket){
//						System.out.println("UdpDispatcherHandler: EncryptedPacket from " + remoteAddress + "!!!");
//						RampInternetNode.parse(gp, remoteAddress, null);
//				}
				else {
					throw new Exception("UdpDispatcherHandler: unknown packet type: " + gp.getClass().getName());
				}
			} 
			catch (InterruptedException ex){
			}
			catch (Exception e) {
				// System.out.println("UdpDispatcherHandler: failed DatagramPacket with remote node "+dp.getAddress()+":"+dp.getPort());
				System.out.println("UdpDispatcherHandler: failed DatagramPacket with remote node " + remoteAddress);
				e.printStackTrace();
			}
			//System.out.println("UdpDispatcherHandler END");
		}
		
//		public void stopUdpDispatcherHandler(){
//			this.active = false;
//			this.interrupt();
//		}

		@Override
		public void onBeforeExecute(EnqueuedUdpGenericPacket item) {
			this.gp = item.getGenericPacket();
			this.remoteAddress = item.getRemoteAddress();
		}

		@Override
		public void onPostExecute() {
			// Auto-generated method stub
		}
		
	}
	
	private void heartbeatRequestHandler(InetAddress remoteAddress, HeartbeatRequest heartbeatRequest) throws Exception{
		System.out.println("UdpDispatcher HeartbeatRequest " + System.currentTimeMillis() + " from " + remoteAddress);
		Heartbeater.getInstance(false).addNeighbor(remoteAddress, heartbeatRequest.getNodeId());

		HeartbeatResponse hResp = new HeartbeatResponse();

		// from object to byte[]
		byte[] bufferDest = E2EComm.serializePacket(hResp);

		DatagramSocket ds = new DatagramSocket();
		ds.setReuseAddress(true);
		DatagramPacket dp2 = new DatagramPacket(
				bufferDest, bufferDest.length,
				remoteAddress, Dispatcher.DISPATCHER_PORT);

		// random delay to avoid multiple simultaneous responses
		float f = RampEntryPoint.nextRandomFloat();
		long delay = Math.round(f * 1000 * 2);
		Thread.sleep(delay);

		ds.send(dp2);
		ds.close();
	}
	
	private void heartbeatResponseHandler(InetAddress remoteAddress, HeartbeatResponse heartbeatResponse){
		System.out.println("UdpDispatcher HeartbeatResponse " + System.currentTimeMillis() + " from " + remoteAddress);
		Heartbeater.getInstance(false).addNeighbor(remoteAddress, heartbeatResponse.getNodeId());
	}

	private void unicastPacketUdpHandler(boolean firstHop, String remoteAddressString, UnicastPacket up) throws Exception{
		// System.out.println("UdpDispatcher UnicastPacket "+System.currentTimeMillis()+" from "+remoteAddress);
		
		// System.out.println("UdpDispatcher UnicastPacket up.getDest() = "+up.getDest()); if(up.getDest()!=null){ System.out.println("UdpDispatcher UnicastPacket up.getDest().length = "+up.getDest().length);
		// System.out.println("UdpDispatcher UnicastPacket up.getDest()[0] = "+up.getDest()[0]); System.out.println("UdpDispatcher UnicastPacket InetAddress.getLocalHost().getHostAddress().replaceAll(\"/\", \"\")) = "+InetAddress.getLocalHost().getHostAddress().replaceAll("/", "")); }

		if (!firstHop ||
				// loopback
				(up.getDest() != null && up.getDest().length == 1 && up.getDest()[0].equals(GeneralUtils.getLocalHost()))) {

			// System.out.println("UdpDispatcher UnicastPacket !firstHop || loopback");

			// update source
			up.addSource(remoteAddressString);
			// update current hop
			up.setCurrentHop((byte) (up.getCurrentHop() + 1));
		}

		// invoke listeners and pass them UnicastPacket
		PacketForwardingListener[] listeners = Dispatcher.getInstance(false).getPacketForwardingListeners();
		for (int i = 0; i < listeners.length; i++) {
			listeners[i].receivedUdpUnicastPacket(up);
		}

		// retrieve the next hop
		int currentHop = up.getCurrentHop();
		String[] dest = up.getDest();

		if (dest != null) {
			String ipDest = null;
			int portDest;
			if (currentHop == dest.length) {
				// a) the localhost is the destination
				// System.out.println("UdpDispatcherHandler InetAddress.getLocalHost().getHostAddress(): "+InetAddress.getLocalHost().getHostAddress());
				ipDest = GeneralUtils.getLocalHost();
				portDest = up.getDestPort();
			} 
			else {
				// b) send to the following dispatcher
				ipDest = dest[currentHop];
				portDest = Dispatcher.DISPATCHER_PORT;
			}

			// send to neighbor or to RIN?
			boolean sendToRin = Dispatcher.sendToRin(ipDest);
			System.out.println("UdpDispatcherHandler UnicastPacket ipDest="+ipDest+" sendToRin="+sendToRin);
			if ( ! sendToRin ) {
				// from object to byte[]
				byte[] bufferDest = E2EComm.serializePacket(up);
				DatagramSocket destS = new DatagramSocket();
				destS.setReuseAddress(true);
				DatagramPacket destDp = new DatagramPacket(bufferDest, bufferDest.length, InetAddress.getByName(ipDest), portDest);
				destS.send(destDp);
				destS.close();
			}
//			else{
//				// either encrypt or sign the message
//				RampInternetNode.sendUnicastUdp(up, ipDest);
//				// System.out.println("UdpDispatcher: RIN sent unicast packet to " + ipDest + ":" + portDest + " "+ sendToRin);
//			} // end sendToRin true
		}
	}
	
	private void broadcastPacketUdpHandler(boolean firstHop, String remoteAddressString, BroadcastPacket bp) throws Exception{
		System.out.println("UdpDispatcher BroadcastPacket "+System.currentTimeMillis()+" from "+remoteAddressString);

		if (bp.alreadyTraversed(Dispatcher.getLocalRampId())) {
			// alreadyTraversed
			//System.out.println("UdpDispatcher broadcast packet: dropping to avoid loop (received packet)");
		}
		else {
			bp.addTraversedId(Dispatcher.getLocalRampId());

			if (!firstHop) {
				// update source
				bp.addSource(remoteAddressString);
				// reduce TTL only if not first hop
				bp.setTtl((byte) (bp.getTtl() - 1));
			}

			// invoke listeners and pass them BroadcastPacket
			PacketForwardingListener[] listeners = Dispatcher.getInstance(false).getPacketForwardingListeners();
			for (int i = 0; i < listeners.length; i++) {
				listeners[i].receivedUdpBroadcastPacket(bp);
			}

			byte[] bufferDest = null;

			if (!firstHop) {
				// from object to byte[]
				bufferDest = E2EComm.serializePacket(bp);

				// 1) send to the localhost (may fail)
				// only if not first hop

				InetAddress ipDest = InetAddress.getLocalHost();

				int portDest = bp.getDestPort();
				DatagramSocket destS = new DatagramSocket();
				try {
					//System.out.println("UdpDispatcherHandler BroadcastPacket ipDest:portDest "+ipDest+":"+portDest);
					destS.setReuseAddress(true);
					DatagramPacket destDp = new DatagramPacket(bufferDest, bufferDest.length, ipDest, portDest);
					destS.send(destDp);
				} 
				catch (Exception e) {
					// no problem...
					// System.out.println("UdpDispatcher.broadcast: send failed to local port " + portDest);
				} 
				finally {
					destS.close();
				}
			}

			// 2) send to neighbors && RINs (if TTL is greater than 0)
			if (bp.getTtl() > 0) {

				// 2a) send to neighbors
				Vector<InetAddress> neighbors = Heartbeater.getInstance(false).getNeighbors();
				if (neighbors.size() == 0) {
					//System.out.println("UdpDispatcherHandler sending broadcast ERROR!!! neighbors.size() == 0 !!!");
				}
				else{
					if(bufferDest == null){ 
						// from object to byte[]
						bufferDest = E2EComm.serializePacket(bp);
					}
					for (int i = 0; i < neighbors.size(); i++) {
						InetAddress aNeighbor = neighbors.elementAt(i);

						Integer destNodeId = Heartbeater.getInstance(false).getNodeId(aNeighbor);
						//if( destNodeId==null || bp.alreadyTraversed(destNodeId)){
						if( destNodeId!=null && bp.alreadyTraversed(destNodeId)){
							// do not send to an already traversed node
							//System.out.println("UdpDispatcher broadcast packet: dropping to avoid loop: destNodeId="+destNodeId+" aNeighbor="+aNeighbor+" (sending packet)");
						}
						else{
							// do not send to the previous node/network
							String neighborString = aNeighbor.getHostAddress().replaceAll("/", "");
							String[] neigh = neighborString.split("[.]");
							String[] rem = remoteAddressString.split("[.]");
							if (!firstHop && rem[0].equals(neigh[0]) && rem[1].equals(neigh[1]) && rem[2].equals(neigh[2])) {
								// System.out.println("UdpDispatcher "+neighbors.elementAt(i)+" same subnet of "+remoteAddress);
							}
							else {
								//System.out.println("UdpDispatcherHandler sending broadcast to neighbors["+i+"]: "+neighbors.elementAt(i)+":"+Dispatcher.DISPATCHER_PORT);
								DatagramSocket destS = new DatagramSocket();
								try {
									destS.setReuseAddress(true);
									DatagramPacket destDp = new DatagramPacket(bufferDest, bufferDest.length, neighbors.elementAt(i), Dispatcher.DISPATCHER_PORT);
									destS.send(destDp);
								} 
								catch (Exception e) {
									System.out.println("UdpDispatcherHandler: failed to send to " + neighbors.elementAt(i) + ":" + Dispatcher.DISPATCHER_PORT + " (" + e.getMessage() + ")");
									// e.printStackTrace();
								} 
								finally {
									destS.close();
								}
							}
						}
					}
				}

//				boolean isFromRin = Dispatcher.isFromRin(remoteAddressString);
//				if ( !isFromRin && RampInternetNode.isActive() ) {
//					// send to other RINs related to ERNs I am connected to
//					RampInternetNode.sendBroadcastUdp(bp);
//				} // end !isFromRin && RampInternetNode.isActive()
			}
		}
	}
	
	private static class EnqueuedUdpGenericPacket {
		
		private GenericPacket gp;
		private InetAddress remoteAddress;
		
		public EnqueuedUdpGenericPacket(GenericPacket gp, InetAddress remoteAddress){
			this.gp = gp;
			this.remoteAddress = remoteAddress;
		}

		public GenericPacket getGenericPacket() {
			return gp;
		}

		public InetAddress getRemoteAddress() {
			return remoteAddress;
		}
		
	}
	
}

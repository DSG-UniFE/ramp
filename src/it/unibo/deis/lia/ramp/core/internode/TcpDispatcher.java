
package it.unibo.deis.lia.ramp.core.internode;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Set;
import java.util.Vector;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.util.Benchmark;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import it.unibo.deis.lia.ramp.util.ThreadPool;
import it.unibo.deis.lia.ramp.util.ThreadPool.IThreadPoolCallback;

/**
 *
 * @author Carlo Giannelli
 */
public class TcpDispatcher extends Thread {

	public static int TCP_CONNECT_TIMEOUT = 2500;
	private static int TCP_DISPATCHER_SERVER_SOCKET_BACKLOG = 100;
	private static int MAX_ATTEMPTS = 3; // attempts for non-delay tolerant packets and without ContinuityManager

	private ServerSocket ss;

	// pooling
	private static ThreadPool<TcpDispatcherHandler, EnqueuedTcpGenericPacket> pool;

	private boolean active = true;

	protected void stopTcpDisptacher() {
		// stop pool threads
		pool.stopThreadPool();
		pool = null;
		active = false;
		try {
			ss.close();
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
		this.interrupt();
	}

	public TcpDispatcher() throws Exception {
		// setup pool and start threads
		pool = new ThreadPool<TcpDispatcherHandler, EnqueuedTcpGenericPacket>(this, TcpDispatcherHandler.class, getClass().getSimpleName()).init();
		ss = new ServerSocket(Dispatcher.DISPATCHER_PORT, TcpDispatcher.TCP_DISPATCHER_SERVER_SOCKET_BACKLOG);
		ss.setReuseAddress(true);
	}

	public static void asyncDispatchTcpGenericPacket(GenericPacket gp, InetAddress remoteAddress){
		pool.enqueueItem(new EnqueuedTcpGenericPacket(gp, remoteAddress));
	}

	//Stefano Lanzone
	public static void asyncDispatchTcpGenericPacket(GenericPacket gp, InetAddress remoteAddress, Set<Integer> exploredNodeIdList){
		pool.enqueueItem(new EnqueuedTcpGenericPacket(gp, remoteAddress, exploredNodeIdList));
	}

	public static void asyncDispatchTcpGenericPacket(UnicastHeader uh, InputStream is, InetAddress remoteAddress){
		pool.enqueueItem(new EnqueuedTcpGenericPacket(uh, is, remoteAddress));
	}

	private static void asyncDispatchTcpGenericPacket(Socket socket){
		try {
			pool.enqueueItem(new EnqueuedTcpGenericPacket(socket));
		} catch (Exception e) {
			e.printStackTrace();
			try {
				socket.close();
			} catch (IOException e1) {}
		}
	}

	@Override
	public void run() {
		try {
			// System.out.println("TcpDispatcher START");
			while (active) {
				// System.out.println("TcpDispatcher accept");
				// receive
				Socket s = ss.accept();
				s.setSoTimeout(60000); // XXX this is to avoid that a thread is blocked indefinitely listening on this socket

				// System.out.println("TcpDispatcher, run(): after accept");

				asyncDispatchTcpGenericPacket(s);
			}
		}
		catch (java.net.BindException be) {
			// be.printStackTrace();
			System.out.println("TcpDispatcher port " + Dispatcher.DISPATCHER_PORT + " already in use; exiting now");
		}
		catch (java.net.SocketException se) {
			// se.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("TcpDispatcher END");
	}

	public class TcpDispatcherHandler implements IThreadPoolCallback<EnqueuedTcpGenericPacket> {

		private GenericPacket gp;
		private InputStream is;
		private InetAddress remoteAddress;
		private Socket socket;
		// Stefano Lanzone
		private Set<Integer> exploredNodeIdList;

		@Override
		public void run() {
			// System.out.println("TcpDispatcherHandler run start");
			try {

				if (gp == null) {
					gp = E2EComm.readPacket(is);
				}

				String remoteAddressString = remoteAddress.toString().replaceAll("/", "").split(":")[0];
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

				boolean firstHop = false;
				if (remoteAddressString.equals("127.0.0.1") || remoteAddressString.equals("127.0.1.1")) {
					firstHop = true;
					// System.out.println("TcpDispatcherHandler firstHop = true A");
				} else {
					Vector<String> localAddresses = Dispatcher.getLocalNetworkAddresses();
					// System.out.println("TcpDispatcherHandler localAddresses = "+localAddresses);
					for (int i = 0; (firstHop == false) && (i < localAddresses.size()); i++) {
						if (localAddresses.elementAt(i).equals(remoteAddressString)) {
							firstHop = true;
							// System.out.println("TcpDispatcherHandler firstHop = true B");
						}
					}
				}
				// System.out.println("TcpDispatcherHandler firstHop="+firstHop+" remoteAddressString="+remoteAddressString);

				if (gp instanceof UnicastHeader) {
					UnicastHeader uh = (UnicastHeader) gp;
					unicastHeaderTcpHandler(firstHop, remoteAddressString, uh);
				} else if (gp instanceof UnicastPacket) {
					UnicastPacket up = (UnicastPacket) gp;

					// FIXME
					Benchmark.append(System.currentTimeMillis(), "tcp_dispatcher_handler_unicast", up.getId(),
							up.getSourceNodeId(), up.getDestNodeId());
					System.out.println("TcpDispatcherHandler.tcp_dispatcher_handler_unicast, packetID: " + up.getId());

					unicastPacketTcpHandler(firstHop, remoteAddressString, up);
				} else if (gp instanceof BroadcastPacket) {
					final BroadcastPacket bp = (BroadcastPacket) gp;

					// FIXME
					Benchmark.append(System.currentTimeMillis(), "tcp_dispatcher_handler_broadcast", bp.getId(),
							bp.getSourceNodeId(), bp.getDestPort());
					System.out
							.println("TcpDispatcherHandler.tcp_dispatcher_handler_broadcast, packetID: " + bp.getId());

					if(exploredNodeIdList == null)
						broadcastPacketTcpHandler(firstHop, remoteAddressString, bp);
					else
						broadcastPacketTcpHandler(firstHop, remoteAddressString, exploredNodeIdList, bp);
				} else {
					// not UnicastPacket, not UnicastHeader, not BroadcastPacket
					throw new Exception("Unknown packet type: " + gp.getClass().getName());
				}
				// System.out.println("TcpDispatcherHandler finished");
			} catch (Exception e) {
				System.out.println("TcpDispatcherHandler exception" + e + " remoteAddress.getHostAddress()="
						+ remoteAddress.getHostAddress());
				e.printStackTrace();
			}

			try {
				if (is != null) {
					is.close();
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

//		public void stopTcpDispatcherHandler() {
//			this.active = false;
//			this.interrupt();
//		}

		private void unicastHeaderTcpHandler(boolean firstHop, String remoteAddressString, UnicastHeader uh) throws Exception{
			// System.out.println("TcpDispatcherHandler UnicastHeader");
			if (!firstHop ||
					// loopback
					(uh.getDest() != null && uh.getDest().length == 1 && uh.getDest()[0].equals(GeneralUtils.getLocalHost()))) {
				// update source
				uh.addSource(remoteAddressString);

				// update current hop
				uh.setCurrentHop((byte) (uh.getCurrentHop() + 1));
			}

			// invoke listeners and pass them UnicastHeader
			PacketForwardingListener[] listeners = Dispatcher.getInstance(false).getPacketForwardingListeners();
			for (int i = 0; i < listeners.length; i++) {
				listeners[i].receivedTcpUnicastHeader(uh);
			}

			int currentHop = uh.getCurrentHop();

			String ipDest = null;
			int portDest = -1;

			// 1) open an outgoing socket with the next dest,
			// either a remote Dispatcher or a local receive()
			Socket destS = null;
			try{
				SocketAddress socketAddress = null;
				boolean retry;
				int attempts = 0;
				boolean missedDeadline;
				int packetRetry;
				boolean sendToRin = true;

				do {
					retry = true;
					packetRetry = uh.getRetry();
					// System.out.println("TcpDispatcherHandler UnicastHeader packetRetry="+packetRetry);
					Exception ex = null;
					if (packetRetry == 0) {
						missedDeadline = true;
					}
					else {
						missedDeadline = false;
						try {
							String[] dest = uh.getDest();
							int destNodeId = uh.getDestNodeId();
							if ((destNodeId != "".hashCode() && destNodeId == Dispatcher.getLocalRampId()) || currentHop == dest.length) {
								// a) localhost is the destination
								ipDest = GeneralUtils.getLocalHost();
								portDest = uh.getDestPort();
								sendToRin = false;
							}
							else {
								// b) send to the following dispatcher
								ipDest = dest[currentHop];
								sendToRin = Dispatcher.sendToRin(ipDest);
								System.out.println("TcpDispatcherHandler UnicastHeader sendToRin="+sendToRin);
								if (!sendToRin) {
									portDest = Dispatcher.DISPATCHER_PORT;
								}
//								else {
//									portDest = RampInternetNode.getRemoteRinTcpPort(ipDest);
//								}
							}

							// nat-t
							// if sending to a RIN and the RIN is relayed, send to the relayed address

//							boolean isRemoteRinRelayed = sendToRin && RampInternetNode.isRemoteRinRelayed(ipDest);
//							String finalIpAddress = isRemoteRinRelayed ? RampInternetNode.getRemoteRinRelayedAddress(ipDest) : ipDest;
							String finalIpAddress = ipDest;

							System.out.println("TcpDispatcherHandler UnicastHeader sending to "+finalIpAddress+":"+portDest);
							socketAddress = new InetSocketAddress(finalIpAddress, portDest);

							destS = new Socket();
							destS.setReuseAddress(true);
							int connectTimeout = uh.getConnectTimeout();
							if (connectTimeout == GenericPacket.UNUSED_FIELD) {
								connectTimeout = TcpDispatcher.TCP_CONNECT_TIMEOUT;
							}
							if( sendToRin ){
								connectTimeout *= 3;
							}
							destS.connect(socketAddress, connectTimeout);
							destS.setSoTimeout(60000); // XXX this is to avoid that a thread is blocked indefinitely listening on this socket
							retry = false;

							// nat-t: wait a while to set up the connection
//							if(isRemoteRinRelayed){
//								Thread.sleep(RampInternetNode.natTDelay); // FIXME nat-t necessario??? sembrerebbe di si'. perche'? quale valore ottimale?
//							}
						}
						catch (Exception e) {
							if (destS != null) {
								destS.close();
								destS = null;
							}
							ex = e;

							System.out.println("TcpDispatcherHandler UnicastHeader exception: " + e.getMessage() + " to " + ipDest + ":" + portDest);
						}
					}
					if (missedDeadline == false && retry == true) {
						if (packetRetry != GenericPacket.UNUSED_FIELD) {
							uh.setRetry((byte) (packetRetry - 1));
						}
						attempts++;
						// invoke listeners and pass them UnicastHeader
						listeners = Dispatcher.getInstance(false).getPacketForwardingListeners();
						for (int i = 0; i < listeners.length; i++) {
							listeners[i].sendingTcpUnicastHeaderException(uh, ex);
						}
					}
				} while (!((packetRetry != GenericPacket.UNUSED_FIELD && missedDeadline == true) || retry == false || (packetRetry == GenericPacket.UNUSED_FIELD && attempts >= TcpDispatcher.MAX_ATTEMPTS)));

				GenericPacket gp = uh;
				if ( destS != null && destS.isConnected() ) {
					// 2) send the header
					OutputStream destOs = destS.getOutputStream();
					// send either to neighbor or to RIN
					if ( ! sendToRin ) {
						E2EComm.writePacket(gp, destOs);
					}
//					else{
//						RampInternetNode.sendUnicastHeaderTcp(uh, ipDest, destOs);
//					} // end sendToRin true

					// System.out.println("TcpDispatcherHandler unicast header sent to "+ipDest+":"+portDest);

					// 3) while payload not completely received
					// 		i) read bufferSize bytes from ingoing socket
					// 		ii) write bufferSize bytes to outgoing socket
//					if( sendToRin ){
//						RampInternetNode.sendUnicastPayloadTcp(uh, ipDest, is, destOs, Dispatcher.getInstance(false).getPacketForwardingListeners());
//					}
//					else{
						byte[] buffer = new byte[uh.getBufferSize()];
						BufferedOutputStream destBos = null;

						destBos = new BufferedOutputStream(destS.getOutputStream(), uh.getBufferSize());

						BufferedInputStream bis = new BufferedInputStream(is, uh.getBufferSize());
						int count = 0;
						int readBytes = -2;
						//int preRead = -3;
						boolean finished = false;

						while ( ! finished ) {
							// attempt to read enough bytes to fulfill the buffer
							//try{
							//preRead = readBytes;
							//System.out.println("TcpDispatcherHandler trying to read (partial): count=" + count + ", buffer.length=" + buffer.length);
							readBytes = bis.read(buffer, 0, buffer.length);
							//System.out.println("TcpDispatcherHandler partial payload read (partial): readBytes "+readBytes);
							//readBytes = is.read(buffer, 0, buffer.length);
							/*}
								catch(SocketException se){
									// socket close, like end of file
									//count = 0;
									readBytes = -1;
								}*/

							// System.out.println("TcpDispatcherHandler partial payload read (partial): readBytes "+readBytes);
							if ( readBytes == -1 ) {
								finished = true;
							}
							else {
								count += readBytes;
								// if(count == buffer.length){
								// qui sarebbe sbagliato aspettare per forza "buffer.length" byte;
								// cosa succede se si invia qualcosa e poi si aspetta una risposta?

								destBos.write(buffer, 0, readBytes);
								destBos.flush();

								//System.out.println("TcpDispatcherHandler partial payload written (partial): count=" + count + ", buffer.length=" + buffer.length);
								//System.out.println("TcpDispatcherHandler partial payload partial buffer = " + new String(buffer, 0, readBytes) );

								// invoke listeners and pass them UnicastHeader + Partial payload
								listeners = Dispatcher.getInstance(false).getPacketForwardingListeners();
								for (int i = 0; i < listeners.length; i++) {
									listeners[i].receivedTcpPartialPayload(uh, buffer, 0, readBytes, false);
								}

								//count = 0;
							}

							//System.out.println(count);
							/*if (count > 0) {
									// write remaining bytes
									destBos.write(buffer, 0, count);
									destBos.flush();
									//System.out.println("TcpDispatcherHandler partial payload written (final): count = " + count);
								}*/
						} // end while
						//System.out.println("TcpDispatcherHandler partial payload written (final): count = " + count);
						//System.out.println("TcpDispatcherHandler partial payload last buffer = " + new String(buffer, 0, preRead) );

						// invoke listeners and pass them UnicastHeader + Partial payload
						listeners = Dispatcher.getInstance(false).getPacketForwardingListeners();
						for (int i = 0; i < listeners.length; i++) {
							listeners[i].receivedTcpPartialPayload(uh, buffer, 0, count, true);
						}
//					} // end ! sendToRin

				}
				else {
					System.out.println("TcpDispatcher unicast header: failed to send to the next hop");
				}
			}
			catch(Exception e){
				// e.printStackTrace();
				System.out.println("TcpDispatcher unicast header: e = "+e+" at "+destS.getRemoteSocketAddress()+" lineNumber = "+e.getStackTrace()[e.getStackTrace().length-1].getLineNumber());
			}
			finally{
				if ( destS != null && ! destS.isClosed() ) {
					destS.getOutputStream().flush();
					destS.getOutputStream().close();
					destS.close();
					destS = null;
				}
			}
			// System.out.println("TcpDispatcherHandler partial payload finished");
		}

		private void unicastPacketTcpHandler(boolean firstHop, String remoteAddressString, UnicastPacket up) throws Exception{
			// System.out.println("TcpDispatcherHandler UnicastPacket");
			if (!firstHop ||
					// loopback
					(up.getDest() != null && up.getDest().length == 1 && up.getDest()[0].equals(GeneralUtils.getLocalHost()))) {

				// System.out.println("TcpDispatcherHandler UnicastPacket update source & currentHop");

				// update source
				up.addSource(remoteAddressString);

				// update current hop
				up.setCurrentHop((byte) (up.getCurrentHop() + 1));
			}

			// invoke listeners and pass them UnicastPacket
			PacketForwardingListener[] listeners = Dispatcher.getInstance(false).getPacketForwardingListeners();
			for (int i = 0; i < listeners.length; i++) {
				listeners[i].receivedTcpUnicastPacket(up);
			}

			int currentHop = up.getCurrentHop();

			String ipDest = null;
			int portDest = -1;

			Socket destS = null;
			SocketAddress socketAddress = null;
			boolean retry;
			int attempts = 0;
			boolean missedDeadline;
			int packetRetry;
			boolean sendToRin = true;
			do {
				retry = true;
				packetRetry = up.getRetry();
				// System.out.println("TcpDispatcherHandler UnicastPacket packetRetry="+packetRetry);
				Exception ex = null;
				if (packetRetry == 0) {
					missedDeadline = true;
				}
				else {
					missedDeadline = false;
					String[] dest = up.getDest();
					if (dest != null) {
						try {
							int destNodeId = up.getDestNodeId();
							if ((destNodeId != "".hashCode() && destNodeId == Dispatcher.getLocalRampId()) || currentHop == dest.length) {
								// a) localhost is the destination
								ipDest = GeneralUtils.getLocalHost();
								portDest = up.getDestPort();
								sendToRin = false;
							}
							else {
								ipDest = dest[currentHop];
								// b) send to the following dispatcher
								sendToRin = Dispatcher.sendToRin(ipDest);
								//System.out.println("TcpDispatcherHandler UnicastPacket ipDest="+ipDest+" sendToRin="+sendToRin);
								if (!sendToRin) {
									portDest = Dispatcher.DISPATCHER_PORT;
								}
//								else if (sendToRin) {
//									portDest = RampInternetNode.getRemoteRinTcpPort(ipDest);
//								}
							}

							// nat-t
							// if sending to a RIN and the RIN is relayed, send to the relayed address

//							boolean isRemoteRinRelayed = sendToRin && RampInternetNode.isRemoteRinRelayed(ipDest);
//							String finalIpDest = isRemoteRinRelayed ? RampInternetNode.getRemoteRinRelayedAddress(ipDest) : ipDest;
							String finalIpDest = ipDest;
//							if(isRemoteRinRelayed){
//								System.out.println("TcpDispatcherHandler unicast packet sending to "+finalIpDest+":"+portDest);
//							}
							socketAddress = new InetSocketAddress(finalIpDest, portDest);

							destS = new Socket();
							destS.setReuseAddress(true);
							int connectTimeout = up.getConnectTimeout();
							if (connectTimeout == GenericPacket.UNUSED_FIELD) {
								connectTimeout = TcpDispatcher.TCP_CONNECT_TIMEOUT;
							}
							if( sendToRin ){
								connectTimeout *= 3;
							}
							destS.connect(socketAddress, connectTimeout);
							//System.out.println("TcpDispatcherHandler unicast packet connected to "+finalIpDest+":"+portDest);
							//System.out.println("TcpDispatcherHandler unicast packet destS.getRemoteSocketAddress()="+destS.getRemoteSocketAddress());
							destS.setSoTimeout(60000); // XXX this is to avoid that a thread is blocked indefinitely listening on this socket
							retry = false;

							// nat-t: wait a while to set up the connection
//							if(isRemoteRinRelayed){
//								Thread.sleep(RampInternetNode.natTDelay); // FIXME nat-t necessario??? sembrerebbe di si'. perche'? quale valore ottimale?
//							}
						}
						catch (Exception e) {
							if (destS != null) {
								destS.close();
								destS = null;
							}
							ex = e;
							System.out.println("TcpDispatcherHandler UnicastPacket exception: " + e.getMessage() + " to " + ipDest + ":" + portDest);
							// Object p = E2EComm.deserialize(up.getBytePayload());
							// System.out.println("TcpDispatcherHandler UnicastPacket payload: " + p.getClass().getName() );
							// System.out.println("TcpDispatcherHandler UnicastPacket payload: " + p );
							// System.out.println("TcpDispatcherHandler UnicastPacket Arrays.toString(up.getSource()): " + Arrays.toString(up.getSource()) );
							// System.out.println("TcpDispatcherHandler UnicastPacket Arrays.toString(up.getDest()): " + Arrays.toString(up.getDest()) );
							// System.out.println("TcpDispatcherHandler UnicastPacket up.getDestPort(): " + up.getDestPort() );
							// System.out.println("TcpDispatcherHandler UnicastPacket up.getCurrentHop(): " + up.getCurrentHop() );
							e.printStackTrace();
						}
					}
				}
				if (missedDeadline == false && retry == true) {
					if (packetRetry != GenericPacket.UNUSED_FIELD) {
						up.setRetry((byte) (packetRetry - 1));
					}
					attempts++;
					// invoke listeners and pass them UnicastPacket
					listeners = Dispatcher.getInstance(false).getPacketForwardingListeners();
					for (int i = 0; i < listeners.length; i++) {
						listeners[i].sendingTcpUnicastPacketException(up, ex);
						System.out.println("POST TcpDispatcherHandler unicast listener ipDest="+ipDest+" portDest="+portDest);

						// FIXME
						Benchmark.append(System.currentTimeMillis(), "tcp_dispatcher_handler_try_to_send", up.getId(),
								up.getSourceNodeId(), up.getDestNodeId());
					}
				}
			}
			while ( !((packetRetry != GenericPacket.UNUSED_FIELD && missedDeadline == true) ||
					retry == false ||
					(packetRetry == GenericPacket.UNUSED_FIELD && attempts >= TcpDispatcher.MAX_ATTEMPTS)) );

			if ( destS != null && destS.isConnected() ) {
				// System.out.println("TcpDispatcher unicast packet: destS.getRemoteSocketAddress() "+destS.getRemoteSocketAddress());
				GeneralUtils.appendLog("TcpDispatcher sent unicast packet: destS.getRemoteSocketAddress() "+destS.getRemoteSocketAddress());

				// FIXME
				Benchmark.append(System.currentTimeMillis(), "tcp_dispatcher_handler_sent_packet", up.getId(),
						up.getSourceNodeId(), up.getDestNodeId());

				OutputStream destOs = destS.getOutputStream();
				GenericPacket gp = up;

				if (!sendToRin) {
					E2EComm.writePacket(gp, destOs);
				}
//				else{
//					RampInternetNode.sendUnicastTcp(up, ipDest, destOs);
//					//System.out.println("TcpDispatcherHandler post RampInternetNode.sendUnicastTcp ipDest="+ipDest);
//				}
			}
			else {
				System.out.println("TcpDispatcher unicast packet: failed to send to the next hop: socketAddress = " + socketAddress);
				// throw new Exception();
			}

			if (destS != null) {
				destS.close();
				destS = null;
			}
		}

		private void broadcastPacketTcpHandler(boolean firstHop, String remoteAddressString, Set<Integer> exploredNodes, final BroadcastPacket bp) throws Exception{
			// Send to neighbors
			sendToNeighbors(firstHop, remoteAddressString, exploredNodes, bp);
		}

		private void broadcastPacketTcpHandler(boolean firstHop, String remoteAddressString, final BroadcastPacket bp) throws Exception{
			// System.out.println("TcpDispatcherHandler BroadcastPacket");

			if (bp.alreadyTraversed(Dispatcher.getLocalRampId())) {
				System.out.println("TcpDispatcher broadcast packet: dropping to avoid loop (received packet)");
			}
			else {
				bp.addTraversedId(Dispatcher.getLocalRampId());

				if (!firstHop) {
					// update source
					bp.addSource(remoteAddressString);
					// update TTL
					bp.setTtl((byte) (bp.getTtl() - 1));
				}

				// invoke listeners and pass them BroadcastPacket
				PacketForwardingListener[] listeners = Dispatcher.getInstance(false).getPacketForwardingListeners();
				for (int i = 0; i < listeners.length; i++) {
						listeners[i].receivedTcpBroadcastPacket(bp);
				}

				if ( ! firstHop ) {
					// send to localhost in another thread
					// to not delay broadcast packet dissemination to other neighbor nodes
					Thread localhostSend = new Thread(new Runnable() {
						@Override
						public void run() {
							// 1) send to the localhost (may fail, no problem)
							int portDest = bp.getDestPort();
							Socket destS = new Socket();
							try {
								InetAddress ipDest = InetAddress.getLocalHost();
								SocketAddress socketAddress = new InetSocketAddress(ipDest, portDest);
								destS.setReuseAddress(true);
								destS.connect(socketAddress, portDest);
								destS.setSoTimeout(60000); // XXX this is to avoid that a thread is blocked indefinitely listening on this socket
								// System.out.println("TcpDispatcherHandler sending broadcast to localhost: "+ipDest+":"+portDest);

								OutputStream destOs = destS.getOutputStream();
								E2EComm.writePacket(bp, destOs);
							}
							catch (Exception e) {
								// no problem...
								// System.out.println("TcpDispatcherHandler.broadcast: send failed to local port " + portDest);
							}
							finally {
								try {
									destS.close();
								} catch (Exception e) {
									// System.out.println("TcpDispatcherHandler.broadcast: send failed to local port " + portDest);
								}
								destS = null;
							}
						}
					});
					localhostSend.start();
				}

				// 2) send to neighbors (if TTL is greater than 0)
				if (bp.getTtl() > 0) {
					// FIXME
					System.out.println("TcpDispatcher broadcastPacketTcpHandler: sendToNeighbors()");


					sendToNeighbors(firstHop, remoteAddressString, null, bp);

//					boolean isFromRin = Dispatcher.isFromRin(remoteAddressString);
//					if ( ! isFromRin  && RampInternetNode.isActive() ) {
//						// not from a remote RIN, so send it to other RINs of my ERNs
//						RampInternetNode.sendBroadcastTcp(bp);
//					} // end if !isFromRin  && RampInternetNode.isActive()
				}
			}
		}

		//Stefano Lanzone
		private void sendToNeighbors(boolean firstHop, String remoteAddressString, Set<Integer> exploredNodes, final BroadcastPacket bp) {
			System.out.println("TcpDispatcher.sendToNeighbors()");
			// FIXME
			Benchmark.append(System.currentTimeMillis(), "tcp_dispatcher_handler_send_to_neighbors", bp.getId(),
					bp.getSourceNodeId(), bp.getDestPort());

			Vector<InetAddress> neighbors = Heartbeater.getInstance(false).getNeighbors();
			if (neighbors.size() == 0) {
				// System.out.println("TcpDispatcherHandler sending broadcast ERROR!!! neighbors.size() == 0 !!!");
			}

			for (int i = 0; i < neighbors.size(); i++) {
				final InetAddress aNeighbor = neighbors.elementAt(i);

				Integer destNodeId = Heartbeater.getInstance(false).getNodeId(aNeighbor);
				//if( destNodeId==null || bp.alreadyTraversed(destNodeId)){
				if( (destNodeId!=null && bp.alreadyTraversed(destNodeId)) || (exploredNodes!=null && exploredNodes.contains(destNodeId))){
					// do not send to already traversed nodes
					//System.out.println("TcpDispatcher broadcast packet: dropping to avoid loop: destNodeId="+destNodeId+" aNeighbor="+aNeighbor+" (sending packet)");
				    if((destNodeId!=null && bp.alreadyTraversed(destNodeId)))
				    	GeneralUtils.appendLog("TcpDispatcher broadcast packet, do not send to already traversed nodes: dropping to avoid loop destNodeId="+destNodeId+" aNeighbor="+aNeighbor+" (sending packet)");

				    if((exploredNodes!=null && exploredNodes.contains(destNodeId)))
				    	GeneralUtils.appendLog("TcpDispatcher broadcast packet, do not send to explored nodes: dropping to avoid loop destNodeId="+destNodeId+" aNeighbor="+aNeighbor+" (sending packet)");
				}
				else{
					// do not send to the previous node/network
					String neighborString = aNeighbor.getHostAddress().replaceAll("/", "");
					String[] neigh = neighborString.split("[.]");
					String[] rem = remoteAddressString.split("[.]");
					if (!firstHop && (rem[0].equals(neigh[0]) && rem[1].equals(neigh[1]) && rem[2].equals(neigh[2]))) {
						// System.out.println("TcpDispatcherHandler broadcast "+neighbors.elementAt(i)+" same subnet of "+remoteAddress);
						GeneralUtils.appendLog("TcpDispatcher broadcast packet, do not send to the previous node/network "+neighborString+" same subnet of "+remoteAddress);
					}
					else {
						// send to a neighbor node in another thread
						// to not delay broadcast packet dissemination to other neighbor nodes
						GeneralUtils.appendLog("TcpDispatcher broadcast packet, send to a neighbor node "+neighborString);
						Thread neighborSend = new Thread(new Runnable() {
							@Override
							public void run() {
								// System.out.println("TcpDispatcherHandler sending broadcast to neighbors["+i+"]: "+neighbors.elementAt(i)+":"+Dispatcher.DISPATCHER_PORT);
								Socket destS = new Socket();
								try {
									// may fail if the remote node has leaved (no problem)
									SocketAddress socketAddress = new InetSocketAddress(aNeighbor, Dispatcher.DISPATCHER_PORT);

									destS.setReuseAddress(true);
									destS.connect(socketAddress, TcpDispatcher.TCP_CONNECT_TIMEOUT);
									destS.setSoTimeout(60000); // XXX this is to avoid that a thread is blocked indefinitely listening on this socket

									OutputStream destOs = destS.getOutputStream();
									E2EComm.writePacket(bp, destOs);
								}
								catch (Exception e) {
									// no problem...
									// System.out.println("TcpDispatcherHandler.broadcast: send failed to local port " + portDest);
								}
								finally {
									try {
										destS.close();
									}
									catch (Exception e) {
										// System.out.println("TcpDispatcherHandler.broadcast: send failed to local port " + portDest);
									}
									destS = null;
								}
							}
						});
						neighborSend.start();
					}
				}
			}
		}

		@Override
		public void onBeforeExecute(EnqueuedTcpGenericPacket item) {
			this.gp = item.getGenericPacket();
			this.is = item.getInputStream();
			this.remoteAddress = item.getRemoteAddress();
			this.socket = item.getSocket();
			//Stefano Lanzone
			this.exploredNodeIdList = item.getExploredNodeIdList();
		}

		@Override
		public void onPostExecute() {
			if(socket != null){
				try {
					socket.close();
				} catch (Exception e) {}
			}
		}

	}

	private static class EnqueuedTcpGenericPacket {

		private GenericPacket gp;
		private InetAddress remoteAddress;
		private InputStream is;
		private Socket socket;
		//Stefano Lanzone
		private Set<Integer> exploredNodeIdList;

		public EnqueuedTcpGenericPacket(Socket socket) throws IOException{
			this.gp = null;
			this.remoteAddress = socket.getInetAddress();
			this.is = socket.getInputStream();
			this.socket = socket;
		}

		public EnqueuedTcpGenericPacket(GenericPacket gp, InetAddress remoteAddress){
			this.gp = gp;
			this.remoteAddress = remoteAddress;
		}

		//Stefano Lanzone
		public EnqueuedTcpGenericPacket(GenericPacket gp, InetAddress remoteAddress, Set<Integer> exploredNodeIdList){
			this.gp = gp;
			this.remoteAddress = remoteAddress;
			this.exploredNodeIdList = exploredNodeIdList;
		}

		public EnqueuedTcpGenericPacket(UnicastHeader uh, InputStream is, InetAddress remoteAddress) {
			this.gp = uh;
			this.remoteAddress = remoteAddress;
			this.is = is;
		}

		public GenericPacket getGenericPacket() {
			return gp;
		}

		public InetAddress getRemoteAddress() {
			return remoteAddress;
		}

		public InputStream getInputStream() {
			return is;
		}

		public Socket getSocket(){
			return socket;
		}

		//Stefano Lanzone
		public Set<Integer> getExploredNodeIdList(){
			return exploredNodeIdList;
		}

	}

}

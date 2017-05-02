/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.core.internode;

import it.unibo.deis.lia.ramp.core.e2e.*;
import it.unibo.deis.lia.ramp.service.application.*;
import it.unibo.deis.lia.ramp.service.application.mpeg.*;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

import java.util.*;

/**
 * 
 * @author useruser
 */
public class BroadcastSplitter implements PacketForwardingListener {

	// key: simple programName
	private Hashtable<String, SimpleRtpMpegTsParser> perProgramQueueDB;
	private Hashtable<String, Long> perProgramLastPacketDB;

	private BroadcastService bs;
	private PeriodicDBCleaner periodicDBCleaner;

	private static BroadcastSplitter broadcastSplitter = null;

	private BroadcastSplitter(BroadcastService bs) {
		this.bs = bs;
		this.perProgramQueueDB = new Hashtable<String, SimpleRtpMpegTsParser>();
		this.perProgramLastPacketDB = new Hashtable<String, Long>();
		this.periodicDBCleaner = new PeriodicDBCleaner();
		this.periodicDBCleaner.start();
	}

	synchronized public static BroadcastSplitter getInstance(BroadcastService bs) {
		if (broadcastSplitter == null) {
			broadcastSplitter = new BroadcastSplitter(bs);
			Dispatcher.getInstance(false).addPacketForwardingListener(broadcastSplitter);
			System.out.println("BroadcastSplitter activated");
		}
		return broadcastSplitter;
	}

	synchronized public void stopBroadcastSplitter() {
		Dispatcher.getInstance(false).removePacketForwardingListener(broadcastSplitter);
		broadcastSplitter = null;
		periodicDBCleaner.stopPeriodicDBCleaner();
		System.out.println("BroadcastSplitter deactivated");
	}

	@Override
	public void receivedUdpUnicastPacket(UnicastPacket up) {
		try {
			if (up.getSource().length > 0
			// not loopback
					//&& !up.getSource()[0].equals(java.net.InetAddress.getLocalHost().getHostAddress().replaceAll("/", ""))) {
					&& !up.getSource()[0].equals(GeneralUtils.getLocalHost())) {
				Object payload = E2EComm.deserialize(up.getBytePayload());
				RTP rtp = null;
				if (payload instanceof byte[]) {
					// TODO parse byte[] to (eventually) retrieve RTP MPEG-TS streams
				}
				if (payload instanceof RTP) {
					rtp = (RTP) payload;
				}
				if (rtp != null) {
					// System.out.println("BroadcastSplitter rtp.getSimpleProgramName() "+rtp.getSimpleProgramName());
					String simpleProgramName = rtp.getSimpleProgramName();
					System.out.println("BroadcastSplitter programName " + simpleProgramName);
					System.out.println("BroadcastSplitter SplitterAmount " + rtp.getSplitterAmount());

					SimpleRtpMpegTsParser rtpMpegParser = perProgramQueueDB.get(simpleProgramName);

					try {
						if (rtpMpegParser == null) {
							rtpMpegParser = new SimpleRtpMpegTsParser();
							byte newSplitterAmount = (byte) (rtp.getSplitterAmount() + 1);
							bs.addProgram(simpleProgramName, rtpMpegParser, newSplitterAmount, up.getSource());
							perProgramQueueDB.put(simpleProgramName, rtpMpegParser);
							System.out.println("BroadcastSplitter program " + simpleProgramName + " added (" + (rtp.getSplitterAmount() + 1) + ")");
						}

						RTP splitterRtp = (RTP) rtp.clone();
						splitterRtp.setSplitterAmount((byte) (splitterRtp.getSplitterAmount() + 1));
						rtpMpegParser.addRtp(splitterRtp);
						perProgramLastPacketDB.put(simpleProgramName, System.currentTimeMillis());
					} catch (Exception e) {
						// e.printStackTrace();
						// System.out.println("BroadcastSplitter Exception "+e.getMessage());
					}
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// System.out.println("BroadcastSplitter.receivedUDPUnicastPacket end");
	}

	private class PeriodicDBCleaner extends Thread {
		boolean active = true;

		@Override
		public void run() {
			// periodically remove obsolete values in BroadcastSplitter DBs
			System.out.println("BroadcastSplitter PeriodicDBCleaner START");
			while (active) {
				try {
					Thread.sleep(2500);
					// System.out.println("BroadcastSplitter PeriodicDBCleaner");
					String[] names = perProgramQueueDB.keySet().toArray(new String[0]);
					for (int i = 0; i < names.length; i++) {
						Long lastUpdate = perProgramLastPacketDB.get(names[i]);
						if (lastUpdate == null || System.currentTimeMillis() - lastUpdate > 2500) {
							perProgramQueueDB.remove(names[i]);
							perProgramLastPacketDB.remove(names[i]);
							System.out.println("BroadcastSplitter PeriodicDBCleaner removing program " + names[i]);
						}
					}

				} catch (Exception e) {
					// e.printStackTrace();
					// System.out.println("BroadcastSplitter Exception "+e.getMessage());
				}
			}
			System.out.println("BroadcastSplitter PeriodicDBCleaner FINISHED");
		}

		private void stopPeriodicDBCleaner() {
			active = false;
			this.interrupt();
		}
	}

	@Override
	public void receivedUdpBroadcastPacket(BroadcastPacket bp) {
	}

	@Override
	public void receivedTcpUnicastPacket(UnicastPacket up) {
	}

	@Override
	public void receivedTcpBroadcastPacket(BroadcastPacket bp) {
	}

	@Override
	public void receivedTcpUnicastHeader(UnicastHeader uh) {
	}

	@Override
	public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
	}

	@Override
	public void sendingTcpUnicastPacketException(UnicastPacket up, Exception e) {
	}

	@Override
	public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e) {
	}

}

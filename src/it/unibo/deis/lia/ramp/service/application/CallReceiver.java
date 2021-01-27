package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.service.application.jpeg.FrameData;
import it.unibo.deis.lia.ramp.service.application.jpeg.InfoPacket;
import it.unibo.deis.lia.ramp.service.application.jpeg.JPEGBufferDecoder;
import it.unibo.deis.lia.ramp.service.application.jpeg.JPEGPacket;
import it.unibo.deis.lia.ramp.service.management.*;
import it.unibo.deis.lia.ramp.core.e2e.*;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unibo.deis.lia.ramp.RampEntryPoint;

import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

public class CallReceiver {
	private String rampProtocol = "udp", streamProtocol = "ts-RTP";

	private static CallReceiver callReceiver = null;
	private static CallServiceJFrame csjf;

	private CallReceiver() {
		if (RampEntryPoint.os.startsWith("linux")) {
			try {
				Process pId = Runtime.getRuntime().exec("id -ru");
				BufferedReader brId = new BufferedReader(new InputStreamReader(pId.getInputStream()));
				String lineId;
				lineId = brId.readLine();
				if (lineId.equals("0")) {
				}
			} catch (IOException ex) {
				Logger.getLogger(StreamClient.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}

	public static synchronized CallReceiver getInstance(CallServiceJFrame callsjf) {
		if (callReceiver == null) {
			csjf = callsjf;
			callReceiver = new CallReceiver();
		}
		return callReceiver;
	}

	public void stopClient() {
		callReceiver = null;
	}

	public String getRampProtocol() {
		return rampProtocol;
	}

	public void setRampProtocol(String rampProtocol) {
		this.rampProtocol = rampProtocol;
	}

	public String getStreamProtocol() {
		return streamProtocol;
	}

	public void setStreamProtocol(String streamProtocol) {
		this.streamProtocol = streamProtocol;
	}

	public Vector<ServiceResponse> findCallService(int ttl, int timeout, int serviceAmount) throws Exception {
		long pre = System.currentTimeMillis();
		Vector<ServiceResponse> services = ServiceDiscovery.findServices(ttl, "Call", timeout, serviceAmount, null);
		long post = System.currentTimeMillis();
		float elapsed = (post - pre) / (float) 1000;
		System.out.println("CallReceiver findStreamService elapsed=" + elapsed + "    services=" + services);
		return services;
	}

	public void getStream(ServiceResponse service, String stream, String streamProtocol, String rampProtocol, int myServicePort) throws Exception {
		int sendProtocol;
		if (rampProtocol.equals("udp")) {
			sendProtocol = E2EComm.UDP;
		} else {
			sendProtocol = E2EComm.TCP;
		}
		BoundReceiveSocket receiveSocket = E2EComm.bindPreReceive(sendProtocol);
		int clientPort = receiveSocket.getLocalPort();

		CallRequest cr = new CallRequest(stream, clientPort, streamProtocol, rampProtocol, myServicePort);
		System.out.println("CallReceiver requiring " + stream);
		E2EComm.sendUnicast(service.getServerDest(), service.getServerPort(), service.getProtocol(), E2EComm.serialize(cr));

		byte[] received = new byte[1];
		Object deserialized = null;
		System.out.println("CallReceiver starting to receive packets");
		int timeout = 25 * 1000;

		Vector<Float> intervals = new Vector<Float>();
		Vector<Integer> payloads = new Vector<Integer>();
		Vector<Integer> packets = new Vector<Integer>();
		Vector<Float> jitters = new Vector<Float>();
		Vector<Float> dif = new Vector<Float>();
		Vector<Byte> loosed = new Vector<Byte>();
		Vector<Byte> jpegQ = new Vector<Byte>();

		// try{

		FrameData fData = null;

		UnicastPacket firstUp = (UnicastPacket) E2EComm.receive(receiveSocket, timeout);
		received = firstUp.getBytePayload();
		deserialized = E2EComm.deserialize(received);

		if (deserialized instanceof FrameData) {
			fData = (FrameData) deserialized;

			Canvas canvas = new Canvas(fData);

			long pre = 0, post = 0;
			float elapsed = 0, jitter = 0;
			short count = -1;
			boolean isJpeg = true;
			timeout = 5 * 1000;
			do {
				try {

					if ( RampEntryPoint.isLogging() ) {
						if (isJpeg) {
							pre = System.currentTimeMillis();
							isJpeg = false;
						}
					}

					UnicastPacket up = null;

					up = (UnicastPacket) E2EComm.receive(receiveSocket, timeout);

					received = up.getBytePayload();
					Object packet = E2EComm.deserialize(received);
					if (packet instanceof JPEGPacket) {
						isJpeg = true;
						short oldCount = count;
						count = ((JPEGPacket) packet).getId();
						short diff = (short) (count - oldCount);
						if (diff < 0)
							diff *= -1;
						if (diff > 1)
							csjf.setLocalQ((byte) diff);
						canvas.put(((JPEGPacket) packet).getData());
						if ( RampEntryPoint.isLogging() ) {
							loosed.addElement((byte) (diff - 1));
							jpegQ.addElement(((JPEGPacket) packet).getQuality());
							post = System.currentTimeMillis();
							float oldElapsed = elapsed;
							elapsed = post - pre;
							int payloadSize = up.getBytePayload().length;
							// System.out.println("CallReceiver payloadSize="+payloadSize);
							int packetSize = E2EComm.objectSizePacket(up);
							// System.out.println("CallReceiver packetSize="+packetSize);
							intervals.addElement(elapsed);
							payloads.addElement(payloadSize);
							packets.addElement(packetSize);
							jitter = Math.abs(elapsed - oldElapsed);
							jitters.addElement(jitter);
							dif.addElement(elapsed - oldElapsed);
						}
					} else if (packet instanceof InfoPacket) {
						csjf.setRemoteQ(((InfoPacket) packet).getInfo());
					}

				} catch (SocketTimeoutException ste) {
					// throw ste;
					System.out.println("CallReceiver timeout");
					csjf.closeCall();
				} catch (SocketException se) {
					se.printStackTrace();
					receiveSocket = E2EComm.bindPreReceive(clientPort, sendProtocol);
				} catch (EOFException eofe) {
					System.out.println("CallReceiver EOF");
					csjf.closeCall();
					// e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} while (received.length > 0 && csjf.isCallActive()); // until End Of Stream
			/*
			 * } catch(SocketTimeoutException ste){ System.out.println("CallReceiver timeout"); }
			 */
			canvas.halt();

			receiveSocket.close();
			System.out.println("CallReceiver FINISHED");
		} else {
			System.out.println("FrameData not received");
		}
		if ( RampEntryPoint.isLogging() ) {
			float count = 0;
			int i;
			FileWriter file = new FileWriter("./temp/stat.csv");
			file.write("delay,payload,package,jitter,delay_variation,loosed,jpeg_quality\n");
			for (i = 0; i < intervals.size(); i++) {
				count += intervals.elementAt(i);
				file.write(intervals.elementAt(i) + "," + payloads.elementAt(i) + "," + packets.elementAt(i) + "," + jitters.elementAt(i) + "," + dif.elementAt(i) + "," + loosed.elementAt(i) + ","
						+ jpegQ.elementAt(i) + "\n");
			}
			file.close();

			float totElapsed = count;
			float average = totElapsed / (float) i;
			count = 0;
			for (i = 0; i < intervals.size(); i++) {
				count += (intervals.elementAt(i) - average) * (intervals.elementAt(i) - average);
			}
			double stdDeviation = Math.sqrt(count / (double) i);
			count = 0;
			for (i = 0; i < payloads.size(); i++) {
				count += payloads.elementAt(i);
			}
			int byteAmountPayload = (int) count;
			count = 0;
			for (i = 0; i < packets.size(); i++) {
				count += packets.elementAt(i);
			}
			int byteAmountPackets = (int) count;
			float bitratePayload = (byteAmountPayload * 8) / (totElapsed / 1000) / 1024; // kbit/s
			float bitratePackets = (byteAmountPackets * 8) / (totElapsed / 1000) / 1024; // kbit/s
			System.out.println("\n\nCallClient \n" + "totElapsed=" + totElapsed / 1000 + " (s)   \n" + "average=" + average + " (ms)   \n" + "stdDeviation=" + stdDeviation + " (ms)   \n"
					+ "byteAmountPayload=" + byteAmountPayload / 1024 + " (kbyte)   \n" + "byteAmountPackets=" + byteAmountPackets / 1024 + " (kbyte)    \n" + "bitratePayload=" + bitratePayload
					+ " (kbit/s)    \n" + "bitratePackets=" + bitratePackets + " (kbit/s)    \n");
		}
	}

	private class Canvas {
		private CanvasFrame cf = null;
		private JPEGBufferDecoder dec;

		public Canvas(FrameData fd) {
			cf = new CanvasFrame("Remote Webcam", fd.getDefaultGamma() / fd.getGamma());
			cf.addWindowListener(new WindowListener() {

				@Override
				public void windowOpened(WindowEvent e) {
				}

				@Override
				public void windowIconified(WindowEvent e) {
				}

				@Override
				public void windowDeiconified(WindowEvent e) {
				}

				@Override
				public void windowDeactivated(WindowEvent e) {
				}

				@Override
				public void windowClosing(WindowEvent e) {
					csjf.closeCall();
				}

				@Override
				public void windowClosed(WindowEvent e) {
					csjf.closeCall();
				}

				@Override
				public void windowActivated(WindowEvent e) {
				}
			});
			dec = new JPEGBufferDecoder();
		}

		public int put(byte[] src) {
			BufferedImage img = dec.decode(src);
			IplImage iplImg = IplImage.createFrom(img);
			cf.showImage(iplImg);
			return src.length;
		}

		public void halt() {
			if (csjf.isCallActive())
				csjf.closeCall();
			cf.dispose();
		}
	}
}

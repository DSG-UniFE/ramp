package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.application.jpeg.FrameData;
import it.unibo.deis.lia.ramp.service.application.jpeg.InfoPacket;
import it.unibo.deis.lia.ramp.service.application.jpeg.JPEGBufferEncoder;
import it.unibo.deis.lia.ramp.service.application.jpeg.JPEGPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import com.googlecode.javacv.FFmpegFrameGrabber;
import com.googlecode.javacv.FrameGrabber;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

public class CallService extends Thread {
	private boolean open = true;

	private int protocol = E2EComm.UDP;
	// private int protocol = E2EComm.TCP;
	private short timeoutConnect = 500; // ms

	private int bitrate; // kbits/s (used only for raw-UDP)

	private int webcam;
	private byte imgQuality = 15;
	private final byte MIN_IQ = 10;
	private final byte MAX_IQ = 30;
	private final int MAX_IQ_C = 20;
	private String audioDev;
	private String sType = "voice";

	private final BoundReceiveSocket localServiceSocket;
	private static CallService callSender = null;

	private static CallServiceJFrame csjf;

	public static synchronized CallService getInstance() {
		try {
			if (callSender == null) {
				callSender = new CallService();
				callSender.start();
			}
			csjf.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return callSender;
	}

	private CallService() throws Exception {
		localServiceSocket = E2EComm.bindPreReceive(protocol);
		ServiceManager.getInstance(false).registerService("Call", localServiceSocket.getLocalPort(), protocol, sType);
		bitrate = 860;

		if (RampEntryPoint.os.startsWith("linux")) {
			webcam = 0;
			audioDev = "default device";
			try {
				Process pId = Runtime.getRuntime().exec("id -ru");
				BufferedReader brId = new BufferedReader(new InputStreamReader(pId.getInputStream()));
				String lineId;
				lineId = brId.readLine();
				if (lineId.equals("0")) {
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (RampEntryPoint.os.startsWith("mac")) {
			// webcam="webcam ???";
			webcam = 0;
			audioDev = "default device";
		} else if (RampEntryPoint.os.startsWith("windows")) {
			// webcam="Creative WebCam (WDM)";
			webcam = 0;
			audioDev = "default device";
			// webcam="Logitech QuickCam Communicate STX";
		} else {
			webcam = 0;
		}

		csjf = new CallServiceJFrame(this);
	}

	public int getServicePort() {
		return localServiceSocket.getLocalPort();
	}

	public int getBitrate() {
		System.out.println("CallService (CallSender) getBitrate: " + bitrate);
		return bitrate;
	}

	public void setBitrate(int bitrate) {
		System.out.println("CallService (CallSender) setBitrate: " + bitrate);
		this.bitrate = bitrate;
	}

	public short getConnectTimeout() {
		return timeoutConnect;
	}

	public void setTimeoutConnect(short timeoutConnect) {
		this.timeoutConnect = timeoutConnect;
	}

	public String getWebcam() {
		System.out.println("CallService (CallSender) getWebcam: " + webcam);
		return "" + webcam;
	}

	public void setWebcam(String webcam) {
		System.out.println("CallService (CallSender) setWebcam: " + webcam);
		this.webcam = Integer.parseInt(webcam);
	}

	public String getImgQuality() {
		return "" + imgQuality;
	}

	public void setImgQuality(String imgQuality) {
		this.imgQuality = Byte.parseByte(imgQuality);
	}

	public String getAudioDev() {
		return audioDev;
	}

	public void setAudioDev(String audioDev) {
		this.audioDev = audioDev;
	}

	private void changeRegistration() {
		ServiceManager.getInstance(false).removeService("Call");
		ServiceManager.getInstance(false).registerService("Call", localServiceSocket.getLocalPort(), protocol, sType);
	}

	public String getsType() {
		return sType;
	}

	public void setsType(String sType) {
		this.sType = sType;
		changeRegistration();
	}

	public void stopService() {
		open = false;
		try {
			localServiceSocket.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		ServiceManager.getInstance(false).removeService("Call");
		callSender = null;
	}

	@Override
	public void run() {
		try {
			System.out.println("CallService (CallSender) START");
			while (open) {
				try {
					GenericPacket gp = E2EComm.receive(localServiceSocket, 5 * 1000);
					System.out.println("CallService (CallSender) new request");
					new CallHandler(gp).start();
				} catch (SocketTimeoutException ste) {
					//
				} catch (SocketException se) {
					se.printStackTrace();
				}
			}
			localServiceSocket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("CallService (CallSender) FINISHED");
	}

	private class CallHandler extends Thread {
		private GenericPacket gp;

		private CallHandler(GenericPacket gp) {
			this.gp = gp;
		}

		@Override
		public void run() {
			try {

				// check packet type
				if (gp instanceof UnicastPacket) {
					// check payload
					UnicastPacket up = (UnicastPacket) gp;
					Object payload = E2EComm.deserialize(up.getBytePayload());

					if (payload instanceof StreamRequest) {
						System.out.println("CallService (CallSender) CallRequest");
						CallRequest request = (CallRequest) payload;
						String streamName = request.getStreamName();
						String[] newDest = E2EComm.ipReverse(up.getSource());
						int newDestNodeId = up.getSourceNodeId();

						System.out.println("Client port= " + request.getClientPort());

						if (streamName.equals("call")) {

							int sendProtocol = E2EComm.UDP;

							int width = 320, height = 240;

							Grabber grabber = new Grabber(webcam, width, height, imgQuality);

							try {

								FrameData fd = grabber.getFramedata();
								E2EComm.sendUnicast(newDest, newDestNodeId, request.getClientPort(), sendProtocol, false, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD,
										GenericPacket.UNUSED_FIELD, timeoutConnect, E2EComm.serialize(fd));
							} catch (Exception e1) {
								e1.printStackTrace();
							}

							// start receiving to obtain double channel
							int rsport = request.getMyServicePort();
							if (rsport > 0)
								csjf.emulateMouseDoubleClickedSignal(rsport);

							// send the stream
							int count = 0;
							try {
								while (csjf.isCallActive()) {

									if (csjf.getRemoteQ() > 0) {
										imgQuality -= csjf.getRemoteQ();
										if (imgQuality < MIN_IQ)
											imgQuality = MIN_IQ;
										grabber.setQuality(imgQuality);
										csjf.setRemoteQ((byte) 0);
										count -= csjf.getRemoteQ();
										if (count < 0)
											count = 0;
									} else {
										if (count >= MAX_IQ_C) {
											imgQuality++;
											if (imgQuality > MAX_IQ)
												imgQuality = MAX_IQ;
											grabber.setQuality(imgQuality);
											count = 0;
										} else
											count++;
									}
									if (csjf.getLocalQ() > 0) {
										InfoPacket ip = new InfoPacket(csjf.getLocalQ());
										E2EComm.sendUnicast(newDest, newDestNodeId, request.getClientPort(), sendProtocol, false, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD,
												GenericPacket.UNUSED_FIELD, timeoutConnect, E2EComm.serialize(ip));
										csjf.setLocalQ((byte) 0);
									}

									JPEGPacket imgPacket = new JPEGPacket(grabber.get(),imgQuality);
									E2EComm.sendUnicast(newDest, newDestNodeId, request.getClientPort(), sendProtocol, false, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD,
											GenericPacket.UNUSED_FIELD, timeoutConnect, E2EComm.serialize(imgPacket));
								}

							} catch (Exception e) {
								System.out.println("CallService webcam finished");
							}
							grabber.halt();
							// send End Of Stream (packet with 0 bytes in the payload)
							try {
								E2EComm.sendUnicast(newDest, newDestNodeId, request.getClientPort(), sendProtocol, false, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD,
										GenericPacket.UNUSED_FIELD, timeoutConnect, // millis
										new byte[0]);
							} catch (Exception e) {
								e.printStackTrace();
							}

							System.out.println("CallService FINISHED StreamRequest");
						} else {
							System.out.println("Warning: streamName is not \"call\": " + streamName);
						}

					} else {
						// received payload is not StreamRequest: do nothing...
						System.out.println("CallService wrong payload: " + payload);
					}
				} else {
					// received packet is not UnicastPacket: do nothing...
					System.out.println("CallService wrong packet: " + gp.getClass().getName());
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		private class Grabber {
			private FrameGrabber fg = null;
			private int width, height;
			private JPEGBufferEncoder enc;

			public Grabber(int arg0, int width, int height, int quality) {
				this.width = width;
				this.height = height;
				try {
					fg = FFmpegFrameGrabber.createDefault(arg0);
					fg.setImageWidth(this.width);
					fg.setImageHeight(this.height);
					fg.start();
					BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
					enc = new JPEGBufferEncoder(image, quality);
				} catch (Exception e) {
					System.out.println("error grabber constructor");
					e.printStackTrace();
				}
			}

			public FrameData getFramedata() {
				return new FrameData(fg);
			}

			public byte[] get() throws com.googlecode.javacv.FrameGrabber.Exception {
				IplImage iplImage = fg.grab();
				if (iplImage == null)
					return null;
				return enc.encode(iplImage.getBufferedImage());
			}

			public void setQuality(int quality) {
				enc.setQuality(quality);
			}

			public void halt() {
				try {
					enc.close();
					fg.stop();
					if (csjf.isCallActive())
						csjf.closeCall();
				} catch (Exception e) {
					System.out.println("error: unable to hald grabber");
					e.printStackTrace();
				}
			}
		}
	}
}

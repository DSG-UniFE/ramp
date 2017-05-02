package it.unibo.deis.lia.ramp.service.application.jpeg;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.sun.image.codec.jpeg.ImageFormatException;
import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageEncoder;

public class JPEGBufferEncoder {
	private JPEGImageEncoder encoder;
	private ByteArrayOutputStream outStream;
	private BufferedOutputStream bufOutStream;

	public JPEGBufferEncoder(BufferedImage img, int quality) {
		outStream = new ByteArrayOutputStream();
		bufOutStream = new BufferedOutputStream(outStream);
		encoder = JPEGCodec.createJPEGEncoder(bufOutStream);
		JPEGEncodeParam param = encoder.getDefaultJPEGEncodeParam(img);
		quality = Math.max(0, Math.min(quality, 100));
		param.setQuality((float) quality / 100.0f, false);
		encoder.setJPEGEncodeParam(param);
	}

	public byte[] encode(BufferedImage input) {
		outStream.reset();
		try {
			encoder.encode(input);
		} catch (ImageFormatException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return outStream.toByteArray();
	}

	public void setQuality(int quality) {
		quality = Math.max(0, Math.min(quality, 100));
		JPEGEncodeParam param = encoder.getJPEGEncodeParam();
		param.setQuality((float) quality / 100.0f, false);
		encoder.setJPEGEncodeParam(param);
	}

	public void close() {
		try {
			bufOutStream.close();
			outStream.close();
		} catch (IOException e) {
			System.out.println("Error: close()");
			e.printStackTrace();
		}
	}
}

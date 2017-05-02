package it.unibo.deis.lia.ramp.service.application.jpeg;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class JPEGBufferDecoder
{
	private ImageReader imageReader;

	public JPEGBufferDecoder()
	{
		Iterator<?> readers = ImageIO.getImageReadersByFormatName("jpg");
		imageReader = (ImageReader) readers.next();

	}

	public BufferedImage decode(byte[] input)
	{
		try
		{
			ByteArrayInputStream bais = new ByteArrayInputStream(input);
			Object objSource = bais;
			ImageInputStream iis = ImageIO.createImageInputStream(objSource);
			imageReader.setInput(iis, true);
			ImageReadParam param = imageReader.getDefaultReadParam();
			Image image = imageReader.read(0, param);
			BufferedImage output = new BufferedImage(image.getWidth(null),
					image.getHeight(null), BufferedImage.TYPE_3BYTE_BGR);
			Graphics2D g2 = output.createGraphics();
			g2.drawImage(image, null, null);
			bais.close();
			iis.close();
			return output;
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public void close()
	{
		imageReader.dispose();
	}
}

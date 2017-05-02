package it.unibo.deis.lia.ramp.util;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;


public class ImageComparison {
	
//	Credit: https://rosettacode.org/wiki/Percentage_difference_between_images#Java
	public static double imageDifference(BufferedImage img1, BufferedImage img2) throws ImagesMismatchException {
	    int width1 = img1.getWidth(null);
	    int width2 = img2.getWidth(null);
	    int height1 = img1.getHeight(null);
	    int height2 = img2.getHeight(null);
	    if ((width1 != width2) || (height1 != height2)) {
	      System.err.println("ImageComparison error: Images dimensions mismatch");
	      GeneralUtils.appendLog("ImageComparison error: Images dimensions mismatch");
	      throw new ImagesMismatchException("ImageComparison error: Images dimensions mismatch");
	    }
	    long diff = 0;
	    for (int y = 0; y < height1; y++) {
	      for (int x = 0; x < width1; x++) {
	        int rgb1 = img1.getRGB(x, y);
	        int rgb2 = img2.getRGB(x, y);
	        int r1 = (rgb1 >> 16) & 0xff;
	        int g1 = (rgb1 >>  8) & 0xff;
	        int b1 = (rgb1      ) & 0xff;
	        int r2 = (rgb2 >> 16) & 0xff;
	        int g2 = (rgb2 >>  8) & 0xff;
	        int b2 = (rgb2      ) & 0xff;
	        diff += Math.abs(r1 - r2);
	        diff += Math.abs(g1 - g2);
	        diff += Math.abs(b1 - b2);
	      }
	    }
	    double n = width1 * height1 * 3;
	    double p = diff / n / 255.0;
	    
	    return (p * 100.0);
	}
	
	public static void main(String[] args) {
		try {
	      // the line that reads the image file
	      BufferedImage img1 = ImageIO.read(new File("/tmp/images/img1.jpg"));
	      BufferedImage img2 = ImageIO.read(new File("/tmp/images/img2.jpg"));
	      double result = imageDifference(img1, img2);
	      System.out.println("Difference: " +  new DecimalFormat("#.###").format(result) + "%");
	    }  catch (ImagesMismatchException e) {
		      e.printStackTrace();
	    } catch (IOException e) {
	    	// log the exception
	    	// re-throw if desired
		      e.printStackTrace();
	    }
		
	}

}

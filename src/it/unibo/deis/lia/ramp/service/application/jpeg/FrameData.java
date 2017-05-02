package it.unibo.deis.lia.ramp.service.application.jpeg;

import java.io.Serializable;

import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.FrameGrabber;

public class FrameData implements Serializable {

	private static final long serialVersionUID = 1L;
	private double defaultGamma,gamma;
	
	public FrameData(FrameGrabber fg) {
		setDefaultGamma(CanvasFrame.getDefaultGamma());
		setGamma(fg.getGamma());
	}

	public double getDefaultGamma() {
		return defaultGamma;
	}

	public void setDefaultGamma(double defaultGamma) {
		this.defaultGamma = defaultGamma;
	}

	public double getGamma() {
		return gamma;
	}

	public void setGamma(double gamma) {
		this.gamma = gamma;
	}
}

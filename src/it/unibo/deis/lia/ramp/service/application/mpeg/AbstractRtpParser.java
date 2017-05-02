/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application.mpeg;

/**
 *
 * @author useruser
 */
public abstract class AbstractRtpParser extends Thread{
    
    public abstract RTP getRtp() throws Exception;
    public abstract void stopRtpMpegParser();

    private boolean parsePayload = true;
    public boolean isParsePayload() {
		return parsePayload;
	}
	public void setParsePayload(boolean parsePayload) {
		this.parsePayload = parsePayload;
	}
	
}

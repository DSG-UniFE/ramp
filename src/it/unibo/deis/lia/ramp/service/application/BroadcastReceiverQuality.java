/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.service.application.mpeg.TSPacket;

/**
 *
 * @author root
 */
public class BroadcastReceiverQuality {
    private float currentQuality;
    private float nsdDropRate; 	// Non Self-Determining
    private float sdDropRate;	// Self-Determining
    //private String currentVideoFrame="";
    private byte currentVideoFrame = TSPacket.UNDEFINED;
    private int currentVideoPid=-1;
    private int currentAudioPid=-1;
    private boolean dropCurrentVideoFrame = false;

    public BroadcastReceiverQuality(float startingQuality) {
        //currentQuality = BroadcastService.MAX_QUALITY;
        currentQuality = startingQuality;
    }

    public int getCurrentAudioPid() {
        return currentAudioPid;
    }

    public void setCurrentAudioPid(int newAudioPid) {
        this.currentAudioPid = newAudioPid;
    }

    public float getCurrentQuality() {
        return currentQuality;
    }

    public void setCurrentQuality(float newQuality) {
        this.currentQuality = newQuality;
    }

    public byte getCurrentVideoFrame() {
        return currentVideoFrame;
    }

    public void setCurrentVideoFrame(byte newVideoFrame) {
        this.currentVideoFrame = newVideoFrame;
    }

    public int getCurrentVideoPid() {
        return currentVideoPid;
    }

    public void setCurrentVideoPid(int newVideoPid) {
        this.currentVideoPid = newVideoPid;
    }

    public float getSdDropRate() {
        return sdDropRate;
    }

    public void setSdDropRate(float newSdDropRate) {
        this.sdDropRate = newSdDropRate;
    }

    public float getNsdDropRate() {
        return nsdDropRate;
    }

    public void setNsdDropRate(float newNsdDropRate) {
        this.nsdDropRate = newNsdDropRate;
    }

    public boolean isDropCurrentVideoFrame() {
        return dropCurrentVideoFrame;
    }
    public void setDropCurrentVideoFrame(boolean dropCurrentVideoFrame) {
        this.dropCurrentVideoFrame = dropCurrentVideoFrame;
    }


    @Override
    public String toString() {
        String res = "";
        res += "currentQuality="+currentQuality;
        res += " pbDropRate="+nsdDropRate;
        res += " iDropRate="+sdDropRate;
        res += " dropCurrentVideoFrame="+dropCurrentVideoFrame;
        return res;
    }
    
}

package it.unibo.deis.lia.ramp.core.internode;

import java.io.Serializable;

/**
 * @author Alessandro Dolci
 */
public class ApplicationRequirements implements Serializable {

    private static final long serialVersionUID = -8841892649004388194L;
    public static final int UNUSED_FIELD = -1;

    private ApplicationType applicationType;
    private int bitrate; // kb/s, used for streams
    private int trafficAmount; // KB, used for files
    private int secondsToStart;
    private int duration; // seconds

    public ApplicationRequirements(ApplicationType applicationType, int bitrate,
                                   int trafficAmount, int secondsToStart, int duration) {
        this.applicationType = applicationType;
        this.bitrate = bitrate;
        this.trafficAmount = trafficAmount;
        this.secondsToStart = secondsToStart;
        this.duration = duration;
    }

    public ApplicationType getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(ApplicationType applicationType) {
        this.applicationType = applicationType;
    }

    public int getBitrate() {
        return bitrate;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    public int getTrafficAmount() {
        return trafficAmount;
    }

    public void setTrafficAmount(int trafficAmount) {
        this.trafficAmount = trafficAmount;
    }

    public int getSecondsToStart() {
        return secondsToStart;
    }

    public void setSecondsToStart(int secondsToStart) {
        this.secondsToStart = secondsToStart;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String toString() {
        String result = "";
        result += "Application Type: " + applicationType.toString() + "\n";
        result += "Bitrate: " + bitrate + "kb/s\n";
        result += "Traffic Amount: " + trafficAmount + "kb\n";
        result += "Seconds to Start: " + secondsToStart + "s\n";
        result += "Duration: " + duration + "s";

        return result;
    }

    public enum ApplicationType {
        DEFAULT,
        FILE_TRANSFER,
        AUDIO_STREAM,
        VIDEO_STREAM
    }


}

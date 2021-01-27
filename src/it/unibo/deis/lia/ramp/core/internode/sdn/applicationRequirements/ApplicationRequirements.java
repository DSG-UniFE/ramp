package it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements;

import java.io.Serializable;

/**
 * @author Alessandro Dolci
 */
public class ApplicationRequirements implements Serializable {

    private static final long serialVersionUID = -8841892649004388194L;
    public static final int UNUSED_FIELD = -1;

    private TrafficType trafficType;
    /**
     *  Kb/s, used for streams
     */
    private int bitrate;

    /**
     * Kb, used for files
     */
    private int trafficAmount;
    private int secondsToStart;

    /**
     * Seconds
     */
    private int duration;

    public ApplicationRequirements(TrafficType trafficType, int bitrate,
                                   int trafficAmount, int secondsToStart, int duration) {
        this.trafficType = trafficType;
        this.bitrate = bitrate;
        this.trafficAmount = trafficAmount;
        this.secondsToStart = secondsToStart;
        this.duration = duration;
    }

    public TrafficType getTrafficType() {
        return trafficType;
    }

    public void setTrafficType(TrafficType trafficType) {
        this.trafficType = trafficType;
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
        result += "Application Type: " + trafficType.toString() + "\n";
        result += "Bitrate: " + bitrate + "Kb/s\n";
        result += "Traffic Amount: " + trafficAmount + "Kb\n";
        result += "Seconds to Start: " + secondsToStart + "s\n";
        result += "Duration: " + duration + "s";

        return result;
    }
}

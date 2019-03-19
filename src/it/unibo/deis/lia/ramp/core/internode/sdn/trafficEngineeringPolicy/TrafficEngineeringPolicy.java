package it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy;

/**
 * @author Alessandro Dolci
 */
public enum TrafficEngineeringPolicy {
    REROUTING,
    SINGLE_FLOW,
    QUEUES,
    TRAFFIC_SHAPING,
    MULTICASTING,
    OS_ROUTING,
    NO_FLOW_POLICY
}
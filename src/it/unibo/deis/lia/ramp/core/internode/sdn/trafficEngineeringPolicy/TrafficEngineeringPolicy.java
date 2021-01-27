package it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public enum TrafficEngineeringPolicy {
    SINGLE_FLOW,
    QUEUES, //multiple flows - single priority
    TRAFFIC_SHAPING,  //multiple flows - multiple priorities
    NO_FLOW_POLICY
}
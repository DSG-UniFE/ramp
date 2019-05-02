package it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public enum MessageType {
    JOIN_SERVICE,
    LEAVE_SERVICE,
    PATH_REQUEST,
    PATH_RESPONSE,
    TOPOLOGY_GRAPH_REQUEST,
    TOPOLOGY_GRAPH_RESPONSE,
    TOPOLOGY_UPDATE,
    PRIORITY_VALUE_REQUEST,
    MULTICAST_REQUEST,
    MULTICAST_CONTROL,
    TRAFFIC_ENGINEERING_POLICY_UPDATE,
    ROUTING_POLICY_UPDATE,
    DEFAULT_FLOW_PATHS_UPDATE,
    FLOW_PRIORITIES_UPDATE,
    OS_ROUTING_REQUEST,
    OS_ROUTING_RESPONSE,
    OS_ROUTING_ADD_ROUTE,
    OS_ROUTING_DELETE_ROUTE,
    OS_ROUTING_ACK,
    OS_ROUTING_ABORT,
    DATA_PLANE_ADD_DATA_TYPE,
    DATA_PLANE_REMOVE_DATA_TYPE,
    DATA_PLANE_DATA_TYPE_ACK,
    DATA_PLANE_DATA_TYPE_ABORT,
    DATA_PLANE_ADD_RULE_FILE, // This is used to send a user defined DataPlaneRule .class file
    DATA_PLANE_REMOVE_RULE_FILE, // This is used to remove a user defined DataPlaneRule .class file
    DATA_PLANE_ADD_RULE, // This is used to set in the database a specific DataPlaneRule for a specific DataType
    DATA_PLANE_REMOVE_RULE, // This is used to remove from the database a specific DataPlaneRule for a specific DataType
    DATA_PLANE_RULE_ACK,
    DATA_PLANE_RULE_ABORT
}

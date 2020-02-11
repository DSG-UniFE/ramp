# RAMP Multi-LANE SDN Control Plane

<p align="left">
  <img width="100" src="https://github.com/DSG-UniFE/ramp/blob/master/docs/images/RAMP_Multi-LANE.png?raw=true" alt="RAMP Multi-LANE Logo"/>
</p>

The RAMP Multi-LANE SDN control plane is based on a request-response approach, where the actors are the SDN Controller and one or mode SDN Control Agents (CAs).

The Control Plane is composed by several protocols that can be grouped in three categories
* Knowledge protocols for collecting information about the current network topology and the current status of the Control Agent nodes.
* Communication protocols for supporting the MLR communication between Control Agents based on the Controller knowledge-base.
* Advanced data plane protocols for dynamically setting at runtime data plane rules to be applied for specific kind of packets traversing the network during an overlay network level communication.

## Knowledge protocols
### Join protocol
Initiated by a CA that wants to become part of an existing RAMP Multi-LANE SDN network. The control message used is:
* `JOIN_SDN`: containing CA id and its network resource status. Controller adds the CA to topology database.

When the Controller receives the JOIN_SDN message it adds the CA to the network it manages by updating a data structure representing the current topology with the information provided by the CA. The topology data structure is periodically updated with the Update Topology Protocol started by each CA in order to reflect the dynamicity of the network

### Leave protocol
Initiated by a CA that wants to leave the RAMP Multi-LANE SDN network. The control message used is:
* `LEAVE_SDN`: containing CA id. Controller removes the CA from topology database.

### Update Topology protocol
Initiated by each CA that periodically sent to the Controller its neighbourhood status. The control message used is:
* `TOPOLOGY_UPDATE`: containing CA id and its neighbours list. Controller updates topology database.

## Communication protocols
### Update Routing Policy protocol
Initiated by the Controller. It let to enable a global routing policy that all the CAs must use in all the overlay network level communications. The control message used is:
* `ROUTING_POLICY_UPDATE`: containing the name of the routing policy to use.

The current routing policies supported are:
* `NO_ROUTING_POLICY`: all packets will use the default RAMP Dynamic Source Routing.
* `REROUTING`
* `MULTICASTING`

### Update Traffic Engineering Policy protocol
Initiated by the Controller. It let to enable a global priority-based traffic engineering policy that all the CAs must use in all the overlay network level communications. The control message used is:
* `TRAFFIC_ENGINEERING_POLICY_UPDATE`: containing the name of the routing policy to use.

The current traffic engineering policies supported are:
* `NO_FLOW_POLICY`
* `SINGLE_FLOW_SINGLE_PRIORITY`
* `MULTIPLE_FLOWS_SINGLE_PRIORITY`
* `MULTIPLE_FLOWS_MULTIPLE_PRIORITIES`

### Get Flow protocol
Initiated by a CA that wants to start a new unicast communication at overlay network routing level. The control messages used are:
* `PATH_REQUEST`: containing CA id, destination id, application requirements and path selection metric.
* `PATH_RESPONSE`: containing the flowId of the communication and the associated path.

When the controller receives the PATH_REQUEST message it generates a unique flow Id, computes the path towards the desired destination using the provided path selection metric and stores the application requirements data to support at runtime the communication according to them and manage the flow lifecycle. At the end the Controller sends back to the CA a PATH_RESPONSE message containing the flowId and the path to be used that will be set in the packet header during the communication.

### Get Path protocol
Initiated by a CA that wants to get the path an overlay network routing layer towards a specified destination. The control messages used are:
* `PATH_REQUEST`: containing CA id, destination id and path selection metric.
* `PATH_RESPONSE`: containing the resulting path.

### Fix Path Protocol
Initiated by a CA when the REROUTING Routing Policy is active. This protocol is triggered when a CA discovers that a pre-computed path associated to a flow is not working anymore due to a broken link towards the next hop. The control messages used are:
* `FIX_PATH_REQUEST`: containing flow source CA id, destination id and flow id.
* `FIX_PATH_PUSH_RESPONSE`: sent to the flow source CA, it contains the new flow path towards the destination.
* `PATH_RESPONSE`: containing the resulting path from the requesting node to the destination.

### Get Multicast Flow Protocol
Initiated by a CA that wants to start a new multicast communication at overlay network routing level. The control messages used are:
* `MULTICAST_PATH_REQUEST`: containing CA id, destination ids, application requirements and path selection metric.
* `MULTICAST_CONTROL`: sent to the spanning tree nodes to be able to duplicate packets to destinations nodes.
* `PATH_RESPONSE`: containing the flowId of the communication and the associated path.

### Get OS Route Protocol
Initiated by a CA that wants to start a new unicast communication at operating system routing level. The control messages used are:
* `OS_ROUTING_REQUEST`: containing CA id, destination id, application requirements and path selection metric.
* `OS_ROUTING_ADD_ROUTE`: sent by Controller, to configure the routing tables of all CAs of the computed path.
* `OS_ROUTING_ACK/OS_ROUTING_ABORT`: sent by each CA to Controller, reporting the routing table modification result.
* `OS_ROUTING_PUSH_RESPONSE`: sent to destination, containing the route id and the path towards the sender CA.
* `OS_ROUTING_RESPONSE`: sent to requesting CA, containing the route id and the path toward the destination CA.

The principle followed by this protocol is the same seen in the Get Flow protocol. The only differences sit in the actions taken by the Controller to enable the communication, in fact in addition to the path computation based on the requesting node application and routing requirements it also modifies all the nodes routing tables, that physically belongs to one or more different sub-networks, in order to make the source and the destination node visible the one with the other at the operating system level. When the CA will start the communication it only needs to know the destination node IP address that is provided by the Controller at the end of the protocol with the OS_ROUTING_RESPONSE message.

### OS Route Update Priority protocol
Initiated by a CA that wants to update the priority value of an existing os route so that the os routing should be preferred or not to the overlay network one. The control messages used are:
* `OS_ROUTING_UPDATE_PRIORITY_REQUEST`: containing CA id, route id and the preferred priority value.
* `OS_ROUTING_PRIORITY_UPDATE`: sent to sender and destination CAs, containing the route id and the preferred priority value.
* `OS_ROUTING_ACK/OS_ROUTING_ABORT`: sent by each CA to Controller, reporting the priority update operation result.
* `OS_ROUTING_UPDATE_PRIORITY_RESPONSE`: sent to requesting CA, reporting the priority update request result.

## Advanced Data Plane Protocols
### Add Data Type protocol
Initiated by the Controller, it spreads across the network at runtime a new data type that can be used during the communication. The control messages used are:
* `DATA_PLANE_ADD_DATA_TYPE`: containing the data type class file.
* `DATA_PLANE_ACK/ DATA_PLANE_ABORT`: sent by each CA to Controller, reporting the data type installation result.

### Add Data Plane Rule protocol
Initiated by the Controller, it spreads across the network at runtime a new data plane rule that can be activated to enable very fine-grained routing policies, the control messages used are:
* `DATA_PLANE_ADD_RULE`: containing the data plane rule class file.
* `DATA_PLANE_ACK/ DATA_PLANE_ABORT`: sent by each CA to Controller, reporting the data plane rule installation result.

### Activate Data Plane Rule protocol
Initiated by the Controller, it activates an existing data plane rule for a specific existing data type for all CAs or a part of them. The control messages used are:
* `DATA_PLANE_ACTIVATE_RULE`: containing the data plane rule id and the data type id.
* `DATA_PLANE_ACK/ DATA_PLANE_ABORT`: sent by each CA to Controller, reporting the data plane rule activation result.

### Deactivate Data Plane Rule protocol
Initiated by the Controller, it deactivates an active data plane rule for a specific existing data type when is no longer required. The control messages used is:
* `DATA_PLANE_DEACTIVATE_RULE`: containing the data plane rule id and the data type id.
* `DATA_PLANE_ACK/ DATA_PLANE_ABORT`: sent by each CA to Controller, reporting the data plane rule deactivation result.



















# Real Ad-hoc Multi-hop Peer-to-peer (RAMP) Getting Started

## Run instructions

* Download the latest RampStandAloneClient_YYYY-MM-DD.zip contained in `ramp/deployment` folder.
* Extract the zip file in your desired location
* Open a terminal in the extracted folder RampStandAloneClient_YYYY-MM-DD

On Linux
* Run RAMP - using `./ramp.sh`

On Windows
* Run RAMP - using `ramp.bat`

The RAMP main window will appear meaning that the middleware is up and running.

<p align="center">
  <img src="https://github.com/DSG-UniFE/ramp/blob/master/deployment/images/RAMP_GUI.jpg?raw=true" alt="RAMP Main Window"/>
</p>

## RAMP Multi-LANE Setup Guide

<p align="left">
  <img width="100" src="https://github.com/DSG-UniFE/ramp/blob/master/deployment/images/RAMP_Multi-LANE.png?raw=true" alt="RAMP Multi-LANE Logo"/>
</p>

Multi-Layer Advanced Networking Environment (Multi-LANE) middleware, originally adopts a Multi Layer Routing (MLR) approach in conjunction with SDN to fully enhance the capabilities of heterogeneous Fog environments.

In this quick tutorial is shown how to use RAMP Multi-LANE in a topology of three nodes.

<p align="center">
  <img src="https://github.com/DSG-UniFE/ramp/blob/master/deployment/images/RAMP_TopologyExample.jpg?raw=true" alt="RAMP Topology Example"/>
</p>

Let's assume that you have a topology like the one showed in the above picture. What we are going to do is to configure RAMP Multi-LANE so that:
* The node 1 will act both as SDN Controller and SDN Client
* The node 2 will act as SDN Client
* The node 3 will act as SDN Client

### Configure the RAMP ID on each node

Once downloaded the the latest RampStandAloneClient_YYYY-MM-DD.zip contained in `ramp/deployment` folder on each node, before running the RAMP Middleware is important to set the right RAMP ID. To do so:
* Extract the zip file in your desired location
* Open the extracted folder and open with a text editor the file `resource/ramp.props` and set the right ID in the `nodeID` field

The above steps must be done for each node so for node 1 the `nodeID` value should be set to 1, for node 2 the value should be 2 and so on.

### Run the SDN Controller

Launch RAMP on node 1

<p align="center">
  <img src="https://github.com/DSG-UniFE/ramp/blob/master/deployment/images/RAMP_GUI.jpg?raw=true" alt="RAMP Main Window"/>
</p>

In order to launch the SDN Controller, in the Application section of the RAMP main window (top-left) select in the drop-down list the service called `SDNControllerService` and press the button `start selected service`. 

It's important to note that at the moment only one SDN Controller service is supported in a topology. This means that only one node can act as a SDN Controller.

<p align="center">
  <img src="https://github.com/DSG-UniFE/ramp/blob/master/deployment/images/RAMP_SDNControllerService.jpg?raw=true" alt="RAMP SDN Controller"/>
</p>

The picture above shows the SDNControllerService GUI, through it you can control the behaviour of the topology. In particular:
* In the `Traffic Engineering Policy` section you can set policy in case of messages having different priorities. If you don't want to use this feature select the value NO_FLOW_POLICY and click on the button Update Traffic Policy.
* In the `Routing Policy` section you can set the routing policy that all SDN Client must follow in order to perform the application-level routing. By setting NO_ROUTING_POLICY the routing inside the SDN will follow a Dynamic Source Routing strategy.
* In the `Active Clients` section you can get a list of all SDN Clients managed by this Controller.
* In the `Topology Graph` section you can get a visual representation of the topology.
* The other sections are related to highest level routing features that RAMP Multi-LANE offers.

### Run the SDN Client

For node 1 since RAMP is already running from the RAMP main window you only need to launch the SDN Client by selecting `SDNControllerClient` and press the button `start selected client` in the Applications section. 

It will ask you the root password used to make the OS Routing Mode working, such feature will create and manage several local ip tables. The source code of this feature is contained in the OSRoutingManager.java.

For node 2 and node 3 you need first run RAMP on each node and after that launch only the SDNControllerClient application.

<p align="center">
  <img src="https://github.com/DSG-UniFE/ramp/blob/master/deployment/images/RAMP_SDNControllerClient.jpg?raw=true" alt="RAMP SDN Client"/>
</p>

Now that all three nodes are running the SDN RAMP applications, in order to verify that the SDN Controller service is managing the three SDN Clients click on the `Get Active Clients` button in the SDNControllerService GUI on node 1. Also by clicking the `Get Topology Graph` button you can monitor the real-time visual representation of the topology.

The picture above shows the SDNControllerClient GUI, through it you can monitor the policies currently active in the topology and communicate with the other SDN Clients. In particular:
* In the `Traffic Engineering Policy` section you can see the active policy in case of messages having different priorities.
* In the `Routing Policy` section you can see the active routing policy set by the SDN Controller.

### Example of communication based on Flow
Assume that we want to send a message from node 1 to node 3 based on flow.

In the SDNControllerClient GUI on node 1:
* Select the protocol you want to use (TCP or UDP) and press the `Find Nodes` button in the Available SDN Controller Client Receivers section. You will see all the available clients.
* The `Destination Node ID` drop-down list will be automatically filled in the Flow ID section, select 3 and press the button `Get Flow ID`.
* In the Send Packet section in the right-side select 3 as `Destination ID`, select the flowID available in the `FlowID` drop-down list.
* Press the `Send Packet` button to send the packet.

In the SDNControllerClient GUI on node 3:
* To view the received message click on the `Update Log` button in the right-bottom.

### Example of communication based on OS Level Routing
Assume that we want to send a message from node 1 to node 3 based on OS Level Routing.

In the SDNControllerClient GUI on node 1:
* Switch the view by selecting the `OsRouting Mode` checkbox in the top-left.
* Select the protocol you want to use (TCP or UDP) and press the `Find Nodes` button in the Available SDN Controller Client Receivers section. You will see all the available clients.
* The `Destination Node ID` drop-down list will be automatically filled in the Route ID section, select 3 and press the button `Get Route ID`.
* In the Send Packet section in the right-side select 3 as `Destination ID`, select the RouteID available in the `RouteID` drop-down list.
* Press the `Send Packet` button to send the packet.

In the SDNControllerClient GUI on node 3:
* To view the received message click on the `Update Log` button in the right-bottom.

### Graceful RAMP shut-down
In order to avoid any kind of inconsistencies, it is recommended to follow these steps to shutdown RAMP.
* Close the SDNControllerClient and close the RAMP main window on node 3.
* Close the SDNControllerClient and close the RAMP main window on node 2.
* Close the SDNControllerClient, close the SDNControllerService and close the RAMP main window on node 1. 

In case of usage of OS Routing Level features if you don't follow the above steps you may have some left local routing table in your machine. Since they are ephemeral you just need to reboot the machine to get rid of them.














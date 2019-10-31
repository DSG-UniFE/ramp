![Alt text](./images/ramp_logo.png?raw=true "RAMP Logo") 
# Real Ad-hoc Multi-hop Peer-to-peer (RAMP) Project 

RAMP supports transparent management of multi-hop heterogeneous spontaneous networks performing middleware-layer routing (without the need of operating system routing support).

RAMP provides end-to-end communication primitives, such as sendUnicast, sendBroadcast and receive, and peer-to-peer service-oriented features, such as registerService and findServices.

## Scenario Example & Motivations

### Spontaneous Networking
To better point out the challenging environments targeted by RAMP, let us rapidly sketch a practical example of spontaneous network. Consider the realistic case of a group of students in a lecture hall carrying on mobile clients equipped with multiple heterogeneous communication interfaces (see figure below), e.g., laptops with IEEE 802.11 and Bluetooth, cell phones with UMTS and Bluetooth, and smart phones with UMTS, IEEE 802.11, and Bluetooth. Students can interact to share lesson notes via subgroups created in an impromptu way, by possibly participating to multiple subnets simultaneously.
The dynamicity of such a scenario pushes for distributed management; nodes should administrate their just created and mission-oriented subnet(s), by providing addresses with local scope and possibly exploiting the same address ranges in different subnets. Therefore, only nodes in the same subnets can directly identify and communicate each other. In addition, nodes can abruptly revoke shared resources with relatively high frequency, e.g., because of mobility.

<p align="center">
  <img src="http://lia.deis.unibo.it/Research/RAMP/images/ramp_scenario.jpg" alt="RAMP scenario"/>
</p>

### RAMP motivations
To leverage the adoption of heterogeneous spontaneous networking:
* RAMP supports both unicast and broadcast communication abstractions, in order to provide a simple and wide-accepted basis for any general-purpose application need;
* RAMP easily enables the registration/discovery/invocation of services dynamically and temporarily offered by spontaneous network peers;
* to facilitate use and leverage adoption, RAMP facilities for communication/service management are transparent (independent) with regard to low-level implementation details about i) how the spontaneous network has been created (single-hop instantiation and control), ii) which specific wireless technologies are employed (e.g., Bluetooth, WiFi in ad-hoc mode, and WiFi in infrastructured mode), and iii) which operating system runs at each participating node.

## Ramp Getting Started
A quick starting guide on how to use RAMP middleware in your environment is available [here](https://github.com/DSG-UniFE/ramp/tree/master/deployment).
This tutorial contains also an example of usage of the last RAMP extension called RAMP Multi-LANE.

## Android Client
RAMP users can access the services offered by client App, which is available [here](https://github.com/DSG-UniFE/ramp-android).
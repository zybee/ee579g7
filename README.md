# EE 579 Study of Channel Allocation in Wireless Networks
## Group 7
* Zainab Bhabhrawala
* Purva Deshpande
* Aloka Dixit

## Description
Our project is about evaluation of different channel allocation schemes in wireless networks.

We have designed and studied three different channel allocation schemes :

1. Distributed Scheme
2. Centralized Scheme
3. Hybrid Scheme


_Description about files:_

DISTRIBUTED:
 The code for this scheme has following files:

 Transmitter (RS_Dist_7D3E) : This source code for this entity is in src folder. This is the transmitter of the distributed system. It 
 mainly has two threads for transmission and receiveing of packets. It is also responsible for selecting the best channel 
 for communication.

 Receiver (RS_Dist_7E5B) : The source code is in the src folder. This is the recevier of the distributed system.The main function of this
 code is receiving the packets and testing the signal strength. If the signal strength is below threshold , it signals the
 transmitter to change the channel.

CENTRALISED:
 The code for this scheme has following files:

 Centralized_Tx_7D3E : The source code is in the src folder. This is the transmitter of the centralised system. It mainly functions with
 two threads, one for transmission and other for receiving. On channel deterioration, it changes the channel to the one decided 
 by the central controller.
 
 Centralized_Rx_7E5B : The source code is in the src folder. This is the receiver of the centralised system. The main function of this
 code is receiving the packets and testing the signal strength. If the signal strength is below threshold , it signals the
 transmitter to change the channel.
 
 Centralized_Cx : The source code is in the src folder. This is the controller for the centralised system. The function of this
 code is receiving the channel condition data from the transmitter, and then making a decision for selecting a channel and sending
 the decision back to the transmitter, that sent the channel change request.

HYBRID:
 The code for this scheme has following files:

 Hybrid_Tx_7D3E : The source code is in the src folder. This is the transmitter of the hybrid system. It mainly functions with two
 threads, one for transmission and other for receiving. In order to change the channel it needs the permission from the central 
 controller. But it decides on which channel to change to.
 
 Hybrid_Rx_7E5B : The source code is in the src folder. This is the receiver of the Hybrid system. The main function of this
 code is receiving the packets and testing the signal strength. If the signal strength is below threshold , it signals the
 transmitter to change the channel.
 
 Hybrid_Cx : The source code is in the src folder. This is the controller for the Hybrid system. The function of this
 code is receiving a permission request from the transmitter for changing the channel, and then allowing the transmitter to change
 the channel only after sending a permission to change the channel.

 *The set of transmitter receiver pairs are fixed.



Reference : 
http://www.sunspotworld.com
http://www.sunspotworld.com/docs/Blue/hostjavadoc/overview-summary.html
* The RadioStrength Demo provided with the sunSPOT sdk 
* SendDataDemo provided with the sunSPOT sdk
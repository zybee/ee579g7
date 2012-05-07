Sun SPOT Radio Signal Strength Meter Demo - Version 1.0

Author: Ron Goldman
June 22, 2006

A simple application for 2 SPOTs. Each SPOT broadcasts 5 packets per
second and listens for radio broadcasts from the other SPOT. The radio
signal strength of the packets received is displayed in the SPOT's LEDs.

The SPOT uses the LEDs to display its status as follows:

  LED 1:
     Red = missed an expected packet
     Green = received a packet

  LED 2-8:
     Blue = signal strength meter for RSSI of received packets
     Red  = transmit power level (in binary) 0 (min) to 32 (max)
     Green = channel number (in binary) 11 (min) to 26 (max)

The SPOT uses the switches to change/view the channel and power level:

  SW1 = channel select
  SW2 = power select 

Pushing down briefly on either switch displays the current power/channel
value in binary. Continuing to depress the switch will cause the value
to be incremented. Upon reaching the maximum allowed value, it will
wrap around to the minimum value. When you release the switch the new
value will be set.

To build and run the demo, on each SPOT do the following:

ant deploy run


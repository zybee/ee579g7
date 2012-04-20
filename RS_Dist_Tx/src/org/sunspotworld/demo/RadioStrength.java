/*
 * Copyright (c) 2006-2010 Sun Microsystems, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to 
 * deal in the Software without restriction, including without limitation the 
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or 
 * sell copies of the Software, and to permit persons to whom the Software is 
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package org.sunspotworld.demo;

import com.sun.spot.peripheral.Spot;
import com.sun.spot.peripheral.TimeoutException;
import com.sun.spot.peripheral.radio.IProprietaryRadio;
import com.sun.spot.peripheral.radio.IRadioPolicyManager;
import com.sun.spot.resources.Resources;
import com.sun.spot.resources.transducers.ILed;
import com.sun.spot.resources.transducers.ISwitch;
import com.sun.spot.resources.transducers.ILightSensor;
import com.sun.spot.resources.transducers.ITriColorLED;
import com.sun.spot.resources.transducers.LEDColor;
import com.sun.spot.resources.transducers.ITriColorLEDArray;
import com.sun.spot.io.j2me.radiogram.Radiogram;
import com.sun.spot.io.j2me.radiogram.RadiogramConnection;

import java.lang.Object;
import java.io.IOException;
import javax.microedition.io.Connector;
import javax.microedition.io.Datagram;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

/**
 * Routines to turn two SPOTs into radio signal strength meters.
 *
 * Application to listen for radio broadcasts from another SPOT and to
 * display the radio signal strength.
 *<p>
 * The SPOT uses the LEDs to display its status as follows:
 *<p>
 * LED 0:
 *<ul>
 *<li> Red = missed an expected packet
 *<li> Green = received a packet
 *</ul>
 * LED 1-7:
 *<ul>
 *<li> Blue = signal strength meter for RSSI of received packets
 *<li> Red  = transmit power level (in binary) 0 (min) to 32 (max)
 *<li> Green = channel number (in binary) 11 (min) to 26 (max)
 *</ul>
 *<p>
 * The SPOT uses the switches to change/view the channel and power level:
 *<ul>
 *<li> SW0 = channel select
 *<li> SW1 = power select 
 *</ul>
 *
 * Note: if the channel is changed then the SPOT will not receive any OTA commands.
 * In that case just reboot the SPOT to switch back to the default radio channel.
 *
 * @author Ron Goldman
 * date: June 15, 2006 
 */
public class RadioStrength extends MIDlet {

    private static final String VERSION = "1.0";
    private static final int INITIAL_CHANNEL_NUMBER = IProprietaryRadio.DEFAULT_CHANNEL;
    private static final short PAN_ID               = IRadioPolicyManager.DEFAULT_PAN_ID;
    private static final String BROADCAST_PORT      = "42";
    private static final int RADIO_TEST_PACKET      = 42;
    private static final int PROBE_PACKET           = 66;
    private static final int PROBE_REPLY_PACKET     = 67;
    private static final int CHANNEL_CHANGE_REQUEST = 99;
    private static final int CHANNEL_CHANGE_PACKET  = 100;
    
    private static final int PACKETS_PER_SECOND     = 5;
    private static final int PACKET_INTERVAL        = 1000 / PACKETS_PER_SECOND;
    private static final int BOOST_LED_THRESHOLD    = 600;
    private static final int MAX_BOOST_LED_THRESHOLD = 725;
    
    private ISwitch sw1 = (ISwitch)Resources.lookup(ISwitch.class, "SW1");
    private ISwitch sw2 = (ISwitch)Resources.lookup(ISwitch.class, "SW2");
    private ITriColorLEDArray leds = (ITriColorLEDArray)Resources.lookup(ITriColorLEDArray.class);
    private ITriColorLED statusLED = leds.getLED(0);
    private ILightSensor light = (ILightSensor)Resources.lookup(ILightSensor.class);

    private LEDColor red   = new LEDColor(50,0,0);
    private LEDColor green = new LEDColor(0,20,0);
    private LEDColor blue  = new LEDColor(0,0,50);
    private LEDColor white = new LEDColor(255,255,255);
    
    private int channel = INITIAL_CHANNEL_NUMBER;
    private int power = 32;                             // Start with max transmit power

    private boolean xmitDo = true;
    private boolean recvDo = true;
    private boolean ledsInUse = false;
    private boolean boostLEDs = false;
    private boolean maxBoostLEDs = false;
    
    private int rssi[] = {0, 0, 0};
    private int lqa[] = {0,0,0};
    private int availChannels[] = {24,25,26};
    
    private int lowThresholdRssi = -30;
    private int lowThresholdLQA = 40;
    private int requestToChange = 0;
    private int probe_flag = 0;
    private int goAhead = 0;
    
    /**
     * Return bright or dim red.
     *
     * @returns appropriately bright red LED settings
     */
    private LEDColor getRed() {
        return boostLEDs ? LEDColor.RED : red;
    }

    /**
     * Return bright or dim green.
     *
     * @returns appropriately bright green LED settings
     */
    private LEDColor getGreen() {
        return boostLEDs ? LEDColor.GREEN : green;
    }

    /**
     * Return bright or dim blue.
     *
     * @returns appropriately bright blue LED settings
     */
    private LEDColor getBlue() {
        return maxBoostLEDs ? white : boostLEDs ? LEDColor.BLUE : blue;
    }

    /**
     * Check if in really bright environment.
     *
     * @returns true if it's really bright, false if not so bright
     */
    private void checkLightSensor() {
        try {
            int val = light.getValue();
            boostLEDs = (val > BOOST_LED_THRESHOLD);
            maxBoostLEDs = (val > MAX_BOOST_LED_THRESHOLD);
        } catch (IOException ex) { }
    }
    
    /**
     * Pause for a specified time.
     *
     * @param time the number of milliseconds to pause
     */
    private void pause (long time) {
        try {
            Thread.currentThread().sleep(time);
        } catch (InterruptedException ex) { /* ignore */ }
    }
    

    /**
     * Initialize any needed variables.
     */
    private void initialize() { 
        checkLightSensor();
        statusLED.setColor(getRed());     // Red = not active
        statusLED.setOn();
        IRadioPolicyManager rpm = Spot.getInstance().getRadioPolicyManager();
        rpm.setChannelNumber(channel);
        rpm.setPanId(PAN_ID);
        rpm.setOutputPower(power - 32);
    }
    

    /**
     * Main application run loop.
     */
    private void run() {
        System.out.println("Radio Signal Strength Test (version " + VERSION + ")");
        System.out.println("Packet interval = " + PACKET_INTERVAL + " msec");
        
        new Thread() {
            public void run () {
                xmitLoop();
            }
        }.start();                      // spawn a thread to transmit packets
        new Thread() {
            public void run () {
                recvLoop();
            }
        }.start();                      // spawn a thread to receive packets
        respondToSwitches();            // this thread will handle User input via switches
    }

    /**
     * Display a number (base 2) in LEDs 1-7
     *
     * @param val the number to display
     * @param col the color to display in LEDs
     */
    private void displayNumber(int val, LEDColor col) {
        for (int i = 0, mask = 1; i < 7; i++, mask <<= 1) {
            leds.getLED(7-i).setColor(col);
            leds.getLED(7-i).setOn((val & mask) != 0);
        }
    }
    
    /**
     * Auxiliary routine to scale the brightness of the LED so it is more in 
     * keeping with how people perceive brightness.
     *
     * @param x the raw value to display
     * @param col the maximum LED brightness to use
     * @param perLed the maximum value to display
     * @returns the scaled brightness to actually display
     */
    private int lightValue (int x, int col, int perLed){
        if (x <= 0 || col <= 0) return 0;
        if (x >= perLed) return col;
        return  ( x * x * x * col ) /(perLed * perLed * perLed);
    }

    /**
     * Display a vU like level in LEDs 1-7
     *
     * @param val the level to display
     * @param max the maximum value expected
     * @param min the minimum value expected
     * @param col the color to display in LEDs
     */
    private void displayLevel(int val, int max, int min, LEDColor col) {
        int LEDS_TO_USE = 7;
        int MAX_LED = 7;
        int range = max - min + 1;
        int perLed = range / LEDS_TO_USE;
        int bucket = (val - min + 1) / perLed;
        int part = (val - min + 1) - bucket * perLed;
        for (int i = 0; i < LEDS_TO_USE; i++) {
            if (bucket > i) {
                leds.getLED(MAX_LED-i).setColor(col);
                leds.getLED(MAX_LED-i).setOn();
            } else if (bucket == i) {
                leds.getLED(MAX_LED-i).setRGB(lightValue(part, col.red(), perLed), lightValue(part, col.green(), perLed), lightValue(part, col.blue(), perLed));
                leds.getLED(MAX_LED-i).setOn();
            } else {
                leds.getLED(MAX_LED-i).setOff();
            }
        }
    }

    /**
     * Loop waiting for user to press a switch.
     *<p>
     * Since ISwitch.waitForChange() doesn't really block we can loop on both switches ourself.
     *<p>
     * Detect when either switch is pressed by displaying the current value.
     * After 1 second, if it is still pressed start cycling through values every 0.5 seconds.
     * After cycling through 4 new values speed up the cycle time to every 0.3 seconds.
     * When cycle reaches the max value minus one revert to slower cycle speed.
     * Ignore other switch transitions for now.
     *
     */
    private void respondToSwitches() {
        while (true) {
            pause(100);         // check every 0.1 seconds
            int cnt = 0;
            if (sw2.isClosed()) {
                ledsInUse = true;
                displayNumber(channel, getGreen());
                pause(1000);    // wait 1.0 second
                if (sw2.isClosed()) {
                    while (sw2.isClosed()) {
                        channel++;
                        if (channel > 24) { cnt = 0; }
                        if (channel > 26) { channel = 11; }
                        displayNumber(channel, getGreen());
                        cnt++;
                        pause(cnt < 5 ? 500 : 300);    // wait 0.5 second
                    }
                    Spot.getInstance().getRadioPolicyManager().setChannelNumber(channel);
                }
                pause(1000);    // wait 1.0 second
                displayNumber(0, blue);
            }
           /* if (sw2.isClosed()) {
                cnt = 0;
                ledsInUse = true;
                displayNumber(power, getRed());
                pause(1000);    // wait 1.0 second
                if (sw2.isClosed()) {
                    while (sw2.isClosed()) {
                        power++;
                        if (power > 30) { cnt = 0; }
                        if (power > 32) { power = 0; }
                        displayNumber(power, getRed());
                        cnt++;
                        pause(cnt < 5 ? 500 : 300);    // wait 0.5 second
                    }
                    Spot.getInstance().getRadioPolicyManager().setOutputPower(power - 32);
                }
                pause(1000);    // wait 1.0 second
                displayNumber(0, blue);
            }*/
            ledsInUse = false;
            checkLightSensor();
        }
    }

    /* Function to probe other channels and check the quality of that channel. 
     * Save the information locally
     */    
    private int probe() {
        
        RadiogramConnection txConn = null;
        
        channel = availChannels[0];
        Spot.getInstance().getRadioPolicyManager().setChannelNumber(channel);
        
        int selectedRssi = 0;
        int selectedChannel = 0;
        int selectedLQA = 0;
               
        synchronized(this){ //indicate that probing has started
            probe_flag = 1;
        }
        
        while(true) 
        {
            try 
            {   
                txConn = (RadiogramConnection)Connector.open("radiogram://broadcast:" + BROADCAST_PORT);
                txConn.setMaxBroadcastHops(1);      // don't want packets being rebroadcasted
                Datagram xdg = txConn.newDatagram(txConn.getMaximumLength());

                xdg.reset();
                xdg.writeByte(PROBE_PACKET);
                xdg.writeInt(power);
                txConn.send(xdg);

                //now that packet is sent, indicate to receiver thread to listen for a reply
                synchronized(this){
                    goAhead = 1;
                }
                
                ledsInUse = true;
                displayNumber(channel, getGreen());  
                //pause(PACKET_INTERVAL);//wait till little more than timeout period to receive probe reply
                pause(1000); 
                displayNumber(0, green);
                
                while(true)
                {
                    synchronized(this){
                        if(goAhead == 0)
                            break;
                    }
                    pause(1000);
                }
                
                if(channel == 26)
                    break;
                
                channel++;
                Spot.getInstance().getRadioPolicyManager().setChannelNumber(channel);
            }
            catch (IOException e){
            }
        }
                  
        synchronized(this){
            probe_flag = 0;
        }
        
        selectedLQA = lqa[0];
        selectedRssi = rssi[0];
        selectedChannel = availChannels[0];
        for(int j = 0; j < 3; j++)
        {           
            if(rssi[j] > selectedRssi || lqa[j] > selectedLQA)
            {
                selectedRssi = rssi[j];
                selectedChannel = availChannels[j];
                selectedLQA = lqa[j];
            }
        }
        
        return selectedChannel;           
    }
    
    /**
     * Loop to continually transmit packets using current power level & channel setting.
     */
    private void xmitLoop () {
        ILed led = Spot.getInstance().getGreenLed();
        RadiogramConnection txConn = null;
        xmitDo = true;
        
        int changeToChannel = 0;
        int request = 0;
        
        while (xmitDo) {
            try {
                txConn = null;
                txConn = (RadiogramConnection)Connector.open("radiogram://0014.4F01.0000.7E5B:" + BROADCAST_PORT);
                txConn.setMaxBroadcastHops(1);      // don't want packets being rebroadcasted
                Datagram xdg = txConn.newDatagram(txConn.getMaximumLength());
                long count = 0;
                boolean ledOn = false;               
                                                                
                while (xmitDo) {
                    led.setOn();
                    long nextTime = System.currentTimeMillis() + PACKET_INTERVAL;
                    count++;
                    if (count >= Long.MAX_VALUE) { count = 0; }
                    xdg.reset();
                    xdg.writeByte(RADIO_TEST_PACKET);
                    xdg.writeInt(power);
                    xdg.writeLong(count);
                    txConn.send(xdg);
                    led.setOff();
                    long delay = (nextTime - System.currentTimeMillis()) - 2;
                    if (delay > 0) {
                        pause(delay);
                    }
                    
                    synchronized(this) {
                        request = requestToChange;
                        requestToChange = 0;
                    }
                    
                    if(request == 1)
                    {
                        ledsInUse = true;
                        displayNumber(31, getGreen()); 
                        pause(500);
                        displayNumber(31, getRed()); 
                        pause(500);
                        displayNumber(31, getBlue()); 
                        pause(500);
                        displayNumber(0, getBlue()); 
                        
                        int presentChannel = channel;
                        changeToChannel = probe();
                        synchronized(this){
                            goAhead = 0;
                        }
                        //send message to change on original channel frequency
                        channel = presentChannel;
                        Spot.getInstance().getRadioPolicyManager().setChannelNumber(channel);

                        xdg.reset();
                        xdg.writeByte(CHANNEL_CHANGE_PACKET);
                        xdg.writeInt(changeToChannel);
                        txConn.send(xdg);
                        channel = changeToChannel;
                        Spot.getInstance().getRadioPolicyManager().setChannelNumber(channel);

                        break;
                    }
                }
            } catch (IOException ex) {
                // ignore
            } finally {
                if (txConn != null) {
                    try {
                        txConn.close();
                    } catch (IOException ex) { }
                }
            }
        }
    }
    
    /**
     * Loop to receive packets and display their RSSI level in the LEDs
     */
    private void recvLoop () {
        ILed led = Spot.getInstance().getRedLed();
        RadiogramConnection rcvConn = null;
        recvDo = true;
        int maxQ = -100;
        int minQ = 100;
        int q = 0;
        int nothing = 0;
        int i = -1;
        int probeValue = 0;
        while (recvDo) {
            try {
                rcvConn = (RadiogramConnection)Connector.open("radiogram://:" + BROADCAST_PORT);
                rcvConn.setTimeout(PACKET_INTERVAL - 5);
                Radiogram rdg = (Radiogram)rcvConn.newDatagram(rcvConn.getMaximumLength());
                long count = 0;
                boolean ledOn = false;
                while (recvDo) {
                    try {
                        rdg.reset();
                        rcvConn.receive(rdg);           // listen for a packet
                        byte packetType = rdg.readByte();
                        if (packetType == CHANNEL_CHANGE_REQUEST) {
                            led.setOn();
                            statusLED.setColor(getGreen());
                            statusLED.setOn();
                            pause(1000);
                            statusLED.setOff();
                            int recvRssi = rdg.readInt();
                            q = rdg.getRssi();

                            synchronized(this){
                                requestToChange = 1;
                            }
                            
                            nothing = 0;
                            led.setOff();
                            
                            while(true)
                            {
                                synchronized(this){
                                    if(goAhead == 1)
                                        break;
                                }
                                pause(1000);
                            }
                            
                        }
                        if (packetType == PROBE_REPLY_PACKET) {
                            i++;
                            int pow = rdg.readInt();
                            rssi[i] = rdg.getRssi();
                            lqa[i] = rdg.getLinkQuality();
                            
                            ledsInUse = true;
                            displayLevel(rssi[i], 40, -50, getRed());
                            pause(1000);
                            displayNumber(0,red); 
                         /*   statusLED.setColor(getGreen());     // Red = not active
                            statusLED.setOn();
                            pause(1000);
                            statusLED.setOff();*/
                            if(i == 2)
                                i = -1;
                            
                            synchronized(this){
                                goAhead = 0;
                            }
                            
                            while(true)
                            {
                                synchronized(this){
                                    if(goAhead == 1)
                                        break;
                                }
                                pause(1000);
                            }
                        }
                    } catch (TimeoutException tex) {        // timeout - display no packet received
                        
                        synchronized(this){
                            probeValue = probe_flag;
                        }
                        if(probeValue == 1)
                        {
                            i++;
                            rssi[i] = 60;
                            lqa[i] = 255;
                            if(i == 2)
                                i = -1;
                            synchronized(this){
                                goAhead = 0;
                            }
                            while(true)
                            {
                                synchronized(this){
                                    if(goAhead == 1)
                                        break;
                                }
                                pause(1000);
                            }
                            
                            statusLED.setColor(getBlue());
                            statusLED.setOn();
                            pause(1000);
                            statusLED.setOff();
                            
                        }
                        
                        nothing++;
                        if (nothing > 2 * PACKETS_PER_SECOND && !ledsInUse) {
                            displayLevel(-50, 40, -50, getBlue());  // if nothing received eventually turn off LEDs
                        }
                    }
                }
            } catch (IOException ex) {
                // ignore
            } finally {
                if (rcvConn != null) {
                    try {
                        rcvConn.close();
                    } catch (IOException ex) { }
                }
            }
        }
    }
    
    /**
     * MIDlet call to start our application.
     */
    protected void startApp() throws MIDletStateChangeException {
	// Listen for downloads/commands over USB connection
	new com.sun.spot.service.BootloaderListenerService().getInstance().start();
        initialize();
        run();
    }

    /**
     * This will never be called by the Squawk VM.
     */
    protected void pauseApp() {
        // This will never be called by the Squawk VM
    }

    /**
     * Called if the MIDlet is terminated by the system.
     * @param unconditional If true the MIDlet must cleanup and release all resources.
     */
    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
    }

}

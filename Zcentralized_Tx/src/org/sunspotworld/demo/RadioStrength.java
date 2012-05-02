/* TRANSMITTER CODE*/
 
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
import com.sun.spot.util.Utils;

import java.lang.Object;
import java.io.IOException;
import javax.microedition.io.Connector;
import javax.microedition.io.Datagram;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;


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
    private static final int DECISION_PACKET        = 105;
    private static final int REGISTRATION_PACKET         = 101;
    private static final int HOST_PORT              = 67;
    private static final int DECISION_PORT          = 69;
    private static final int SAMPLE_PERIOD          = 10 * 1000;  // in milliseconds
    
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
    private int i = 0;
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
    private int newChannel = 0;
    
    /**
     * Return bright or dim red.
     *
     * @returns appropriately bright red LED settings
     */
    private LEDColor getRed() {
        return boostLEDs ? LEDColor.RED : red;
    }
     private LEDColor getGreen() {
        return boostLEDs ? LEDColor.GREEN : green;
    }
    private LEDColor getBlue() {
        return maxBoostLEDs ? white : boostLEDs ? LEDColor.BLUE : blue;
    }
    private void checkLightSensor() {
        try {
            int val = light.getValue();
            boostLEDs = (val > BOOST_LED_THRESHOLD);
            maxBoostLEDs = (val > MAX_BOOST_LED_THRESHOLD);
        } catch (IOException ex) { }
    }
    private void pause (long time) {
        try {
            Thread.currentThread().sleep(time);
        } catch (InterruptedException ex) { /* ignore */ }
    }
     /* Initialize any needed variables.*/
    private void initialize() { 
        checkLightSensor();
        statusLED.setColor(getRed());     // Red = not active
        statusLED.setOn();
        IRadioPolicyManager rpm = Spot.getInstance().getRadioPolicyManager();
        rpm.setChannelNumber(channel);
        rpm.setPanId(PAN_ID);
        rpm.setOutputPower(power - 32);
    }
     /* Main application run loop.*/
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

    /*Display a number (base 2) in LEDs 1-7
     *@param val the number to display
     * @param col the color to display in LED*/
    private void displayNumber(int val, LEDColor col) {
        for (int i = 0, mask = 1; i < 7; i++, mask <<= 1) {
            leds.getLED(7-i).setColor(col);
            leds.getLED(7-i).setOn((val & mask) != 0);
        }
    }
    
    private int lightValue (int x, int col, int perLed){
        if (x <= 0 || col <= 0) return 0;
        if (x >= perLed) return col;
        return  ( x * x * x * col ) /(perLed * perLed * perLed);
    }
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
           
            ledsInUse = false;
            checkLightSensor();
        }
    }
    /* Function to probe other channels and check the quality of that channel. 
     * Save the information locally*/    
    private int probe(int currentChannel) {
        
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
                System.out.println("Probing channel "+channel+" now");
                synchronized(this){//now that packet is sent, indicate to receiver thread to listen for a reply
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
            goAhead = 1;//turn for the receiver function
        }
        synchronized(this){
            probe_flag = 0;
        }
        // here send the packet to the server
        //-------------------------------------------------
        RadiogramConnection rCon = null;
        Datagram forw_req = null;
        // Listen for downloads/commands over USB connection
        new com.sun.spot.service.BootloaderListenerService().getInstance().start();
        Spot.getInstance().getRadioPolicyManager().setChannelNumber(11);
        try {
        // Open up a broadcast connection to the host port
        // where the 'on Desktop' portion of this demo is listening
        System.out.println("Trying to connect to the server");
        rCon = (RadiogramConnection) Connector.open("radiogram://0014.4F01.0000.7FD3:" + HOST_PORT);
        //rCon.setMaxBroadcastHops(3);
        forw_req = rCon.newDatagram(rCon.getMaximumLength());  
        //recvd_rep = rCon.newDatagram(rCon.getMaximumLength());
        } catch (Exception e) {
                System.err.println("Caught " + e + " in connection initialization.");
                notifyDestroyed();
        }
        try {
           // Get the current time and sensor reading
           long now = System.currentTimeMillis();
           // Package the time and sensor reading into a radio datagram and send it.
           //instead of this i need to send the rssi values
            forw_req.reset();
            forw_req.writeByte(CHANNEL_CHANGE_REQUEST);
            forw_req.writeInt(currentChannel);
            for(int j = 0; j < 3; j++)
            {
                System.out.println("sending data");
                forw_req.writeInt(rssi[j]);
                forw_req.writeInt(j+24);
            }
            rCon.send(forw_req);
            try {
                   rCon.close();
                } catch (IOException ex) { }
            
            } catch (Exception e) {
            System.err.println("Caught " + e + " while collecting/sending sensor sample.");
            }
         
            
            
        while(true)
        {
            synchronized(this){
                if(newChannel != 0){
                    selectedChannel = newChannel;
                    newChannel = 0;
                    break;
                }
            }
            pause(1000);
        }
        
         return selectedChannel;           
    }
    
    /**
     * Loop to continually transmit packets using current power level & channel setting.
     */
    private void xmitLoop () {
        ILed led = Spot.getInstance().getGreenLed();
        RadiogramConnection txConn = null;
        RadiogramConnection regConn = null;
        xmitDo = true;
        
        int changeToChannel = 0;
        int request = 0;
        
        //-------------SENDING REGISTRATION PACKET TO BASESTATION-----------
           
        try {
            
            Spot.getInstance().getRadioPolicyManager().setChannelNumber(11);
            regConn = (RadiogramConnection)Connector.open("radiogram://0014.4F01.0000.7FD3:" + HOST_PORT);
            regConn.setMaxBroadcastHops(3);      // don't want packets being rebroadcasted
            Datagram reg = regConn.newDatagram(regConn.getMaximumLength());
            System.out.println("Open connection for registration");
            reg.reset();
            reg.writeByte(REGISTRATION_PACKET);
            reg.writeInt(channel);
            System.out.println("done writing for registration");
            regConn.send(reg);
            System.out.println("Sent registration packet");
            statusLED.setColor(getGreen());
            statusLED.setOn();
            pause(1000);
            //statusLED.setOff();
                            
        } catch (IOException ex) {
                // ignore
        } 
        try {
            regConn.close();
        } catch (IOException ex) { }
           
      
                
      //----------------------------------------------------
        Spot.getInstance().getRadioPolicyManager().setChannelNumber(channel);
        while (xmitDo) {
            try {
                txConn = null;
                txConn = (RadiogramConnection)Connector.open("radiogram://0014.4F01.0000.798A:" + BROADCAST_PORT);
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
                        //xdg.getRssi();
                        int presentChannel = channel;
                        changeToChannel = probe(presentChannel);
                        displayNumber(changeToChannel,getBlue());
                        
                        System.out.println("Finished probing");
                       //send message to change on original channel frequency
                        channel = presentChannel;
                        Spot.getInstance().getRadioPolicyManager().setChannelNumber(channel);

                        xdg.reset();
                        xdg.writeByte(CHANNEL_CHANGE_PACKET);
                        xdg.writeInt(changeToChannel);
                        txConn.send(xdg);
                        //displayNumber(255,getBlue());
                        pause(1000);
                        System.out.println("Sent CHANNEL_CHANGE_PACKET");
                        
                        channel = changeToChannel;
                        Spot.getInstance().getRadioPolicyManager().setChannelNumber(channel);
                        statusLED.setColor(getBlue());//was blue before
                        statusLED.setOn();
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
                rcvConn.setTimeout(1500);
                Radiogram rdg = (Radiogram)rcvConn.newDatagram(rcvConn.getMaximumLength());
                long count = 0;
                boolean ledOn = false;
                while (recvDo) {
                    try {
                        rdg.reset();
                        System.out.println("About to Receive");
                        rcvConn.receive(rdg);           // listen for a packet
                        byte packetType = rdg.readByte();
                        if (packetType == CHANNEL_CHANGE_REQUEST) {
                            led.setOn();
                            int recvRssi = rdg.readInt();
                            q = rdg.getRssi();

                            System.out.println("Got CHANNEL_CHANGE_REQUEST");
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
                            System.out.println("Leaving CHANNEL_CHANGE_REQUEST block");
                        }
                        if(packetType == DECISION_PACKET)
                        {
                            synchronized(this){
                                newChannel = rdg.readInt();
                            }
                            System.out.println("Got DECISION_PACKET");
                        }
                        if (packetType == PROBE_REPLY_PACKET) {
                            i++;
                            int pow = rdg.readInt();
                            rssi[i] = rdg.getRssi();
                            lqa[i] = rdg.getLinkQuality();
                            System.out.println("rssi["+i+"]="+rssi[i]+", lqa["+i+"]="+lqa[i]);
                            
                            ledsInUse = true;
                            displayLevel(rssi[i], 40, -50, getRed());
                            pause(1000);
                            displayNumber(0,red); 
                            
                            synchronized(this){
                                goAhead = 0;
                            }
                            while(true)
                            {
                                synchronized(this){
                                    if(goAhead == 1)
                                    {
                                        //System.out.println("goAhead = 1");
                                        break;
                                    }
                                }
                                pause(1000);
                            }
                            
                            if(i == 2)
                            {
                                i = -1;
                                while(true)
                                {
                                    synchronized(this){
                                        if(probe_flag == 0)
                                        {
                                            goAhead = 0;
                                            break;
                                        }
                                    }
                                    pause(1000);
                                }
                            }
                            System.out.println("Leaving PROBE_REPLY_PACKET block");
                        }
                    } catch (TimeoutException tex) {        // timeout - display no packet received
                        
                        synchronized(this){
                            probeValue = probe_flag;
                        }
                        if(probeValue == 1)
                        {
                            System.out.println("Timeout during probe on channel "+channel);
                            i++;
                            rssi[i] = 60;
                            lqa[i] = 255;
                            System.out.println("rssi["+i+"]="+rssi[i]+", lqa["+i+"]="+lqa[i]);
                            synchronized(this){
                                goAhead = 0;
                            }
                            while(true)
                            {
                                synchronized(this){
                                    if(goAhead == 1)
                                    {
                                        //System.out.println("goAhead = 1");
                                        break;
                                    }
                                }
                                pause(1000);
                            }
                            
                            statusLED.setColor(getBlue());
                            statusLED.setOn();
                            pause(1000);
                            statusLED.setOff();
                            
                            if(i == 2)
                            {
                                i = -1;
                                while(true)
                                {
                                    synchronized(this){
                                        if(probe_flag == 0)
                                        {
                                            goAhead = 0;
                                            break;
                                        }
                                    }
                                    pause(1000);
                                }
                            }
                            System.out.println("Leaving Timeout Exception block");
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

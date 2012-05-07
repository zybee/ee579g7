/**************** Hybrid System : Controller*/
 

package org.sunspotworld.demo;

import com.sun.spot.io.j2me.radiogram.*;
import com.sun.spot.peripheral.Spot;

import com.sun.spot.peripheral.ota.OTACommandServer;
import com.sun.spot.peripheral.radio.IRadioPolicyManager;
import com.sun.spot.peripheral.radio.RadioFactory;
import com.sun.spot.util.Utils;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.microedition.io.*;
import java.util.*;

public class SendDataDemoHostApplication {
    // Broadcast port on which we listen for sensor samples
    private static final int HOST_PORT = 67; 
    private static final int DECISION_PORT = 42;
    private static final int REGISTRATION_PACKET = 101;
    private static final int DECISION_PACKET  = 105;
    private static final int CHANNEL_CHANGE_REQUEST = 99;
    private int busy[] = { 0 , 0, 0 };
    long prev_time = System.currentTimeMillis();
    private int change_timeout = 20000;

    private void pause (long time) {
        try {
            Thread.currentThread().sleep(time);
        } catch (InterruptedException ex) { /* ignore */ }
    }
    
    private void run() throws Exception {
       
        new Thread() {
            public void run () {
                try {
                    recvLoop();
                } catch (Exception ex) {
                    //Logger.getLogger(SendDataDemoHostApplication.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } .start(); 
        
    }

    // The receive loop for central controller
    private void recvLoop () throws Exception {
        
        RadiogramConnection rCon;
        Datagram dg;
         
        IRadioPolicyManager rpm = RadioFactory.getRadioPolicyManager();
        rpm.setChannelNumber(11);
        
        try {
            // Open up a server-side broadcast radiogram connection
            // to listen for sensor readings being sent by different SPOTs
            rCon = (RadiogramConnection) Connector.open("radiogram://:" + HOST_PORT);
            dg = rCon.newDatagram(rCon.getMaximumLength());
            int x = rCon.getMaximumLength();
            
            System.out.println("The maximum length is : " + x);
        } catch (Exception e) {
             System.err.println("setUp caught " + e.getMessage());
             throw e;
        }

        // Main data collection loop
        while (true) {
            try {
                // Read sensor sample received over the radio
                System.out.println("Again Reading");
                rCon.receive(dg);
                String addr = dg.getAddress();  // read sender's Id
                byte packetType = dg.readByte();
                
                // If the packet is the registration packet, make entry that this particular frequency is busy now
                if(packetType == REGISTRATION_PACKET)
                {
                    int index = dg.readInt();
                    busy[index - 24] = 1;
                    System.out.println("Received the registration packet from "+ addr + " for channel " + index);
                }
                 
                // If it is a channel change request packet then save the address from where it is received so that unicast 
                // reply can be sent to this particular device
                // After that call the function which will send the channel change reply
                if(packetType == CHANNEL_CHANGE_REQUEST)
                {
                    int currentchannel = dg.readInt();
                    System.out.println("Timestamp: "+ System.currentTimeMillis()+"; Received from: " + addr + "; Old Channel: " + currentchannel);
                    xmitFunc(addr);
                    System.out.println("Timestamp: "+ System.currentTimeMillis()+"; Sent to: " + addr);    
                }
            } 
            catch (Exception e) {
                    System.err.println("Caught " + e + " while reading data.");
                    throw e;
            }
        }
    }
    
    // The transmit loop for central controller
    private void xmitFunc (String addr) {
        //open a connection on addr
        RadiogramConnection dCon = null;
        Datagram forw_rep = null;
       
        try {
            // Open up a broadcast connection to the host port whose address has been obtained from incoming packet
            System.out.println("Trying to connect to the transmitter "+ addr);
            dCon = (RadiogramConnection) Connector.open("radiogram://"+ addr +":" + DECISION_PORT);
            dCon.setMaxBroadcastHops(3);
            forw_rep = dCon.newDatagram(dCon.getMaximumLength());
        } catch (Exception e) {
                 System.err.println("Caught " + e + " in connection initialization.");
        }
        try {
            // Write the packet type to ceate the reply packet
            forw_rep.reset();
            forw_rep.writeByte(DECISION_PACKET);
            
            // Check if sufficient time has passed since the last channel change reply
            // If yes then send the reply else wait for remaining time
            if((System.currentTimeMillis() - prev_time) < change_timeout)
                pause(change_timeout - (System.currentTimeMillis() - prev_time));

            dCon.send(forw_rep);
            prev_time = System.currentTimeMillis();
        } catch (Exception e) {
            System.err.println("Caught " + e + " while collecting/sending sensor sample.");
        }
         pause(1000);
         try {
            dCon.close();
         } catch (IOException ex) { }
         
     }
    
    /**
     * Start up the host application.
     *
     * @param args any command line arguments
     */
    public static void main(String[] args) throws Exception {
        // register the application's name with the OTA Command server & start OTA running
        OTACommandServer.start("SendDataDemo");

        SendDataDemoHostApplication app = new SendDataDemoHostApplication();
        app.run();
    }
}

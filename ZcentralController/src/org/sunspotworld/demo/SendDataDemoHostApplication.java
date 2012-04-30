/**************** FOR MY PROJECT - Central Controller*/
 

package org.sunspotworld.demo;

import com.sun.spot.io.j2me.radiogram.*;

import com.sun.spot.peripheral.ota.OTACommandServer;
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
    private static final int DECISION_PACKET  = 105;
    private int goAhead = 0;
    
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
        Datagram forw_rep = null;
        DateFormat fmt = DateFormat.getTimeInstance();
        int selectedRssi = 0;
        int selectedChannel = 0;
        int rssi[] = {0, 0, 0};
        int availChannels[] = {24,25,26};
         
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
                System.out.println(" TRYING TO READ");
                rCon.receive(dg);
                System.out.println("Received the packet");
                String addr = dg.getAddress();  // read sender's Id
                byte packetType = dg.readByte();
                int currentchannel = dg.readInt();
                int channel_nos[] = {0,0,0};
                System.out.println(" The packet type is :" + packetType);
                for(int i=0 ; i < 3 ; i++)
                {
                   rssi[i]  = dg.readInt();
                   System.out.println("RSSI:"+rssi[i]);
                   channel_nos[i] = dg.readInt();
                   System.out.println("Channel:"+channel_nos[i]);
                }
        // before returning this channel, i want the decision of the server.
                // read the sensor value
                System.out.println("Received from: " + addr + " " + currentchannel);
                selectedRssi = rssi[0];
                selectedChannel = availChannels[0];
                for(int j = 0; j < 3; j++)
                {           
                    if(rssi[j] > selectedRssi )
                    {
                    selectedRssi = rssi[j];
                    selectedChannel = availChannels[j];
                    }
                }
                
                xmitFunc (selectedChannel,addr) ;
                System.out.println("Sent packet to client");
                System.out.println("Selected Channel :"+ selectedChannel);
       
                //now as the descision is made, ask transmitter fuction to send the message
        // before returning this channel, i want the decision of the server.
       // return selectedChannel; 
            } catch (Exception e) {
                System.err.println("Caught " + e +  " while reading sensor samples.");
                throw e;
            }
        }
    }
    
     // The transmit loop for central controller
     private void xmitFunc (int selectedChannel ,String addr) {
       //open a connection on addr 
         RadiogramConnection dCon = null;
         Datagram forw_rep = null;
       
         try {
              // Open up a broadcast connection to the host port
              // where the 'on Desktop' portion of this demo is listening
              System.out.println("Trying to connect to the transmitter");
              System.out.println("The address connected to : "+ addr);
              dCon = (RadiogramConnection) Connector.open("radiogram://"+ addr +":" + DECISION_PORT);
              dCon.setMaxBroadcastHops(3);
              forw_rep = dCon.newDatagram(dCon.getMaximumLength());  
             } catch (Exception e) {
                 System.err.println("Caught " + e + " in connection initialization.");
             }
         try {
               forw_rep.reset();
               forw_rep.writeByte(DECISION_PACKET);
               forw_rep.writeInt(selectedChannel);
               dCon.send(forw_rep);
               
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

/**************** FOR MY PROJECT - Central Controller*/
 

package org.sunspotworld.demo;

import com.sun.spot.io.j2me.radiogram.*;

import com.sun.spot.peripheral.ota.OTACommandServer;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.microedition.io.*;
import java.util.*;

public class SendDataDemoHostApplication {
    // Broadcast port on which we listen for sensor samples
    private static final int HOST_PORT = 67;
        
    private void run() throws Exception {
        /*new Thread() {
            public void run () {
                xmitLoop();
            }
        }.start();*/
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
                
        // before returning this channel, i want the decision of the server.
       // return selectedChannel; 
            } catch (Exception e) {
                System.err.println("Caught " + e +  " while reading sensor samples.");
                throw e;
            }
        }
    }
    
        
  
    
    
    // The transmit loop for central controller
    
   /* private void xmitLoop () throws Exception{
        
        
        
     }*/
    
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

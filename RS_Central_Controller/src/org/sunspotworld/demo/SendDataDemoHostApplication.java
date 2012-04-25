/**************** FOR MY PROJECT - Central Controller
 * SendDataDemoHostApplication.java
 *
 * Copyright (c) 2008-2009 Sun Microsystems, Inc.
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

import com.sun.spot.io.j2me.radiogram.*;

import com.sun.spot.peripheral.ota.OTACommandServer;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.microedition.io.*;
import java.util.*;


/**
 * This application is the 'on Desktop' portion of the SendDataDemo. 
 * This host application collects sensor samples sent by the 'on SPOT'
 * portion running on neighboring SPOTs and just prints them out. 
 *   
 * @author: Vipul Gupta
 * modified: Ron Goldman
 */

 /**
     * Main application run loop.
     */

public class SendDataDemoHostApplication {
    // Broadcast port on which we listen for sensor samples
    private static final int HOST_PORT = 67;
        
    private void run() throws Exception {
        new Thread() {
            public void run () {
                xmitLoop();
            }

            private void xmitLoop() {
                throw new UnsupportedOperationException("Not yet implemented");
            }
        }.start();
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
                int currentchannel = dg.readInt();
                int channel_nos[] = {0,0,0};
                for(int i=0 ; i < 3 ; i++)
                {
                   rssi[i]  = dg.readInt();
                   System.out.println("RSSI:"+rssi[i]);
                   channel_nos[i] = dg.readInt();
                   System.out.println("Channel:"+channel_nos[i]);
                }
        // before returning this channel, i want the decision of the server.
                // read the sensor value
                System.out.println("Received from: " + addr);
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

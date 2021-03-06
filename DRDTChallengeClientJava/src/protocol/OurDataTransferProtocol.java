package protocol;


import client.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import client.Utils;
import java.util.List;

public class OurDataTransferProtocol extends IRDTProtocol {

    // change the following as you wish:
    static final int HEADERSIZE=4;   // number of header bytes in each packet
    static final int DATASIZE=60;   // max. number of user data bytes in each packet

    private int header;
    private int headerPartTwo;
    int totalHeader;
    private int totalAmountOfPacketsPartTwo;
    List<Integer> acked;
    WaitThread wt;
    Integer[][] buffer;

    public OurDataTransferProtocol() {
        acked = new ArrayList<Integer>();
        wt = new WaitThread(this);
    }


    @Override
    public void sender() {
        System.out.println("Sending...");

        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());

        // keep track of where we are in the data
        int filePointer = 0;

        // create a new packet of appropriate size
        // write something random into the header byte
        boolean finished = false;
        header = 0;
        headerPartTwo = 0;
        int totalAmountOfPackets = ((int) Math.ceil(fileContents.length / DATASIZE));
        totalAmountOfPacketsPartTwo = (int)totalAmountOfPackets / 255;
        totalAmountOfPackets = totalAmountOfPackets - totalAmountOfPacketsPartTwo * 255;
        buffer = new Integer[totalAmountOfPacketsPartTwo * 255 + totalAmountOfPackets + 2][];
        Thread t = new Thread(wt);
        t.start();

        SendAllPackets(fileContents, filePointer, finished,totalAmountOfPackets, totalAmountOfPacketsPartTwo);

        while (notAllAcked()){
            try {
                Thread.sleep(100);
                handleAcks();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.exit(1);
    }

    private void SendAllPackets(Integer[] fileContents, int filePointer, boolean finished, int totalAmountOfPackets, int totalAmountOfPacketsPartTwo) {
        while (!finished) {
            //Length of one data packet
            int dataLength = Math.min(DATASIZE, fileContents.length - filePointer);
            Integer[] pkt = new Integer[HEADERSIZE + dataLength];
            //Set index 0 to header
            pkt[0] = header;
            pkt[1] = headerPartTwo;
            //set index 1 to total amount of packets to be send
            pkt[2] = totalAmountOfPackets;
            pkt[3] = totalAmountOfPacketsPartTwo;
            System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, dataLength);
            //store packet in buffer, for resending
            System.out.println("totalheader" + totalHeader);
            System.out.println("array length" + buffer.length);
            buffer[headerPartTwo * 255 + header] = pkt;
            filePointer += dataLength;
            //send packet
            getNetworkLayer().sendPacket(pkt);
            //set time-out
            Utils.Timeout.SetTimeout(20000, this, totalHeader);
            System.out.println("Sent one packet with header=" + pkt[0] + " and data " + pkt[1] + " as first data");
            if(!(header + headerPartTwo * 255 == totalAmountOfPacketsPartTwo * 255 + totalAmountOfPackets)){
                if(header == 255){
                    header = 0;
                    headerPartTwo++;
                    totalHeader++;
                } else {
                    header++;
                    totalHeader++;
                }
            }
            else {
                finished = true;
            }
        }
    }

    private void handleAcks() {
        boolean finished = false;
        Integer[] reveicedPacket;
        Integer[] packet;

        while(!finished) {
            if((reveicedPacket = getNetworkLayer().receivePacket()) != null) {
                packet = reveicedPacket;
                System.out.println("Received ack for " + packet[0]  + " and second is "+ (packet[1] * 255));
                acked.add(packet[0] + (packet[1] * 255));
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean notAllAcked() {
        for (int i = 0; i < totalHeader; i++) {
            if(!wt.acked.contains(i)){
                return true;
            }
        }
        return false;
    }

    @Override
    public void TimeoutElapsed(Object tag) {
        int z =(Integer)tag;
        if(!acked.contains(z)) {
            resend(z);
        }
    }

    private void resend(int i) {
        if(i < buffer.length && buffer[i] != null) {
            System.out.println("Resend " + i);
            getNetworkLayer().sendPacket(buffer[i]);
            client.Utils.Timeout.SetTimeout(18000, this, i);
        }
    }

    @Override
    public void receiver() {
        System.out.println("Receiving...");

        // create the array that will contain the file contents
        // note: we don't know yet how large the file will be, so the easiest (but not most efficient)
        //   is to reallocate the array every time we find out there's more data
        
        Map<Integer, Integer[]> map = new HashMap<Integer, Integer[]>(); 

        // loop until we are done receiving the file
        boolean stop = false;
        while (!stop) {

            // try to receive a packet from the network layer
            Integer[] packet = getNetworkLayer().receivePacket();

            // if we indeed received a packet
            if (packet != null) {
            	
                int packetNr = packet[0] + 255 * packet[1];
                int totalPackets = packet[2] + 255 * packet[3];
                
                // ack versturen
                Integer[] ack = new Integer[]{packetNr%255, packetNr/255};
                System.out.println("ack: "+ packetNr + "--> " + ack[0] + " " + ack[1] );
                try {
                	getNetworkLayer().sendPacket(ack);
                } catch (IllegalArgumentException e){
                	e.printStackTrace();
                }

                // tell the user
                System.out.println("Received packet, length="+packet.length+"  first byte=" + packetNr + " last header=" + totalPackets );
                
                // Als het pakketje nog niet in de map zit moet deze worden toegevoegd
                if (!map.containsKey(packetNr)) {
                	 Integer[] newArray = new Integer[packet.length - HEADERSIZE];
                     System.arraycopy(packet, HEADERSIZE, newArray, 0, packet.length - HEADERSIZE);
                     map.put(packetNr, newArray);
				}

                // Als alle pakketjes ontvangen zijn moet het stoppen
                if (everythingReceived(map, totalPackets)) {
					stop = true;
				}

            } else {
                // wait ~10ms (or however long the OS makes us wait) before trying again
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
        }
        
        // Alle pakketjes zijn ontvangen, 
        int destPos = 0;
        Integer[] fileContents = new Integer[0];
        int oldlength;
		int datalen;
		
        for(Integer i : map.keySet()){
        	datalen = map.get(i).length;
        	oldlength = fileContents.length;
        	fileContents = Arrays.copyOf(fileContents, oldlength+datalen);
        	System.arraycopy(map.get(i), 0, fileContents, destPos, datalen);
        	destPos += datalen;
        }
        
        System.out.println("Alles ontvangen!!");
        
        // write to the output file
        Utils.setFileContents(fileContents, getFileID());
    }

	private boolean everythingReceived(Map<Integer, Integer[]> map, int nrOfPackets) {
		for (int i = 0; i < nrOfPackets; i++) {
			if (!map.containsKey(i)) {
				System.out.println("Nog niet ontvangen: " + i);
				return false;
			}
		}
		return true;
	}
}

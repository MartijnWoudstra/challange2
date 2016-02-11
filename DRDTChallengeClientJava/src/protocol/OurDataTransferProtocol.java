package protocol;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import client.Utils;

public class OurDataTransferProtocol extends IRDTProtocol {

    // change the following as you wish:
    static final int HEADERSIZE=4;   // number of header bytes in each packet
    static final int DATASIZE=60;   // max. number of user data bytes in each packet

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
        int header = 0;
        int amount = ((int)Math.ceil(fileContents.length / DATASIZE));
        System.out.println(amount);

        while(!finished){
            int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
            Integer[] pkt = new Integer[HEADERSIZE + datalen];
            pkt[0] = header;
            header++;
            pkt[1] = amount;
            System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);
            filePointer += datalen;
            getNetworkLayer().sendPacket(pkt);
            System.out.println("Sent one packet with header="+pkt[0] + " and data " + pkt[1] + " as first data");
            if(filePointer == fileContents.length)
                finished = true;
        }



        // schedule a timer for 1000 ms into the future, just to show how that works:
        client.Utils.Timeout.SetTimeout(1000, this, 28);
        // and loop and sleep; you may use this loop to check for incoming acks...
        boolean stop = false;
        while (!stop) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                stop = true;
            }
        }

    }

    @Override
    public void TimeoutElapsed(Object tag) {
        int z=(Integer)tag;
        // handle expiration of the timeout:
        System.out.println("Timer expired with tag="+z);
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

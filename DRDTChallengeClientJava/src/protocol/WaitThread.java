package protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by martijn on 11-2-16.
 */
public class WaitThread implements Runnable{

    OurDataTransferProtocol protocol;
    public boolean finished;
    public List<Integer> acked;

    public WaitThread(OurDataTransferProtocol protocol){
        this.protocol = protocol;
        finished = false;
        acked = new ArrayList<Integer>();
    }

    @Override
    public void run() {
        Integer[] reveicedPacket;
        Integer[] packet;

        while(!finished) {
            if((reveicedPacket = protocol.getNetworkLayer().receivePacket()) != null) {
                packet = reveicedPacket;
                System.out.println("Received ack for " + packet[0]  + " and second is "+ (packet[1] * 255));
                acked.add(packet[0] + (packet[1] * 255));
            }
        }

    }

    public List<Integer> getAcked() {
        return acked;
    }
}

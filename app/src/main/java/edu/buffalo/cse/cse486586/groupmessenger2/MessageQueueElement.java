package edu.buffalo.cse.cse486586.groupmessenger2;

import java.util.Timer;

/**
 * Created by arindam on 2/25/15.
 */
public class MessageQueueElement implements Comparable {



    private Packet packet;
    private Timer timer;
    private boolean isDeliverable;





    public MessageQueueElement(Packet packet, boolean isDeliverable) {

        this.isDeliverable = isDeliverable;
        this.packet = packet;
        timer = new Timer();

    }




    @Override
    public int compareTo(Object another) {

        MessageQueueElement element = (MessageQueueElement) another;

        Packet elemPacket = element.getPacket();
        if(packet.getPriority() < elemPacket.getPriority()) {
            return -10;
        }
        else if (packet.getPriority() > elemPacket.getPriority()) {
            return 10;
        }
        else {
            if(!isDeliverable && element.isDeliverable()) {
                return -10;
            }
            else if(isDeliverable && !element.isDeliverable()) {
                return 10;
            }
            else {
                if(packet.getPriorityProposer() < elemPacket.getPriorityProposer()) {
                    return -10;
                }
                else if(packet.getPriorityProposer() > elemPacket.getPriorityProposer()) {
                    return 10;
                }
                else return 0;
            }
        }

    }






    public boolean isDeliverable() {
        return isDeliverable;
    }

    public void setDeliverable(boolean isDeliverable) {
        this.isDeliverable = isDeliverable;
    }

    public Packet getPacket() {
        return packet;
    }

    public String toString() {
        return Integer.valueOf(packet.getMessageSender()).toString()
                +"."+Long.valueOf(packet.getMessageID()).toString()
                +"\tPriority"+Long.valueOf(packet.getPriority()).toString()
                +"\tDeliverable: "+isDeliverable;
    }

    public Timer getTimer() {
        return timer;
    }

}

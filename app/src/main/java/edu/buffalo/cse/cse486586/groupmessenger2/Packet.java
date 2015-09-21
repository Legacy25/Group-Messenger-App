package edu.buffalo.cse.cse486586.groupmessenger2;

import android.util.Log;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by arindam on 2/24/15.
 * Class to encapsulate a message
 *
 *
 */
public class Packet {

    private final String SEPARATOR = ".";

    private MessageType messageType;

    private String content;
    private long messageID;
    private long priority;
    private int messageSender;
    private int priorityProposer;



    public enum MessageType {
        INITIAL_DELIVERY, PRIORITY_PROPOSAL, FINAL_DELIVERY
    }





    /**
     * Constructor
     */
    public Packet(MessageType messageType) {

        this.messageType = messageType;

        content = null;
        messageID = -1;
        priority = -1;
        messageSender = -1;
        priorityProposer = -1;

    }

    public Packet(String formattedString) {

        String fields[] = formattedString.split("\\.");
        if(fields[0].equals("INITIAL_DELIVERY")) {
            messageType = MessageType.INITIAL_DELIVERY;
        }
        else if(fields[0].equals("FINAL_DELIVERY")) {
            messageType = MessageType.FINAL_DELIVERY;
        }
        else {
            messageType = MessageType.PRIORITY_PROPOSAL;
        }

        Log.i("PACKET", Arrays.toString(fields));
        content = fields[1];
        messageID = Long.parseLong(fields[2]);
        messageSender = Integer.parseInt(fields[3]);
        priority = Long.parseLong(fields[4]);
        priorityProposer = Integer.parseInt(fields[5]);

    }






    /**
     * Getters and Setters for class fields
     */
    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public long getMessageID() {
        return messageID;
    }

    public void setMessageID(long messageID) {
        this.messageID = messageID;
    }

    public long getPriority() {
        return priority;
    }

    public void setPriority(long priority) {
        this.priority = priority;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public int getMessageSender() {
        return messageSender;
    }

    public void setMessageSender(int messageSender) {
        this.messageSender = messageSender;
    }

    public int getPriorityProposer() {
        return priorityProposer;
    }

    public void setPriorityProposer(int priorityProposer) {
        this.priorityProposer = priorityProposer;
    }

    public String getSeparator() {
        return SEPARATOR;
    }



    public String toString() {
        return messageSender+"."+messageID+"\tPriority: "+priority+"\tProposer: "+priorityProposer+"\t"+messageType;
    }

    public String getFormattedString() {
        return messageType+SEPARATOR
                +content+SEPARATOR
                +messageID+SEPARATOR
                +messageSender+SEPARATOR+
                priority+SEPARATOR
                +priorityProposer;
    }

}

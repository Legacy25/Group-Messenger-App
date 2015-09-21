package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    private static final String TAG = GroupMessengerActivity.class.getName();
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private static final int[] REMOTE_PORTS = {11108, 11112, 11116, 11120, 11124};
    private static final int SERVER_PORT = 10000;

    private TextView tv;
    private Uri uri;
    private int myPort;

    private long priorityCounter = 0;
    private long messageIDCounter = 0;
    private long receiveCounter = 0;
    private long[] nextExpectedFIFO = {0, 0, 0, 0, 0};

    private boolean[] isAlive;
    private static final long PPTIMEOUT = 5000;
    private static final long MTIMEOUT = 9500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);


        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = (Integer.parseInt(portStr) * 2);



        Log.i(TAG, "Process number "+myPort);

        tv = (TextView) findViewById(R.id.textView1);
        uri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

        isAlive = new boolean[REMOTE_PORTS.length];
        Arrays.fill(isAlive, true);

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }




        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));


        final EditText editText = (EditText) findViewById(R.id.editText1);

        findViewById(R.id.button4).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Packet msg = new Packet(Packet.MessageType.INITIAL_DELIVERY);

                        msg.setContent(editText.getText().toString());
                        msg.setMessageSender(myPort);
                        msg.setMessageID(messageIDCounter);
                        messageIDCounter++;

                        editText.setText("");

                        Log.i(TAG, "Sending initial packet: "+msg);
                        new MultiCast().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
                    }
                }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class MultiCast extends AsyncTask<Packet, Void, Void> {

        @Override
        protected Void doInBackground(Packet... messages) {

            try {
                for(int i:REMOTE_PORTS) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), i);
                    Packet msgToSend = messages[0];
                    String formatString = msgToSend.getFormattedString();

                    Log.e(TAG, "MultiCasting to "+ i +" packet "+msgToSend);

                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    bw.write(formatString);
                    bw.flush();
                    bw.close();
                    socket.close();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "MultiCast UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "MultiCast IOException");
            }

            return null;
        }
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        private ArrayList<ArrayList<Packet>> holdBackQueuesFifo;
        private ArrayList<MessageQueueElement> holdBackQueueTotal;
        private HashMap<String, ConnectionStatus> proposedPriorities;

        public class ConnectionStatus {
            Long[] priorities;
            Timer timer;
            boolean timerSetFlag;

            public ConnectionStatus() {
                priorities = new Long[REMOTE_PORTS.length];
                timer = new Timer();
                timerSetFlag = false;
            }
        }

        private ServerTask() {
            holdBackQueuesFifo = new ArrayList<>();
            holdBackQueueTotal = new ArrayList<>();
            for(int i:REMOTE_PORTS)
                holdBackQueuesFifo.add(new ArrayList<Packet>());

            proposedPriorities = new HashMap<>();
        }


        private int portToArrayIndex(int port) {
            return port/4 - 2777;
        }
        private int arrayIndexToPort(int index) { return (index + 2777) * 4; }


        /*
         * Fifo Ordering Code-----------------------------------------------------------------------
         */
        private void fifoReceive(Packet packet) {

            Log.i(TAG, "Inside fifoReceive");
            int messageSender = portToArrayIndex(packet.getMessageSender());
            long messageID = packet.getMessageID();

            if(nextExpectedFIFO[messageSender] == messageID) {

                /*
                 * Deliver this packet and increment counter
                 */

                Log.i(TAG, "FIFODelivering packet "+packet);
                fifoDeliver(packet);
                nextExpectedFIFO[messageSender]++;

                /*
                 * Deliver any queued messages till the next
                 * discontinuous message or till queue is empty
                 */
                fifoDeliverQueued(messageSender);

            }
            else if(nextExpectedFIFO[messageSender] < messageID) {
                /*
                 * Queue message for later deliver
                 */
                addToFifoQueue(holdBackQueuesFifo.get(messageSender), packet);
            }
        }

        private void fifoDeliverQueued(int sender) {
            Log.i(TAG, "Inside fifoDeliverQueued");

            ArrayList<Packet> queue = holdBackQueuesFifo.get(sender);

            while(!queue.isEmpty()) {
                if(nextExpectedFIFO[sender] == queue.get(0).getMessageID()) {

                    Log.i(TAG, "fifoDeliverQueued deliver queue elem "+queue.get(0));
                    fifoDeliver(queue.get(0));
                    queue.remove(0);
                    nextExpectedFIFO[sender]++;
                }
                else {
                    break;
                }
            }

        }

        private void addToFifoQueue(ArrayList<Packet> queue, Packet packet) {

            Log.i(TAG, "Inside addToFifoQueue");

            if(queue.isEmpty()) {
                queue.add(packet);
                return;
            }

            long messageID = packet.getMessageID();

            for(int i=0; i<queue.size(); i++) {
                if(queue.get(i).getMessageID() > messageID) {

                    Log.i(TAG, "addToFifoQueue at position "+i+" packet: "+packet);

                    queue.add(i, packet);
                    return;
                }
            }

            queue.add(packet);

        }

        private void fifoDeliver(Packet packet) {
            Log.i(TAG, "Inside fifoDeliver");


            finalDeliver(packet);
        }

        /*
         * Total Ordering Code ---------------------------------------------------------------------
         */
        private void totalReceive(Packet packet) {
            Log.i(TAG, "Inside totalReceive");

            priorityCounter++;

            final MessageQueueElement mqe = new MessageQueueElement(packet, false);
            mqe.getPacket().setPriority(priorityCounter);
            mqe.getTimer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.i(TAG, "Dead message packet "+mqe.getPacket());
                    isAlive[ portToArrayIndex(mqe.getPacket().getMessageSender()) ] = false;
                    sortTotalOrderQueue();
                    totalDeliver();
                }
            }, MTIMEOUT);

            holdBackQueueTotal.add(mqe);
            sortTotalOrderQueue();

            Packet proposal = new Packet(Packet.MessageType.PRIORITY_PROPOSAL);
            proposal.setMessageID(packet.getMessageID());
            proposal.setPriority(priorityCounter);
            proposal.setMessageSender(packet.getMessageSender());       // Here messsage sender
                                                                        // field is in fact set
                                                                        // to be the destination.

            proposal.setPriorityProposer(myPort);

            Log.i(TAG, "Sending proposal packet "+proposal);

            reply(proposal);
        }

        private void reply(Packet msgToSend) {
            try {
                int destination = msgToSend.getMessageSender();
                String formatString = msgToSend.getFormattedString();

                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), destination);

                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                bw.write(formatString);
                bw.flush();
                bw.close();
                socket.close();

            } catch (UnknownHostException e) {
                Log.e(TAG, "Reply UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "Reply IOException");
            }
        }

        private void registerProposal(Packet packet) {
            Log.i(TAG, "Inside registerProposal");

            final ConnectionStatus cStatus;

            String key = Integer.valueOf(packet.getMessageSender()).toString()
                    + "."
                    + Long.valueOf(packet.getMessageID()).toString();

            if(proposedPriorities.containsKey(key)) {

                cStatus = proposedPriorities.get(key);
                cStatus.priorities[portToArrayIndex(packet.getPriorityProposer())] = packet.getPriority();

            }
            else {

                cStatus = new ConnectionStatus();
                cStatus.priorities = new Long[REMOTE_PORTS.length];
                Arrays.fill(cStatus.priorities, Long.valueOf(-1));

                cStatus.priorities[portToArrayIndex(packet.getPriorityProposer())] = packet.getPriority();
                proposedPriorities.put(key, cStatus);

            }

            final Packet p = packet;

            if(!cStatus.timerSetFlag) {
                cStatus.timer.schedule(new TimerTask() {
                    @Override
                    public void run() {

                        Log.i(TAG, "Timer went off for "+p.getMessageSender()+"."+p.getMessageID());
                        Log.i(TAG, "Priorities array: "+Arrays.toString(cStatus.priorities));

                        for(int i=0; i<cStatus.priorities.length; i++) {
                            if(cStatus.priorities[i] == -1) {
                                isAlive[i] = false;
                            }
                        }


                        Log.i(TAG, "isAlive update: "+Arrays.toString(isAlive));

                        int maxPos = max(cStatus.priorities);
                        Long maxPriority = cStatus.priorities[maxPos];
                        int maxPriorityProposer = arrayIndexToPort(maxPos);


                        Packet finalDelivery = new Packet(Packet.MessageType.FINAL_DELIVERY);
                        finalDelivery.setMessageID(p.getMessageID());
                        finalDelivery.setPriority(maxPriority);
                        finalDelivery.setMessageSender(myPort);
                        finalDelivery.setPriorityProposer(maxPriorityProposer);
                        Log.i(TAG, "Sending final packet "+finalDelivery);
                        new MultiCast().executeOnExecutor(SERIAL_EXECUTOR, finalDelivery);
                    }
                }, PPTIMEOUT);

                cStatus.timerSetFlag = true;
            }

            if(gotAllPriorities(cStatus.priorities)) {
                cStatus.timer.cancel();
                Log.i(TAG, "Cancelling timer");
                int maxPos = max(cStatus.priorities);
                Long maxPriority = cStatus.priorities[maxPos];
                int maxPriorityProposer = arrayIndexToPort(maxPos);


                Packet finalDelivery = new Packet(Packet.MessageType.FINAL_DELIVERY);
                finalDelivery.setMessageID(packet.getMessageID());
                finalDelivery.setPriority(maxPriority);
                finalDelivery.setMessageSender(myPort);
                finalDelivery.setPriorityProposer(maxPriorityProposer);


                Log.i(TAG, "Sending final packet "+finalDelivery);
                new MultiCast().executeOnExecutor(SERIAL_EXECUTOR, finalDelivery);
            }

        }

        private void totalSetDeliverable(Packet packet) {
            Log.i(TAG, "Inside totalSetDeliverable with packet "+packet);

            if(priorityCounter < packet.getPriority())
                priorityCounter = packet.getPriority();



            for(int i=0; i<holdBackQueueTotal.size(); i++) {
                Packet mqePacket = holdBackQueueTotal.get(i).getPacket();

                Log.i(TAG, "tsdPacket "+mqePacket);

                if(mqePacket.getMessageSender() == packet.getMessageSender()
                        && mqePacket.getMessageID() == packet.getMessageID()) {

                    holdBackQueueTotal.get(i).getTimer().cancel();
                    holdBackQueueTotal.get(i).getPacket().setPriority(packet.getPriority());
                    holdBackQueueTotal.get(i).getPacket().setPriorityProposer(packet.getPriorityProposer());
                    holdBackQueueTotal.get(i).setDeliverable(true);

                    sortTotalOrderQueue();
                    return;
                }
            }
        }

        private void totalDeliver() {
            Log.i(TAG, "Inside totalDeliver");


            while(!holdBackQueueTotal.isEmpty()) {
                if(holdBackQueueTotal.get(0).isDeliverable()) {
                    Log.i(TAG, "Total Delivering packet "+holdBackQueueTotal.get(0).getPacket());
                    fifoReceive(holdBackQueueTotal.get(0).getPacket());
                    holdBackQueueTotal.remove(0);
                }
                else return;
            }

        }

        private void display(ArrayList<MessageQueueElement> queue) {
            String res = "";
            for(int i=0; i<queue.size(); i++) {
                res = res+queue.get(i) +"\t";
            }

            Log.i(TAG, res);
        }

        private void sortTotalOrderQueue() {
            Log.i(TAG, "Inside sort");

            Log.i(TAG, "Before sort");
            display(holdBackQueueTotal);

            if(!holdBackQueueTotal.isEmpty()) {
                int i=0;
                while(true) {
                    MessageQueueElement mqe = holdBackQueueTotal.get(i);
                    Log.i(TAG, "Purging dead mqes, isAlive: "+Arrays.toString(isAlive));
                    if( !isAlive[portToArrayIndex(mqe.getPacket().getMessageSender())] ) {
                        holdBackQueueTotal.remove(i);
                    }
                    else {
                        i++;
                    }
                    if(i >= holdBackQueueTotal.size()) {
                        break;
                    }
                }
            }

            /*
             * Using Insertion Sort because
             *      (1) queue will be small most of the time
             *      (2) queue will be mostly sorted
             */

            for(int i=1; i<holdBackQueueTotal.size(); i++) {
                MessageQueueElement key = holdBackQueueTotal.get(i);
                int j;
                for(j=i-1; (j >= 0) && (holdBackQueueTotal.get(j).compareTo(key) > 0); j--) {
                    holdBackQueueTotal.set(j+1, holdBackQueueTotal.get(j));
                }

                holdBackQueueTotal.set(j+1, key);
            }



            Log.i(TAG, "After sort");
            display(holdBackQueueTotal);
        }

        private void finalDeliver(Packet packet) {
            Log.i(TAG, "Inside finalDeliver");

            ContentValues cv = new ContentValues(2);
            Log.i(TAG, "Final received "+packet);

            String key = Long.valueOf(receiveCounter).toString();
            receiveCounter++;


            cv.put(KEY_FIELD, key);
            cv.put(VALUE_FIELD, packet.getContent());

            getContentResolver().insert(uri, cv);
            CharSequence newText = key + ": P_" + packet.getPriority() + " PPSR: " + packet.getPriorityProposer() + " ID: " + packet.getMessageSender() + "." + packet.getMessageID() + "\n\n";

            Log.i("FINALOUTPUT", newText.toString());

            publishProgress(newText.toString());
        }

        private boolean gotAllPriorities(Long arr[]) {

            Log.i(TAG, "Got all priorities called with arr: "+Arrays.toString(arr));

            for(int i=0; i<arr.length; i++) {
                if(isAlive[i] && arr[i] == -1) {
                    Log.i(TAG, "Returning false");
                    return false;
                }
            }
            Log.i(TAG, "Returning true");
            return true;
        }
        private int max(Long arr[]) {

            long maxVal = arr[0];
            int maxPos = 0;

            for(int i=0; i<arr.length; i++) {
                if(arr[i] > maxVal) {
                    maxVal = arr[i];
                    maxPos = i;
                }
            }

            return maxPos;
        }



        /*
         * Android Required Code--------------------------------------------------------------------
         */

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            try {
                while (true) {

                    /*
                     * Read incoming message
                     */
                    Socket socket = serverSocket.accept();
                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String formatString = br.readLine();

                    Log.i(TAG, "Format String: "+formatString);

                    Packet msgReceived = new Packet(formatString);

                    br.close();
                    socket.close();



                    /*
                     * Message handling code
                     */

                    switch (msgReceived.getMessageType()) {

                        case INITIAL_DELIVERY:
                            Log.i(TAG, "Received initial packet "+msgReceived);
                            totalReceive(msgReceived);
                            break;

                        case PRIORITY_PROPOSAL:
                            Log.i(TAG, "Received proposal packet "+msgReceived);
                            registerProposal(msgReceived);
                            break;

                        case FINAL_DELIVERY:
                            Log.i(TAG, "Received final packet "+msgReceived);
                            totalSetDeliverable(msgReceived);
                            totalDeliver();
                            break;

                        default:
                            return null;

                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Server IOException");
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... newTexts) {
            tv.append(newTexts[0]);
        }
    }




    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
}
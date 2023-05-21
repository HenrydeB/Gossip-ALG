/* 
 * 1. Name: Henry deBuchananne
 * 2. Date: 2023-05-21
 * 3. Java Version: 20 (build 20+36-2344)
 * 4. Command-line compilation: javac Gossip.java
 * 5. Instructions to run the program:
 *    To run the program, you need to ensure you pass in at least one number between 0 and 9 where I put the '%%' symbol:
 *    > java Gossip %%
 * This will enable you to assign a port and an ID to the node(s)
 * passing in multiple values like the following example will create multiple nodes
 * > java Gossip 0 1 2 3
 * here you will create 4 nodes: Node0, Node1, Node2, Node3
 * 
 * 6. Full list of files needed: 
 * --Gossip.java
 * 
 * 7. Notes:
 * --I did use the  code provided by prof Elliot to get the basic idea of how to do UPD packaging
 * if I did anything to change it so that it is no longer UDP, I am totally unaware
 * 
 * Thank Yous:
 * - to Dr. Clark Elliot for providing the GossipStarter.java file
 * - Ian F. Darwin and O'Rielly publishing for 'Java Cookbook: Problems and Solutions for Java Developers 4ed', which helped translate my thoughts in C# to Java ... https://www.oreilly.com/library/view/java-cookbook-4th/9781492072577/
 * - The Oracle team and the Java documentation, so I could ensure I could create the structures that I wanted to (such as the nested map in my Node class)
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.time.*;


/* 
 * For the Gossip Data class (and later the node class) I utilized a lot of different varaibles to represent the
 * different values we were looking for, and some other values that helped with the functions and organization
 * 
 * Note that this class implements both seralizable for passing data AND Comparable, which I will explain later:
 */

class GossipData implements Serializable, Comparable<GossipData>{
    int MID; //this is the message ID, which I use to keep track if I had seen it or not
    String mType; //For some functions, I use types such as "request" and "response", this is what signifies that
    int number; //number is the port number we are shooting for
    int avg; //current calculated average
    double size; //current calculated size
    int highVal; //current max
    int lowVal;// current min
    String info; //what command was passed to trigger this event
    int from; //what was the port that sent this
    int id; //what was the node ID that sent this
    int src; //what was the source node
    int cycle; //what cycle are we on
    LocalDateTime created; // when this was created

    //To mitigate issues with overlapping messages, I implemented a Priority Queue in the server code that goes
    //off of the message "created" LocalDateTime value
    @Override
    public int compareTo(GossipData gossip){
        return created.compareTo(gossip.created);
    }

    //constructore for Gossip data
    public GossipData() {
        this.MID = SetMesssageID();
        this.mType = "";
    }
    //This gives the message a random message with 5 digits
    public int SetMesssageID(){
        Random r =  new Random();
        return r.nextInt(90000) + 10000;
    }
}

class GossipWorker extends Thread{
    GossipData gossip;
    Node node;
    GossipWorker (GossipData g, Node n) { //initialize GossipWorker variables
        gossip = g;
        node = n;
    }

    public void run(){
        //before we do anything, I want to check to see if we hit the max number of cycles for 
        // our gossip. If we did we just cancel out of this process
        if(gossip.cycle == 0){
            System.out.println("\nMaximum cycles reached\n");
            return;
        }

        /* 
         * Apologies for this, I know it is a bit messy. I wanted the main gossip worker code to essentially 
         * just route to the correct helper function, and this is how I thought of implementing the process initially
         */
        if(gossip.info.equals("Hello") && gossip.number == node.port){ 
            //if we get a message "Hello" and the current port is our target, then we received a ping and need to send it back
            ReturnPing(gossip, node);
        } else if(gossip.info.equals("Hello") && gossip.number != node.port) {
            //If the info is "hello" and we are currently at the source of the message, we need to package the data to send it
            SendPing(gossip, node.port);
        } else if(gossip.info.equals("Here") && node.issuedCmd.equals("p")){
            //This part handles receiving the response
            ReceivePing(gossip, node);
        } else if(gossip.info.equals("ran")){
            //if the info is indicating we need to randomize, we follow these steps
            HandleRandomization(gossip, node);
        } else if(gossip.info.equals("MinMax")){
            //MinMax triggers the checking for min or max across the [sub]network
            CheckMinMax(gossip, node);
        } else if(gossip.info.equals("n")){
            //If we want to change the number of cycles, this process takes care of this
            SetCycles(gossip, node);
        } else if(gossip.info.equals("d")){
            //This takes care of deleting the current node, sending it to our own server
            Update(gossip, node, node.port);
        } else if(gossip.info.equals("a")){
            //handles the calculation of the average in the [sub]network
            Average(gossip, node); 
        } else if(gossip.info.equals("z")){
            //this handles the calculation of the size in the [sub]network
            Average(gossip, node);
        }

        //Our node object keeps track of the 15 most recently seen messages, if we get higher than 15, we clear the list
        //ideally this should just push out the oldest message and add the new one, this could be a future change
        if(node.Seen.size() == 15)
            node.Seen.clear();
    }

    //Handles the resetting of the new cycles
    private void SetCycles(GossipData gossip, Node node){
        if(node.Seen.containsKey(gossip.MID))//If our "Seen" data already contains this message, then we don't have to set the cyles again
            return;
        
        System.out.println("\nNode" + node.id + ": Setting Cycles\n");
        node.cycle = gossip.cycle; //set the local node cycle number to the gossip cycle value
        System.out.println("Node" + node.id +": cycle changed to " + node.cycle);
        if(gossip.src == node.id)
            SetUpdateDirection(gossip, node, true); //if this is the source node, then we want the isUpdated parameter to be true
        else
            SetUpdateDirection(gossip, node, false); //otherwise we want it to be false
    }

    private void Average(GossipData gossip, Node node){
        Timer timer = new Timer(); //I implemented a timeout  for this function, this starts that process

        if(gossip.mType.equals("")){ //if the source is the current node and the current average is 0 (our starting point)
            gossip.cycle--; //lower the cycle value
            node.RecordMessage(gossip); //record the current message in the current node
            gossip.mType = "req"; //set the new message type to "request"

            if(gossip.info.equals("a")) //if the command we passed was "average" then we need to check the edge case of whether the node.avg value is 0
                gossip.avg = (node.avg == 0) ? node.data : node.avg; //if it is then we base our calculation off of the "data" value
            else if(gossip.info.equals("z")) //if the command is passed is "size" then we can just use the node size value
                gossip.size = node.size;
            //We need to target the desired port. The way I implemented this process is to have it to "ping pong" back and forth acrosss the network
            //the following sets the code to follow this process. If the gossip just came from the source or "from" is the "previous" node to our  current node,
            //then we want to continue in the same direction going "next" otherwise we want to go to "previous"
            int target = (gossip.from == node.port || gossip.from == node.previous.get("port")) ? node.next.get("port") : node.previous.get("port"); 
            Update(gossip, node, target); //send the "update" data to the next node
            timer.schedule(new TimerTask() {
            //start the timeout
            public void run(){
                //if we didn't get a response from our target node, then we need to try the node in the opposite direction
                if(!node.Seen.get(gossip.MID).containsKey("res")){
                    if(target == node.next.get("port"))
                        Update(gossip, node, node.previous.get("port"));
                    else
                        Update(gossip, node, node.next.get("port"));
                } else
                    return;
            }
            }, 5000); //set the time for the time out
        } else if(gossip.mType.equals("req")){
            //if the message type is "request"
            node.RecordMessage(gossip); //record the message
            if(gossip.info.equals("a")){
                //for an "average" calculation, we want to calculate the average with the "Calc" function
                gossip.avg = Calc(gossip.avg, node.avg, node.data);
                System.out.println("\nNode" + node.id + ": Request from " + gossip.from + " New avg= " + gossip.avg + "\n");
                node.avg = gossip.avg; //set the node's average as the current average
            } else if( gossip.info.equals("z")){
                //if we are calculating the size, we need to go through a different process
                System.out.println("\nNode" + node.id + ": Calculating size from gossip " + gossip.size + " and local node " + node.size);
                gossip.size = CalcSize(gossip.size, node.size); //size has it's own function. I tried to have the same one, but there were slight differences I had to account for
                node.size = gossip.size; //set the local size
                double recip = 1.0/gossip.size;
                double round = (double)Math.round(recip * 100) / 100;
                System.out.println("Node" + node.size + ": Network size is " + node.size);
            }
            gossip.mType = "res"; //set the message to be a "response"
            Update(gossip, node, gossip.from); //send the message back to whence it came
            gossip.mType = ""; //set the message to a blank message type
            int target = (gossip.from == node.next.get("port"))? node.previous.get("port") : node.next.get("port"); //prepare the target node to be the next in the line of the network
            Update(gossip,node, target); // move on to the next node
            //Similar to above, I set a timeout here to wait to see if we got a response, otherwise we reset to the next port
            timer.schedule(new TimerTask() {
                public void run(){
                    if(!node.Seen.get(gossip.MID).containsKey("res")){
                        if(target == node.next.get("port"))
                            Update(gossip, node, node.previous.get("port"));
                        else
                            Update(gossip, node, node.next.get("port"));
                    } else
                        return;
                }
                }, 5000);
        }else{
            //If our message is a "response" then we have a much cleaner process
            node.RecordMessage(gossip); //record the gossip message
            if(gossip.info.equals("a")) {
                //display whether we got the message
                System.out.println("\nNode" + node.id + ": Response from " + gossip.from + " New avg= "+ gossip.avg + "\n");
                node.avg = gossip.avg; // set new average
            } else if(gossip.info.equals("z")){
                System.out.println("\nNode" + node.id + ": Response from " + gossip.from + " New size= "+ gossip.size + "\n");
                node.size = gossip.size; //set new size
            }
            return;
         }

    }
 
    //For calculating the "average"
    private int Calc(int gossip, int local, int data){
        int average = 0;
        if(local == 0){ //if our local average value is 0, then we need to go off of the "data" value of our local node
            average = gossip + data;
            average = average / 2; //compute average
        } else {
            average = gossip + local; //otherwise we can use the local average value
            average = average / 2;
        }
        return average;
    }
    // caluclating the size of the network
    private double CalcSize(double gossip, double local){
        double average = 0.0;
        average = gossip + local;
        average = average / 2.0;
        
        return average; //return average between the two values
    }

    //some functions utilize this to help decide which direction the update should go
    private void SetUpdateDirection(GossipData gossip, Node node, boolean isUpdated){
        //each node is aware of where their neighbors should be
        int next = node.next.get("port");
        int prev = node.previous.get("port");

        if(isUpdated){ //add check so that it doesn't run twice. Maybe we set if it has seen the message after update?
        //if the value we are checking has been updated, then we want to send it across the network both directions, this is what this does
            node.RecordMessage(gossip);
            gossip.from = node.port;
            Update(gossip, node, next); //go to "next"
            Update(gossip, node, prev); //go to "previous"
        }
        node.RecordMessage(gossip); //record the message
        //if we came from (generally) this.port -1, then move to the next port (this.port + 1 in general)
            if(gossip.from == prev){
                Update(gossip, node, next);
            } else if(gossip.from == next){
                //otherwise if we came from (generally) port + 1, go to port - 1
                Update(gossip, node, prev);
            }
    }
    
    //This is the workhorse of this assignment
    //I believe this is following the UPD process of creating a message, as I got this from the example code
    private void Update(GossipData gossip, Node node, int target){
        try{
            DatagramSocket dgSock = new DatagramSocket(); //create socket
            InetAddress IPAddress = InetAddress.getByName("localhost"); //create IP variable
            gossip.from = node.port; //before we send a message, make sure we set that this gossip is coming from this node
            gossip.id = node.id; //the gossip id came from current id
            gossip.number = target; //set the gossip number (the desired port) to target
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream outObj = new ObjectOutputStream(out);
            outObj.writeObject(gossip); // we want to create and package our gossip into a streamable object (above two steps)
            byte[] data = out.toByteArray(); //turn our information into a byte array
            DatagramPacket send = new DatagramPacket(data, data.length, IPAddress, gossip.number); //prepare packet to send
            dgSock.send(send); //send it!!
            dgSock.close(); //close the socket as we do not need it anymore
            return; 
        } catch(IOException io){
            io.printStackTrace();
        } 
    }

    //adjust to fit our new method SetUpdateDirection
    private void CheckMinMax(GossipData gossip, Node node){
        //if we have already seen this message
        if(node.Seen.containsKey(gossip.MID)){
            //and if the min of the gossip is the current data or the max of the gossip is the current data, no need to update
            if((gossip.lowVal == node.data || gossip.highVal == node.data) )
                return;
        } else {
            //otherwise record the message on the node
            node.RecordMessage(gossip);
        }

        boolean isUpdated = false; //this isUpdated is used in SetUpdateDirection to determine if this message should either continue or "restart"
        gossip.cycle--; //we have read a message, so we need to recognize that we are going through the network
        if (gossip.cycle == 0){
            return;
        }

        /* 
         * Quick Note: I may have misunderstood the range of the values for min and max. I had understood that the minimum is only 0 if the
         * process had not started yet and a Node could not have a min of 0 because it should have SOMETHING... though 0 is something I guess
         * The following goes based on my assumption of 0
         */
        if(gossip.lowVal == 0){
            //if the current min is 0 then we want to set the lowval to the current node's data
            gossip.lowVal = node.data;
            node.kMin =  gossip.lowVal; //we want the node's known min to be the gossip's lowval
            isUpdated = true; //we have an update! set to true
        }
        else if(gossip.highVal == 0){
            //same with min, if the highVal is 0, then we need to set it to the current node's value
            gossip.highVal = node.data; 
            node.kMax = gossip.highVal; //set the node's known max to the highval
            isUpdated = true;//we have an update! set to true
        }
        else if(gossip.lowVal > node.data){
            //if our gossip's low value is greater than our node's data, then we need to set it to the node's data
            gossip.lowVal = node.data;
            node.kMin = gossip.lowVal; //update known min 
            isUpdated = true;//we have an update! set to true
        }else if(gossip.highVal < node.data){
            //if our gossip's high value is less than the node's data, then we set it to the node's data
            gossip.highVal = node.data;
            node.kMax = gossip.highVal; //update the known max value
            isUpdated = true;//we have an update! set to true
        }

        if(isUpdated){
            //full update
            System.out.println("\nNode" + node.id + ": current min = " + gossip.lowVal + 
            "\nNode" + node.id + ": current max = " + gossip.highVal);
            gossip.MID = gossip.SetMesssageID(); //if we encountered a full update, then we need to prepare the packet to send in all directions
            gossip.created = LocalDateTime.now(ZoneOffset.UTC); // this is like we are restarting so we want to set a new created time
            gossip.cycle = node.cycle; // and we also want to restart the cycle
            SetUpdateDirection(gossip, node, isUpdated); //prepare to set the directions
        } else {
            //Continue through the process
            System.out.println("\nNode" + node.id + ": current min = " + gossip.lowVal + 
            "\nNode" + node.id + ": current max = " + gossip.highVal);    
            SetUpdateDirection(gossip, node, false);
        }
    } 

    private void HandleRandomization(GossipData gossip, Node node){
        //if we have already seen this message
        if(node.Seen.containsKey(gossip.MID))
            return;
        
        if(gossip.src != node.id){
            //the source node gets updated at the start, but if this isn't the source
            int prevData = node.data; //store previous data so we can display the old and new values
            node.data = node.SetData(); //randomly set the new value
            node.avg = 0; //the average in the network is 0 now because we have a new data set
            System.out.println("\nNode" + node.id + ": Previous data in Node = " + prevData); //print old and new data
            System.out.println("Node" + node.id + ": Current data in Node = " + node.data +"\n");      
        }
        SetUpdateDirection(gossip, node, true); //prepare to send to correct port
    }


    private void ReceivePing(GossipData gossip, Node node){
        if(gossip.from == node.previous.get("port")){
            node.previous.put("active", 1); //if we received a ping, we set the "active" part to 'true'
            System.out.println("Node" + node.id + ": received ping from Node" + gossip.id); //recognize that we got a ping
            node.previous.put("prevID", gossip.id); //set this node's previous node id to the gossip id because that is where it came from
        } else if(gossip.from == node.next.get("port")) {
            node.next.put("active", 1); //if we received a ping from the "next" node then we set it active
            System.out.println("Node" + node.id + ": received ping from Node" + gossip.id);
            node.next.put("nextID", gossip.id); //set the ID of the node in the current node's nextID to the gossip id
        }
    }

    //this function is repeated from the "UPDATE" function, this just follows the same pattern for sending values across the network
    private void SendPing(GossipData goss, int currentPort){
        try{
            DatagramSocket dgSock = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName("localhost");

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ObjectOutputStream outObj = new ObjectOutputStream(outStream);

            outObj.writeObject(goss);

            byte[] data = outStream.toByteArray();
            DatagramPacket send = new DatagramPacket(data, data.length, IPAddress, goss.number);
            dgSock.send(send);
            dgSock.close();
        } catch(IOException io){
            io.printStackTrace();
        }
    }

    //this again follows very similar UDP message sending code, with the purpose of returning to the previous port with a few differences
    private void ReturnPing(GossipData goss, Node node){
        try{
            System.out.println("Node" + node.id + ": Preparing datagram packet to send to " + goss.from);
            DatagramSocket dgSock = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName("localhost");
    
            //we create a whole new gossip object to send back
            GossipData gossipObj = new GossipData();
            gossipObj.info = "Here"; //new message signifying that we are here and active
            gossipObj.from = node.port; //from is current port
            gossipObj.number = goss.from; //set port target to where we just came from
            gossipObj.id = node.id; //the id of this gossip is this current node id
            gossipObj.created = LocalDateTime.now(ZoneOffset.UTC); //this is a new message so we have a new created date
            gossipObj.MID = gossipObj.SetMesssageID(); //also need to set a new message ID
            //package and send the new data
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ObjectOutputStream outObj = new ObjectOutputStream(outStream);
            outObj.writeObject(gossipObj);
            byte[] data = outStream.toByteArray();
            DatagramPacket send = new DatagramPacket(data, data.length, IPAddress, gossipObj.number);
            dgSock.send(send);
            System.out.println("Node" + node.id +": Datagram has been  sent to port: " + gossipObj.number);
            dgSock.close(); 
        } catch(IOException io){
            io.printStackTrace();
        }
    }

}

//Like our GossipData class, the node class has a lot of data as well
class Node {
    int id; //the ID of the current node
    int data; //the current data of the node
    double size; //size of the network
    int avg; //average of the network
    int cycle = 20; //default total cycles is 20
    int port; // port number for this port
    String issuedCmd; //command from command line
    HashMap<String, Integer> next = new HashMap<>(); //stored information for this node's port + 1 (generally)
    HashMap<String, Integer> previous = new HashMap<>(); //stored information for this node's port - 1 (generally)
    Map<Integer, Map<String, LocalDateTime>> Seen = new HashMap<>(); //We have a nested map that stores the Message ID and the message's type and the time it was created
    int kMin; //known min
    int kMax; //known max

    //the node constructor
    public Node(String id){
        this.id = Integer.parseInt(id); //the ID comes from the command line, which we pass into here when we initialize the node
        this.data = SetData(); //set the local data 
        this.port = SetPort(this.id); //we set the port with the ID number
        //we use this for "PING", if we get a response, the 0 is flipped to 1
        this.next.put("active", 0);
        this.previous.put("active", 0);

        //In this exercies the neighboring ports are in order, the following is a simple
        // calculation for all neighbors, including the edge ones that wrap around
        int nextPort = (this.port == 48109) ? 48100 : this.port +1;
        int prevPort = (this.port == 48100) ? this.port + 9 : this.port - 1;
        //add the neighbor ports to their respective HashMap
        this.next.put("port", nextPort);
        this.previous.put("port", prevPort);
    }

    public void RecordMessage(GossipData gossip){
        //custom method to record the current message into our hashmap
        Map<String, LocalDateTime> inner = new HashMap<>();
        inner.put(gossip.mType, LocalDateTime.now(ZoneOffset.UTC));
        Seen.put(gossip.MID, inner);
    }

    public int SetData(){
        //randomly sets data between 1 and 100
        Random r = new Random();
        return r.nextInt(99) + 1;
    }
    //the "Tell" function is the response to the cmd input 't'
    public void Tell(){
        System.out.println("\n=====Available Commands=====");
        System.out.println("'t': Prints available commands");
        System.out.println("'l': Prints local Node values");
        System.out.println("'p': 'Pings' neighboring ports to check for active nodes");
        System.out.println("'v': Generates new random values for all nodes in Network");
        System.out.println("'m': Finds the minimum and maximum values in the network");
        System.out.println("'n': Sets the total number of Cycles we can have in our [sub]network (default =  20)");
        System.out.println("'a': Calculates the average value in the network");
        System.out.println("'z': Calculates the size of the network");
    }
    //this is the response to the cmd input 'l' which shows the local node values
    public void Locals(){
        System.out.println("=====Local Data=====");
        System.out.println("Node ID:            "+ this.id);
        System.out.println("Node Port location: " + this.port);
        System.out.println("Local Data:         " + this.data);
        System.out.println("Total size:         " + this.size);
        System.out.println("Average Value:      " + this.avg);
        System.out.println("Current Cycle:      " + this.cycle);
        System.out.println("Next Node port:     " + this.next.get("port"));
        System.out.println("Previous Node port: " + this.previous.get("port"));
    }
    //sets the port based on our ID
    private int SetPort(int id){
        int port = 48100 + id;
        return port;
    }
}

 class Gossip{
//This is the class that gets run, the "main" class of this project
    public static void main(String[] args) throws Exception{
        Node current = new Node(args[0]); // when this gets run, we first need to initialize the node
        PriorityQueue<GossipData> queue = new PriorityQueue<>(); //I encountered some issues with overloading of messages, setting up this priority queue seemed to do the trick

        System.out.println("Henry deBuchananne's Gossip Server 1.0 booting up, listening at port " + current.port + "\n");
        //here we initialize the Console looper, which listens for input from the console
        ConsoleLoop loop = new ConsoleLoop(current);
        Thread t = new Thread(loop);
        t.start();
        
         boolean control = true; //this will control the loop duration
         try{
            DatagramSocket dgsock = new DatagramSocket(current.port);
            //we set the buffer size for our socket. This seems to do the job for the project
            System.out.println("SERVER: Buffer size: " + dgsock.getReceiveBufferSize() + "\n");
            byte[] inData = new byte[1024];
            InetAddress IPAddr = InetAddress.getByName("localhost");

            while(control){
                //This datagram packet is the information that we are waiting to be sent to use from the myriad of gossip workers in the network
                DatagramPacket inPacket  = new DatagramPacket(inData, inData.length);
                dgsock.receive(inPacket); //we receive the data in the socket
                byte[] data = inPacket.getData(); //put the data in the array

                //this packages the data in a way that lets it be usable to us to place within a gossip object
                ByteArrayInputStream in = new ByteArrayInputStream(data);
                ObjectInputStream inStream = new ObjectInputStream(in);

                try{
                    GossipData gObj = (GossipData) inStream.readObject(); //we cast the data type of GossipData onto the instream object
                    if(gObj.info.indexOf("stopserver") > -1){
                        //if the info from the gossip is "stop server" then we need to kill this process
                        System.out.println("SERVER: Stopping UPD listener \n");
                        control = false;
                        dgsock.close();
                    } else if (gObj.info.indexOf("d") > -1){
                        //if we want to delete the node, we have to go through a few steps
                        System.out.println("Node" + current.id + ": Deleting Node" + current.id);
                        //we have the ConsoleLooper thread join back up with us, which should effectively end that thread based on the way it handles the cmd input of "d"
                        t.join();
                        dgsock.close(); //close the socket this node is on
                        control = false; //set the loop to false, and that should wrap up this process
                    }
                    //Otherwise, we need to add our gossip object to our priority queue
                    //With the @Override compare function, the polling SHOULD be based on the Creation time of the message itself
                    // This seems to straigten out the issue I was having with messages getting crossed
                    queue.add(gObj);
                    while(!queue.isEmpty()){ //we have this loop run while it isn't empty, polling the PQ to get the messages we need to respond to and send
                        GossipData obj = queue.poll();
                        if(obj.cycle == 0){ //however if we already reached the end of the cycle we have to jump out of this
                            System.out.println("MAX NUMBER OF CYCLES REACHED");
                            break;
                        }
                        new GossipWorker(obj, current).start(); //if we haven't broken out, we start the gossip worker
                    }

                    //standard error handling dealing with in case the GossipWorker cannot be found && IOException
                } catch (ClassNotFoundException e){
                    e.printStackTrace();
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
            if(control = false){
                //if our loop has broken, we exit out of this process
                System.exit(0);
            }
        } catch (SocketException e){ //error handling in case we cannot connect to the socket
            e.printStackTrace();
        } catch(IOException io){ //input/output handling
            io.printStackTrace();
        } 
    }
}

class ConsoleLoop implements Runnable{
    /* 
     * This class is a thread that handles getting information from the command line
     * the main method mostly handles the reception of commands and then referring them to the correct helper function
     */
    Node node;
    public ConsoleLoop(Node n){
        node = n; //takes in the local node from the main Gossip thread
    }

    public void run(){
        //The buffered reader reads in the information from the command line using the InputStreamReader System.in
        BufferedReader read = new BufferedReader(new InputStreamReader(System.in)); 
        boolean control = true;
        try{
            String str;//set up and empty  string to set the input to
            System.out.print("Enter 't' to list available commands, or (quit/stopserver): \n");
            do{
                //clear out the command line so we can read the input into the str variable
                System.out.flush();
                str = read.readLine();
                //from here, we have a switch statement that decides which function to run based on the input command
                switch(str){
                    case "quit":
                        node.issuedCmd = "quit"; //kills the local process
                        System.out.println("Exiting process per user request\n");
                        System.exit(0);
                        break;
                    case "t":
                        node.issuedCmd = "t";
                        node.Tell(); //this will trigger the node's method that tells us what available commands are
                        break;
                    case "l":
                        node.issuedCmd = "l"; //shows us what data we have stored currently in the node
                        node.Locals();
                        break;
                    case "p":
                        node.issuedCmd = "p"; //begin the pinging process, checking to see if there are active neighbors
                        Ping(node);
                        break;
                    case "m":
                        node.issuedCmd = "m";
                        StartGossip(node, "MinMax"); //most of the logic for MinMax lies in the gossip worker, so we want to package the gossip and get to it
                        break;
                    case "a":
                        node.issuedCmd = "a";
                        StartGossip(node, "a");//most of the logic for average lies in the gossip worker, so we want to package the gossip and get to it
                        break;
                    case "z":
                        node.issuedCmd = "z";
                        node.size = 1;
                        StartGossip(node, "z");//most of the logic for size lies in the gossip worker, so we want to package the gossip and get to it
                        break;
                    case "v":
                        node.issuedCmd = "v";
                        int prevData = node.data;
                        node.data = node.SetData();// For randomization, we randomize on the local node here, we use SetData() for this
                        node.avg = 0; //when we do this, we need to make sure we set the average to 0
                        System.out.println("\nNode" + node.id + ": Previous data in Node = " + prevData);
                        System.out.println("Node" + node.id + ": Current data in Node = " + node.data + "\n"); 
                        StartGossip(node, "ran"); //now we have to 'spread the word'
                        break;
                    case "n":
                        node.issuedCmd = "n";
                        GetInput(node, read);//to create a new cycle, I created a function that takes in a new command from the command line, just to separate concerns
                        StartGossip(node, node.issuedCmd); //now we start the gossip from here
                        break;
                    case "d":
                        node.issuedCmd = "d";
                        control = false; // we set this to false so that we can break out of the process here and rejoin the main thread
                        StartGossip(node, node.issuedCmd);//we do this by calling the gossip worker back on our local node
                        break;
                }
            } while(control);

        } catch(IOException io) { io.printStackTrace();}
    }

    public void Ping(Node node){
        //if we are pinging locally, we basically need to make sure that we don't already know that they are active. Kinda like a fresh start
        node.next.put("active", 0);
        node.previous.put("active", 0);
        //now we check if the node exists on either side
        NodeExists(node.next.get("port") ,node); 
        NodeExists(node.previous.get("port"),node);
    }

    private void NodeExists(int port, Node node){
    //The first step is to package the gossip data and send it to the gossip worker to handle sending it to the right location
      GossipData goss = new GossipData();
      goss.info = "Hello";
      goss.number = port;
      goss.from = node.port;
      goss.created = LocalDateTime.now(ZoneOffset.UTC);
      goss.MID = goss.SetMesssageID();
      new GossipWorker(goss, node).start();
      //I created a timeout that waits to see if the "active" k/v pair is set to 1 for the port that we targeted
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
        public void run(){
            if(goss.number == node.previous.get("port")){
                //if the target port is "previous" and if the node is active, then we say it is active. If not then we have a different message saying that it is not active
                String response = (node.previous.get("active") == 1) ? "NODE" + node.id + ": Neighbor Node " + node.previous.get("prevID") + " is active on Port: " + goss.number 
                : "NODE" + node.id + ": Inactive Node on Port: "+ goss.number;
                System.out.println(response);
            } else if(goss.number == node.next.get("port")){
                //if the target port is "next" and if the node is active, then we say it is active. If not then we have a different message saying that it is not active
                String response = (node.next.get("active") == 1) ? "NODE" + node.id + ": Neighbor Node " + node.next.get("nextID") + " is active on Port: " + goss.number 
                : "NODE" + node.id + ": Inactive Node on Port: " + goss.number;
                System.out.println(response);
            }
        }
        }, 1000);
    }

    //This function is just so we can pull further input from the user
    private void GetInput(Node node, BufferedReader read){
        try{
            System.out.println("How many cycles would you like to have?");
            String in = read.readLine();
            node.cycle = Integer.parseInt(in);
            System.out.println("Updating cycles to " + in + " cycles");
        } catch(IOException e){
            e.printStackTrace();
        }
    }

    //This creates a GossipData object to send to the gossip worker 
    private void StartGossip(Node node, String cmd){
        GossipData goss = new GossipData();
        goss.number = (cmd.equals("d")) ? node.port : node.next.get("port"); //if the command is "delete", then we send it back to us, otherwise we set it up so that we send it to the "next" node (if we send to previous as well or instead the GossipWorker handles the change)
        goss.from = node.port; //the gossip is coming from the current port
        goss.src = node.id; //since we started the gossip here, the source is here
        goss.cycle = node.cycle; // set the total cycles to the max cyles in a node
        goss.info = cmd; // the info is whatever cmd we passed in
        goss.size = node.size; //size is the known size in the network
        goss.created = LocalDateTime.now(ZoneOffset.UTC); //gossip  was created at current time but in UTC time
        goss.MID = goss.SetMesssageID(); // set the message ID
        new GossipWorker(goss, node).start(); //send it on it's way
    }
}

/* 
 * Comments:
 * ================================
 * The protocol, I'm sure we can find something to gossip about regarding the news or something. 
 * While learning about the gossip protocol, the way it worked sounded familiar. Apache Cassandra implements the Gossip protocol, 
 * linked are the docs and a quick rundown on how it works from YouTube! (If this is not appropriate I can take this down)
 * https://docs.datastax.com/en/cassandra-oss/3.x/cassandra/architecture/archGossipAbout.html#:~:text=Internode%20communications%20(gossip)-,Cassandra%20uses%20a%20protocol%20called%20gossip%20to%20discover%20location%20and,other%20nodes%20they%20know%20about.
 * https://www.youtube.com/watch?v=ziq7FUKpCS8
 * 
 * 
 * So, to be clear, for size we are basically just looking at the number of nodes in the network. 
 * Whereas for Average, we are taking the average of the values of the network and creating an overall average. 
 * The value that we use for the average is the same value that can be affected by the randomization, min and max, etc.
 * 
 * Did I understand that correctly? I worry that I have misunderstood this part
 */

 /* 
  * TODO:
  * NETWORK WIDE UNLESS OTHERWISE SPECIFIED
  *  - m: Print minimum and maximum values in network ALL NODES => GOOD
  *  - a: calculate the average of all the local values in the network HARD
  *  - z: calculate the average of the local values in the network HARD
  *  - d: delete the current node. fully stop the process, gracefully close the socket (see HostServer) LOCAL
  *  - k: kill the entire network Nodes may need to stick around to kill future nodes
  *  - y: display the number of cycles since the beginning
  *  - n: set N as the max value of gossip messags for the network

  NEED TO UPDATE CODE FOR JUST LOCAL CALLS



  */
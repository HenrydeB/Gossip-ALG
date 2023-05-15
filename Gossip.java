import java.io.*;
import java.net.*;
import java.util.*;
import java.time.*;




class GossipData implements Serializable{
    int MID;
    int number;
    int avg;
    int highVal;
    int lowVal;
    String info;
    int from;
    int id;
    int src;
    int cycle;
    LocalDateTime created;

    public GossipData() {
        this.MID = SetMesssageID();
    }

    public int SetMesssageID(){
        Random r =  new Random();
        return r.nextInt(90000) + 10000;
    }
}

class GossipWorker extends Thread{
    GossipData gossip;
    Node node;
    GossipWorker (GossipData g, Node n) {
        gossip = g;
        node = n;
    }

    public void run(){
        //run object
        if(gossip.info.equals("Hello") && gossip.number == node.port){
            ReturnPing(gossip, node);
        } else if(gossip.info.equals("Hello") && gossip.number != node.port) {
            SendPing(gossip, node.port);
        } else if(gossip.info.equals("Here") && node.issuedCmd.equals("p")){
            ReceivePing(gossip, node);
        } else if(gossip.info.equals("ran")){
            HandleRandomization(gossip, node);
        } else if(gossip.info.equals("MinMax")){
            CheckMinMax(gossip, node);
        } else if(gossip.info.equals("N")){
            SetCycles(gossip, node);
        }

        if(node.Seen.size() == 15)
            node.Seen.clear();
    }

    private void SetCycles(GossipData gossip, Node node){
        if(node.Seen.containsKey(gossip.MID))
            return;
        else
            node.Seen.put(gossip.MID, gossip.created);

        if(node.cycle != gossip.cycle)
            node.cycle = gossip.cycle;

        SetUpdateDirection(gossip, node, true);
    }

    private GossipData CreateGossipMessage(GossipData old, Node node){
        GossipData newGossip = new GossipData();
        newGossip = old;
        newGossip.MID = newGossip.SetMesssageID(); //may not have to deal with this
        newGossip.src = node.id;
        newGossip.number = node.port;
        newGossip.from = node.port;

        return newGossip;
    }

    private void SetUpdateDirection(GossipData gossip, Node node, boolean isUpdated){

        if(gossip.src == node.id && !(node.Seen.containsKey(gossip.MID))){ //add check so that it doesn't run twice. Maybe we set if it has seen the message after update?
            node.Seen.put(gossip.MID, gossip.created);
            Update(gossip, node, node.next.get("port"));
            Update(gossip, node, node.previous.get("port"));
            return;
        }
        node.Seen.put(gossip.MID, gossip.created);

        //this is the general idea of what we are trying to accomplish here, will probably have to adjust
        if(gossip.from == node.previous.get("port")){
            Update(gossip, node, node.next.get("port"));
            return;
        } else if(gossip.from == node.next.get("port")){
            Update(gossip, node, node.previous.get("port"));
            return;
        }
    }
    
    private void Update(GossipData gossip, Node node, int target){
        try{
            DatagramSocket dgSock = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName("localhost");

            gossip.id = node.id;
            gossip.number = target;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream outObj = new ObjectOutputStream(out);
            outObj.writeObject(gossip);
            byte[] data = out.toByteArray();
            DatagramPacket send = new DatagramPacket(data, data.length, IPAddress, gossip.number);
            dgSock.send(send);
            dgSock.close();
            return;
        } catch(IOException io){
            io.printStackTrace();
        } 
    }

    //adjust to fit our new method SetUpdateDirection
    private void CheckMinMax(GossipData gossip, Node node){
        //if our node is already updated, ignore .. May need to change how the part of returning home is
        //idk if we are supposed to return home after updating all values?
        if(node.Seen.containsKey(gossip.MID)){
            if((gossip.lowVal == node.data || gossip.highVal == node.data) )
                return;
        } else {
            node.Seen.put(gossip.MID, LocalDateTime.now(ZoneOffset.UTC));
        }
        boolean isUpdated = false;


        //If randomization has happened in the process, we need to reset the local node's
        //data just in case

        if(gossip.lowVal == 0){
            gossip.lowVal = node.data;
            node.kMin =  gossip.lowVal;
            isUpdated = true;
        }
        else if(gossip.highVal == 0){
            gossip.highVal = node.data;
            node.kMax = gossip.highVal;
            isUpdated = true;
        }
        else if(gossip.lowVal > node.data){
            gossip.lowVal = node.data;
            node.kMin = gossip.lowVal;
            isUpdated = true;
        }else if(gossip.highVal < node.data){
            gossip.highVal = node.data;
            node.kMax = gossip.highVal;
            isUpdated = true;
        }

        if(isUpdated){
            System.out.println("\nNode" + node.id + ": current min = " + gossip.lowVal + 
            "\nNode" + node.id + ": current max = " + gossip.highVal);
            gossip.MID = gossip.SetMesssageID(); //if we encountered a full updated, then we need to prepare the packet to send in all directions
            SetUpdateDirection(gossip, node, isUpdated);
        }
    } 

    private void HandleRandomization(GossipData gossip, Node node){
        
        if(node.Seen.containsKey(gossip.MID))
            return;
        
        if(gossip.src != node.id){
            int prevData = node.data;
            node.data = node.SetData();
    
            System.out.println("\nNode" + node.id + ": Previous data in Node = " + prevData);
            System.out.println("Node" + node.id + ": Current data in Node = " + node.data +"\n");      
        }
        //update this so that we can choose a direction
/*             Update(gossip, node, node.previous.get("port"));
            Update(gossip, node, node.next.get("port"));   */
        SetUpdateDirection(gossip, node, true);
    }

    private void ReceivePing(GossipData gossip, Node node){
        if(gossip.from < node.port){
            node.previous.put("active", 1);
            System.out.println("Previous ID: " + gossip.id);
            node.previous.put("prevID", gossip.id);
        } else if(gossip.from > node.port) {
            node.next.put("active", 1);
            System.out.println("next ID: " + gossip.id);
            node.next.put("nextID", gossip.id);
        }
    }

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

    private void ReturnPing(GossipData goss, Node node){
        try{
            System.out.println("Preparing datagram packet, current Port is: " + node.port);
            System.out.println("gossip port is " + goss.from);
            DatagramSocket dgSock = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName("localhost");
    
            GossipData gossipObj = new GossipData();
            gossipObj.info = "Here";
            gossipObj.from = node.port;
            gossipObj.number = goss.from;
            gossipObj.id = node.id;
            gossipObj.created = LocalDateTime.now(ZoneOffset.UTC);
    
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ObjectOutputStream outObj = new ObjectOutputStream(outStream);
            outObj.writeObject(gossipObj);
            byte[] data = outStream.toByteArray();
            DatagramPacket send = new DatagramPacket(data, data.length, IPAddress, gossipObj.number);
            dgSock.send(send);
            System.out.println("Datagram has been  sent to port: " + gossipObj.number);
            dgSock.close();
        } catch(IOException io){
            io.printStackTrace();
        }
    }

}

class Node {
    int id;
    int data;
    int size;
    int avg;
    int cycle = 20;
    int port;
    String issuedCmd;
    HashMap<String, Integer> next = new HashMap<>();
    HashMap<String, Integer> previous = new HashMap<>();
    HashMap<Integer, LocalDateTime> Seen = new HashMap<>(); //may want to change the String to DateTime for the time of the message
    int kMin;
    int kMax;


    public Node(String id){
        this.id = Integer.parseInt(id);
        this.data = SetData();
        this.port = SetPort(this.id);

        this.next.put("port", 0);
        this.next.put("active", 0);
        this.previous.put("port", 0);
        this.previous.put("active", 0);

        //In this exercies the neighboring ports are in order, the following is a simple
        // calculation for all neighbors, including the edge ones that wrap around
        int nextPort = (this.port == 48109) ? this.port = 48100 : this.port +1;
        int prevPort = (this.port == 48100) ? this.port + 9 : this.port - 1;

        this.next.put("port", nextPort);
        this.previous.put("port", prevPort);
    }

    public int SetData(){
        Random r = new Random();
        return r.nextInt(100);
    }

    public void Tell(){
        System.out.println("\n=====Available Commands=====");
        System.out.println("'t': Prints available commands");
        System.out.println("'l': Prints local Node values");
        System.out.println("'p': 'Pings' neighboring ports to check for active nodes");
        System.out.println("'v': Generates new random values for all nodes in Network");
        System.out.println("'m': Finds the minimum and maximum values in the network");
        System.out.println("'N': Sets the total number of Cycles we can have in our [sub]network (default =  20)");
    }

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

    private int SetPort(int id){
        int port = 48100 + id;
        return port;
    }
}

//may have to add public in front of class
 class Gossip{
   // public static int server = 45565;
    //public static int Node = 0;

    public static void main(String[] args) throws Exception{
        Node current = new Node(args[0]);


        System.out.println("Henry deBuchananne's Gossip Server 1.0 booting up, listening at port " + current.port + "\n");
        ConsoleLoop loop = new ConsoleLoop(current);
        Thread t = new Thread(loop);
        t.start();
        
         boolean control = true;
         try{
            DatagramSocket dgsock = new DatagramSocket(current.port);
            //Datagram sockets are great, but we may need to watch out for buffer size
            //maybe change the object that we use? is there a buffered one we can use?
            System.out.println("SERVER: Buffer size: " + dgsock.getReceiveBufferSize() + "\n");
            byte[] inData = new byte[1024];
            InetAddress IPAddr = InetAddress.getByName("localhost");

            while(control){
                DatagramPacket inPacket  = new DatagramPacket(inData, inData.length);
                dgsock.receive(inPacket);
                byte[] data = inPacket.getData();

                ByteArrayInputStream in = new ByteArrayInputStream(data);
                ObjectInputStream inStream = new ObjectInputStream(in);

                try{
                    GossipData gObj = (GossipData) inStream.readObject();
                    if(gObj.info.indexOf("stopserver") > -1){
                        System.out.println("SERVER: Stopping UPD listener \n");
                        control = false;
                        dgsock.close();
                    }

                    new GossipWorker(gObj, current).start();

                } catch (ClassNotFoundException e){
                    e.printStackTrace();
                }
            }
        } catch (SocketException e){
            e.printStackTrace();
        } catch(IOException io){
            io.printStackTrace();
        } 
    }
}

class ConsoleLoop implements Runnable{
    Node node;
    public ConsoleLoop(Node n){
        node = n;
    }

    public void run(){
        System.out.println("In Console Looper thread");
        BufferedReader read = new BufferedReader(new InputStreamReader(System.in));
        try{
            String str;
            System.out.print("Enter 't' to list available commands, or (quit/stopserver): \n");
            do{
                System.out.flush();
                str = read.readLine();

                switch(str){
                    case "quit":
                        node.issuedCmd = "quit";
                        System.out.println("Exiting process per user request\n");
                        System.exit(0); //find other way to do this
                        break;
                    case "t":
                        node.issuedCmd = "t";
                        node.Tell();
                        break;
                    case "l":
                        node.issuedCmd = "l";
                        node.Locals();
                        break;
                    case "p":
                        node.issuedCmd = "p";
                        Ping(node);
                        break;
                    case "v":
                        node.issuedCmd = "v";
                        int prevData = node.data;
                        node.data = node.SetData();
                        System.out.println("\nNode" + node.id + ": Previous data in Node = " + prevData);
                        System.out.println("Node" + node.id + ": Current data in Node = " + node.data + "\n"); 
                        StartGossip(node, "ran");
                        break;
                    case "m":
                        node.issuedCmd = "m";
                        StartGossip(node, "MinMax");
                        break;
                    case "N":
                        node.issuedCmd = "N";
                        GetInput(node, read);
                        StartGossip(node, "N");
                        break;
                        
                }
            } while(true);
        } catch(IOException io) { io.printStackTrace();}
    }

    public void Ping(Node node){
        node.next.put("active", 0);
        node.previous.put("active", 0);
        NodeExists(node.next.get("port") ,node);
        NodeExists(node.previous.get("port"),node);
    }

    private void NodeExists(int port, Node node){
      GossipData goss = new GossipData();
      goss.info = "Hello";
      goss.number = port;
      goss.from = node.port;
      goss.created = LocalDateTime.now(ZoneOffset.UTC);
      new GossipWorker(goss, node).start();
// Will need to change this for the case where port ends in 0 and port ends with 9, but this is fine for now I guess
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
        public void run(){
            if(goss.number == node.previous.get("port")){
                String response = (node.previous.get("active") == 1) ? "NODE" + node.id + ": Neighbor Node " + node.previous.get("prevID") + " is active on Port: " + goss.number : "NODE" + node.id + ": Inactive Node on Port: "+ goss.number;
                System.out.println(response);
            } else if(goss.number == node.next.get("port")){
                String response = (node.next.get("active") == 1) ? "NODE" + node.id + ": Neighbor Node " + node.next.get("nextID") + " is active on Port: " + goss.number : "NODE" + node.id + ": Inactive Node on Port: " + goss.number;
                System.out.println(response);
            }
        }
        }, 1000);
    }

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

    private void StartGossip(Node node, String cmd){

        GossipData goss = new GossipData();
        goss.number = node.next.get("port");
        goss.from = node.port;
        goss.src = node.id;
        goss.info = cmd;
        goss.cycle = node.cycle;
        goss.created = LocalDateTime.now(ZoneOffset.UTC);
        
        new GossipWorker(goss, node).start();
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


  Currently working on the MINMAX section, need to be able to send the values back to the root node as well
  additionally, need to remember to se the low val and the high vals when we randomize the data? I feel like if we randomize this should trigger a minmax situation
  -Maybe when we randomize we check to see if either value is less than or greater than respectively

  Consider adding a "TYPE" variable to the GossipData object. The type could be req and res, after we send a req we need to send a res
    - need to find a way to coordinate the "pings" accross the network. If the req returns null, what do we do? How can we tell?
    - MAYBE what we need to do is set it up so that every time we make a request we send a response that just tells us we made it, if 
        we don't make it, then it's time to turn around?

    if type = req
        return and don't do anything
    else if type = res
        go back to start and go back through the network? Need to see what he means by all nodes need to reply

    1. initialize
    2. local
    3. update
    4. propogate/update
    5. convergance:

What we want to try to do is have it so that our code runs in one direction when we are at the initial state
then on update we backtrack, AND keep going
since this works in a circle

Need to add back in known max and known min on the nodes
Maybe we don't base our data on them, but that is what we have to update

  */
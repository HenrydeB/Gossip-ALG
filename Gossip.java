import java.io.*;
import java.net.*;
import java.util.*;





class GossipData implements Serializable{
    int number;
    int avg;
    int highVal;
    int lowVal;
    String info;
    int from;
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
            ReturnPing(gossip, node.port);
        } else if(gossip.info.equals("Hello") && gossip.number != node.port) {
            SendPing(gossip, node.port);
        } else if(gossip.info.equals("Here") && node.issuedCmd.equals("p")){
            ReceivePing(gossip, node);
        }
    }

    private void ReceivePing(GossipData gossip, Node node){
        if(gossip.from < node.port){
            node.previous.put("active", 1);
        } else if(gossip.from > node.port) {
            System.out.println("\nabove\n");
            node.next.put("active", 1);
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

    private void ReturnPing(GossipData goss, int currentPort){
        try{
            System.out.println("Preparing datagram packet, current Port is: " + currentPort);
            System.out.println("gossip port is " + goss.from);
            DatagramSocket dgSock = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName("localhost");
    
            GossipData gossipObj = new GossipData();
            gossipObj.info = "Here";
            gossipObj.from = currentPort;
            gossipObj.number = goss.from;
    
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
    int cycle;
    int port;
    String issuedCmd;
    HashMap<String, Integer> next = new HashMap<>();
    HashMap<String, Integer> previous = new HashMap<>();


    public Node(String id){
        this.id = Integer.parseInt(id);
        this.data = SetData();
        this.port = SetPort(this.id);

        this.next.put("port", 0);
        this.next.put("active", 0);
        this.previous.put("port", 0);
        this.previous.put("active", 0);

        int nextPort = (this.port == 48109) ? this.port = 48100 : this.port +1;
        int prevPort = (this.port == 48100) ? this.port + 9 : this.port - 1;

        this.next.put("port", nextPort);
        this.previous.put("port", prevPort);
    }

    private int SetData(){
        Random r = new Random();
        return r.nextInt(100);
    }

    public void Tell(){
        System.out.println("\n=====Available Commands=====");
        System.out.println("'t': Prints available commands");
        System.out.println("'l': Prints local Node values");
        System.out.println("'p': 'Pings' neighboring ports to check for active nodes");
    }

    public void Locals(){
        System.out.println("=====Local Data=====");
        System.out.println("Node ID:            "+ this.id);
        System.out.println("Node Port location: " + this.port);
        System.out.println("Local Data:         " + this.data);
        System.out.println("Total size:         " + this.size);
        System.out.println("Average Value:      " + this.avg);
        System.out.println("Current Cycle:      " + this.cycle);
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
      new GossipWorker(goss, node).start();
// Will need to change this for the case where port ends in 0 and port ends with 9, but this is fine for now I guess
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
        public void run(){
            if(goss.number == node.previous.get("port")){
                String response = (node.previous.get("active") == 1) ? "There is an active Node on port :" + port : "There is no active Node on port :" + port;
                System.out.println(response);
            } else if(goss.number == node.next.get("port")){
                String response = (node.next.get("active") == 1) ? "There is an active Node on port :" + port : "There is no active Node on port :" +  port;
                System.out.println(response);
            }
        }
        }, 1000);
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
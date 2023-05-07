import java.io.*;
import java.net.*;
import java.util.*;






class GossipData implements Serializable{
    int number;
    int avg;
    int highVal;
    int lowVal;
    String info;
}

class GossipWorker extends Thread{
    GossipData gossip;
    GossipWorker (GossipData g) {gossip = g;}

    public void run(){
        //run object
        if(gossip.info.equals("Hello")){
            ReturnPing(gossip);
        }
    }

    private void ReturnPing(GossipData goss){
        try{
            System.out.println("Preparing datagram packet");
            DatagramSocket dgSock = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName("localhost");
    
            GossipData gossipObj = new GossipData();
            gossipObj.info = "Here";
    
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ObjectOutputStream outObj = new ObjectOutputStream(outStream);
            outObj.writeObject(gossipObj);
            byte[] data = outStream.toByteArray();
            DatagramPacket send = new DatagramPacket(data, data.length, IPAddress, goss.number);
            dgSock.send(send);
            System.out.println("Datagram has been sent");
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

    public Node(String id){
        this.id = Integer.parseInt(id);
        this.data = SetData();
        this.port = SetPort(this.id);
    }

    private int SetData(){
        Random r = new Random();
        return r.nextInt(100);
    }

    public void Tell(){
        System.out.println("\n=====Available Commands=====");
        System.out.println("'t': Prints available commands");
        System.out.println("'l': Prints local Node values");
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

    public void Ping(){
        /* 
         * Checks for nodes above and below
         * needs to return Node Numbers and display in console
         */

        int nextPort = (this.port == 48109) ? this.port = 48100 : this.port +1;
        int prevPort = (this.port == 48100) ? this.port + 9 : this.port - 1;

        if(NodeExists(nextPort, this.port)){
            System.out.println("\n");
        }
        if(NodeExists(prevPort, this.port)){
            System.out.println("\n");
        }
    }

    private boolean NodeExists(int port, int prevPort){

        boolean ctrl = true;

        try{
            System.out.println("Preparing datagram packet");
            DatagramSocket dgSock = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName("localhost");

            GossipData gossipObj = new GossipData();
            gossipObj.info = "Hello";
            gossipObj.number = prevPort;

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ObjectOutputStream outObj = new ObjectOutputStream(outStream);

            outObj.writeObject(gossipObj);

            byte[] data = outStream.toByteArray();
            DatagramPacket send = new DatagramPacket(data, data.length, IPAddress, port);

            dgSock.send(send);
            System.out.println("Datagram has been sent");

            byte[] inData = new byte[1024];
            InetAddress IPAddr = InetAddress.getByName("localhost");
            DatagramSocket dgsock = new DatagramSocket(port); //error here, Cannot create a new socket, need to have the same socket instance. Read this method, you can find what's wrong in here

            while(ctrl){
                DatagramPacket inPacket  = new DatagramPacket(inData, inData.length);
                dgsock.receive(inPacket);;
                byte[] storage = inPacket.getData();

                ByteArrayInputStream in = new ByteArrayInputStream(storage);
                ObjectInputStream inStream = new ObjectInputStream(in);

                GossipData gObj = (GossipData) inStream.readObject();
                if(gObj.info.indexOf("Here") > -1){
                    System.out.println("\n We have a response \n");
                    ctrl = false;
                    dgsock.close();
                    dgSock.close();
                    return true;
                }
            }    
        } catch(UnknownHostException uh){
            System.out.println("\n  Unknown Host \n");
            uh.printStackTrace();
        }catch(SocketException s){
            s.printStackTrace();
        } catch(IOException io){
            io.printStackTrace();
        } catch(ClassNotFoundException cf){ // may have to write a different response
            cf.printStackTrace();
        }

        return false;
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
                dgsock.receive(inPacket);;
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
                    

                    System.out.println("\nSERVER: GossipObject recieved: " + gObj.info + "\n");
                    new GossipWorker(gObj).start();
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
            do{
                System.out.print("Enter a string to send to the server, or (quit/stopserver): ");
                System.out.flush();
                str = read.readLine();

/*                 if(str.indexOf("quit") > -1){
                    System.out.println("\n");
                    System.exit(0); //find other way to do this
                } */
                switch(str){
                    case "quit":
                        System.out.println("Exiting process per user request\n");
                        System.exit(0); //find other way to do this
                        break;
                    case "t":
                        node.Tell();
                        break;
                    case "l":
                        node.Locals();
                        break;
                    case "p":
                        node.Ping();
                        break;
                }
                try{
                    System.out.println("Preparing datagram packet");
                    DatagramSocket dgSock = new DatagramSocket();
                    InetAddress IPAddress = InetAddress.getByName("localhost");

                    GossipData gossipObj = new GossipData();
                    gossipObj.info = str;

                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    ObjectOutputStream outObj = new ObjectOutputStream(outStream);
                    outObj.writeObject(gossipObj);
                    byte[] data = outStream.toByteArray();
                    DatagramPacket send = new DatagramPacket(data, data.length, IPAddress, node.port);
                    dgSock.send(send);
                    System.out.println("Datagram has been sent");
                } catch(UnknownHostException uh){
                    System.out.println("\n  Unknown Host \n");
                    uh.printStackTrace();
                }

            } while(true);
        } catch(IOException io) { io.printStackTrace();}
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
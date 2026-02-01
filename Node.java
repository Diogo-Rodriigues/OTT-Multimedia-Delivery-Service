import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.Timer;
import java.time.temporal.ChronoUnit;
import java.awt.event.*;

public class Node {
    private InetAddress ip; // cada nodo vai ter um ip
    private Map<InetAddress, InetAddress> previous = new HashMap<>(); // Origin -> Parent
    private List<InetAddress> neighbors = new ArrayList<>(); // List of all physical neighbors

    private Map<InetAddress, Map<InetAddress, String>> routing = new HashMap<>(); // Origin -> Neighbor -> Status
    private Map<InetAddress, Long> link_delay = new HashMap<>(); // delay e address do vizinho (Legacy, maybe per origin?) -> this should be per origin? 
    // Wait, link delay is physical link delay to a neighbor. It doesn't depend on origin.
    // However, `previous` depends on origin. `delay` logic in flood checks `link_delay.get(previous)`. 
    // `previous` is now `previous.get(origin)`. 
    // So `link_delay.get(previous.get(origin))` works if `link_delay` is `Neighbor -> Delay`.
    
    private Map<InetAddress, Map<InetAddress, Integer>> cost = new HashMap<>(); // Origin -> Node -> Cost
    private ReentrantLock lock = new ReentrantLock();

    private DatagramSocket overlay;
    private DatagramSocket flood;
    private DatagramSocket active;
    private DatagramSocket openGates;

    private Map<InetAddress, SimpleEntry<Integer,Integer>> clientsTimer = new HashMap<>();
    private Set<UUID> seenMessages = new HashSet<>(); // Conjunto de mensagens já processadas

    // RTP variables:
    // ----------------
    DatagramPacket rcvdp; // UDP packet received from the server (to receive)
    DatagramSocket RTPsocket; // socket to be used to send and receive UDP packet
    static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets

    Timer cTimer; // timer used to receive data from the UDP socket
    byte[] cBuf; // buffer used to store data received from the server

    public Node(InetAddress ipNode, InetAddress ipServer) throws IOException {
        this.ip = ipNode;

        try{
            this.overlay = new DatagramSocket(1234, this.ip);
            this.flood = new DatagramSocket(5678, this.ip);
            this.active = new DatagramSocket(2345, this.ip);
            this.openGates = new DatagramSocket(6789, this.ip);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        

        new Thread(() -> {
          try {
            overlay(ipServer);
        } catch (Exception e) {
            e.printStackTrace();
        }  
        }).start();
        

        new Thread(() -> {
            try {
              flood();
            } catch (Exception e) {
              e.printStackTrace();
            }
          }).start();

        new Thread(() -> {
            try {
                clientActive();
            } catch (Exception e) {
              e.printStackTrace();
            }
        }).start();  
  
        new Thread(() -> {
            try {
              openGates();
            } catch (Exception e) {
              e.printStackTrace();
            }
          }).start();

	    new Thread(this::rtpTimer).start();
    }


    public void rtpTimer() {

        // init para a parte do nodo
        // --------------------------
        cTimer = new Timer(20, new Node.nodeTimerListener());
        cTimer.setInitialDelay(0);
        cTimer.setCoalesce(true);
        cBuf = new byte[15000]; // allocate enough memory for the buffer used to receive data from the server
        
        try {
            // socket e video
            RTPsocket = new DatagramSocket(RTP_RCV_PORT, this.ip); // init RTP socket bound to node IP
            RTPsocket.setSoTimeout(5000); // set timeout to 5s
            cTimer.start(); // Start timer only after socket is ready
        } catch (SocketException e) {
            System.out.println("Nodo: erro no socket: " + e.getMessage());
            System.exit(1);
        }
    }

    // ------------------------------------
    // Handler for timer (para nodo)
    // ------------------------------------

    class nodeTimerListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {

            // Construct a DatagramPacket to receive data from the UDP socket
            rcvdp = new DatagramPacket(cBuf, cBuf.length);

            try {
                // receive the DP from the socket:
                RTPsocket.receive(rcvdp);

                // create an RTPpacket object from the DP
                RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

                // get the source from the payload (or header if we changed it)
                // We added Source to RTPHeader.
                byte[] sourceBytes = rtp_packet.getSource();
                InetAddress sourceIp = InetAddress.getByAddress(sourceBytes);

                if (routing.containsKey(sourceIp)) {
                     Map<InetAddress, String> routeTable = routing.get(sourceIp);
                     for (InetAddress ip : routeTable.keySet()) {
                        if (!ip.equals(previous.get(sourceIp))) {
                             if (routeTable.get(ip).equals("on")) {
                                // get the payload bitstream from the RTPpacket object
                                int length = rtp_packet.getlength();
                                byte[] packet = new byte[length];
                                rtp_packet.getpacket(packet);

                                DatagramPacket dp = new DatagramPacket(packet, length, ip, RTP_RCV_PORT);
                                RTPsocket.send(dp);
                             }
                        }
                     }
                }
            } catch (InterruptedIOException iioe) {
                System.out.println("Nothing to read");
            } catch (IOException ioe) {
                System.out.println("Exception caught: " + ioe);
            }

        }
    }

    public Map.Entry<Packet,InetAddress> receivePacket(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket data = new DatagramPacket(buffer, buffer.length);
        socket.receive(data);
    
        buffer = data.getData();
        Packet packet = new Packet(buffer);

        System.out.println("\n>>> NODE RECEIVED PACKET FROM: " + data.getAddress());
        System.out.println(packet.toString());
    
        InetAddress nodo = data.getAddress();
        Map.Entry<Packet,InetAddress> res = new AbstractMap.SimpleEntry<Packet,InetAddress>(packet, nodo);
    
        return res;
    
      }

    public void sendPacket(InetAddress ip, int port, DatagramSocket socket, int id, int cost, LocalTime time, List<InetAddress> neighbours, UUID uniqueId, int ttl, InetAddress origin) throws IOException {
        
        Packet p;
        if (uniqueId == null) {
             p = new Packet(id, cost, time, neighbours, origin);
        } else {
             p = new Packet(id, cost, time, neighbours, uniqueId, ttl, origin);
        }

        byte[] b = p.serialize();

        DatagramPacket data = new DatagramPacket(b, b.length, ip, port);
        socket.send(data);

        System.out.println("\n<<< NODE SENDING PACKET TO: " + ip);
        System.out.println(p.toString());
    }

    public void overlay(InetAddress server) throws IOException {
        LocalTime time = LocalTime.now();
        sendPacket(server, 1234, overlay, 0, 0, time, null, null, 20, null); // pedido dos vizinhos

        Map.Entry<Packet,InetAddress> packetReceived = receivePacket(overlay);
        Packet packet = packetReceived.getKey();

        if (packet.getNeighbours() != null) {
            this.neighbors = packet.getNeighbours();
            System.out.println("Node: Neighbors received: " + this.neighbors);
        } else {
             System.out.println("Node: No neighbors received in overlay setup!");
        }
    }


    public void flood() throws IOException {
        while(true){
            
            Map.Entry<Packet,InetAddress> packetReceived = receivePacket(flood);
            Packet packet = packetReceived.getKey();
            InetAddress ip_origin = packetReceived.getValue() ;

            if(packet.getId() == 2){
                // Verificar se mensagem já foi vista
                if (seenMessages.contains(packet.getUniqueId())) {
                    System.out.println("Duplicate flood packet received, dropping: " + packet.getUniqueId());
                    continue;
                }
                seenMessages.add(packet.getUniqueId());

                // Verificar TTL
                if (packet.getTtl() <= 0) {
                     System.out.println("TTL expired for packet: " + packet.getUniqueId());
                     continue;
                }

                InetAddress origin = packet.getOrigin();
                if (origin == null) continue;

                // Ensure maps exist for this origin
                cost.putIfAbsent(origin, new HashMap<>());
                routing.putIfAbsent(origin, new HashMap<>());
                // We should initialize the routing table for this origin with all known neighbors if not done
                // But we don't know all neighbors explicitly if we didn't save them in overlay().
                // The previous code relied on `routing` keyset being neighbors.
                // We need to capture neighbors in overlay() or infer them.
                // Let's assume the previous code did: `routing.put(nb, "off");` in overlay.
                // To support multiple trees, when we see a NEW origin, we should probably add all KNOWN neighbors to its routing table.
                // But where are KNOWN neighbors? The previous code only had them in `routing`.
                // Let's cheat: The first time we see a flood for an origin, we look at who sent it.
                // No, that's wrong.
                
                // Let's modify `overlay` (above) to return neighbors or store them.
                // Since I can't easily change `overlay` return type without breaking flows, and `overlay` runs once at startup...
                // Wait, `routing` was used as "My Neighbors".
                // I should introduce `List<InetAddress> myNeighbours` class member.

                int nodeCost = packet.getCost();

                LocalTime currentTime = LocalTime.now();
                long delay = packet.getDelay().until(currentTime, ChronoUnit.MILLIS);

                if(!previous.containsKey(origin)){
                    previous.put(origin, ip_origin);

                    cost.get(origin).put(ip_origin, nodeCost);
                    link_delay.put(ip_origin, delay); // Keep this heuristic

                    // Forward to all neighbors (except where it came from)
                    // We need to know our neighbors!
                    // Assuming we can get them from the packet 1 (overlay) if we saved them.
                    // For now, let's assume `myGenericRouting` exists or something.
                    // IMPORTANT: The original code's `routing` map WAS the list of neighbors.
                    // So I should populate `routing.get(origin)` with those neighbors.
                    // See fix in `overlay` method below this replacement chunk which I will start differently.

                    // Check if I added `private List<InetAddress> neighbors = new ArrayList<>();`? No.
                    // I will add it now in a separate chunk.
                
                    for(InetAddress nb : neighbors){
                         routing.get(origin).put(nb, "off"); // Init routing for this origin
                         if(!nb.equals(ip_origin)) 
                             sendPacket(nb, 5678, flood, 2, nodeCost+1, packet.getDelay(), null, packet.getUniqueId(), packet.getTtl() - 1, origin);
                    }
                } else {
                     InetAddress currentPrev = previous.get(origin);
                     int currentCost = cost.get(origin).getOrDefault(currentPrev, Integer.MAX_VALUE);
                     long currentDelay = link_delay.getOrDefault(currentPrev, Long.MAX_VALUE);

                    if (delay < currentDelay || (delay == currentDelay && nodeCost < currentCost)){
                        previous.put(origin, ip_origin);

                        cost.get(origin).put(ip_origin, nodeCost);
                        link_delay.put(ip_origin, delay);
                    } 
                    for(InetAddress nb : neighbors){ 
                        // Ensure routing table exists (it should if we did it right)
                        routing.get(origin).putIfAbsent(nb, "off");

                        if(!nb.equals(previous.get(origin))) 
                            sendPacket(nb, 5678, flood, 2, nodeCost+1, packet.getDelay(), null, packet.getUniqueId(), packet.getTtl() - 1, origin);
                    }
                }  
            } 
            try{
                Thread.sleep(60);
            } catch (InterruptedException e){
                System.out.println("Flooding interrupted");
            }  
        }
    }

    public void clientActive() throws IOException, InterruptedException {
        while(true){
            for (InetAddress client : clientsTimer.keySet()){
                
                sendPacket(client, 2345, active, 6, 0, LocalTime.now() , null, null, 20, null);

        try{
                    lock.lock();
            int countP4 = clientsTimer.get(client).getKey();
            int countP6 = clientsTimer.get(client).getValue();
                    SimpleEntry<Integer,Integer> aux = new AbstractMap.SimpleEntry<Integer,Integer>(countP4, countP6+1);
                    clientsTimer.put(client, aux);

            if (countP6 - countP4 >= 3){
                // Client timed out. Close all streams for this client.
                
                for (InetAddress origin : routing.keySet()) {
                    if (routing.get(origin).containsKey(client) && routing.get(origin).get(client).equals("on")) {
                        routing.get(origin).put(client, "off");
                        System.out.println("Client " + client + " timed out. Closing gate for origin " + origin);

                         // If no more clients for this origin, propagate upstream
                         boolean hasClients = false;
                         for (String status : routing.get(origin).values()) {
                             if (status.equals("on")) hasClients = true;
                         }
                         if (!hasClients && previous.containsKey(origin)) {
                            sendPacket(previous.get(origin), 6789, openGates, 5, 0, LocalTime.now(), null, null, 20, origin);
                         }
                    }
                }
            SimpleEntry<Integer,Integer> restartClientTimer = new AbstractMap.SimpleEntry<Integer,Integer>(0, 0);
            clientsTimer.put(client, restartClientTimer);
            }
                } finally{
                    lock.unlock();
                }
            }
            Thread.sleep(2000);
        }
    }


    

    public void openGates() throws IOException {
        while(true){
          Map.Entry<Packet,InetAddress> packetReceived = receivePacket(openGates);
          Packet packet = packetReceived.getKey();
          InetAddress ip_nodo = packetReceived.getValue() ;
    
            if (packet.getId() == 4) { // Abrir comporta de streaming
                InetAddress origin = packet.getOrigin();
                if (origin != null && previous.containsKey(origin)) {
                    routing.putIfAbsent(origin, new HashMap<>());
                    routing.get(origin).put(ip_nodo, "on"); 
                    System.out.println("Gate openned for streaming at node: " + ip_nodo + " for origin " + origin);

                    if(clientsTimer.containsKey(ip_nodo)) {
                        try{
                            lock.lock();
			                int countP4 = clientsTimer.get(ip_nodo).getKey();
			                int countP6 = clientsTimer.get(ip_nodo).getValue();
                            SimpleEntry<Integer,Integer> aux = new AbstractMap.SimpleEntry<Integer,Integer>(countP4+1, countP6);
                            clientsTimer.put(ip_nodo, aux);
                        } finally{
                            lock.unlock();
                        }
                    } else {
			            SimpleEntry<Integer,Integer> initClientTimer = new AbstractMap.SimpleEntry<Integer,Integer>(0, 0);
			            clientsTimer.put(ip_nodo, initClientTimer);
		            }
                    sendPacket(previous.get(origin), 6789, openGates, 4, 0, packet.getDelay(), null, null, 20, origin);
                }
            }



            if (packet.getId() == 5) { // Fechar comporta de streaming
                InetAddress origin = packet.getOrigin();
                if (origin != null && routing.containsKey(origin)) {
		            routing.get(origin).put(ip_nodo, "off");
            	    System.out.println("Gate closed at node: " + ip_nodo + " for origin " + origin);

                    boolean hasClients = false;
                    for (String status : routing.get(origin).values()) {
                        if (status.equals("on")) hasClients = true;
                    }

                    if (!hasClients && previous.containsKey(origin)) {
                        sendPacket(previous.get(origin), 6789, openGates, 5, 0, packet.getDelay(), null, null, 20, origin);
                    }
                }
                
            }
        }
    }
    
}


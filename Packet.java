import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;


public class Packet implements Serializable{
    private int id_packet;
    private int cost; // Number of jumps
    private LocalTime delay;  // Time of creation
    private List<InetAddress> neighbours;
    private UUID uniqueId;
    private int ttl;
    private InetAddress origin; // Source/Origin of the stream/tree (Server IP)

    // Construtor principal
    public Packet(int id, int cost, LocalTime delay, List<InetAddress> nb, UUID uniqueId, int ttl){
        this.id_packet = id;
        this.cost = cost;
        this.delay = delay;
        this.neighbours = nb;
        this.uniqueId = uniqueId;
        this.ttl = ttl;
    }

    // Construtor principal
    public Packet(int id, int cost, LocalTime delay, List<InetAddress> nb, UUID uniqueId, int ttl, InetAddress origin){
        this.id_packet = id;
        this.cost = cost;
        this.delay = delay;
        this.neighbours = nb;
        this.uniqueId = uniqueId;
        this.ttl = ttl;
        this.origin = origin;
    }

    // Construtor legado / conveniência (gera novo UUID e TTL default)
    // Construtor legado / conveniência (gera novo UUID e TTL default)
    public Packet(int id, int cost, LocalTime delay, List<InetAddress> nb, InetAddress origin){
        this(id, cost, delay, nb, UUID.randomUUID(), 20, origin);
    }

    public Packet(byte[] info){
        try{
            Packet res = deserialize(info);
            this.id_packet = res.getId();
            this.cost = res.getCost();
            this.delay = res.getDelay();
            this.neighbours = res.getNeighbours();
            this.uniqueId = res.getUniqueId();
            this.neighbours = res.getNeighbours();
            this.uniqueId = res.getUniqueId();
            this.ttl = res.getTtl();
            this.origin = res.getOrigin();
        } catch (Exception e) {
            System.out.println("Packet not created.");
        }
    }

    // Get methods
    public int getId(){
        return this.id_packet;
    }

    public int getCost(){
        return this.cost;
    }

    public LocalTime getDelay(){
        return this.delay;
    }
    
    public List<InetAddress> getNeighbours (){ 
        return this.neighbours;
    }

    public UUID getUniqueId() {
        return this.uniqueId;
    }

    public int getTtl() {
        return this.ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public InetAddress getOrigin() {
        return this.origin;
    }

 //Serialize
    public byte[] serialize () throws IOException {
        
        byte[] packet;
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(file);
        out.writeObject(this);
        out.close();
        
        packet = file.toByteArray();
        return packet;

    }
 
 //Deserialize
    public Packet deserialize (byte[] info) throws IOException, ClassNotFoundException {
        
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(info));
        Packet res = (Packet) in.readObject();
        in.close();

        return res;
    }

    public String toString (){
        StringBuilder sb = new StringBuilder();
        String border = "------------------------------------------------------------";
        sb.append(border).append("\n");
        sb.append(String.format("| %-56s |\n", "PACKET INFO (ID: " + this.id_packet + ")"));
        sb.append(border).append("\n");
        sb.append(String.format("| %-12s : %s\n", "UUID", this.uniqueId));
        sb.append(String.format("| %-12s : %s\n", "Origin", this.origin));
        sb.append(String.format("| %-12s : %s\n", "TTL", this.ttl));
        sb.append(String.format("| %-12s : %s\n", "Cost", this.cost));
        sb.append(String.format("| %-12s : %s\n", "Timestamp", this.delay));
        sb.append(String.format("| %-12s : %s\n", "Neighbours", this.neighbours));
        sb.append(border);
        return sb.toString();
    }


}




/*------------------
Cliente
usage: java Cliente
adaptado dos originais pela equipa docente de ESR (nenhumas garantias)
colocar o cliente primeiro a correr que o servidor dispara logo!
---------------------- */

import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.util.UUID;

public class Client {

    private InetAddress ip;
    private InetAddress ipNode;

    private Map<InetAddress, Integer> cost = new HashMap<>(); 
    private Map<InetAddress, Long> link_delay; 

    private DatagramSocket connect;
    private DatagramSocket openGates;

    // GUI
    // ----
    JFrame f = new JFrame("Cliente de Testes");
    JButton playButton = new JButton("Play");
    JButton pauseButton = new JButton("Pause");
    JButton tearButton = new JButton("Exit");
    JPanel videoPanel = new JPanel();
    JLabel iconLabel = new JLabel();
    JLabel statusLabel = new JLabel("Status: Idle");
    ImageIcon icon;

    // RTP variables:
    // ----------------
    DatagramPacket rcvdp; 
    DatagramSocket RTPsocket; 
    static int RTP_RCV_PORT = 25000; 

    Timer cTimer; 
    byte[] cBuf; 

    private InetAddress ipServer;
    private boolean isPaused = false;
    private boolean isConnected = false;

    public Client(InetAddress ip, InetAddress ipServer) throws IOException { 
        this.ip = ip;
        this.ipServer = ipServer;

        try{
            this.connect = new DatagramSocket(4567, this.ip);
            this.openGates = new DatagramSocket(6789, this.ip);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Connecting to server: " + ipServer);
        try {
            connect(ipServer);
            isConnected = true;
        } catch (Exception e) {
            System.out.println("FATAL: Could not connect to server!");      
            e.printStackTrace();
            return;
        }
    
        if (this.ipNode == null) {
            System.out.println("FATAL: Server did not assign a node!");
            return;
        }

        System.out.println("Successfully connected! Assigned node: " + this.ipNode);
    
    
        new Thread(() -> {
            try {
              openGates();
            } catch (Exception e) {
              System.out.println("Error in openGates thread:");
              e.printStackTrace();
            }
          }).start();

        new Thread(() -> {
            try {
              client();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
  
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Arial", Font.BOLD, 12));
    }

    public void client() {
        f.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { System.exit(0); }});
        f.setLayout(new BorderLayout());
        f.getContentPane().setBackground(new Color(50, 50, 50));

        videoPanel.setLayout(new BorderLayout());
        videoPanel.setBackground(Color.BLACK);
        videoPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        videoPanel.add(iconLabel, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout());
        controls.setBackground(new Color(50, 50, 50));
        controls.add(playButton);
        controls.add(pauseButton);
        controls.add(tearButton);

        styleButton(playButton, new Color(34, 139, 34));
        styleButton(pauseButton, new Color(218, 165, 32));
        styleButton(tearButton, new Color(178, 34, 34));

        statusLabel.setForeground(Color.WHITE);
        f.add(videoPanel, BorderLayout.CENTER);
        f.add(controls, BorderLayout.SOUTH);
        f.add(statusLabel, BorderLayout.NORTH);

        playButton.addActionListener(e -> {
            if (!isConnected) return;
            statusLabel.setText("Playing: " + ipServer);
            isPaused = false;
            playButton.setEnabled(false);
            pauseButton.setEnabled(true);
            if (!cTimer.isRunning()) cTimer.start();
        });

        pauseButton.addActionListener(e -> {
            statusLabel.setText("Paused (Buffering)");
            isPaused = true;
            playButton.setEnabled(true);
            pauseButton.setEnabled(false);
        });

        tearButton.addActionListener(e -> System.exit(0));
        f.setSize(500, 450);
        f.setVisible(true);

        cBuf = new byte[15000]; 
        
        cTimer = new Timer(20, e -> {
            rcvdp = new DatagramPacket(cBuf, cBuf.length);
            try {
                RTPsocket.receive(rcvdp);
                if (!isPaused) {
                    RTPpacket rtp = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
                    int len = rtp.getpayload_length();
                    byte[] pl = new byte[len];
                    rtp.getpayload(pl);
                    icon = new ImageIcon(Toolkit.getDefaultToolkit().createImage(pl, 0, len));
                    iconLabel.setIcon(icon);
                    // System.out.println("Got RTP packet with SeqNum # " + rtp.getsequencenumber());
                }
            } catch (Exception ex) {}
        });
        
        try { 
            RTPsocket = new DatagramSocket(RTP_RCV_PORT); 
            RTPsocket.setSoTimeout(5000); 
        } catch (Exception e) {
             System.out.println("Cliente: erro no socket: " + e.getMessage());
        }
    }

    public Map.Entry<Packet,InetAddress> receivePacket(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket data = new DatagramPacket(buffer, buffer.length);
        socket.receive(data);
    
        buffer = data.getData();
        Packet packet = new Packet(buffer);

        System.out.println("\n>>> CLIENT RECEIVED PACKET FROM: " + data.getAddress());
        System.out.println(packet.toString());
    
        InetAddress nodo = data.getAddress();
        Map.Entry<Packet,InetAddress> res = new AbstractMap.SimpleEntry<Packet,InetAddress>(packet, nodo);
    
        return res;
    
      }

    public void sendPacket(InetAddress ip, int port, DatagramSocket socket, int id, int cost, List<InetAddress> neighbours, UUID uniqueId, int ttl, InetAddress origin) throws IOException {
        LocalTime time = LocalTime.now();
        Packet p;
        if (uniqueId == null) {
            p = new Packet(id, cost, time, neighbours, origin);
        } else {
            p = new Packet(id, cost, time, neighbours, uniqueId, ttl, origin);
        }

        byte[] b = p.serialize();

        DatagramPacket data = new DatagramPacket(b, b.length, ip, port);
        socket.send(data);

        System.out.println("\n<<< CLIENT SENDING PACKET TO: " + ip);
        System.out.println(p.toString());
    }

    public void connect(InetAddress ipServer) throws IOException {
        System.out.println("Sending connection request to server...");
        sendPacket(ipServer, 4567, connect, 3, 0, new ArrayList<>(), null, 20, null);

        System.out.println("Waiting for server response...");
        Map.Entry<Packet,InetAddress> packetReceived = receivePacket(connect);
        Packet packet = packetReceived.getKey();
        
        if (packet.getNeighbours() == null || packet.getNeighbours().isEmpty()) {
            System.out.println("ERROR: Server response has no neighbours!");
            return;
        }
        
        this.ipNode = packet.getNeighbours().get(0);
        System.out.println("\n\nIP FORNECIDO " + ipNode);
        
    }

    public void openGates() throws IOException, InterruptedException {
        System.out.println("openGates thread started");

        int attempts = 0;
        while (this.ipNode == null && attempts < 10) {
            System.out.println("openGates: Waiting for connection... (" + attempts + "/10)");
            Thread.sleep(1000);
            attempts++;
        }

        if (this.ipNode == null) {
            System.out.println("openGates: FATAL - No node assigned after 10s!");
            return;
        }

        System.out.println("openGates: Starting heartbeat to " + this.ipNode);

        while(true) {
            Thread.sleep(1000);
            System.out.println("openGates: Sending heartbeat (ID=4) for origin " + this.ipServer);
            sendPacket(this.ipNode, 6789, openGates, 4, 0, new ArrayList<>(), null, 20, this.ipServer);
        }
    }

}// end of Class Cliente

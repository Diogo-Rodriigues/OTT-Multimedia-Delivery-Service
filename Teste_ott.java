import java.io.IOException;
import java.net.InetAddress;

public class Teste_ott {
 

    public static void main(String args[]) throws IOException {

        if (args[0].compareTo("Server") == 0) { 
            try {
                InetAddress ipServer = InetAddress.getByName(args[2]);
                String videoFile = "movie.Mjpeg";
                if(args.length > 3) videoFile = args[3];
                new Server(args[1], videoFile, ipServer); // indicar o ficheiro usado para bootstrapper e video file
            } catch (Exception e){
                e.printStackTrace();
                System.out.println("Error creating server");
            } 

        } else if (args[0].compareTo("Client") == 0) { 
            try{
                new Client(InetAddress.getByName(args[1]), InetAddress.getByName(args[2])); // ip do próprio e ip do server 
            } catch (Exception e) {
                System.out.println("Error creating client");
            }

        } else { // quando não é passado um tipo de nodo (server ou cliente) abre um nodo de overlay
            try {
                new Node(InetAddress.getByName(args[0]), InetAddress.getByName(args[1])); // ip do próprio e ip do server a que se liga
            } catch (Exception e) {
                System.out.println("Error creating node");
            }
        } 
    }   
} 
//class RTPpacket

public class RTPpacket{

    //size of the RTP header:
    static int HEADER_SIZE = 12;
  
    //Fields that compose the RTP header
    public int Version;
    public int Padding;
    public int Extension;
    public int CC;
    public int Marker;
    public int PayloadType;
    public int SequenceNumber;
    public int TimeStamp;
    public int Ssrc;
    public int[] Source; // Server IP as int array [4]
    
    //Bitstream of the RTP header
    public byte[] header;
  
    //size of the RTP payload
    public int payload_size;
    //Bitstream of the RTP payload
    public byte[] payload;
    
  
  
    //--------------------------
    //Constructor of an RTPpacket object from header fields and payload bitstream
    //--------------------------
    public RTPpacket(int PType, int Framenb, int Time, byte[] data, int data_length, byte[] sourceIp){
      //fill by default header fields:
      Version = 2;
      Padding = 0;
      Extension = 0;
      CC = 0;
      Marker = 0;
      Ssrc = 0;
  
      //fill changing header fields:
      SequenceNumber = Framenb;
      TimeStamp = Time;
      TimeStamp = Time;
      PayloadType = PType;

      Source = new int[4];
      for(int i=0; i<4; i++) {
        Source[i] = sourceIp[i] & 0xFF;
      }
      
      //build the header bistream:
      //--------------------------
      header = new byte[HEADER_SIZE + 4];
  
      //.............
      //TO COMPLETE
      //.............
      //fill the header array of byte with RTP header fields
      header[0] = (byte)(Version << 6 | Padding << 5 | Extension << 4 | CC);
      header[1] = (byte)(Marker << 7 | PayloadType & 0x000000FF);
      header[2] = (byte)(SequenceNumber >> 8);
      header[3] = (byte)(SequenceNumber & 0xFF); 
      header[4] = (byte)(TimeStamp >> 24);
      header[5] = (byte)(TimeStamp >> 16);
      header[6] = (byte)(TimeStamp >> 8);
      header[7] = (byte)(TimeStamp & 0xFF);
      header[8] = (byte)(Ssrc >> 24);
      header[9] = (byte)(Ssrc >> 16);
      header[10] = (byte)(Ssrc >> 8);
      header[11] = (byte)(Ssrc & 0xFF);
      header[12] = (byte)(Source[0] & 0xFF);
      header[13] = (byte)(Source[1] & 0xFF);
      header[14] = (byte)(Source[2] & 0xFF);
      header[15] = (byte)(Source[3] & 0xFF);
  
      //fill the payload bitstream:
      //--------------------------
      payload_size = data_length;
      payload = new byte[data_length];
  
      //fill payload array of byte from data (given in parameter of the constructor)
      //......
      for (int i=0; i < data_length; i++)
        payload[i] = data[i];
  
      // ! Do not forget to uncomment method printheader() below !
  
    }
      
    //--------------------------
    //Constructor of an RTPpacket object from the packet bistream 
    //--------------------------
    public RTPpacket(byte[] packet, int packet_size)
    {
      //fill default fields:
      Version = 2;
      Padding = 0;
      Extension = 0;
      CC = 0;
      Marker = 0;
      Ssrc = 0;
  
      //check if total packet size is lower than the header size
      if (packet_size >= HEADER_SIZE) 
        {
      //get the header bitsream:
      header = new byte[HEADER_SIZE + 4];
      for (int i=0; i < HEADER_SIZE + 4; i++)
        header[i] = packet[i];
  
      //get the payload bitstream:
      payload_size = packet_size - (HEADER_SIZE + 4);
      payload = new byte[payload_size];
      for (int i=(HEADER_SIZE + 4); i < packet_size; i++)
        payload[i-(HEADER_SIZE + 4)] = packet[i];
  
      //interpret the changing fields of the header:
      PayloadType = header[1] & 127;
      SequenceNumber = unsigned_int(header[3]) + 256*unsigned_int(header[2]);
      SequenceNumber = unsigned_int(header[3]) + 256*unsigned_int(header[2]);
      TimeStamp = unsigned_int(header[7]) + 256*unsigned_int(header[6]) + 65536*unsigned_int(header[5]) + 16777216*unsigned_int(header[4]);

      Source = new int[4];
      Source[0] = unsigned_int(header[12]);
      Source[1] = unsigned_int(header[13]);
      Source[2] = unsigned_int(header[14]);
      Source[3] = unsigned_int(header[15]);
        }
   }
  
    //--------------------------
    //getpayload: return the payload bistream of the RTPpacket and its size
    //--------------------------
    public int getpayload(byte[] data) {
  
      for (int i=0; i < payload_size; i++)
        data[i] = payload[i];
  
      return(payload_size);
    }
  
    //--------------------------
    //getpayload_length: return the length of the payload
    //--------------------------
    public int getpayload_length() {
      return(payload_size);
    }
  
    //--------------------------
    //getlength: return the total length of the RTP packet
    //--------------------------
    public int getlength() {
      return(payload_size + HEADER_SIZE + 4);
    }
  
    //--------------------------
    //getpacket: returns the packet bitstream and its length
    //--------------------------
    public int getpacket(byte[] packet)
    {
      //construct the packet = header + payload
      for (int i=0; i < HEADER_SIZE + 4; i++)
      packet[i] = header[i];
      for (int i=0; i < payload_size; i++)
      packet[i+HEADER_SIZE+4] = payload[i];

      //return total size of the packet
      return(payload_size + HEADER_SIZE + 4);
    }
  
    //--------------------------
    //gettimestamp
    //--------------------------
  
    public int gettimestamp() {
      return(TimeStamp);
    }
  
    //--------------------------
    //getsequencenumber
    //--------------------------
    public int getsequencenumber() {
      return(SequenceNumber);
    }
  
    //--------------------------
    //getpayloadtype
    //--------------------------
    public int getpayloadtype() {
      return(PayloadType);
    }

    public byte[] getSource() {
        byte[] s = new byte[4];
        for(int i=0; i<4; i++) s[i] = (byte)Source[i];
        return s;
    }
  
  
    //--------------------------
    //print headers without the SSRC
    //--------------------------
    public void printheader()
    {
          System.out.print("[RTP-Header] ");
          System.out.println("Version: " + Version 
                             + ", Padding: " + Padding
                             + ", Extension: " + Extension 
                             + ", CC: " + CC
                             + ", Marker: " + Marker 
                             + ", PayloadType: " + PayloadType
                             + ", SequenceNumber: " + SequenceNumber
                             + ", TimeStamp: " + TimeStamp);
    }
  
    //return the unsigned value of 8-bit integer nb
    static int unsigned_int(int nb) {
      if (nb >= 0)
        return(nb);
      else
        return(256+nb);
    }
  
  }
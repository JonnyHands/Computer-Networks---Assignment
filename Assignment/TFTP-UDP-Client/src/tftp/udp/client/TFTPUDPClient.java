package tftp.udp.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Computer Networks 
 * Trivial File Transfer Protocol Assignment 
 * @Author Candidate number: 198397
 */

public class TFTPUDPClient 
{

    int port = 1111; //initialises the default port and sets it to 1111
    DatagramSocket socket; // initialises the socket and sets it to null
    byte[] buffer = new byte[516];
    InetAddress address; // the ip address
    final int packetSize = 516;
    DatagramPacket packetSent; // the packet to be sent
    DatagramPacket packetReceived; // the packet received   
    Random random = new Random();
    final int default_port = random.nextInt(65535 - 1024) + 1024; // picks a random port between 1024 and 65535 and finalises it
    byte opcode_ReadRequest = 1; // opcode for the read request
    byte opcode_WriteRequest = 2; // opcode for the write request
    byte opcode_Data = 3; // opcode for the data
    byte opcode_Acknowledgement = 4; // opcode for the ackowledgement
    byte opcode_Error = 5; // opcode for an errorOccurance    
    
    byte[] write = {}; // a byte list to store what needs to be written
    int blockToSend = 0; // blockToSend to be sent with the packet
    int blockExpected = 0; // blockToSend number expected from the packet 
    boolean errorOccurance = false; // boolean for checking if error has occured


    public void get(String nameOfFile, String[] arguments) throws UnknownHostException, SocketException, Exception //Handler for the transactions ensuring the data is handled correctly
    {
        byte opcode = 0; //resets opcode
        System.out.println("File name is: " + nameOfFile); // console output for visualisation
        address = InetAddress.getByName(arguments[0]); //sets the address
        socket = new DatagramSocket(default_port, address); //creates the socket
        String filename = arguments[2]; //assigns the file name
        
        switch (arguments[1]) 
        {
        //checks if input for read request was made
            case "1":
                opcode = opcode_ReadRequest; //sets opcode to read request
                System.out.println("Read request received."); // console output for visualisation
                break;
        //checks if input for write request was made
            case "2":
                opcode = opcode_WriteRequest; //sets opcode to write request
                try
                {
                    write = readFile(filename); //puts file name into write byte array to be written
                }
                
                catch (NoSuchFileException exception)
                {
                    socket.close(); //closes socket
                    System.exit(0); //terminates client
                }   System.out.println("File size is: " + write.length + " long."); // console output for visualisation
                System.out.println("Write request received."); // console output for visualisation
                break;
            default:
                System.err.println("Invalid command, please input 1 for read and 2 for write."); // console output for visualisation
                socket.close(); //closes socket
                System.exit(0); // terminates client
        }

        ByteArrayOutputStream requestByteArray = new ByteArrayOutputStream(); // sets the ByteArrayOutputStream
        requestByteArray.write(makeRequest(opcode, nameOfFile)); // inputs the bytes needed for first request
        byte[] requests = requestByteArray.toByteArray(); // puts bytes for first request into a byte array
        System.out.println("Request length is: " + requests.length + " long."); // console output for visualisation
        packetSent = new DatagramPacket(requests, requests.length, address, port); // makes a request packet using the servers port
        socket.send(packetSent);
        System.out.println("Request has been sent."); // console output for visualisation
        socket.setSoTimeout(3000); // timer for a packet to eb received
        ByteArrayOutputStream received = retrieve(); //retrieves from the network
        
        if (opcode == opcode_ReadRequest && errorOccurance == false)
        {
            writeFile(received, nameOfFile);
        }
        
        System.out.println("The socket has been closed."); // console output for visualisation
        socket.close(); // closes the socket
    }

    public byte[] makeRequest(byte opcode, String nameOfFile) throws UnsupportedEncodingException //sets up byte array used for the first request packet to be sent
    {

        String mode = "octet"; //creates the octet mode
        byte[] readRequestByte = new byte[(2 + nameOfFile.length() + 1 + mode.length() + 1)]; //initialises the readRequestByte array where spacing is sued for seperation seen with the additional 1's
        int pointerNameOfFileByte = 3; // pointer to iterate through nameoffile
        int pointerModeByte = (nameOfFile.length() + 2); // pointer to iterate through mode
        readRequestByte[0] = opcode; //sets opcode
        readRequestByte[1] = (byte) 0; //creates a space between opcode and nameofile in ASCII
        byte[] nameOfFileByte = nameOfFile.getBytes("US-ASCII"); // converts the name of the file into bytes
        byte[] modebyte = mode.getBytes("US-ASCII"); //converts the byte mode into bytes     
                
        for (byte i  : nameOfFileByte) //loops through the name of file byte array inputting the name into the read request byte array
        {
            readRequestByte[pointerNameOfFileByte] = i;
            pointerNameOfFileByte++;
        }        
        
        readRequestByte[(nameOfFile.length() + 1)] = (byte) 0; //creates space between nameoffile and mode in ASCII
        
        for (byte j : modebyte) 
        {
            readRequestByte[pointerModeByte] = j; //loops through the mode byte array inputting the name into the readRequestByte array
            pointerModeByte++;
        }
        
        readRequestByte[pointerModeByte+1] = (byte) 0; // creates a space at the end of the packet, this is the end of the packet.
        return readRequestByte;
    }


    public ByteArrayOutputStream retrieve() throws IOException //handles communication between server and client by sending correct packets to sustain connection
    {
        buffer = new byte[packetSize]; //initialises buffer
        ByteArrayOutputStream output = new ByteArrayOutputStream(); // sets the ByteArrayOutputStream        
        packetReceived = new DatagramPacket(buffer, packetSize, address, socket.getLocalPort()); //initialises packet received
        int bytesSent = 0; // how much has been sent so far, only used for read
        boolean isLastPacket = false; // checks if is is the last packet that needs to be sent
        boolean isAllSent = false; // checks if all data has been sent from a file

        while (isLastPacket == false) 
        {
            
            try 
            {
                socket.receive(packetReceived);
            } 
            
            catch (SocketTimeoutException socketTimeoutException) 
            {
                System.out.println("Timeout after 3 seconds"); // console output for visualisation
                socket.send(packetSent);
                System.out.println("Packet being resent..."); // console output for visualisation
            }

            byte[] opcode = {buffer[0], buffer[1]}; 
            
            if (opcode[1] == opcode_Data) //checks if opcode is data
            { 
                System.out.println("Client data packet."); // console output for visualisation
                byte[] blockNumber = {buffer[2], buffer[3]};
                byte[] blockNumberExpected = blockIncrease(blockExpected); // increases block number by 1 before check

                if (blockNumber[0] == blockNumberExpected[0] && blockNumber[1] == blockNumberExpected[1]) 
                {
                    blockExpected++;

                    if (packetReceived.getLength() < 516) // checks if is last packet of data
                    { 
                        isLastPacket = true;
                        System.out.println("Last packet detected."); // console output for visualisation
                    }

                    System.out.println("Block number passed."); // console output for visualisation
                    output.write(packetReceived.getData(), 4, packetReceived.getLength() - 4); // gets data from byte
                    byte[] ackowledgement = {0, opcode_Acknowledgement, blockNumber[0], blockNumber[1]};
                    packetSent = new DatagramPacket(ackowledgement, ackowledgement.length, address, packetReceived.getPort());
                    socket.send(packetSent);
                    System.out.println("Acknowledgment has been sent."); // console output for visualisation
                } 
                
                else 
                {
                    System.out.println("Error with block number."); // console output for visualisation
                }
            }
            
            else if (opcode[1] == opcode_Acknowledgement) //checks if opcode is an acknowledgement
            { 
                
                if (isAllSent == true) //checks all packets have been sent
                {
                    isLastPacket = true; //sets boolean true to confirm last packet
                    System.out.println("Received last and can now close"); // console output for visualisation
                    break;
                }
                
                else
                {
                    System.out.println("Acknowledgement received."); // console output for visualisation
                    ByteArrayOutputStream pendingWrite = new ByteArrayOutputStream();
                    byte[] blockNumber = blockIncrease(blockToSend); // may have to seperate up 
                    blockToSend++;
                    System.out.println("Block number: " + blockToSend + " sent."); // console output for visualisation
                    int bytesToBeSent = write.length; // amount of bytes needing to be sent
                    System.out.println("Amount " + bytesToBeSent); // console output for visualisation
                    int bytesLeftToSend = 0;  // initilises bytesLeftToSend

                    if ((bytesToBeSent - bytesSent) / 512 >= 1) // checks if there is more than 512 bytes if so sends 512 bytes
                    {
                        bytesLeftToSend = 512; // sets bytesLeftToBeSent to 512 (maximum number of bytes)
                    }
                    
                    else 
                    {
                        bytesLeftToSend = (bytesToBeSent - bytesSent); // if there is less than 512 bytes sends the left over bytes
                        System.out.println("amount left " + bytesLeftToSend); // console output for visualisation
                    }

                    int bytesSentSoFar = bytesSent;
                    
                    for (int i = bytesSent; i < (bytesSent + bytesLeftToSend); i++) // loop so it doesnt fill with space
                    {
                        pendingWrite.write(write[i]); // write to the bytearray
                        bytesSentSoFar++; //increments bytesSentSoFar
                    }
                    bytesSent = bytesSentSoFar; // reassigned back after the loop

                    if (bytesSent == bytesToBeSent) //checks all bytes have been sent
                    {
                        isAllSent = true;
                        System.out.println("Sent last of the data"); // console output for visualisation
                    }

                    byte[] dataToBeSent = pendingWrite.toByteArray(); // convert dataToBeSent into byte array so can go into packet
                    byte[] startingData = {0, opcode_Data, blockNumber[0], blockNumber[1]}; //sets startingData
                    byte[] data = new byte[dataToBeSent.length + startingData.length]; //initialises data
                    System.arraycopy(startingData, 0, data, 0, startingData.length); // copies the startingData into the data
                    System.arraycopy(dataToBeSent, 0, data, startingData.length, dataToBeSent.length); // copies the data to be sent into the data
                    packetSent = new DatagramPacket(data, data.length, address, packetReceived.getPort()); //initiates packet sent
                    socket.send(packetSent);
                    System.out.println("send data to write"); // console output for visualisation

                    if (bytesSent == bytesToBeSent && bytesToBeSent % 512 == 0) // checks if the last packet is 512 to determine if empty packet needs to be sent
                    { 
                        blockNumber = blockIncrease(blockToSend); 
                        blockToSend++;
                        byte[] emptyPacket = {0, opcode_Data, blockNumber[0], blockNumber[1]}; //creates an empty packet
                        packetSent = new DatagramPacket(emptyPacket, emptyPacket.length, address, packetReceived.getPort());
                        socket.send(packetSent); //sends empty packet
                        isAllSent = true;
                        System.out.println("Sent EMPTY packet of data"); // console output for visualisation
                    }
                }
            }
            
            else if (opcode[1] == opcode_Error) //check if is an error opcode
            {
                isLastPacket = true; // check if its the last packet to send
                errorOccurance = true; // check for error
                System.err.println("File not found."); // console output for visualisation
            } 
        }
        System.out.println("Retrieval completed."); // console output for visualisation
        return output;
    }

    

    private byte[] readFile(String nameOfFile) throws IOException  //reads content of file in byte array
    {
        Path p = Paths.get(nameOfFile);
        byte[] fileContent = Files.readAllBytes(p);
        return fileContent;
    }
    

    private void writeFile(ByteArrayOutputStream output, String fileName) 
                throws FileNotFoundException, IOException //writes byte array to file
    {
        System.out.println("Enter write"); // console output for visualisation
        File file = new File(fileName);
        OutputStream outputStream = new FileOutputStream(file);
        outputStream.write(output.toByteArray());
    }         

    public byte[] blockIncrease(int blockNumber) //increases the blockNumber by 1, turns it into a byte,takes the first 8 bits and puts them into a byte array,then takes sent 8 bits and puts them into the second byte array
    {
        blockNumber++;
        byte[] bytes = new byte[2];
        bytes[0] = (byte) (blockNumber & 0xFF); // take first 8 bits
        bytes[1] = (byte) ((blockNumber >> 8) & 0xFF); // shift 8 bits and take that
        return bytes;
    }    

    public static void main(String[] arguments) throws IOException, SocketException, Exception 
    {
        if (arguments.length != 3) 
        {
            System.out.println("Arguments must be in the following structure: IP(i.e 127.0.0.1) | Request(1 or 2) | Filename (i.e 'test') each seperated by a space"); // console output for visualisation
            return;
        }
        String nameOfFile = arguments[2];
        TFTPUDPClient client = new TFTPUDPClient(); //initialises the client variable
        client.get(nameOfFile, arguments);

    }
}

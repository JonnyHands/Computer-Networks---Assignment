package tftp.udp.server;
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


public class TFTPUDPServer extends Thread 
{
    int port = 1111; //initialises the default port and sets it to 1111
    DatagramSocket socket; // initialises the socket and sets it to null

    public TFTPUDPServer() throws SocketException, IOException //sets up the server with the default port
    {
        this.socket = new DatagramSocket(port);
        while (true) 
        {
            byte[] buffer = new byte[516];
            DatagramPacket packet = new DatagramPacket(buffer, 516);
            System.out.println("Server is waiting for a packet..."); // console output for visualisation            
            socket.receive(packet);            
            new Thread(new TFTPUDPServerHandler(packet)).start();
            System.out.println("A new thread server has started."); // console output for visualisation
        }
    }

    public class TFTPUDPServerHandler implements Runnable 
    {
        InetAddress address; // the ip address
        int port; // the port number
        protected DatagramSocket socket = null ;
        DatagramPacket packetSent; // the packet to be sent
        DatagramPacket packetReceived; // the packet received        
        Random random = new Random();
        byte opcode_ReadRequest = 1; // opcode for the read request
        byte opcode_WriteRequest = 2; // opcode for the write request
        byte opcode_Data = 3; // opcode for the data
        byte opcode_Acknowledgement = 4; // opcode for the ackowledgement
        byte opcode_Error = 5; // opcode for an error
        byte requestType; // the request type
        final int default_Port = random.nextInt(65535 - 1024) + 1024; // picks a random port between 1024 and 65535 and finalises it
        

        public TFTPUDPServerHandler(DatagramPacket packet) throws SocketException //creates new socket with random port
        {
            packetReceived = packet; //sets the packet received to the packet given
            socket = new DatagramSocket(default_Port); // random port
            socket.setSoTimeout(3000); //sets the SoTimeout to 3000ms (3 seconds)
        }

        @Override
        public void run() 
        {
            String nameOfFile = ""; //string for the file name
            ByteArrayOutputStream out = new ByteArrayOutputStream(); // sets the ByteArrayOutputStream
            int bytesSent = 0; // how much has been sent so far, only used for read
            byte[] write = {}; // a byte list to store what needs to be written
            int blockToSend = 0; // block to be sent with the packet
            int blockExpected = 0; // block number expected from the packet 

            try // run forever
            {
                
                boolean isLastPacket = false; // checks if is is the last packet that needs to be sent
                boolean isAllSent = false; // checks if all data has been sent from a file
                while (isLastPacket == false) //checks it isnt the last packet
                {
                    address = packetReceived.getAddress(); //sets the address
                    port = packetReceived.getPort(); //sets the port
                    byte[] receivedBuffer = packetReceived.getData();
                    byte[] opcode = {receivedBuffer[0], receivedBuffer[1]};

                    if (opcode[1] == opcode_ReadRequest) { // gets file and starts sending 
                        nameOfFile = getName(receivedBuffer); // gets filename out of the packet
                        requestType = opcode_ReadRequest; // sets request type

                        System.out.println("Read request received"); // console output for visualisation
                        try // run forever
                        {
                            write = readFile(nameOfFile); // load in the file to send over
                        } 
                        catch (NoSuchFileException n) 
                        {
                            errorMessage("File not found."); // send an error if file not found
                            System.err.println("File not found."); // console output for visualisation
                            break;
                        }

                        byte[] blockNumber = blockIncrease(blockToSend); // sets the blockNumber 
                        blockToSend++; // first block starts with 1 so needs to be increased by 1 initially
                        System.out.println(blockToSend); // console output for visualisation
                        ByteArrayOutputStream contentChunk = new ByteArrayOutputStream(); // data sent in the packet
                        int bytesToBeSent = write.length; // amount of bytes needing to be sent
                        System.out.println("Number of bytes to be sent: " + 
                                bytesToBeSent); // console output for visualisation
                        int bytesLeftToBeSent; // initilises bytesLeftToBeSent and sets to 0
                        int bytesSentSoFar = bytesSent; // used to stop infinite loop
                        
                        if (((bytesToBeSent - bytesSent) / 512 ) >= 1 ) // checks if there is more than 512 bytes if so sends 512 bytes
                        {
                            bytesLeftToBeSent = 512; // sets bytesLeftToBeSent to 512 (maximum number of bytes)
                        } 
                        else 
                        { 
                            bytesLeftToBeSent = (bytesToBeSent - bytesSent); // if there is less than 512 bytes sends the left over bytes
                            System.out.println("All that was requested has been sent."); // console output for visualisation
                        }

                        
                        for (int i = bytesSent; i < (bytesSent + bytesLeftToBeSent); i++) // loop so it doesnt fill with space
                        { 
                            contentChunk.write(write[i]); // write to the bytearray
                            bytesSentSoFar++; //increments bytesSentSoFar
                        }
                        bytesSent = bytesSentSoFar; // reassigned back after the loop

                        if (bytesSent == bytesToBeSent) //checks all bytes have been sent
                        {
                            isAllSent = true; 
                            System.out.println("All data has been sent."); // console output for visualisation
                        }

                        byte[] chunk = contentChunk.toByteArray(); // convert chunk into byte array so can go into packet
                        byte[] data = {0, opcode_Data, blockNumber[0],
                            blockNumber[1]}; //initialises data
                        byte[] buffer = new byte[data.length + chunk.length]; //initialises buffer
                        System.arraycopy(data, 0, buffer, 0, data.length); // copies the packet header into the buffer
                        System.arraycopy(chunk, 0, buffer, data.length, 
                                chunk.length); // copies the packet data from file into buffer
                        packetSent = new DatagramPacket(buffer, buffer.length, 
                                address, port); //sets up packetSent variable
                        socket.send(packetSent);
                        System.out.println("Read data has been sent."); // console output for visualisation

                        if (bytesSent == bytesToBeSent) //checks all bytes have been sent
                        {
                            if (bytesToBeSent % 512 == 0) // checks if the last packet is 512 to determine if empty packet needs to be sent
                            { 
                                blockNumber = blockIncrease(blockToSend); // callibrates blockNumber
                                blockToSend++;
                                byte[] emptyPacket = {0, opcode_Data, 
                                    blockNumber[0], blockNumber[1]}; //creates an empty packet
                                packetSent = new DatagramPacket(emptyPacket, 
                                        emptyPacket.length, address, packetReceived.getPort());
                                socket.send(packetSent); // sneds empty packet
                                isAllSent = true;
                                System.out.println("Empty packet of data has been sent."); // console output for visualisation
                            }
                        }
                    } 
                    else if (opcode[1] == opcode_WriteRequest) //checks if opcode is write request
                    { 
                        nameOfFile = getName(receivedBuffer); // gets filename out of the packet
                        requestType = opcode_WriteRequest; // sets request type
                        System.out.println("Write request received."); // console output for visualisation
                        byte[] blockNumber = {0, 0}; // sends a block number of 0 for the first request
                        byte[] acknowledgement = {0, opcode_Acknowledgement, blockNumber[0], 
                            blockNumber[1]}; // sends an acknowledgement
                        packetSent = new DatagramPacket(acknowledgement, acknowledgement.length, address, port);
                        socket.send(packetSent);        
                    } 
                    else if (opcode[1] == opcode_Data) //checks if opcode is data
                    { 
                        requestType = opcode_Data; // sets request type
                        System.out.println("Data to write has been received."); // console output for visualisation
                        byte[] blockNumber = {receivedBuffer[2], 
                            receivedBuffer[3]};
                        byte[] blockNumberExpected = blockIncrease(blockExpected);// increases block number by 1 before check
                        System.out.println("Block number expected is " + 
                                (blockExpected + 1) + "."); // console output for visualisation
                        if (blockNumber[0] == blockNumberExpected[0] && 
                                blockNumber[1] == blockNumberExpected[1]) 
                        {
                            System.out.println("Block number passed."); // console output for visualisation
                            blockExpected++;
                            out.write(packetReceived.getData(), 4, 
                                    packetReceived.getLength() - 4); // gets data from byte
                            System.out.println("The length of the byte array is " + 
                                    out.size() + "."); // console output for visualisation
                            byte[] acknowledgement = {0, opcode_Acknowledgement, 
                                blockNumber[0], blockNumber[1]};
                            packetSent = new DatagramPacket(acknowledgement, 
                                    acknowledgement.length, address, port);
                            socket.send(packetSent);
                        } 
                        else 
                        {
                            System.out.println("Error with block number."); // console output for visualisation                            
                        }
                    } 
                    else if (opcode[1] == opcode_Acknowledgement) //checks if opcode is acknowledgement
                    { 
                        requestType = opcode_Acknowledgement; // sets request type
                        if (isAllSent == true)  //checks if all is sent
                        { 
                            System.out.println("Last packet received."); // console output for visualisation
                            break;
                        } 
                        else 
                        {
                            System.out.println("Acknowledgement received."); // console output for visualisation
                            ByteArrayOutputStream pendingWrite = 
                                    new ByteArrayOutputStream();
                            byte[] blockNumber = blockIncrease(blockToSend); 
                            blockToSend++;
                            System.out.println("Block number: " + blockToSend + " sent."); // console output for visualisation                            
                            int bytesToBeSent = write.length; // amount of bytes needing to be sent
                            int bytesLeftToSend; // initilises bytesLeftToSend
                            if ((bytesToBeSent - bytesSent) / 512 >= 1) { // checks if there is more than 512 bytes if so sends 512 bytes
                                bytesLeftToSend = 512; // sets bytesLeftToBeSent to 512 (maximum number of bytes)
                            } 
                            else 
                            {
                                bytesLeftToSend = (bytesToBeSent - bytesSent); // if there is less than 512 bytes sends the left over bytes
                                System.out.println("Number of bytes left to send: " +
                                        bytesLeftToSend + "."); // console output for visualisation
                            }

                            int bytesSentSoFar = bytesSent;
                            for (int i = bytesSent; i < (bytesSent + 
                                    bytesLeftToSend); i++)  // loop so it doesnt fill with space
                            {
                                pendingWrite.write(write[i]); // write to the bytearray
                                bytesSentSoFar++; //increments bytesSentSoFar
                            }
                            bytesSent = bytesSentSoFar; // reassigned back after the loop

                            if (bytesSent == bytesToBeSent) //checks all bytes have been sent
                            {
                                isAllSent = true;
                                System.out.println("Last of the data has been sent."); // console output for visualisation
                            }

                            byte[] dataToBeSent = pendingWrite.toByteArray();  // convert dataToBeSent into byte array so can go into packet
                            byte[] startingData = {0, opcode_Data, 
                                blockNumber[0], blockNumber[1]}; //sets startingData
                            byte[] data = new byte[dataToBeSent.length + 
                                    startingData.length]; //initialises data
                            System.arraycopy(startingData, 0, data, 0, 
                                    startingData.length); // copies the startingData into the data
                            System.arraycopy(dataToBeSent, 0, data, 
                                    startingData.length, dataToBeSent.length); // copies the data to be sent into the data
                            packetSent = new DatagramPacket(data, data.length, 
                                    address, packetReceived.getPort()); //initiates packet sent
                            socket.send(packetSent);
                            System.out.println("Data to write has been sent."); // console output for visualisation

                            if (bytesSent == bytesToBeSent && 
                                    bytesToBeSent % 512 == 0) // checks if the last packet is 512 to determine if empty packet needs to be sent
                            { 
                                blockNumber = blockIncrease(blockToSend); 
                                blockToSend++;
                                byte[] emptyPacket = {0, opcode_Data, 
                                    blockNumber[0], blockNumber[1]}; //creates an empty packet
                                packetSent = new DatagramPacket(emptyPacket, 
                                        emptyPacket.length, address, 
                                        packetReceived.getPort());
                                socket.send(packetSent); //sends empty packet
                                isAllSent = true;
                                System.out.println("Empty packet of data has been sent."); // console output for visualisation
                            }
                        }
                    }            

                    if (receivedBuffer[1] == opcode_Data &&packetReceived.getLength() < 516) // checks if is last packet of data
                    { 
                        isLastPacket = true;
                    }
                    else 
                    {
                        try 
                        {
                            socket.receive(packetReceived);
                        } 
                        catch (SocketTimeoutException socketTimeoutException) 
                        {
                            socket.send(packetSent);
                            System.out.println("Timeout initiated."); // console output for visualisation
                        } 
                    }
                } 
            } 
            catch (IOException exception) 
            {
                System.err.println(exception);
            }

            if (requestType == opcode_WriteRequest) // checks if request is a write request
            { 
                try 
                {
                    writeFile(out, nameOfFile); // writes data to file
                } 
                catch (IOException ex) 
                {
                    System.err.println(ex);
                    System.out.println("Didn't write file "); // console output for visualisation
                }
            }
            System.out.println("Socket for this thread is closed"); // console output for visualisation
            socket.close(); //closes socket
        }

        private String getName(byte[] receivedBuffer) 
                throws UnsupportedEncodingException // gets name of the file from request packet
        {
            int nameLength = 0;
            for (int i = 2; receivedBuffer[i] != (byte) 0; i++)  // counts through until a zero is hit
            { 
                nameLength++;
            }
            byte[] fileName = new byte[nameLength];
            for (int j = 0; j < nameLength; j++) // uses the count to get full filename
            { 
                fileName[j] = receivedBuffer[j + 2];
            }
            String fileName1 = new String(fileName); //makes  a stirng for the filename
            return fileName1;
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

        private void errorMessage(String message) throws IOException //sends error packet to client which causes sockets to close
        {
            requestType = opcode_Error; // sets request type
            byte[] byteMessage = message.getBytes("US-ASCII"); // send a message with the error
            byte[] opcodeError = {0, 1};
            byte[] mainError = {0, opcode_Error, opcodeError[0], 
                opcodeError[1]};
            byte[] error = new byte[mainError.length + byteMessage.length + 1];
            System.arraycopy(mainError, 0, error, 0, mainError.length); // copies the main error into the error
            System.arraycopy(byteMessage, 0, error, mainError.length, 
                    byteMessage.length); // copies the byteMessage into the error
            error[mainError.length + byteMessage.length] = (byte) 0;
            DatagramPacket packetError = new DatagramPacket(error, error.length,
                    address, packetReceived.getPort());
            socket.send(packetError);
        }

        public byte[] blockIncrease(int blockNumber) //increases the blockNumber by 1, turns it into a byte,takes the first 8 bits and puts them into a byte array,then takes sent 8 bits and puts them into the second byte array
        {
            blockNumber++;
            byte[] bytes = new byte[2];
            bytes[0] = (byte) (blockNumber & 0xFF); // take first 8 bits
            bytes[1] = (byte) ((blockNumber >> 8) & 0xFF); // shift 8 bits and take that
            return bytes;
        }             
    }
    
    public static void main(String[] arguments) throws IOException 
    {
            new TFTPUDPServer().start();
            System.out.println("Time server has started."); // console output for visualisation             
    }    
    
}

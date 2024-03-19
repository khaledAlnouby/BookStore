import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class client {
    public static void main(String[] args) {
        final String serverAddress = "localhost";
        final int serverPort = 8000;

        try (Socket socket = new Socket(serverAddress, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to server");

            // Start the client loop
            String userInput;
            while (true) {
                // Read user input
                userInput = stdIn.readLine();
                if (userInput == null) {
                    break; // Exit loop if input is null (end of input stream)
                }

                // Send user input to server
                out.println(userInput);

                // Receive and print response from server
                String response = in.readLine();
                System.out.println("Response from server: " + response);

                // If the response indicates list of books, print them
                if (response.equals("LIST_BOOKS")) {
                    printBookList(in);
                }
            }


        } catch (IOException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printBookList(BufferedReader in) throws IOException {
        System.out.println("Books available in inventory:");
        String bookInfo;
        while ((bookInfo = in.readLine()) != null && !bookInfo.isEmpty()) {
            System.out.println(bookInfo);
        }
    }
}

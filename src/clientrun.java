import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class clientrun {

    public static void main(String[] args) {
        final String serverAddress = "localhost"; // Change this to the IP address or hostname of your server
        final int serverPort = 8000; // Change this to the port on which your server is listening

        try (Socket socket = new Socket(serverAddress, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Connected to server");

            // Start the client thread
            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                out.println(userInput);
                if (userInput.startsWith("LOGIN")) {
                    System.out.println("Response from server: " + in.readLine());
                }
            }

        } catch (IOException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

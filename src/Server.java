import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
public class Server {
    private static final int PORT = 8000;
    private static final Map<String, String> userCredentials = new HashMap<>();
    private static final Map<Integer, Book> bookInventory = new HashMap<>();
    private static int borrowedBooksCount = 0;
    private static int availableBooksCount = 0;
    private static int acceptedRequestsCount = 0;
    private static int rejectedRequestsCount = 0;
    private static int pendingRequestsCount = 0;

    public static void main(String[] args) {
        userCredentials.put("user1", "password1");
        userCredentials.put("user2", "password2");
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server started. Listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);

                // Handle each client connection in a separate thread
                client clientHandler = new client(clientSocket);
                clientHandler.start();
                printLibraryStatistics();
            }
        } catch (IOException e) {
            System.err.println("Error starting the server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void printLibraryStatistics() {
        System.out.println("Library Statistics:");
        System.out.println("Borrowed Books: " + borrowedBooksCount);
        System.out.println("Available Books: " + availableBooksCount);
        System.out.println("Accepted Requests: " + acceptedRequestsCount);
        System.out.println("Rejected Requests: " + rejectedRequestsCount);
        System.out.println("Pending Requests: " + pendingRequestsCount);
    }
    static void updateLibraryStatistics() {
        availableBooksCount++; // Increment available books count after adding a book
    }
}

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
    private static int bookIdCounter = 1; // Initialize bookIdCounter

    public static void main(String[] args) {

        userCredentials.put("user1", "password1");
        userCredentials.put("user2", "password2");
        addInitialBooks();
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

    private static void addInitialBooks() {
        // Add books to the book inventory
        addBook("Book1 Title", "Book1 Author", "Fiction", 10.99, 5);
        addBook("Book2 Title", "Book2 Author", "Science", 15.99, 3);
        // Add more books as needed...
    }

    private static void addBook(String title, String author, String genre, double price, int quantity) {
        Book book = new Book(title, author, genre, price, quantity);
        bookInventory.put(bookIdCounter++, book);
        availableBooksCount++; // Increment available books count after adding a book
    }

    static void printLibraryStatistics() {
        System.out.println("Library Statistics:");
        System.out.println("Borrowed Books: " + borrowedBooksCount);
        System.out.println("Available Books: " + availableBooksCount);
        System.out.println("Accepted Requests: " + acceptedRequestsCount);
        System.out.println("Rejected Requests: " + rejectedRequestsCount);
        System.out.println("Pending Requests: " + pendingRequestsCount);
        System.out.println(bookInventory);
    }

    static void updateLibraryStatistics() {
        availableBooksCount++; // Increment available books count after adding a book
    }

    public static void sendBookList(PrintWriter out) {
        out.println("LIST_BOOKS"); // Send a command to signal the start of the book list
        for (Book book : bookInventory.values()) {
            out.println(book.toString());
        }
        out.println(); // Send an empty line to signal the end of the book list
    }
}



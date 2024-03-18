import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class client extends Thread {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private static Map<Integer, Book> bookInventory = new HashMap<>();
    private static Map<String, String> userCredentials = new HashMap<>();
    private List<String> pendingRequests = new ArrayList<>();
    private List<String> requestHistory = new ArrayList<>();
    private static int bookIdCounter = 1;

    public client(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));


            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received from client: " + inputLine);
                String[] tokens = inputLine.split(" ");
                String command = tokens[0];

                switch (command) {
                    case "REGISTER":
                        // Register new user
                        String name = tokens[1];
                        String newUsername = tokens[2];
                        String newPassword = tokens[3];
                        if (registerUser(name, newUsername, newPassword)) {
                            out.println("Registration successful.");
                        } else {
                            if (userCredentials.containsKey(newUsername)) {
                                out.println("ERROR 409: Username already exists. Please choose another one.");
                            } else {
                                out.println("ERROR 500: Internal server error. Please try again later.");
                            }
                        }
                        break;
                    case "LOGIN":
                        // Check user credentials
                        String enteredUsername = tokens[1];
                        String enteredPassword = tokens[2];
                        if (authenticateUser(enteredUsername, enteredPassword)) {
                            out.println("Login successful.");
                        } else {
                            if (!userCredentials.containsKey(enteredUsername)) {
                                out.println("ERROR 404: not found.");
                            } else {
                                out.println("ERROR 401: Invalid username or password Unauthorized.");
                            }
                        }
                        break;
                    case "ADD_BOOK":
                        // Mock: Add book to inventory
                        String title = tokens[1];
                        String author = tokens[2];
                        String genre = tokens[3];
                        double price = Double.parseDouble(tokens[4]);
                        int quantity = Integer.parseInt(tokens[5]);
                        addBook(title, author, genre, price, quantity);
                        out.println("Book added successfully.");
                        Server.updateLibraryStatistics(); // Update library statistics after adding a book
                        break;
                    case "LIST_BOOKS":
                        // Mock: List all books in inventory
//                        out.println("LIST_BOOKS");
//                        receiveBookList(in);
                        Server.sendBookList(out);

                        break;
                    case "SUBMIT_REQUEST":
                        // Mock: Add request to pending list
                        String request = tokens[1];
                        pendingRequests.add(request);
                        out.println("Request submitted successfully.");
                        break;
                    case "BORROW_BOOK":
                        // Mock: Borrow book (update quantity)
                        int bookId = Integer.parseInt(tokens[1]);
                        int quantityToBorrow = Integer.parseInt(tokens[2]);
                        borrowBook(bookId, quantityToBorrow);
                        out.println("Book borrowed successfully.");
                        break;
                    case "ACCEPT_REQUEST":
                        // Mock: Remove request from pending list
                        String acceptedRequest = tokens[1];
                        pendingRequests.remove(acceptedRequest);
                        out.println("Request accepted.");
                        break;
                    case "REJECT_REQUEST":
                        // Mock: Remove request from pending list
                        String rejectedRequest = tokens[1];
                        pendingRequests.remove(rejectedRequest);
                        out.println("Request rejected.");
                        break;
                    case "VIEW_REQUEST_HISTORY":
                        // Mock: Send request history to client
                        sendRequestHistory();
                        break;
                    case "BROWSE_BOOKS":
                        // Browse books
//                        browseBooks();
                        break;
                    case "SEARCH_BOOKS":
                        // Send search query to the server
                        String query = tokens[1];
                        System.out.println("Received search query: " + query);
                        searchBooks(query);
                        break;
                    case "VIEW_DETAILS":
                        // Send book ID to the server
                        int bookkId = Integer.parseInt(tokens[1]);
                        viewBookDetails(bookkId, out);
                        break;


                    default:
                        out.println("Unknown command: " + command);
                        break;
                }
                Server.printLibraryStatistics(); // Print library statistics after each request
            }

            clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error handling client request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void searchBooks(String query) {
        System.out.println("Received search query: " + query);
        StringBuilder response = new StringBuilder();
        boolean found = false;
        for (Book book : bookInventory.values()) {
            System.out.println("Checking book: " + book.getTitle() + " - " + book.getAuthor() + " - " + book.getGenre());
            if (book.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                    book.getAuthor().toLowerCase().contains(query.toLowerCase()) ||
                    book.getGenre().toLowerCase().contains(query.toLowerCase())) {
                response.append(book.toString()).append("\n");
                found = true;
            }
        }
        if (!found) {
            System.out.println("No books found matching the query: " + query);
        } else {
            System.out.println("Books found matching the query: " + query);
            System.out.println(response.toString());
        }
    }

    static void viewBookDetails(int bookId, PrintWriter out) {
        Book book = bookInventory.get(bookId);
        if (book != null) {
            out.println(book.toString());
        } else {
            out.println("Book with ID " + bookId + " not found.");
        }
    }

    //    private void viewBookDetails(int bookId) {
//        Book book = bookInventory.get(bookId);
//        if (book != null) {
//            out.println(book.toString());
//        } else {
//            out.println("Book with ID " + bookId + " not found.");
//        }
//    }
    private void addBook(String title, String author, String genre, double price, int quantity) {
        Book book = new Book(title, author, genre, price, quantity);
        bookInventory.put(bookIdCounter++, book);

    }




    private boolean authenticateUser(String username, String password) {
        String storedPassword = userCredentials.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }
    private void borrowBook(int bookId, int quantityToBorrow) {
        Book book = bookInventory.get(bookId);
        if (book != null && book.getQuantity() >= quantityToBorrow) {
            book.setQuantity(book.getQuantity() - quantityToBorrow);
        }
    }

    private void sendRequestHistory() {
        for (String request : requestHistory) {
            out.println(request);
        }
    }

    private boolean registerUser(String name, String username, String password) {
        if (!userCredentials.containsKey(username)) {
            userCredentials.put(username, password);
            return true;
        }
        return false;
    }




    public static void main(String[] args) {
        final String serverAddress = "localhost";
        final int serverPort = 8000;

        try (Socket socket = new Socket(serverAddress, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Connected to server");

            // Start the client thread
            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                out.println(userInput);
                String response = in.readLine();

                System.out.println("Response from server: " + response); // Print response for all inputs
                if (response.equals("LIST_BOOKS")) {
                    // Call the sendBookList method to receive and print the list of books
                    receiveBookList(in);
                } else if (response.startsWith("Library Statistics:")) {
                    // Print library statistics received from the server
                    String line;
                    while (!(line = in.readLine()).isEmpty()) {
                        System.out.println(line);
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void receiveBookList(BufferedReader in) throws IOException {
        System.out.println("Books available in inventory:");
        String bookInfo;
        while (!(bookInfo = in.readLine()).isEmpty()) {
            System.out.println(bookInfo);
        }
    }
}
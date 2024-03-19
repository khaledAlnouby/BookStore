import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 8000;
    private static Map<String, String> userCredentials = new HashMap<>();
    private static Map<Integer, Book> bookInventory = new HashMap<>();
    static List<Request> pendingRequests = new ArrayList<>();
    private static int requestIdCounter = 1; // Initial request ID counter value
    private static int borrowedBooksCount = 0;
    private static int availableBooksCount = 0;
    private static int acceptedRequestsCount = 0;
    private static int rejectedRequestsCount = 0;
    private static int pendingRequestsCount = 0;
    private static int bookIdCounter = 1;

    public static void main(String[] args) {
        // Initialize some test data
        userCredentials.put("user1", "password1");
        userCredentials.put("user2", "password2");
        addInitialBooks();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);

                // Handle client in a separate thread
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting the server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void addInitialBooks() {
        // Add some initial books to the inventory
        addBook("Book1", "Author1", "Fiction", 10.99, 5);
        addBook("Book2", "Author2", "Science", 15.99, 3);
    }

    private static void addBook(String title, String author, String genre, double price, int quantity) {
        Book book = new Book(title, author, genre, price, quantity);
        bookInventory.put(bookIdCounter++, book);
        availableBooksCount++; // Increment available books count after adding a book
    }

    static boolean authenticateUser(String username, String password) {
        return userCredentials.containsKey(username) && userCredentials.get(username).equals(password);
    }

    public static class ClientHandler extends Thread {
        private String loggedInUsername; // Store the username of the logged-in user
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // Read and process client requests
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Received from client: " + inputLine);

                    // Process the request
                    String[] tokens = inputLine.split(" ");
                    String command = tokens[0];

                    switch (command) {
                        case "REGISTER":
                            String name = tokens[1];
                            String newUsername = tokens[2];
                            String newPassword = tokens[3];
                            if (userCredentials.containsKey(newUsername)) {
                                out.println("ERROR 409: Username already exists. Please choose another one.");
                            } else {
                                userCredentials.put(newUsername, newPassword);
                                out.println("Registration successful.");
                            }
                            break;
                        case "LOGIN":
                            String enteredUsername = tokens[1];
                            String enteredPassword = tokens[2];
                            loggedInUsername = tokens[1]; // Store the username of the logged-in user
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
                        case "LIST_BOOKS":
                            sendBookList();
                            // Add other cases for book browsing, searching, adding, removing, etc.
                            break;
                        case "SEARCH_TITLE":
                            // Handle searching by title
                            searchBooksByTitle(tokens);
                            break;
                        case "SEARCH_AUTHOR":
                            // Handle searching by author
                            searchBooksByAuthor(tokens);
                            break;
                        case "SEARCH_GENRE":
                            // Handle searching by genre
                            searchBooksByGenre(tokens);
                            break;
                        case "VIEW_DETAILS":
                            // Handle viewing detailed information
                            viewBookDetails(tokens);
                            break;
                        case "ADD_BOOK":
                            // Extract book details from tokens
                            String title = tokens[1];
                            String author = tokens[2];
                            String genre = tokens[3];
                            double price = Double.parseDouble(tokens[4]);
                            int quantity = Integer.parseInt(tokens[5]);

                            // Create a new Book object with user's username as lender
                            Book newBook = new Book(title, author, genre, price, quantity);
                            newBook.setLenderUsername(loggedInUsername); // Store the username of the logged-in user); // Set lender username
                            bookInventory.put(bookIdCounter++, newBook);
                            // Send confirmation message to the client
                            out.println("BOOK_ADDED");
                            break;
                        case "REMOVE_BOOK":
                            // Extract book details from tokens
                            String titleToRemove = tokens[1];
                            String authorToRemove = tokens[2];

                            // Iterate over the book inventory to find the book to remove
                            boolean bookRemoved = false;
                            for (Iterator<Map.Entry<Integer, Book>> iterator = bookInventory.entrySet().iterator(); iterator.hasNext(); ) {
                                Map.Entry<Integer, Book> entry = iterator.next();
                                Book book = entry.getValue();
                                if (book.getTitle().equalsIgnoreCase(titleToRemove) && book.getAuthor().equalsIgnoreCase(authorToRemove)) {
                                    // Remove the book from the inventory
                                    iterator.remove();
                                    bookRemoved = true;
                                    break; // No need to continue searching once the book is found and removed
                                }
                            }

                            // Send response to the client based on whether the book was removed or not
                            if (bookRemoved) {
                                out.println("BOOK_REMOVED");
                            } else {
                                out.println("ERROR 404: Book not found.");
                            }
                            break;
                        case "SUBMIT_REQUEST":
                            // Extract request details from tokens
                            String borrower = tokens[1];
                            String lender = tokens[2];
                            String requestedBookTitle = tokens[3];

                            // Find the book in the inventory based on the title
                            Book requestedBook = null;
                            for (Book book : bookInventory.values()) {
                                if (book.getTitle().equalsIgnoreCase(requestedBookTitle)) {
                                    requestedBook = book;
                                    break;
                                }
                            }

                            // Check if the requested book was found
                            if (requestedBook != null) {
                                // Create and store the request
                                int requestId = requestIdCounter++; // Increment the request ID counter
                                Request request = new Request(requestId, borrower, lender, requestedBook, "pending");
                                pendingRequests.add(request); // Add the request to the pendingRequests list
                                out.println("REQUEST_SUBMITTED " + requestId);
                            } else {
                                out.println("ERROR 404: Requested book not found."); // Notify client if the book was not found
                            }
                            break;

                        case "ACCEPT_REQUEST":
                            // Extract request details from tokens
                            String borrowerToAccept = tokens[1];
                            int requestId = Integer.parseInt(tokens[2]);

                            // Find the request in pending requests
                            for (Request req : pendingRequests) {
                                if (req.getBorrower().equals(borrowerToAccept) && req.getId() == requestId) {
                                    req.setStatus("accepted");
                                    pendingRequests.remove(req); // Remove the request from the pendingRequests list
                                    out.println("REQUEST_ACCEPTED");
                                    // Start chat or notify borrower
                                    break;
                                }
                            }
                            break;

                        case "REJECT_REQUEST":
                            // Extract request details from tokens
                            String borrowerToReject = tokens[1];
                            int requestIdToReject = Integer.parseInt(tokens[2]);

                            // Find the request in pending requests
                            for (Request req : pendingRequests) {
                                if (req.getBorrower().equals(borrowerToReject) && req.getId() == requestIdToReject) {
                                    req.setStatus("rejected");
                                    pendingRequests.remove(req); // Remove the request from the pendingRequests list
                                    out.println("REQUEST_REJECTED");
                                    // Notify borrower
                                    break;
                                }
                            }
                            break;

                    }

                }
            } catch (IOException e) {
                System.err.println("Error handling client request: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        private void searchBooksByTitle(String[] tokens) {
            // Extract the title from tokens
            String titleToSearch = tokens[1];

            // Search for books with matching title
            List<Book> matchingBooks = new ArrayList<>();
            for (Book book : bookInventory.values()) {
                if (book.getTitle().equalsIgnoreCase(titleToSearch)) {
                    matchingBooks.add(book);
                }
            }

            // Send the list of matching books to the client
            sendMatchingBooks(matchingBooks);
        }
        private void searchBooksByAuthor(String[] tokens) {
            // Extract the author from tokens
            String authorToSearch = tokens[1];

            // Search for books with matching author
            List<Book> matchingBooks = new ArrayList<>();
            for (Book book : bookInventory.values()) {
                if (book.getAuthor().equalsIgnoreCase(authorToSearch)) {
                    matchingBooks.add(book);
                }
            }

            // Send the list of matching books to the client
            sendMatchingBooks(matchingBooks);
        }
        private void searchBooksByGenre(String[] tokens) {
            // Extract the genre from tokens
            String genreToSearch = tokens[1];

            // Search for books with matching genre
            List<Book> matchingBooks = new ArrayList<>();
            for (Book book : bookInventory.values()) {
                if (book.getGenre().equalsIgnoreCase(genreToSearch)) {
                    matchingBooks.add(book);
                }
            }

            // Send the list of matching books to the client
            sendMatchingBooks(matchingBooks);
        }
        private void viewBookDetails(String[] tokens) {
            // Extract the book ID from tokens
            int bookId = Integer.parseInt(tokens[1]);

            // Find the book with the given ID
            Book book = bookInventory.get(bookId);

            // Send detailed information about the book to the client
            if (book != null) {
                out.println("BOOK_DETAILS " + book.toString());
            } else {
                out.println("ERROR 404: Book not found.");
            }
        }
        private void sendMatchingBooks(List<Book> matchingBooks) {
            StringBuilder response = new StringBuilder("MATCHING_BOOKS\n"); // Start of response
            if (!matchingBooks.isEmpty()) {
                for (Book book : matchingBooks) {
                    response.append(book.toString()).append("\n"); // Append book details
                }
            } else {
                response.append("No books found matching the criteria.");
            }
            out.println(response.toString().trim()); // Send the response
        }



        private void sendBookList() {
            StringBuilder response = new StringBuilder("LIST_BOOKS\n"); // Start of response
            for (Book book : bookInventory.values()) {
                response.append(book.toString()).append("\n"); // Append book details
            }
            out.println(response.toString().trim()); // Send the response
        }
    }



}

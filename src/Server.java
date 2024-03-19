import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 8000;
    private static Map<String, String> userCredentials = new HashMap<>();
    private static Map<Integer, Book> bookInventory = new HashMap<>();
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

    static class ClientHandler extends Thread {
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

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Server {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/bookstore";
    private static final String DB_USER = "root";

    private static Connection connection;
    private static final int PORT = 8000;
    private static Map<String, String> userCredentials = new HashMap<>();
    private static Map<Integer, Book> bookInventory = new HashMap<>();
    static List<Request> pendingRequests = new ArrayList<>();
    private static List<Request> acceptedRequests = new ArrayList<>();
    private static List<Request> rejectedRequests = new ArrayList<>();
    private static int requestIdCounter = 1; // Initial request ID counter value
    private static int borrowedBooksCount = 0;
    private static int availableBooksCount = 0;
    private static int acceptedRequestsCount = 0;
    private static int rejectedRequestsCount = 0;
    private static int pendingRequestsCount = 0;
    private static int bookIdCounter = 1;

    public static void main(String[] args) throws IOException {

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Listening on port " + PORT);

            try (Connection connection = DriverManager.getConnection(DB_URL , DB_USER,null)){
                System.out.println("Connected to database.");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);

                // Handle client in a separate thread
                new ClientHandler(clientSocket, connection).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting the server: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
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
        private Connection connection; // Add connection field


        public ClientHandler(Socket socket ,Connection connection) {
            this.clientSocket = socket;
            this.connection = connection;
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
                            registerUser(newUsername, newPassword);
                            break;
                        case "LOGIN":
                            String enteredUsername = tokens[1];
                            String enteredPassword = tokens[2];
                            loggedInUsername = enteredUsername; // Store the username of the logged-in user

                            // Check credentials in the database
                            if (checkCredentials(enteredUsername, enteredPassword)) {
                                out.println("Login successful.");
                            } else {
                                out.println("ERROR 401: Invalid username or password Unauthorized.");
                            }
                            break;

                        case "LIST_BOOKS":
                            sendBookListFromDatabase();
                            break;
                        case "SEARCH_TITLE":
                            // Handle searching by title
                            searchBooksByTitle(tokens[1]);
                            break;
                        case "SEARCH_AUTHOR":
                            // Handle searching by author
                            searchBooksByAuthor(tokens[1]);
                            break;
                        case "SEARCH_GENRE":
                            // Handle searching by genre
                            searchBooksByGenre(tokens[1]);
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

                            // Insert the book into the database
                            if (addBookToDatabase(title, author, genre, price, quantity, loggedInUsername)) {
                                out.println("BOOK_ADDED");
                            } else {
                                out.println("Error adding book to the database.");
                            }
                            break;

                        case "REMOVE_BOOK":
                            // Extract book details from tokens
                            String titleToRemove = tokens[1];
                            String authorToRemove = tokens[2];

                            // Remove the book from the database
                            if (removeBookFromDatabase(titleToRemove, authorToRemove)) {
                                out.println("BOOK_REMOVED Successfully ");
                            } else {
                                out.println("Error removing book from the database.");
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
                                Request request = new Request(requestId, borrower, lender, requestedBook, "pending",out,in);
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
                            Iterator<Request> iteratorAccept = pendingRequests.iterator();
                            while (iteratorAccept.hasNext()) {
                                Request req = iteratorAccept.next();
                                if (req.getBorrower().equals(borrowerToAccept) && req.getId() == requestId) {
                                    req.setStatus("accepted");
                                    iteratorAccept.remove(); // Remove the request from the pendingRequests list
                                    acceptedRequests.add(req); // Add the request to the acceptedRequests list
                                    out.println("REQUEST_ACCEPTED");

                                    // Start chat session
                                    Chat chat = new Chat(req.getBorrower(), req.getLender(), out, in);
                                    req.setChat(chat);
                                    chat.startChat();

                                    break;
                                }
                            }
                            break;

                        case "REJECT_REQUEST":
                            // Extract request details from tokens
                            String borrowerToReject = tokens[1];
                            int requestIdToReject = Integer.parseInt(tokens[2]);

                            // Find the request in pending requests
                            Iterator<Request> iteratorReject = pendingRequests.iterator();
                            while (iteratorReject.hasNext()) {
                                Request req = iteratorReject.next();
                                if (req.getBorrower().equals(borrowerToReject) && req.getId() == requestIdToReject) {
                                    req.setStatus("rejected");
                                    iteratorReject.remove(); // Remove the request from the pendingRequests list
                                    rejectedRequests.add(req); // Add the request to the rejectedRequests list
                                    out.println("REQUEST_REJECTED");

                                    // Notify borrower
                                    break;
                                }
                            }
                            break;
                        case "REQUEST_HISTORY":
                            sendRequestHistory(loggedInUsername);
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

        private void registerUser(String username, String password) {
            try {
                // Check if the username already exists in the database
                String checkQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
                PreparedStatement checkStatement = connection.prepareStatement(checkQuery);
                checkStatement.setString(1, username);
                ResultSet resultSet = checkStatement.executeQuery();
                resultSet.next();
                int count = resultSet.getInt(1);
                if (count > 0) {
                    out.println("ERROR 409: Username already exists. Please choose another one.");
                    return;
                }

                // If the username is unique, proceed with registration
                String insertQuery = "INSERT INTO users (username, password) VALUES (?, ?)";
                PreparedStatement statement = connection.prepareStatement(insertQuery);
                statement.setString(1, username);
                statement.setString(2, password);
                int rowsAffected = statement.executeUpdate();

                if (rowsAffected > 0) {
                    out.println("Registration successful.");
                } else {
                    out.println("Error occurred during registration.");
                }
            } catch (SQLException e) {
                System.err.println("SQL Error: " + e.getMessage());
                e.printStackTrace();
                out.println("Error occurred during registration.");
            }
        }

        private boolean checkCredentials(String username, String password) {
            try {
                String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setString(1, username);
                statement.setString(2, password);
                ResultSet resultSet = statement.executeQuery();

                // If there is at least one row, the credentials are valid
                return resultSet.next();
            } catch (SQLException e) {
                e.printStackTrace();
                return false; // Return false in case of any SQL error
            }
        }
        private boolean addBookToDatabase(String title, String author, String genre, double price, int quantity, String lenderUsername) {
            try {
                String sql = "INSERT INTO books (title, author, genre, price, quantity, lender_username) VALUES (?, ?, ?, ?, ?, ?)";
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setString(1, title);
                statement.setString(2, author);
                statement.setString(3, genre);
                statement.setDouble(4, price);
                statement.setInt(5, quantity);
                statement.setString(6, lenderUsername);

                int rowsAffected = statement.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false; // Return false in case of any SQL error
            }
        }
        private boolean removeBookFromDatabase(String title, String author) {
            try {
                String sql = "DELETE FROM books WHERE title = ? AND author = ?";
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setString(1, title);
                statement.setString(2, author);

                int rowsAffected = statement.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false; // Return false in case of any SQL error
            }
        }

        private void sendBookListFromDatabase() {
            try {
                String sql = "SELECT * FROM books";
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql);

                StringBuilder response = new StringBuilder("LIST_BOOKS\n"); // Start of response
                while (resultSet.next()) {
                    String title = resultSet.getString("title");
                    String author = resultSet.getString("author");
                    String genre = resultSet.getString("genre");
                    double price = resultSet.getDouble("price");
                    int quantity = resultSet.getInt("quantity");
                    String lenderUsername = resultSet.getString("lender_username");

                    response.append("Title: ").append(title).append(", Author: ").append(author)
                            .append(", Genre: ").append(genre).append(", Price: $").append(price)
                            .append(", Quantity: ").append(quantity).append(", Lender: ").append(lenderUsername)
                            .append("\n");
                }

                out.println(response.toString().trim()); // Send the response
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("Error fetching book list from the database.");
            }
        }

        private void searchBooksByTitle(String titleToSearch) {
            try {
                String sql = "SELECT * FROM books WHERE title LIKE ?";
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setString(1, "%" + titleToSearch + "%");
                ResultSet resultSet = statement.executeQuery();
                sendMatchingBooks(resultSet);
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("Error occurred during book search by title.");
            }
        }

        private void searchBooksByAuthor(String authorToSearch) {
            try {
                String sql = "SELECT * FROM books WHERE author LIKE ?";
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setString(1, "%" + authorToSearch + "%");
                ResultSet resultSet = statement.executeQuery();
                sendMatchingBooks(resultSet);
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("Error occurred during book search by author.");
            }
        }

        private void searchBooksByGenre(String genreToSearch) {
            try {
                String sql = "SELECT * FROM books WHERE genre LIKE ?";
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setString(1, "%" + genreToSearch + "%");
                ResultSet resultSet = statement.executeQuery();
                sendMatchingBooks(resultSet);
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("Error occurred during book search by genre.");
            }
        }

        private void sendMatchingBooks(ResultSet resultSet) {
            try {
                StringBuilder response = new StringBuilder("MATCHING_BOOKS\n");
                while (resultSet.next()) {
                    String title = resultSet.getString("title");
                    String author = resultSet.getString("author");
                    String genre = resultSet.getString("genre");
                    double price = resultSet.getDouble("price");
                    int quantity = resultSet.getInt("quantity");
                    response.append("Title: ").append(title).append(", Author: ").append(author)
                            .append(", Genre: ").append(genre).append(", Price: $").append(price)
                            .append(", Quantity: ").append(quantity).append("\n");
                }
                out.println(response.toString().trim());
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("Error occurred while processing search results.");
            }
        }



        private void sendRequestHistory(String username) {
            StringBuilder response = new StringBuilder("REQUEST_HISTORY\n");
            // Iterate over pending requests
            for (Request req : pendingRequests) {
                if (req.getBorrower().equals(username) || req.getLender().equals(username)) {
                    response.append(req.getId()).append(" ").append(req.getStatus()).append("\n");
                }
            }
            // Iterate over accepted requests
            for (Request req : acceptedRequests) {
                if (req.getBorrower().equals(username) || req.getLender().equals(username)) {
                    response.append(req.getId()).append(" ").append(req.getStatus()).append("\n");
                }
            }
            // Iterate over rejected requests
            for (Request req : rejectedRequests) {
                if (req.getBorrower().equals(username) || req.getLender().equals(username)) {
                    response.append(req.getId()).append(" ").append(req.getStatus()).append("\n");
                }
            }
            out.println(response.toString().trim());
        }
        private void viewBookDetails(String[] tokens) {
            try {
                // Extract the book ID from tokens
                int bookId = Integer.parseInt(tokens[1]);

                // Query the database to get book details
                String query = "SELECT * FROM books WHERE book_id = ?";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setInt(1, bookId);
                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    // Book found, send detailed information to the client
                    String title = resultSet.getString("title");
                    String author = resultSet.getString("author");
                    String genre = resultSet.getString("genre");
                    double price = resultSet.getDouble("price");
                    int quantity = resultSet.getInt("quantity");

                    out.println("BOOK_DETAILS Title: " + title + ", Author: " + author +
                            ", Genre: " + genre + ", Price: $" + price + ", Quantity: " + quantity);
                } else {
                    // Book not found
                    out.println("ERROR 404: Book not found.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("ERROR: Failed to fetch book details from the database.");
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // Handle invalid input format
                out.println("ERROR: Invalid command format for viewing book details.");
            }
        }

    }
}

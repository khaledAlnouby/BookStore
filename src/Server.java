import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;


public class Server {
    static Map<String, PrintWriter> clientWriters = new HashMap<>();
    private static final String DB_URL = "jdbc:mysql://localhost:3306/bookstore";
    private static final String DB_USER = "root";

    private static final int PORT = 8000;



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

    public static class ClientHandler extends Thread {
        private String loggedInUsername;
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private Connection connection;


        public ClientHandler(Socket socket ,Connection connection) {
            this.clientSocket = socket;
            this.connection = connection;
        }

        @Override
        public void run() {
            String userRole = null;
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // Read client requests
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
                            String role;
                            if (tokens.length >= 5) {
                                // Role provided by the user
                                role = tokens[4];
                            } else {
                                // Default role if not provided by the user
                                role = "user";
                            }
                            registerUser(newUsername, newPassword, role);
                            break;
                        case "LOGIN":
                            String enteredUsername = tokens[1];
                            String enteredPassword = tokens[2];
                            loggedInUsername = enteredUsername; // Store the username of the logged-in user

                            // Check credentials in the database
                            userRole = checkCredentialsAndGetRole(enteredUsername, enteredPassword);

                            if (userRole != null) {
                                // Login successful
                                out.println("Login successfully .");
                                clientWriters.put(enteredUsername, out);
                            } else {
                                // Invalid credentials
                                out.println("ERROR 401: Invalid username or password .");
                            }
                            break;
                        case "LIST_BOOKS":
                            sendBookListFromDatabase();
                            break;
                        case "SEARCH_GENRE":
                            // searching by genre
                            searchBooksByGenre(tokens[1]);
                            break;
                        case "VIEW_DETAILS":
                            // view detailed info by book id
                            viewBookDetails(tokens);
                            break;
                        case "SEARCH_TITLE":
                            // view detailed info by book title
                            searchByTitle(tokens);
                            break;

                        case "ADD_BOOK":
                            String title = tokens[1];
                            String author = tokens[2];
                            String genre = tokens[3];
                            double price = Double.parseDouble(tokens[4]);
                            int quantity = Integer.parseInt(tokens[5]);
                            // add the book in the database
                            if (addBookToDatabase(title, author, genre, price, quantity, loggedInUsername)) {
                                out.println("BOOK_ADDED");
                            } else {
                                out.println("Error adding book to the database.");
                            }
                            break;
                        case "REMOVE_BOOK":
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
                            String borrower = tokens[1];
                            String lender = tokens[2];
                            String requestedBookTitle = tokens[3];

                            try {
                                String query = "SELECT book_id FROM books WHERE title = ?";
                                PreparedStatement statement = connection.prepareStatement(query);
                                statement.setString(1, requestedBookTitle);
                                ResultSet resultSet = statement.executeQuery();

                                if (resultSet.next()) {
                                    // extract book ID
                                    int bookId = resultSet.getInt("book_id");

                                    // Save the request in the database
                                    String insertRequestQuery = "INSERT INTO requests (borrower_username, lender_username, book_id, book_title, status) VALUES (?, ?, ?, ?, ?)";
                                    PreparedStatement insertStatement = connection.prepareStatement(insertRequestQuery);
                                    insertStatement.setString(1, borrower);
                                    insertStatement.setString(2, lender);
                                    insertStatement.setInt(3, bookId);
                                    insertStatement.setString(4, requestedBookTitle);
                                    insertStatement.setString(5, "pending");
                                    int rowsAffected = insertStatement.executeUpdate();

                                    if (rowsAffected > 0) {
                                        out.println("REQUEST_SUBMITTED");
                                    } else {
                                        // Failed to add it to the database
                                        out.println("ERROR: Failed to submit the request.");
                                    }
                                } else {
                                    // Book not found
                                    out.println("ERROR 404: Requested book not found.");
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                                out.println("ERROR: Failed to submit the request.");
                            }
                            break;
                        case "ACCEPT_REQUEST":
                            String borrowerToAccept = tokens[1];
                            int requestId = Integer.parseInt(tokens[2]);

                            try {
                                // Update request status in the database
                                String updateStatusQuery = "UPDATE requests SET status = 'accepted' WHERE borrower_username = ? AND request_id = ?";
                                PreparedStatement updateStatement = connection.prepareStatement(updateStatusQuery);
                                updateStatement.setString(1, borrowerToAccept);
                                updateStatement.setInt(2, requestId);
                                int rowsAffected = updateStatement.executeUpdate();

                                if (rowsAffected > 0) {
                                    // Decrement the quantity of the book in the database
                                    String decrementQuantityQuery = "UPDATE books SET quantity = quantity - 1 WHERE book_id = (SELECT book_id FROM requests WHERE request_id = ?)";
                                    PreparedStatement decrementing = connection.prepareStatement(decrementQuantityQuery);
                                    decrementing.setInt(1, requestId);
                                    decrementing.executeUpdate(); // Decrease the quantity
                                    out.println("REQUEST_ACCEPTED");

                                    // Notify lender
                                    out.println("[Server]: Request accepted. You can now chat with " + borrowerToAccept + ".");

                                    // Notify borrower
                                    PrintWriter borrowerWriter = clientWriters.get(borrowerToAccept);
                                    if (borrowerWriter != null) {
                                        borrowerWriter.println("[Server]: Your request has been accepted by " + loggedInUsername + ". You can now start chatting.");
                                    } else {
                                        out.println("ERROR: Failed to notify borrower.");
                                    }
                                    startChatSession(borrowerToAccept, loggedInUsername);
                                } else {
                                    // Failed to update request status
                                    out.println("ERROR: Failed to accept the request.");
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                                out.println("ERROR: Failed to accept the request.");
                            }
                            break;

                        case "REJECT_REQUEST":
                            String borrowerToReject = tokens[1];
                            int requestIdToReject = Integer.parseInt(tokens[2]);

                            try {
                                // Update the request status in the database
                                String updateStatusQuery = "UPDATE requests SET status = 'rejected' WHERE borrower_username = ? AND request_id = ?";
                                PreparedStatement updateStatement = connection.prepareStatement(updateStatusQuery);
                                updateStatement.setString(1, borrowerToReject);
                                updateStatement.setInt(2, requestIdToReject);
                                int rowsAffected = updateStatement.executeUpdate();

                                if (rowsAffected > 0) {
                                    // Request status updated successfully
                                    out.println("REQUEST_REJECTED");

                                } else {
                                    // Failed to update request status
                                    out.println("ERROR: Failed to reject the request.");
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                                out.println("ERROR: Failed to reject the request.");
                            }
                            break;
                        case "REQUEST_HISTORY":
                            sendRequestHistory(loggedInUsername);
                           break;
                        case "GET_LIBRARY_STATISTICS":
                            // Check if the user is an admin
                            if ("admin".equals(userRole)) {
                                sendLibraryStatistics();
                            } else {
                                out.println("ERROR: Only admins have access to see library statistics.");
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

        private void sendLibraryStatistics() {
            try {
                // GET library statistics
                String query = "SELECT COUNT(*) AS borrowed_books_count FROM requests WHERE status = 'accepted'";
                PreparedStatement statement = connection.prepareStatement(query);
                ResultSet resultSet = statement.executeQuery();

                int borrowedBooksCount = 0;
                int availableBooksCount = 0;
                int acceptedRequestsCount = 0;
                int rejectedRequestsCount = 0;
                int pendingRequestsCount = 0;

                if (resultSet.next()) {
                    borrowedBooksCount = resultSet.getInt("borrowed_books_count");
                }

                // count available books
                query = "SELECT COUNT(*) AS available_books_count FROM books WHERE quantity > 0";
                statement = connection.prepareStatement(query);
                resultSet = statement.executeQuery();

                // GET available books count
                if (resultSet.next()) {
                    availableBooksCount = resultSet.getInt("available_books_count");
                }

                // count accepted requests
                query = "SELECT COUNT(*) AS accepted_requests_count FROM requests WHERE status = 'accepted'";
                statement = connection.prepareStatement(query);
                resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    acceptedRequestsCount = resultSet.getInt("accepted_requests_count");
                }

                //  count rejected requests
                query = "SELECT COUNT(*) AS rejected_requests_count FROM requests WHERE status = 'rejected'";
                statement = connection.prepareStatement(query);
                resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    rejectedRequestsCount = resultSet.getInt("rejected_requests_count");
                }

                //  count pending requests
                query = "SELECT COUNT(*) AS pending_requests_count FROM requests WHERE status = 'pending'";
                statement = connection.prepareStatement(query);
                resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    pendingRequestsCount = resultSet.getInt("pending_requests_count");
                }

                String response = String.format("LIBRARY_STATISTICS Borrowed Books: %d, Available Books: %d, Accepted Requests: %d, Rejected Requests: %d, Pending Requests: %d",
                        borrowedBooksCount, availableBooksCount, acceptedRequestsCount, rejectedRequestsCount, pendingRequestsCount);

                // Send the response to the admin
                out.println(response);
            } catch (SQLException e) {
                e.printStackTrace();
                out.println("ERROR: Failed to retrieve library statistics.");
            }
        }

        private void startChatSession(String borrower, String lender) {
            // Add both borrowers and lenders to the map of writers
            Chat chat = new Chat(borrower, lender, out, in);
//            clientWriters.put(borrower, out);
//            clientWriters.put(lender, out);
            chat.startChat();
        }

        private void registerUser(String username, String password, String role) {
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

                String insertQuery;
                if (role == null || role.isEmpty()) {
                    // If role is not assigned, set it to default value as user
                    role = "user";
                }
                insertQuery = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
                PreparedStatement statement = connection.prepareStatement(insertQuery);
                statement.setString(1, username);
                statement.setString(2, password);
                statement.setString(3, role); // Set the role
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


        private String checkCredentialsAndGetRole(String username, String password) {
            try {
                String sql = "SELECT role FROM users WHERE username = ? AND password = ?";
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setString(1, username);
                statement.setString(2, password);
                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    // If there is at least one row, return the user's role
                    return resultSet.getString("role");
                } else {
                    return null; // Return null if no matching user
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
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
                return false;
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
                return false;
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
                out.println(" fetching book list from the database.");
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
                out.println(" occurred during book search by genre.");
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
                out.println(" occurred while processing search results.");
            }
        }

        private void sendRequestHistory(String username) {
            try {
                StringBuilder response = new StringBuilder("REQUEST_HISTORY\n");

                // Query the database to retrieve the request history for the user
                String query = "SELECT * FROM requests WHERE borrower_username = ? OR lender_username = ?";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setString(1, username);
                statement.setString(2, username);
                ResultSet resultSet = statement.executeQuery();

                // Process the ResultSet and construct the response
                while (resultSet.next()) {
                    int requestId = resultSet.getInt("request_id");
                    String borrower = resultSet.getString("borrower_username");
                    String lender = resultSet.getString("lender_username");
                    String bookTitle = resultSet.getString("book_title");
                    String status = resultSet.getString("status");
                    response.append("Request ID: ").append(requestId)
                            .append(", Borrower: ").append(borrower)
                            .append(", Lender: ").append(lender)
                            .append(", Book Title: ").append(bookTitle)
                            .append(", Status: ").append(status)
                            .append("\n");
                }

                out.println(response.toString().trim());
            } catch (SQLException e) {
                e.printStackTrace();
                out.println(" Failed to retrieve request history from the database.");
            }
        }

        private void viewBookDetails(String[] tokens) {
            try {
                int bookId = Integer.parseInt(tokens[1]);

                // Query the database to get book details
                String query = "SELECT * FROM books WHERE book_id = ?";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setInt(1, bookId);
                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    String title = resultSet.getString("title");
                    String author = resultSet.getString("author");
                    String genre = resultSet.getString("genre");
                    double price = resultSet.getDouble("price");
                    int quantity = resultSet.getInt("quantity");

                    out.println("BOOK_DETAILS Title: " + title + ", Author: " + author +
                            ", Genre: " + genre + ", Price: $" + price + ", Quantity: " + quantity);
                } else {
                    out.println("ERROR 404: Book not found.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                out.println(" Failed to fetch book details from the database.");
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // Handle invalid input format
                out.println(" Invalid command format for viewing book details.");
            }
        }
        private void searchByTitle(String[] tokens) {
            try {
                // Extract the book title from tokens
                String titleToSearch = tokens[1];

                // Query the database to get book details
                String query = "SELECT * FROM books WHERE title = ?";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setString(1, titleToSearch);
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
                    out.println(" 404: Book not found.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                out.println(" Failed to fetch book details from the database.");
            } catch (ArrayIndexOutOfBoundsException e) {
                // Handle invalid input format
                out.println("Invalid command format for viewing book details.");
            }
        }
    }
}
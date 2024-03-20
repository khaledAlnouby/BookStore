import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class Request {
    private int id; // Unique identifier for the request
    private String borrower;
    private String lender;
    private Book requestedBook;
    private String status;


    private Chat chat;

    public Request(int id, String borrower, String lender, Book requestedBook, String status, PrintWriter out, BufferedReader in) {
        this.id = id;
        this.borrower = borrower;
        this.lender = lender;
        this.requestedBook = requestedBook;
        this.status = status;
        this.chat= new Chat(borrower,lender,out,in);
    }
    public void setChat(Chat message) {
        this.chat= message;
    }

    public Chat getChat() {
        return this.chat ;
    }
    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBorrower() {
        return borrower;
    }

    public void setBorrower(String borrower) {
        this.borrower = borrower;
    }

    public String getLender() {
        return lender;
    }

    public void setLender(String lender) {
        this.lender = lender;
    }

    public Book getRequestedBook() {
        return requestedBook;
    }

    public void setRequestedBook(Book requestedBook) {
        this.requestedBook = requestedBook;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
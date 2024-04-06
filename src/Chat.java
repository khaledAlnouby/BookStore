import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;


public class Chat {
    private String borrower;
    private String lender;
    private PrintWriter out;
    private BufferedReader in;

    public Chat(String borrower, String lender, PrintWriter out, BufferedReader in) {
        this.borrower = borrower;
        this.lender = lender;
        this.out = out;
        this.in = in;
    }

    public void startChat() {
        try {
            out.println("[Server]: You are now chatting with " + borrower);
            out.println("[Server]: Type 'exit' to end the chat.");

            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("exit")) {
                    break;
                }
                sendMessageToRecipient(lender, message);
                receiveMessage(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void sendMessageToRecipient(String recipient, String message) {
        PrintWriter recipientWriter = Server.clientWriters.get(recipient);
        if (recipientWriter != null) {
            recipientWriter.println("[" + lender + "]: " + message);
        } else {
            out.println("ERROR: Failed to send message.");
        }
    }

    public void receiveMessage(String message) {
        System.out.println("[" + borrower + "]: " + message);
    }

    private void close() {
        try {
            out.close();
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
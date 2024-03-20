import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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
            this.messages = new ArrayList<>();
        }

    public void startChat() {
        try {
            while (true) {
                // Receive a chat message from the client (lender)
                String message = in.readLine();
                if (message == null || message.equals("END_CHAT")) {
                    break;
                }
                // Send the message to the borrower
                out.println(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            // Close resources (if necessary)
            close();
        }
    }
    private void close() {
        try {
            out.close();
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Add methods to send and receive messages


    public List<String> getMessages() {
        return messages;
    }


    private List<String> messages;
}

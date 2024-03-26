public class Book {
    private String lenderUsername;
    int id;
    private String title;
    private String author;
    private String genre;
    private double price;
    private int quantity;
    public Book(String title, String author, String genre, double price, int quantity) {
        // Input validation
        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException("Title cannot be null or empty");
        }
        if (author == null || author.isEmpty()) {
            throw new IllegalArgumentException("Author cannot be null or empty");
        }
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (quantity < 0) {
            throw  new IllegalArgumentException("Quantity cannot be negative");
        }
        this.lenderUsername= lenderUsername;
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.price = price;
        this.quantity = quantity;
    }
    @Override
    public String toString() {
        return "Title: " + title +
                ", Author: " + author +
                ", Genre: " + genre +
                ", Price: $" + price +
                ", Quantity: " + quantity;
    }
    // Getters
    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getGenre() {
        return genre;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    // Setters
    public void setTitle(String title) {
        if (title == null || title.isEmpty()) {
        }
        this.title = title;
    }

    public void setAuthor(String author) {
        if (author == null || author.isEmpty()) {
            throw new IllegalArgumentException("Author cannot be null or empty");
        }
        this.author = author;
    }
    public void setLenderUsername(String lenderUsername) {
        if (lenderUsername == null || lenderUsername.isEmpty()) {
            throw new IllegalArgumentException("lenderUsername cannot be null or empty");
        }
        this.lenderUsername = lenderUsername;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public void setPrice(double price) {
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        this.price = price;
    }

    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw  new IllegalArgumentException("Quantity cannot be negative");
        }
        this.quantity = quantity;
    }

    public void setBookId(int bookId) {
        this.id= bookId;
    }
}

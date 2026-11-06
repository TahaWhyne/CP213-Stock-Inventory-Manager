package inventory;

public class Grocery extends Product {
    private String expirationDate;
    
    public Grocery(String productId, String productName, double price, 
                   int quantity, String expirationDate) {
        super(productId, productName, price, quantity, "Grocery");
        this.expirationDate = expirationDate;
    }
    
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }
    
    @Override
    public String getProductDetails() {
        return toString() + " | Expires: " + expirationDate;
    }
}
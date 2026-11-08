package inventory;

public class Clothing extends Product {
    private String size;
    
    public Clothing(String productId, String productName, double price, 
                    int quantity, String size) {
        super(productId, productName, price, quantity, "Clothing");
        this.size = size;
    }
    
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    
    @Override
    public String getProductDetails() {
        return toString() + " | Size: " + size;
    }
}
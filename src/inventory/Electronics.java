package inventory;

public class Electronics extends Product {
    private int warrantyMonths;
    
    public Electronics(String productId, String productName, double price, 
                      int quantity, int warrantyMonths) {
        super(productId, productName, price, quantity, "Electronics");
        this.warrantyMonths = warrantyMonths;
    }
    
    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    
    @Override
    public String getProductDetails() {
        return toString() + " | Warranty: " + warrantyMonths + " months";
    }
}
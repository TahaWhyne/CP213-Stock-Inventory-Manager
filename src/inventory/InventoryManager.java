package inventory;

import java.util.ArrayList;

public class InventoryManager {
    private ArrayList<Product> inventory;
    private final int LOW_STOCK_THRESHOLD = 10;
    
    public InventoryManager() {
        this.inventory = new ArrayList<>();
        initializeSampleData();
    }
    
    private void initializeSampleData() {
        try {
            addProduct(new Electronics("E001", "Laptop", 999.99, 15, 24));
            addProduct(new Electronics("E002", "Smartphone", 699.99, 8, 12));
            addProduct(new Grocery("G001", "Milk", 3.99, 50, "2025-11-25"));
            addProduct(new Grocery("G002", "Bread", 2.49, 30, "2025-11-20"));
            addProduct(new Clothing("C001", "T-Shirt", 19.99, 25, "M"));
            addProduct(new Clothing("C002", "Jeans", 49.99, 12, "L"));
        } catch (InvalidProductException e) {
            System.err.println("Error initializing data: " + e.getMessage());
        }
    }
    
    public void addProduct(Product product) throws InvalidProductException {
        if (product == null) {
            throw new InvalidProductException("Product cannot be null");
        }
        if (product.getProductName() == null || product.getProductName().trim().isEmpty()) {
            throw new InvalidProductException("Product name cannot be empty");
        }
        if (product.getPrice() < 0) {
            throw new InvalidProductException("Price cannot be negative");
        }
        if (product.getQuantity() < 0) {
            throw new InvalidProductException("Quantity cannot be negative");
        }
        
        if (findProductById(product.getProductId()) != null) {
            throw new InvalidProductException("Product with ID " + product.getProductId() + " already exists");
        }
        
        inventory.add(product);
    }
    
    public boolean removeProduct(String productId) {
        Product product = findProductById(productId);
        if (product != null) {
            inventory.remove(product);
            return true;
        }
        return false;
    }
    
    public void updateQuantity(String productId, int newQuantity) throws InvalidProductException {
        Product product = findProductById(productId);
        if (product == null) {
            throw new InvalidProductException("Product not found: " + productId);
        }
        if (newQuantity < 0) {
            throw new InvalidProductException("Quantity cannot be negative");
        }
        product.setQuantity(newQuantity);
    }
    
    public void updatePrice(String productId, double newPrice) throws InvalidProductException {
        Product product = findProductById(productId);
        if (product == null) {
            throw new InvalidProductException("Product not found: " + productId);
        }
        if (newPrice < 0) {
            throw new InvalidProductException("Price cannot be negative");
        }
        product.setPrice(newPrice);
    }
    
    public Product findProductById(String productId) {
        for (Product product : inventory) {
            if (product.getProductId().equals(productId)) {
                return product;
            }
        }
        return null;
    }
    
    public ArrayList<Product> searchByName(String name) {
        ArrayList<Product> results = new ArrayList<>();
        for (Product product : inventory) {
            if (product.getProductName().toLowerCase().contains(name.toLowerCase())) {
                results.add(product);
            }
        }
        return results;
    }
    
    public ArrayList<Product> getProductsByCategory(String category) {
        ArrayList<Product> results = new ArrayList<>();
        for (Product product : inventory) {
            if (product.getCategory().equalsIgnoreCase(category)) {
                results.add(product);
            }
        }
        return results;
    }
    
    public ArrayList<Product> getAllProducts() {
        return new ArrayList<>(inventory);
    }
    
    public ArrayList<Product> getLowStockProducts() {
        ArrayList<Product> lowStock = new ArrayList<>();
        for (Product product : inventory) {
            if (product.isLowStock(LOW_STOCK_THRESHOLD)) {
                lowStock.add(product);
            }
        }
        return lowStock;
    }
    
    public double calculateTotalValue() {
        double total = 0;
        for (Product product : inventory) {
            total += product.getPrice() * product.getQuantity();
        }
        return total;
    }
    
    public int getInventorySize() {
        return inventory.size();
    }
}
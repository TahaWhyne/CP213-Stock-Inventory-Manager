package inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Inventory business layer. The manager owns validation and delegates
 * persistence to SqliteProductRepository; the GUI continues to use its
 * original manager-facing methods.
 */
public class InventoryManager {
    private static final int LOW_STOCK_THRESHOLD = 10;
    private final SqliteProductRepository repository;

    /** Opens the application's persistent local database and seeds it if empty. */
    public InventoryManager() {
        this(new SqliteProductRepository("inventory.db"));
        seedSampleDataIfEmpty();
    }

    public InventoryManager(SqliteProductRepository repository) {
        if (repository == null) throw new IllegalArgumentException("Repository cannot be null");
        this.repository = repository;
    }

    public void seedSampleDataIfEmpty() {
        if (repository.count() != 0) return;
        try {
            addProduct(new Electronics("E001", "Laptop", 999.99, 15, 24));
            addProduct(new Electronics("E002", "Smartphone", 699.99, 8, 12));
            addProduct(new Grocery("G001", "Milk", 3.99, 50, "2025-11-25"));
            addProduct(new Grocery("G002", "Bread", 2.49, 30, "2025-11-20"));
            addProduct(new Clothing("C001", "T-Shirt", 19.99, 25, "M"));
            addProduct(new Clothing("C002", "Jeans", 49.99, 12, "L"));
        } catch (InvalidProductException e) {
            throw new RepositoryException("Could not seed sample inventory", e);
        }
    }

    public void addProduct(Product product) throws InvalidProductException {
        validate(product);
        try {
            repository.add(product);
        } catch (DuplicateProductException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new InvalidProductException(e.getMessage());
        }
    }

    private void validate(Product product) throws InvalidProductException {
        if (product == null) throw new InvalidProductException("Product cannot be null");
        if (product.getProductId() == null || product.getProductId().trim().isEmpty())
            throw new InvalidProductException("Product ID cannot be empty");
        if (product.getProductName() == null || product.getProductName().trim().isEmpty())
            throw new InvalidProductException("Product name cannot be empty");
        if (!Double.isFinite(product.getPrice()) || product.getPrice() < 0)
            throw new InvalidProductException("Price cannot be negative");
        if (product.getQuantity() < 0)
            throw new InvalidProductException("Quantity cannot be negative");
        if (product instanceof Electronics e && e.getWarrantyMonths() < 0)
            throw new InvalidProductException("Warranty cannot be negative");
        if (product instanceof Grocery g && (g.getExpirationDate() == null || g.getExpirationDate().isBlank()))
            throw new InvalidProductException("Expiration date cannot be empty");
        if (product instanceof Clothing c && (c.getSize() == null || c.getSize().isBlank()))
            throw new InvalidProductException("Size cannot be empty");
    }

    public boolean removeProduct(String productId) { return repository.delete(productId); }

    public void updateQuantity(String productId, int newQuantity) throws InvalidProductException {
        if (newQuantity < 0) throw new InvalidProductException("Quantity cannot be negative");
        Product product = requireProduct(productId);
        product.setQuantity(newQuantity);
        repository.update(product);
    }

    public void updatePrice(String productId, double newPrice) throws InvalidProductException {
        if (!Double.isFinite(newPrice) || newPrice < 0) throw new InvalidProductException("Price cannot be negative");
        Product product = requireProduct(productId);
        product.setPrice(newPrice);
        repository.update(product);
    }

    public void updateProduct(String productId, double newPrice, int newQuantity) throws InvalidProductException {
        if (!Double.isFinite(newPrice) || newPrice < 0) throw new InvalidProductException("Price cannot be negative");
        if (newQuantity < 0) throw new InvalidProductException("Quantity cannot be negative");
        Product product = requireProduct(productId);
        product.setPrice(newPrice);
        product.setQuantity(newQuantity);
        repository.update(product);
    }

    private Product requireProduct(String productId) {
        return repository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
    }

    public Product findProductById(String productId) {
        return repository.findById(productId).orElse(null);
    }

    public ArrayList<Product> searchByName(String name) {
        return new ArrayList<>(repository.find(null, name, null));
    }

    public ArrayList<Product> getProductsByCategory(String category) {
        return new ArrayList<>(repository.find(category, null, null));
    }

    public List<Product> findProducts(String category, String search, boolean lowStock) {
        return repository.find(category, search, lowStock ? LOW_STOCK_THRESHOLD : null);
    }

    public ArrayList<Product> getAllProducts() {
        return new ArrayList<>(repository.find(null, null, null));
    }

    public ArrayList<Product> getLowStockProducts() {
        return new ArrayList<>(repository.find(null, null, LOW_STOCK_THRESHOLD));
    }

    public int adjustStock(String productId, int delta) throws InsufficientStockException {
        OptionalInt quantity = repository.adjustQuantity(productId, delta);
        if (quantity.isEmpty()) {
            if (repository.findById(productId).isEmpty())
                throw new ProductNotFoundException("Product not found: " + productId);
            throw new InsufficientStockException("Insufficient stock for product: " + productId);
        }
        return quantity.getAsInt();
    }

    public double calculateTotalValue() {
        double total = 0;
        for (Product product : getAllProducts()) total += product.getPrice() * product.getQuantity();
        return total;
    }

    public int getInventorySize() { return repository.count(); }
}

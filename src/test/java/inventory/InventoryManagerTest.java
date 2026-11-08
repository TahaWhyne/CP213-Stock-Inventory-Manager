package inventory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class InventoryManagerTest {
    @TempDir Path tempDir;

    @Test
    void managerOperationsPersistAcrossManagerInstances() throws Exception {
        String path = tempDir.resolve("manager.db").toString();
        InventoryManager manager = new InventoryManager(new SqliteProductRepository(path));
        manager.addProduct(new Clothing("C10", "Jacket", 80.00, 6, "L"));
        manager.updateProduct("C10", 75.50, 9);

        InventoryManager reopened = new InventoryManager(new SqliteProductRepository(path));
        Product stored = reopened.findProductById("C10");
        assertNotNull(stored);
        assertEquals(75.50, stored.getPrice(), 0.0);
        assertEquals(9, stored.getQuantity());
        assertEquals(1, reopened.getInventorySize());
    }

    @Test
    void managerRejectsInvalidProductsAndMissingUpdates() throws Exception {
        InventoryManager manager = new InventoryManager(new SqliteProductRepository(tempDir.resolve("validation.db").toString()));
        assertThrows(InvalidProductException.class,
                () -> manager.addProduct(new Electronics("E1", " ", 10, 1, 12)));
        assertThrows(InvalidProductException.class,
                () -> manager.addProduct(new Electronics("E2", "Laptop", -1, 1, 12)));
        assertThrows(ProductNotFoundException.class,
                () -> manager.updateProduct("missing", 10, 1));
    }

    @Test
    void managerFiltersLowStockAndCalculatesInventoryValue() throws Exception {
        InventoryManager manager = new InventoryManager(new SqliteProductRepository(tempDir.resolve("totals.db").toString()));
        manager.addProduct(new Grocery("G1", "Milk", 2.50, 5, "2027-01-01"));
        manager.addProduct(new Clothing("C1", "Coat", 100.00, 20, "M"));
        assertEquals(1, manager.getLowStockProducts().size());
        assertEquals(2012.50, manager.calculateTotalValue(), 0.001);
    }
}

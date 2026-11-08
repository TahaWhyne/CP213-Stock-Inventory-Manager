package inventory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Tests against a real SQLite file (a fresh temporary one for every test). */
class SqliteProductRepositoryTest {

    @TempDir
    Path tempDir;

    private String dbPath;
    private SqliteProductRepository repo;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test.db").toString();
        repo = new SqliteProductRepository(dbPath);
    }

    // ---- saving and loading ---------------------------------------------

    @Test
    void add_thenFind_keepsTheCorrectSubclassAndDetails() throws Exception {
        repo.add(new Electronics("E001", "Laptop", 999.99, 15, 24));
        repo.add(new Grocery("G001", "Milk", 3.99, 50, "2025-11-25"));
        repo.add(new Clothing("C001", "T-Shirt", 19.99, 25, "M"));

        Electronics e = assertInstanceOf(Electronics.class, repo.findById("E001").orElseThrow());
        assertEquals(24, e.getWarrantyMonths());
        assertEquals(999.99, e.getPrice(), 0.0);
        Grocery g = assertInstanceOf(Grocery.class, repo.findById("G001").orElseThrow());
        assertEquals("2025-11-25", g.getExpirationDate());
        Clothing c = assertInstanceOf(Clothing.class, repo.findById("C001").orElseThrow());
        assertEquals("M", c.getSize());
        assertEquals(3, repo.count());
    }

    @Test
    void findById_unknownId_isEmpty() {
        assertTrue(repo.findById("NOPE").isEmpty());
    }

    @Test
    void data_survivesClosingAndReopeningTheDatabase() throws Exception {
        repo.add(new Electronics("E001", "Laptop", 999.99, 15, 24));

        SqliteProductRepository reopened = new SqliteProductRepository(dbPath);

        assertEquals("Laptop", reopened.findById("E001").orElseThrow().getProductName());
        assertEquals(1, reopened.count());
    }

    @Test
    void prices_roundTripExactly_withoutFloatingPointNoise() throws Exception {
        // 0.29 * 100 is 28.999999999999996 in floating point
        repo.add(new Grocery("G1", "Candy", 0.29, 1, "2026-01-01"));
        repo.add(new Electronics("E1", "Laptop", 19.99, 1, 12));
        repo.add(new Electronics("E2", "Cable", 1234567.89, 1, 12));

        assertEquals(0.29, repo.findById("G1").orElseThrow().getPrice(), 0.0);
        assertEquals(19.99, repo.findById("E1").orElseThrow().getPrice(), 0.0);
        assertEquals(1234567.89, repo.findById("E2").orElseThrow().getPrice(), 0.0);
    }

    @Test
    void add_duplicateId_throwsDuplicateAndKeepsOriginal() throws Exception {
        repo.add(new Electronics("E001", "Laptop", 999.99, 15, 24));

        assertThrows(DuplicateProductException.class,
                () -> repo.add(new Electronics("E001", "Other", 1.0, 1, 1)));

        assertEquals("Laptop", repo.findById("E001").orElseThrow().getProductName());
        assertEquals(1, repo.count());
    }

    @Test
    void update_changesStoredValues_andReportsMissingProducts() throws Exception {
        repo.add(new Clothing("C001", "T-Shirt", 19.99, 25, "M"));

        Clothing changed = new Clothing("C001", "T-Shirt", 24.5, 3, "L");
        assertTrue(repo.update(changed));

        Clothing stored = (Clothing) repo.findById("C001").orElseThrow();
        assertEquals(24.5, stored.getPrice(), 0.0);
        assertEquals(3, stored.getQuantity());
        assertEquals("L", stored.getSize());

        assertFalse(repo.update(new Clothing("NOPE", "Ghost", 1.0, 1, "S")));
    }

    @Test
    void delete_removesRow_andReportsMissingProducts() throws Exception {
        repo.add(new Clothing("C001", "T-Shirt", 19.99, 25, "M"));

        assertTrue(repo.delete("C001"));
        assertTrue(repo.findById("C001").isEmpty());
        assertFalse(repo.delete("C001"));
    }

    // ---- searching ---------------------------------------------------------

    private void addSet() throws Exception {
        repo.add(new Electronics("E001", "Laptop", 999.99, 15, 24));
        repo.add(new Electronics("E002", "Smartphone", 699.99, 8, 12));
        repo.add(new Grocery("G001", "Milk", 3.99, 50, "2025-11-25"));
        repo.add(new Clothing("C002", "Jeans", 49.99, 12, "L"));
    }

    @Test
    void find_noFilters_returnsEverythingOrderedById() throws Exception {
        addSet();

        List<Product> all = repo.find(null, null, null);

        assertEquals(List.of("C002", "E001", "E002", "G001"),
                all.stream().map(Product::getProductId).toList());
    }

    @Test
    void find_byCategory_ignoresCase() throws Exception {
        addSet();

        assertEquals(2, repo.find("electronics", null, null).size());
        assertEquals(1, repo.find("GROCERY", null, null).size());
        assertTrue(repo.find("Toys", null, null).isEmpty());
    }

    @Test
    void find_byName_isPartialAndIgnoresCase() throws Exception {
        addSet();

        assertEquals(List.of("E002"),
                repo.find(null, "PHONE", null).stream().map(Product::getProductId).toList());
    }

    @Test
    void find_byMaxQuantity_isInclusive() throws Exception {
        addSet();

        assertEquals(List.of("E002"),
                repo.find(null, null, 8).stream().map(Product::getProductId).toList());
        assertEquals(List.of("C002", "E002"),
                repo.find(null, null, 12).stream().map(Product::getProductId).toList());
    }

    @Test
    void find_combinedFilters_mustAllMatch() throws Exception {
        addSet();

        assertEquals(List.of("E002"),
                repo.find("Electronics", "s", 10).stream().map(Product::getProductId).toList());
        assertTrue(repo.find("Grocery", null, 10).isEmpty());
    }

    @Test
    void find_treatsSqlWildcardsAndQuotesAsPlainText() throws Exception {
        repo.add(new Clothing("C1", "50% Off Hoodie", 30.0, 5, "M"));
        repo.add(new Clothing("C2", "Plain_Tee", 10.0, 5, "M"));
        repo.add(new Clothing("C3", "Plain Sweater", 10.0, 5, "M"));
        repo.add(new Clothing("C4", "Ben & Jerry's Tee", 10.0, 5, "M"));

        assertEquals(List.of("C1"), ids(repo.find(null, "50%", null)));
        assertEquals(List.of("C2"), ids(repo.find(null, "n_t", null)));     // _ is not "any character" here
        assertEquals(List.of("C4"), ids(repo.find(null, "Jerry's", null)));
    }

    @Test
    void find_sqlInjectionAttempt_isHarmlessSearchText() throws Exception {
        addSet();

        List<Product> results = repo.find(null, "'; DROP TABLE products; --", null);

        assertTrue(results.isEmpty());
        assertEquals(4, repo.count());   // table is still there with all rows
    }

    private static List<String> ids(List<Product> products) {
        return products.stream().map(Product::getProductId).toList();
    }

    // ---- database-level rules ---------------------------------------------

    private void rawSql(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    @Test
    void database_rejectsNegativeQuantity_evenWhenJavaValidationIsBypassed() {
        assertThrows(SQLException.class, () -> rawSql(
                "INSERT INTO products (id, name, price_cents, quantity, category, warranty_months) "
                        + "VALUES ('X1', 'Bad', 100, -1, 'Electronics', 12)"));
    }

    @Test
    void database_rejectsNegativePrice_blankName_andUnknownCategory() {
        assertThrows(SQLException.class, () -> rawSql(
                "INSERT INTO products (id, name, price_cents, quantity, category, warranty_months) "
                        + "VALUES ('X1', 'Bad', -1, 1, 'Electronics', 12)"));
        assertThrows(SQLException.class, () -> rawSql(
                "INSERT INTO products (id, name, price_cents, quantity, category, warranty_months) "
                        + "VALUES ('X2', '   ', 100, 1, 'Electronics', 12)"));
        assertThrows(SQLException.class, () -> rawSql(
                "INSERT INTO products (id, name, price_cents, quantity, category, size) "
                        + "VALUES ('X3', 'Toy', 100, 1, 'Toys', 'M')"));
    }

    @Test
    void database_rejectsMissingSubtypeColumn() {
        // An Electronics row must have warranty_months
        assertThrows(SQLException.class, () -> rawSql(
                "INSERT INTO products (id, name, price_cents, quantity, category) "
                        + "VALUES ('X1', 'Laptop', 100, 1, 'Electronics')"));
    }

    // ---- atomic stock changes --------------------------------------------

    @Test
    void adjustQuantity_returnsNewQuantity_andPersistsIt() throws Exception {
        repo.add(new Electronics("E001", "Laptop", 999.99, 10, 24));

        assertEquals(OptionalInt.of(13), repo.adjustQuantity("E001", 3));
        assertEquals(OptionalInt.of(0), repo.adjustQuantity("E001", -13));
        assertEquals(0, repo.findById("E001").orElseThrow().getQuantity());
    }

    @Test
    void adjustQuantity_belowZero_changesNothing() throws Exception {
        repo.add(new Electronics("E001", "Laptop", 999.99, 10, 24));

        assertTrue(repo.adjustQuantity("E001", -11).isEmpty());
        assertEquals(10, repo.findById("E001").orElseThrow().getQuantity());
    }

    @Test
    void adjustQuantity_unknownProduct_isEmpty() {
        assertTrue(repo.adjustQuantity("NOPE", 1).isEmpty());
    }

    @Test
    void adjustQuantity_underConcurrentBuyers_neverOversells() throws Exception {
        repo.add(new Electronics("E001", "Laptop", 999.99, 10, 24));

        int buyers = 25;
        List<Callable<Boolean>> tasks = new ArrayList<>();
        CountDownLatch startTogether = new CountDownLatch(1);
        for (int i = 0; i < buyers; i++) {
            tasks.add(() -> {
                startTogether.await();
                return repo.adjustQuantity("E001", -1).isPresent();
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(pool.submit(task));
            }
            startTogether.countDown();

            int succeeded = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            assertEquals(10, succeeded, "exactly the 10 units in stock can be sold");
            assertEquals(0, repo.findById("E001").orElseThrow().getQuantity());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void add_sameIdFromManyThreads_onlyOneSucceeds() throws Exception {
        int threads = 12;
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int n = i;
                futures.add(pool.submit(() -> {
                    startTogether.await();
                    try {
                        repo.add(new Electronics("E001", "Laptop " + n, 999.99, 1, 24));
                        return true;
                    } catch (DuplicateProductException e) {
                        return false;
                    }
                }));
            }
            startTogether.countDown();

            int succeeded = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            assertEquals(1, succeeded);
            assertEquals(1, repo.count());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- wiring with the manager -------------------------------------------

    @Test
    void manager_onTopOfSqlite_enforcesRulesAndPersists() throws Exception {
        InventoryManager manager = new InventoryManager(repo);
        manager.seedSampleDataIfEmpty();

        assertEquals(6, manager.getInventorySize());
        assertThrows(DuplicateProductException.class,
                () -> manager.addProduct(new Electronics("E001", "Again", 1.0, 1, 1)));
        assertThrows(InsufficientStockException.class, () -> manager.adjustStock("E002", -9));

        // a second manager on the same file sees the same data, and seeding does not repeat
        InventoryManager second = new InventoryManager(new SqliteProductRepository(dbPath));
        second.seedSampleDataIfEmpty();
        assertEquals(6, second.getInventorySize());
        assertEquals(21973.60, second.calculateTotalValue(), 0.01);
    }
}
package inventory.api;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import inventory.InventoryManager;
import inventory.SqliteProductRepository;

class InventoryApiServerTest {
    @TempDir Path tempDir;
    private InventoryApiServer server;
    private HttpClient client;
    private Gson gson;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        InventoryManager manager = new InventoryManager(
                new SqliteProductRepository(tempDir.resolve("api-test.db").toString()));
        server = new InventoryApiServer(manager, 0);
        server.start();
        base = "http://localhost:" + server.getPort();
        client = HttpClient.newHttpClient();
        gson = new Gson();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String electronicsJson(String id, String name, double price, int quantity) {
        ProductDto dto = new ProductDto();
        dto.productId = id; dto.productName = name; dto.price = price;
        dto.quantity = quantity; dto.category = "Electronics"; dto.warrantyMonths = 24;
        return gson.toJson(dto);
    }

    @Test
    void healthEndpoint_reportsStatusAndCount() throws Exception {
        HttpResponse<String> response = request("GET", "/health", null);
        assertEquals(200, response.statusCode());
        assertEquals("ok", gson.fromJson(response.body(), com.google.gson.JsonObject.class).get("status").getAsString());
        assertEquals(0, gson.fromJson(response.body(), com.google.gson.JsonObject.class).get("products").getAsInt());
    }

    @Test
    void createThenGet_returnsCreatedProductAndLocationHeader() throws Exception {
        HttpResponse<String> created = request("POST", "/products", electronicsJson("E100", "Keyboard", 49.99, 7));
        assertEquals(201, created.statusCode());
        assertEquals("/products/E100", created.headers().firstValue("Location").orElseThrow());

        HttpResponse<String> fetched = request("GET", "/products/E100", null);
        assertEquals(200, fetched.statusCode());
        ProductDto dto = gson.fromJson(fetched.body(), ProductDto.class);
        assertEquals("Keyboard", dto.productName);
        assertEquals(24, dto.warrantyMonths.intValue());
    }

    @Test
    void duplicateCreate_returnsConflict() throws Exception {
        request("POST", "/products", electronicsJson("E100", "Keyboard", 49.99, 7));
        HttpResponse<String> duplicate = request("POST", "/products", electronicsJson("E100", "Other", 5, 1));
        assertEquals(409, duplicate.statusCode());
    }

    @Test
    void update_changesPriceAndQuantity() throws Exception {
        request("POST", "/products", electronicsJson("E100", "Keyboard", 49.99, 7));
        HttpResponse<String> updated = request("PUT", "/products/E100", "{\"price\":59.5,\"quantity\":12}");
        assertEquals(200, updated.statusCode());
        ProductDto dto = gson.fromJson(updated.body(), ProductDto.class);
        assertEquals(59.5, dto.price, 0.0);
        assertEquals(12, dto.quantity.intValue());
    }

    @Test
    void adjustStock_isAtomicAndRejectsOverselling() throws Exception {
        request("POST", "/products", electronicsJson("E100", "Keyboard", 49.99, 7));
        HttpResponse<String> adjusted = request("POST", "/products/E100/adjust", "{\"delta\":-2}");
        assertEquals(200, adjusted.statusCode());
        assertEquals(5, gson.fromJson(adjusted.body(), com.google.gson.JsonObject.class).get("quantity").getAsInt());

        HttpResponse<String> rejected = request("POST", "/products/E100/adjust", "{\"delta\":-8}");
        assertEquals(409, rejected.statusCode());
        ProductDto stillFive = gson.fromJson(request("GET", "/products/E100", null).body(), ProductDto.class);
        assertEquals(5, stillFive.quantity.intValue());
    }

    @Test
    void listEndpoint_appliesCategorySearchAndLowStockFilters() throws Exception {
        request("POST", "/products", electronicsJson("E100", "Wireless Keyboard", 49.99, 4));
        request("POST", "/products", electronicsJson("E101", "Monitor", 199.99, 30));
        HttpResponse<String> response = request("GET", "/products?category=electronics&search=wireless&lowStock=true", null);
        assertEquals(200, response.statusCode());
        ProductDto[] products = gson.fromJson(response.body(), ProductDto[].class);
        assertEquals(1, products.length);
        assertEquals("E100", products[0].productId);
    }

    @Test
    void missingProduct_returnsNotFound() throws Exception {
        assertEquals(404, request("GET", "/products/missing", null).statusCode());
    }

    @Test
    void malformedBody_returnsBadRequest() throws Exception {
        assertEquals(400, request("POST", "/products", "{not-json").statusCode());
    }

    @Test
    void unsupportedMethod_returnsMethodNotAllowedWithAllowHeader() throws Exception {
        HttpResponse<String> response = request("PATCH", "/products", "{}");
        assertEquals(405, response.statusCode());
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("GET"));
    }

    @Test
    void delete_removesProductAndReturnsNoContent() throws Exception {
        request("POST", "/products", electronicsJson("E100", "Keyboard", 49.99, 7));
        assertEquals(204, request("DELETE", "/products/E100", null).statusCode());
        assertEquals(404, request("GET", "/products/E100", null).statusCode());
    }
}

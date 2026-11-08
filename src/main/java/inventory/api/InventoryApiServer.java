package inventory.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import inventory.DuplicateProductException;
import inventory.InsufficientStockException;
import inventory.InvalidProductException;
import inventory.InventoryManager;
import inventory.Product;
import inventory.ProductNotFoundException;
import inventory.RepositoryException;
import inventory.SqliteProductRepository;

/**
 * A small REST API over InventoryManager, built on the JDK's own HttpServer
 * (no web framework needed).
 *
 *   GET    /health
 *   GET    /products?category=&search=&lowStock=true
 *   POST   /products                       create (201, 409 if the ID exists)
 *   GET    /products/{id}
 *   PUT    /products/{id}                  body: {"price":..,"quantity":..}
 *   DELETE /products/{id}                  204
 *   POST   /products/{id}/adjust           body: {"delta":-3}  (409 if stock too low)
 *
 * Errors are JSON: {"error":"message"}.
 * The server only listens on localhost because it has no login.
 */
public class InventoryApiServer {
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final InventoryManager manager;
    private final HttpServer server;
    private final ExecutorService executor;
    private final Gson gson = new Gson();

    /** @param port port to listen on, or 0 to let the OS pick a free one */
    public InventoryApiServer(InventoryManager manager, int port) throws IOException {
        this.manager = manager;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        this.executor = Executors.newFixedThreadPool(8);
        server.createContext("/health", exchange -> safely(exchange, this::handleHealth));
        server.createContext("/products", exchange -> safely(exchange, this::handleProducts));
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    // ---- request handling ----------------------------------------------

    private interface Handler {
        void handle(HttpExchange exchange) throws Exception;
    }

    /** Runs a handler and turns every kind of failure into a JSON error response. */
    private void safely(HttpExchange exchange, Handler handler) throws IOException {
        try {
            handler.handle(exchange);
        } catch (ApiException e) {
            if (e.allowedMethods != null) {
                exchange.getResponseHeaders().set("Allow", e.allowedMethods);
            }
            sendJson(exchange, e.status, error(e.getMessage()));
        } catch (ProductNotFoundException e) {
            sendJson(exchange, 404, error(e.getMessage()));
        } catch (DuplicateProductException | InsufficientStockException e) {
            sendJson(exchange, 409, error(e.getMessage()));
        } catch (InvalidProductException e) {
            sendJson(exchange, 400, error(e.getMessage()));
        } catch (RepositoryException e) {
            e.printStackTrace();
            sendJson(exchange, 500, error("Database error"));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, error("Internal server error"));
        } finally {
            exchange.close();
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        body.put("products", manager.getInventorySize());
        sendJson(exchange, 200, body);
    }

    private void handleProducts(HttpExchange exchange) throws Exception {
        List<String> parts = pathParts(exchange);   // ["products"], ["products","E001"], ["products","E001","adjust"]
        String method = exchange.getRequestMethod();

        if (parts.size() == 1) {
            if (method.equals("GET")) {
                listProducts(exchange);
            } else if (method.equals("POST")) {
                createProduct(exchange);
            } else {
                throw ApiException.methodNotAllowed("GET, POST");
            }
        } else if (parts.size() == 2) {
            String id = parts.get(1);
            switch (method) {
                case "GET":
                    getProduct(exchange, id);
                    break;
                case "PUT":
                    updateProduct(exchange, id);
                    break;
                case "DELETE":
                    deleteProduct(exchange, id);
                    break;
                default:
                    throw ApiException.methodNotAllowed("GET, PUT, DELETE");
            }
        } else if (parts.size() == 3 && parts.get(2).equals("adjust")) {
            requireMethod(exchange, "POST");
            adjustStock(exchange, parts.get(1));
        } else {
            throw new ApiException(404, "Not found");
        }
    }

    private void listProducts(HttpExchange exchange) throws IOException {
        Map<String, String> query = queryParams(exchange);
        String category = query.get("category");
        if (category != null && !ProductMapper.isKnownCategory(category)) {
            throw new ApiException(400, "category must be Electronics, Grocery or Clothing");
        }
        boolean lowStock = "true".equalsIgnoreCase(query.get("lowStock"));

        List<ProductDto> body = new ArrayList<>();
        for (Product p : manager.findProducts(category, query.get("search"), lowStock)) {
            body.add(ProductMapper.toDto(p));
        }
        sendJson(exchange, 200, body);
    }

    private void createProduct(HttpExchange exchange) throws Exception {
        Product product = ProductMapper.fromDto(readJson(exchange, ProductDto.class));
        manager.addProduct(product);
        exchange.getResponseHeaders().set("Location", "/products/" + product.getProductId());
        sendJson(exchange, 201, ProductMapper.toDto(manager.findProductById(product.getProductId())));
    }

    private void getProduct(HttpExchange exchange, String id) throws IOException {
        Product product = manager.findProductById(id);
        if (product == null) {
            throw new ApiException(404, "Product not found: " + id);
        }
        sendJson(exchange, 200, ProductMapper.toDto(product));
    }

    /** Request body for PUT /products/{id}. */
    private static class UpdateRequest {
        Double price;
        Integer quantity;
    }

    private void updateProduct(HttpExchange exchange, String id) throws Exception {
        UpdateRequest request = readJson(exchange, UpdateRequest.class);
        if (request == null || request.price == null || request.quantity == null) {
            throw new ApiException(400, "price and quantity are required");
        }
        manager.updateProduct(id, request.price, request.quantity);
        sendJson(exchange, 200, ProductMapper.toDto(manager.findProductById(id)));
    }

    private void deleteProduct(HttpExchange exchange, String id) throws IOException {
        if (!manager.removeProduct(id)) {
            throw new ApiException(404, "Product not found: " + id);
        }
        exchange.sendResponseHeaders(204, -1);
    }

    /** Request body for POST /products/{id}/adjust. */
    private static class AdjustRequest {
        Integer delta;
    }

    private void adjustStock(HttpExchange exchange, String id) throws Exception {
        AdjustRequest request = readJson(exchange, AdjustRequest.class);
        if (request == null || request.delta == null) {
            throw new ApiException(400, "delta is required");
        }
        int newQuantity = manager.adjustStock(id, request.delta);

        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("previousQuantity", newQuantity - request.delta);
        body.put("change", request.delta);
        body.put("quantity", newQuantity);
        sendJson(exchange, 200, body);
    }

    // ---- small helpers -------------------------------------------------

    private static void requireMethod(HttpExchange exchange, String allowed) {
        if (!exchange.getRequestMethod().equals(allowed)) {
            throw ApiException.methodNotAllowed(allowed);
        }
    }

    private static List<String> pathParts(HttpExchange exchange) {
        List<String> parts = new ArrayList<>();
        for (String part : exchange.getRequestURI().getPath().split("/")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static Map<String, String> queryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                       URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "Request body too large");
        }
        try {
            T body = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
            if (body == null) {
                throw new ApiException(400, "Request body must be a JSON object");
            }
            return body;
        } catch (JsonParseException e) {
            throw new ApiException(400, "Request body is not valid JSON");
        }
    }

    private static Map<String, String> error(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", message);
        return body;
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** An error with an HTTP status, thrown anywhere in a handler. */
    private static class ApiException extends RuntimeException {
        final int status;
        final String allowedMethods;

        ApiException(int status, String message) {
            this(status, message, null);
        }

        ApiException(int status, String message, String allowedMethods) {
            super(message);
            this.status = status;
            this.allowedMethods = allowedMethods;
        }

        static ApiException methodNotAllowed(String allowed) {
            return new ApiException(405, "Method not allowed", allowed);
        }
    }

    // ---- run it on its own ---------------------------------------------

    /** Usage: InventoryApiServer [databaseFile] [port]   (defaults: inventory.db, 8080) */
    public static void main(String[] args) throws IOException {
        String dbPath = args.length > 0 ? args[0] : "inventory.db";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        InventoryManager manager = new InventoryManager(new SqliteProductRepository(dbPath));
        manager.seedSampleDataIfEmpty();

        InventoryApiServer api = new InventoryApiServer(manager, port);
        api.start();
        Runtime.getRuntime().addShutdownHook(new Thread(api::stop));
        System.out.println("Inventory API running at http://localhost:" + api.getPort() + "/products");
        System.out.println("Database file: " + dbPath + "   (Ctrl+C to stop)");
    }
}
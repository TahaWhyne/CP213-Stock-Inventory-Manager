

## Open in Eclipse
Import using **File > Import > Maven > Existing Maven Projects**, then select this directory. Let Maven resolve the dependencies from `pom.xml`.

## Run tests
From this directory:

```bash
mvn test
```

The tests use temporary SQLite database files, so they do not alter the application's `inventory.db`.

## Run the desktop application

```bash
mvn exec:java -Dexec.mainClass=inventory.InventoryManagerGUI
```

The GUI now persists data to `inventory.db` in the working directory. The six sample products are inserted only when the database is empty.

## Run the REST API

```bash
mvn exec:java -Dexec.mainClass=inventory.api.InventoryApiServer
```

Defaults to `http://localhost:8080` and `inventory.db`. You can provide a database path and port as arguments to the Java main method. The API binds to loopback only because it has no authentication.

## REST endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Health check and product count |
| GET | `/products` | List products |
| GET | `/products?category=Electronics` | Filter by category |
| GET | `/products?search=phone` | Case-insensitive partial name search |
| GET | `/products?lowStock=true` | Products with quantity at or below 10 |
| POST | `/products` | Create a product (JSON body) |
| GET | `/products/{id}` | Retrieve one product |
| PUT | `/products/{id}` | Update price and quantity |
| DELETE | `/products/{id}` | Delete a product |
| POST | `/products/{id}/adjust` | Atomically adjust stock using `{"delta":-3}` |

Product creation JSON examples:

```json
{
  "productId": "E100",
  "productName": "Keyboard",
  "price": 49.99,
  "quantity": 7,
  "category": "Electronics",
  "warrantyMonths": 24
}
```

For Grocery use `expirationDate`; for Clothing use `size`.

## Design notes
- The GUI and API both call `InventoryManager`; neither accesses SQL directly.
- `SqliteProductRepository` owns SQL and maps database rows back to the correct product subclass.
- Prices are stored as integer cents to avoid floating-point storage errors.
- Stock changes use one conditional SQL `UPDATE`, preventing concurrent requests from reducing stock below zero.
- SQL statements use prepared parameters; user search text is treated literally rather than as SQL syntax.
- The API returns HTTP status codes for validation errors, missing products, duplicate IDs, and insufficient stock.


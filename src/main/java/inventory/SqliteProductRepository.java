package inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * SQLite-backed persistence for inventory products.
 * Each operation uses its own connection so the repository is safe to share
 * between the Swing application and API request threads.
 */
public class SqliteProductRepository {
    private final String dbPath;
    private final String jdbcUrl;

    public SqliteProductRepository(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) throw new IllegalArgumentException("Database path is required");
        this.dbPath = dbPath;
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        initializeSchema();
    }

    public String getDatabasePath() { return dbPath; }

    private Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA busy_timeout = 10000");
            st.execute("PRAGMA foreign_keys = ON");
        }
        return c;
    }

    private void initializeSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS products (
              id TEXT PRIMARY KEY NOT NULL CHECK(length(trim(id)) > 0),
              name TEXT NOT NULL CHECK(length(trim(name)) > 0),
              price_cents INTEGER NOT NULL CHECK(price_cents >= 0),
              quantity INTEGER NOT NULL CHECK(quantity >= 0),
              category TEXT NOT NULL CHECK(category IN ('Electronics','Grocery','Clothing')),
              warranty_months INTEGER,
              expiration_date TEXT,
              size TEXT,
              CHECK (
                (category = 'Electronics' AND warranty_months IS NOT NULL AND warranty_months >= 0
                    AND expiration_date IS NULL AND size IS NULL)
                OR
                (category = 'Grocery' AND expiration_date IS NOT NULL AND length(trim(expiration_date)) > 0
                    AND warranty_months IS NULL AND size IS NULL)
                OR
                (category = 'Clothing' AND size IS NOT NULL AND length(trim(size)) > 0
                    AND warranty_months IS NULL AND expiration_date IS NULL)
              )
            )
            """;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RepositoryException("Could not initialize inventory database", e);
        }
    }

    public void add(Product p) throws InvalidProductException {
        validate(p);
        String sql = "INSERT INTO products(id,name,price_cents,quantity,category,warranty_months,expiration_date,size) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindProduct(ps, p);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isConstraint(e)) {
                if (findById(p.getProductId()).isPresent())
                    throw new DuplicateProductException("Product with ID " + p.getProductId() + " already exists");
                throw new InvalidProductException("Invalid product data: " + e.getMessage());
            }
            throw new RepositoryException("Could not save product", e);
        }
    }

    private static boolean isConstraint(SQLException e) {
        return e.getErrorCode() == 19 || (e.getMessage() != null && e.getMessage().toLowerCase().contains("constraint"));
    }

    private static void validate(Product p) {
        if (p == null) throw new IllegalArgumentException("Product cannot be null");
        if (p.getProductId() == null || p.getProductId().isBlank()) throw new IllegalArgumentException("Product ID cannot be empty");
        if (p.getProductName() == null || p.getProductName().isBlank()) throw new IllegalArgumentException("Product name cannot be empty");
        if (!Double.isFinite(p.getPrice()) || p.getPrice() < 0) throw new IllegalArgumentException("Price cannot be negative");
        if (p.getQuantity() < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        if (p instanceof Electronics e && e.getWarrantyMonths() < 0) throw new IllegalArgumentException("Warranty cannot be negative");
        if (p instanceof Grocery g && (g.getExpirationDate() == null || g.getExpirationDate().isBlank())) throw new IllegalArgumentException("Expiration date cannot be empty");
        if (p instanceof Clothing c && (c.getSize() == null || c.getSize().isBlank())) throw new IllegalArgumentException("Size cannot be empty");
    }

    private static long cents(double price) {
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static void bindProduct(PreparedStatement ps, Product p) throws SQLException {
        ps.setString(1, p.getProductId());
        ps.setString(2, p.getProductName());
        ps.setLong(3, cents(p.getPrice()));
        ps.setInt(4, p.getQuantity());
        ps.setString(5, p.getCategory());
        if (p instanceof Electronics e) {
            ps.setInt(6, e.getWarrantyMonths()); ps.setNull(7, Types.VARCHAR); ps.setNull(8, Types.VARCHAR);
        } else if (p instanceof Grocery g) {
            ps.setNull(6, Types.INTEGER); ps.setString(7, g.getExpirationDate()); ps.setNull(8, Types.VARCHAR);
        } else if (p instanceof Clothing cl) {
            ps.setNull(6, Types.INTEGER); ps.setNull(7, Types.VARCHAR); ps.setString(8, cl.getSize());
        } else throw new IllegalArgumentException("Unsupported product type");
    }

    public Optional<Product> findById(String id) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT * FROM products WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        } catch (SQLException e) { throw new RepositoryException("Could not find product", e); }
    }

    public List<Product> find(String category, String search, Integer maxQuantity) {
        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) { sql.append(" AND lower(category)=lower(?)"); args.add(category); }
        if (search != null && !search.isBlank()) { sql.append(" AND instr(lower(name), lower(?)) > 0"); args.add(search); }
        if (maxQuantity != null) { sql.append(" AND quantity <= ?"); args.add(maxQuantity); }
        sql.append(" ORDER BY id");
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i=0;i<args.size();i++) {
                Object arg=args.get(i);
                if (arg instanceof Integer n) ps.setInt(i+1,n); else ps.setString(i+1,(String)arg);
            }
            List<Product> result = new ArrayList<>();
            try (ResultSet rs=ps.executeQuery()) { while(rs.next()) result.add(map(rs)); }
            return result;
        } catch(SQLException e) { throw new RepositoryException("Could not search products",e); }
    }

    public boolean update(Product p) {
        validate(p);
        String sql = "UPDATE products SET name=?,price_cents=?,quantity=?,category=?,warranty_months=?,expiration_date=?,size=? WHERE id=?";
        try(Connection c=connect(); PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setString(1,p.getProductName()); ps.setLong(2,cents(p.getPrice())); ps.setInt(3,p.getQuantity());
            ps.setString(4,p.getCategory());
            if(p instanceof Electronics e){ps.setInt(5,e.getWarrantyMonths());ps.setNull(6,Types.VARCHAR);ps.setNull(7,Types.VARCHAR);}
            else if(p instanceof Grocery g){ps.setNull(5,Types.INTEGER);ps.setString(6,g.getExpirationDate());ps.setNull(7,Types.VARCHAR);}
            else if(p instanceof Clothing cl){ps.setNull(5,Types.INTEGER);ps.setNull(6,Types.VARCHAR);ps.setString(7,cl.getSize());}
            else throw new IllegalArgumentException("Unsupported product type");
            ps.setString(8,p.getProductId()); return ps.executeUpdate()>0;
        } catch(SQLException e){throw new RepositoryException("Could not update product",e);}
    }

    public boolean delete(String id) {
        try(Connection c=connect();PreparedStatement ps=c.prepareStatement("DELETE FROM products WHERE id=?")){
            ps.setString(1,id);return ps.executeUpdate()>0;
        }catch(SQLException e){throw new RepositoryException("Could not delete product",e);}
    }

    /** Atomic stock update. Empty means missing product or insufficient stock. */
    public OptionalInt adjustQuantity(String id,int delta) {
        String sql="UPDATE products SET quantity=quantity+? WHERE id=? AND quantity+?>=0";
        try(Connection c=connect();PreparedStatement ps=c.prepareStatement(sql)){
            ps.setInt(1,delta);ps.setString(2,id);ps.setInt(3,delta);
            if(ps.executeUpdate()==0)return OptionalInt.empty();
            try(PreparedStatement read=c.prepareStatement("SELECT quantity FROM products WHERE id=?")){
                read.setString(1,id);try(ResultSet rs=read.executeQuery()){return rs.next()?OptionalInt.of(rs.getInt(1)):OptionalInt.empty();}
            }
        }catch(SQLException e){throw new RepositoryException("Could not adjust stock",e);}
    }

    public int count() {
        try(Connection c=connect();Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM products")){
            return rs.next()?rs.getInt(1):0;
        }catch(SQLException e){throw new RepositoryException("Could not count products",e);}
    }

    private static Product map(ResultSet rs) throws SQLException {
        String id=rs.getString("id"), name=rs.getString("name");
        double price=rs.getLong("price_cents")/100.0; int qty=rs.getInt("quantity");
        return switch(rs.getString("category")){
            case "Electronics" -> new Electronics(id,name,price,qty,rs.getInt("warranty_months"));
            case "Grocery" -> new Grocery(id,name,price,qty,rs.getString("expiration_date"));
            case "Clothing" -> new Clothing(id,name,price,qty,rs.getString("size"));
            default -> throw new SQLException("Unknown product category");
        };
    }
}

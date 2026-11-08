package inventory.api;

/** JSON representation of a product. */
public class ProductDto {
    public String productId;
    public String productName;
    public Double price;
    public Integer quantity;
    public String category;
    public Integer warrantyMonths;
    public String expirationDate;
    public String size;
}

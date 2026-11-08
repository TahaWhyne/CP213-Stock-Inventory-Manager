package inventory.api;

import inventory.*;

public final class ProductMapper {
    private ProductMapper() {}

    public static boolean isKnownCategory(String category) {
        return category != null && (category.equalsIgnoreCase("Electronics")
                || category.equalsIgnoreCase("Grocery") || category.equalsIgnoreCase("Clothing"));
    }

    public static Product fromDto(ProductDto dto) throws InvalidProductException {
        if (dto == null) throw new InvalidProductException("Product body is required");
        if (dto.category == null) throw new InvalidProductException("Product category is required");
        if (dto.price == null || dto.quantity == null)
            throw new InvalidProductException("price and quantity are required");
        try {
            Product p;
            if (dto.category.equalsIgnoreCase("Electronics")) {
                if (dto.warrantyMonths == null) throw new InvalidProductException("warrantyMonths is required");
                p = new Electronics(dto.productId, dto.productName, dto.price, dto.quantity, dto.warrantyMonths);
            } else if (dto.category.equalsIgnoreCase("Grocery")) {
                p = new Grocery(dto.productId, dto.productName, dto.price, dto.quantity, dto.expirationDate);
            } else if (dto.category.equalsIgnoreCase("Clothing")) {
                p = new Clothing(dto.productId, dto.productName, dto.price, dto.quantity, dto.size);
            } else {
                throw new InvalidProductException("Unknown product category");
            }
            return p;
        } catch (NullPointerException e) {
            throw new InvalidProductException("Missing product information");
        }
    }

    public static ProductDto toDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.productId = product.getProductId();
        dto.productName = product.getProductName();
        dto.price = product.getPrice();
        dto.quantity = product.getQuantity();
        dto.category = product.getCategory();
        if (product instanceof Electronics e) dto.warrantyMonths = e.getWarrantyMonths();
        else if (product instanceof Grocery g) dto.expirationDate = g.getExpirationDate();
        else if (product instanceof Clothing c) dto.size = c.getSize();
        return dto;
    }
}

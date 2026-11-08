package inventory;

public class DuplicateProductException extends InvalidProductException {
    public DuplicateProductException(String message) { super(message); }
}

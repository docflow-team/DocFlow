package at.fhtw.docflow.identity.application;

public class InvalidPasswordException extends RuntimeException {
    private final String field;

    public InvalidPasswordException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() { return field; }
}

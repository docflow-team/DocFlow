package at.fhtw.docflow.identity.application;

public class InvalidLoginException extends RuntimeException {
    public InvalidLoginException() { super("Invalid login or password"); }
}

package at.fhtw.docflow.identity.application;

public interface PasswordHasher {

    String hash(String password);

    boolean matches(String password, String encodedPassword);
}

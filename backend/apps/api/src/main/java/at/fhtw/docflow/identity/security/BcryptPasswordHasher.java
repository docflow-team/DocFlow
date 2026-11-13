package at.fhtw.docflow.identity.security;

import at.fhtw.docflow.identity.application.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class BcryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public BcryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public String hash(String password) {
        if (password == null || password.length() < 8 || exceedsBcryptLimit(password)) {
            throw new IllegalArgumentException("Password must contain at least 8 characters and at most 72 UTF-8 bytes");
        }
        return encoder.encode(password);
    }

    public boolean matches(String password, String encodedPassword) {
        return password != null && encodedPassword != null && !exceedsBcryptLimit(password)
                && encoder.matches(password, encodedPassword);
    }

    private boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72;
    }
}

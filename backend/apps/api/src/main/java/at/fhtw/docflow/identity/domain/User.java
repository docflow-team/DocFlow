package at.fhtw.docflow.identity.domain;

import java.util.Locale;
import java.util.Objects;

public record User(Long id, String username, String email, String passwordHash, UserRole role,
                   boolean enabled, boolean mustChangePassword, long tokenVersion) {

    public User {
        username = normalizeIdentifier(username);
        email = normalizeIdentifier(email);
        if (username == null || !username.matches("[a-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException("Username must contain 3-50 letters, digits, dots, underscores or hyphens");
        }
        if (email == null || email.isBlank() || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+")) {
            throw new IllegalArgumentException("A valid email address of at most 254 characters is required");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("A password hash is required");
        }
        Objects.requireNonNull(role, "A user role is required");
        if ((id != null && id <= 0) || tokenVersion < 0) {
            throw new IllegalArgumentException("User identity or token version is invalid");
        }
    }

    public static User initialAdmin(String username, String email, String passwordHash) {
        return new User(null, username, email, passwordHash, UserRole.ADMIN, true, true, 0);
    }

    public static String normalizeIdentifier(String identifier) {
        return identifier == null ? null : identifier.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "User[id=" + id + ", role=" + role + "]";
    }
}

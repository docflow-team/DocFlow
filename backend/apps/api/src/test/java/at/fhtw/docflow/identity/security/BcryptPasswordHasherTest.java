package at.fhtw.docflow.identity.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher(new PasswordConfiguration().passwordEncoder());

    @Test
    void hashesWithBcryptAndDoesNotNormalizeThePassword() {
        String password = "  MixedCaseSecret  ";
        String encoded = hasher.hash(password);

        assertTrue(encoded.startsWith("{bcrypt}"));
        assertNotEquals(password, encoded);
        assertTrue(hasher.matches(password, encoded));
        assertFalse(hasher.matches(password.trim(), encoded));
        assertFalse(hasher.matches(password.toLowerCase(), encoded));
    }

    @Test
    void rejectsShortAndOversizedUtf8PasswordsWithoutTruncation() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("short"));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("x".repeat(73)));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("ä".repeat(37)));
        String password = "ä".repeat(36);
        String encoded = hasher.hash(password);
        assertTrue(hasher.matches(password, encoded));
        assertFalse(hasher.matches(password + "x", encoded));
    }

    @Test
    void generatesDifferentSaltedHashesAndRejectsWrongOrAbsentPasswords() {
        String encoded = hasher.hash("SyntheticPassword");
        assertNotEquals(encoded, hasher.hash("SyntheticPassword"));
        assertFalse(hasher.matches("DifferentPassword", encoded));
        assertFalse(hasher.matches(null, encoded));
        assertFalse(hasher.matches("SyntheticPassword", null));
    }
}

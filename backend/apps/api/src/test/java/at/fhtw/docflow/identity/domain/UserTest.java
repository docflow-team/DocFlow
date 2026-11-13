package at.fhtw.docflow.identity.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void initialAdminNormalizesIdentifiersAndRequiresPasswordChange() {
        User admin = User.initialAdmin("  Team.Admin  ", " ADMIN@Example.invalid ", "encoded-secret");

        assertEquals("team.admin", admin.username());
        assertEquals("admin@example.invalid", admin.email());
        assertNull(admin.id());
        assertEquals(UserRole.ADMIN, admin.role());
        assertTrue(admin.enabled());
        assertTrue(admin.mustChangePassword());
        assertEquals(0, admin.tokenVersion());
        assertFalse(admin.toString().contains("encoded-secret"));
        assertFalse(admin.toString().contains(admin.email()));
    }

    @Test
    void rejectsInvalidIdentityAndAccountStateWithoutExposingValues() {
        assertThrows(IllegalArgumentException.class, () -> User.initialAdmin("ab", "admin@example.invalid", "hash"));
        assertThrows(IllegalArgumentException.class, () -> User.initialAdmin("admin@example.invalid", "admin@example.invalid", "hash"));
        assertThrows(IllegalArgumentException.class, () -> User.initialAdmin("admin", "not-an-email", "hash"));
        assertThrows(IllegalArgumentException.class, () -> User.initialAdmin("admin", "admin@example.invalid", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new User(1L, "admin", "admin@example.invalid", "hash", UserRole.ADMIN, true, false, -1));
    }
}

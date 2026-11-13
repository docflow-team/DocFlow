package at.fhtw.docflow.identity.persistence;

import at.fhtw.docflow.identity.application.AdminBootstrapService;
import at.fhtw.docflow.identity.application.PasswordHasher;
import at.fhtw.docflow.identity.application.UserRepository;
import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS api",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "docflow.jwt.secret=synthetic-test-key-at-least-thirty-two-bytes"
})
class UserPersistenceTest {

    @Test
    void bootstrapAndUserStateRoundTripWithoutResettingExistingCredentials(
            @Autowired AdminBootstrapService bootstrap, @Autowired UserRepository users, @Autowired PasswordHasher passwords) {
        assertTrue(bootstrap.bootstrap(" Initial.Admin ", "INITIAL@example.invalid", "SyntheticPassword"));
        User admin = users.findByUsername(" INITIAL.ADMIN ").orElseThrow();
        assertEquals(admin, users.findByEmail("INITIAL@example.invalid").orElseThrow());
        assertNotNull(admin.id());
        assertTrue(passwords.matches("SyntheticPassword", admin.passwordHash()));
        assertEquals(UserRole.ADMIN, admin.role());
        assertTrue(admin.enabled());
        assertTrue(admin.mustChangePassword());
        assertEquals(0, admin.tokenVersion());

        assertFalse(bootstrap.bootstrap("other.admin", "OTHER@example.invalid", "DifferentPassword"));
        assertEquals(admin, users.findByUsername("initial.admin").orElseThrow());
        assertTrue(users.findByUsername("other.admin").isEmpty());

        assertThrows(DataIntegrityViolationException.class,
                () -> users.save(User.initialAdmin("INITIAL.ADMIN", "distinct@example.invalid", admin.passwordHash())));
        assertThrows(DataIntegrityViolationException.class,
                () -> users.save(User.initialAdmin("distinct.admin", "INITIAL@example.invalid", admin.passwordHash())));

        User changed = new User(admin.id(), admin.username(), admin.email(), admin.passwordHash(),
                UserRole.USER, false, false, 1);
        assertEquals(changed, users.save(changed));
        assertEquals(changed, users.findByUsername(admin.username()).orElseThrow());
    }
}

package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final AdminBootstrapService service = new DefaultAdminBootstrapService(users, passwords);

    @Test
    void createsFirstAdminWithOnlyTheEncodedPassword() {
        when(passwords.hash("SyntheticPassword")).thenReturn("encoded-password");

        assertTrue(service.bootstrap(" Admin ", "ADMIN@example.invalid", "SyntheticPassword"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("admin", saved.getValue().username());
        assertEquals("admin@example.invalid", saved.getValue().email());
        assertEquals("encoded-password", saved.getValue().passwordHash());
        assertEquals(UserRole.ADMIN, saved.getValue().role());
        assertTrue(saved.getValue().mustChangePassword());
    }

    @Test
    void existingAccountsAreNeverResetOrRehashed() {
        when(users.existsAny()).thenReturn(true);

        assertFalse(service.bootstrap("admin", "admin@example.invalid", "DifferentPassword"));

        verify(users, never()).save(any());
        verifyNoInteractions(passwords);
    }
}

package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final TokenIssuer tokens = mock(TokenIssuer.class);
    private final CurrentUser current = () -> 1L;
    private AuthenticationService service;
    private final User user = new User(1L, "student", "student@example.invalid", "hash", UserRole.USER, true, true, 3);

    @BeforeEach
    void setup() {
        when(passwords.hash(anyString())).thenReturn("dummy-hash");
        service = new DefaultAuthenticationService(users, passwords, tokens, current);
    }

    @Test
    void normalizedLoginIssuesTokenForMatchingEnabledAccount() {
        when(users.findByUsername("student")).thenReturn(Optional.of(user));
        when(passwords.matches(" exact password ", "hash")).thenReturn(true);
        when(tokens.issue(user)).thenReturn("synthetic-token");
        var result = service.login(" Student ", " exact password ");
        assertEquals(user, result.user());
        assertEquals("synthetic-token", result.accessToken());
        verify(tokens).issue(user);
    }

    @Test
    void unknownAccountStillChecksPasswordButNeverIssuesToken() {
        assertThrows(InvalidLoginException.class, () -> service.login("missing", "wrong"));
        verify(passwords).matches("wrong", "dummy-hash");
        verifyNoInteractions(tokens);
    }

    @Test
    void passwordChangeClearsTemporaryFlagAndRevokesTokensWithoutChangingRole() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(passwords.matches("old", "hash")).thenReturn(true);
        when(passwords.hash("new-password")).thenReturn("new-hash");
        service.changePassword("old", "new-password");
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("new-hash", saved.getValue().passwordHash());
        assertFalse(saved.getValue().mustChangePassword());
        assertEquals(4, saved.getValue().tokenVersion());
        assertEquals(user.role(), saved.getValue().role());
    }

    @Test
    void incorrectCurrentPasswordDoesNotWriteAccountState() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        assertThrows(InvalidPasswordException.class, () -> service.changePassword("wrong", "new-password"));
        verify(users, never()).save(any());
    }
}

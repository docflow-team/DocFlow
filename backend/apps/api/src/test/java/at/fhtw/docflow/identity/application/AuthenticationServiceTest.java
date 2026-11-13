package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import at.fhtw.docflow.identity.application.AuthenticationService.InvalidAccountException;
import at.fhtw.docflow.identity.application.AuthenticationService.InvalidLoginException;
import at.fhtw.docflow.identity.application.AuthenticationService.InvalidPasswordException;
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
    @ParameterizedTest
    @EnumSource(UserRole.class)
    void createsNormalizedAccountWithHashedTemporaryPassword(UserRole role) {
        when(users.findById(1L)).thenReturn(Optional.of(
                new User(1L, "admin", "admin@example.invalid", "admin-hash", UserRole.ADMIN, true, false, 2)));
        when(passwords.hash(" ExactPassword ")).thenReturn("encoded-password");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));

        User result = service.createUser(" Team.User ", " TEAM@example.invalid ", role, " ExactPassword ");

        verify(users).save(result);
        assertEquals("team.user", result.username());
        assertEquals("team@example.invalid", result.email());
        assertEquals("encoded-password", result.passwordHash());
        assertEquals(role, result.role());
        assertTrue(result.enabled());
        assertTrue(result.mustChangePassword());
        assertEquals(0, result.tokenVersion());
        assertNull(result.id());
    }

    @Test
    void requiresAnEnabledAdministratorWhoHasChangedTheirPassword() {
        for (User denied : new User[]{
                new User(1L, "admin", "admin@example.invalid", "hash", UserRole.USER, true, false, 0),
                new User(1L, "admin", "admin@example.invalid", "hash", UserRole.ADMIN, true, true, 0),
                new User(1L, "admin", "admin@example.invalid", "hash", UserRole.ADMIN, false, false, 0)}) {
            when(users.findById(1L)).thenReturn(Optional.of(denied));
            assertThrows(AccessDeniedException.class,
                    () -> service.createUser("new-user", "new@example.invalid", UserRole.USER, "SyntheticPassword"));
        }
        verify(passwords, never()).hash("SyntheticPassword");
        verify(users, never()).save(any());
    }

    @Test
    void passwordValidationUsesHasherAndIdentifiesTheTemporaryPasswordField() {
        when(users.findById(1L)).thenReturn(Optional.of(
                new User(1L, "admin", "admin@example.invalid", "admin-hash", UserRole.ADMIN, true, false, 2)));
        when(passwords.hash("short")).thenThrow(new IllegalArgumentException("Password is too short"));
        var error = assertThrows(InvalidPasswordException.class,
                () -> service.createUser("new-user", "new@example.invalid", UserRole.USER, "short"));
        assertEquals("temporaryPassword", error.field());
        verify(users, never()).save(any());
    }

    @Test
    void identifierValidationUsesTheUserModel() {
        when(users.findById(1L)).thenReturn(Optional.of(
                new User(1L, "admin", "admin@example.invalid", "admin-hash", UserRole.ADMIN, true, false, 2)));
        when(passwords.hash(anyString())).thenReturn("hash");
        assertThrows(InvalidAccountException.class,
                () -> service.createUser("ab", "new@example.invalid", UserRole.USER, "SyntheticPassword"));
        assertThrows(InvalidAccountException.class,
                () -> service.createUser("new-user", "invalid-email", UserRole.USER, "SyntheticPassword"));
        verify(users, never()).save(any());
    }
}

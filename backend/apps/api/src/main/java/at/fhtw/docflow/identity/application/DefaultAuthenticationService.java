package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;
import at.fhtw.docflow.identity.application.AuthenticationService.InvalidAccountException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap-admin")
@Transactional
public class DefaultAuthenticationService implements AuthenticationService {
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final TokenIssuer tokens;
    private final CurrentUser currentUser;
    private final String dummyHash;

    public DefaultAuthenticationService(UserRepository users, PasswordHasher passwords,
                                        TokenIssuer tokens, CurrentUser currentUser) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.currentUser = currentUser;
        // Unknown accounts still perform a password comparison.
        this.dummyHash = passwords.hash("DocFlow synthetic login comparison");
    }

    @Transactional(readOnly = true)
    public LoginResult login(String login, String password) {
        String normalized = User.normalizeIdentifier(login);
        User user = normalized == null ? null : (normalized.contains("@")
                ? users.findByEmail(normalized) : users.findByUsername(normalized)).orElse(null);
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.passwordHash());
        if (user == null || !matches || !user.enabled()) {
            throw new InvalidLoginException();
        }
        return new LoginResult(user, tokens.issue(user));
    }

    @Transactional(readOnly = true)
    public User me() {
        return users.findById(currentUser.id()).filter(User::enabled).orElseThrow(InvalidLoginException::new);
    }

    public void logout() {
        users.save(me().revokeTokens());
    }

    public void changePassword(String currentPassword, String newPassword) {
        User user = me();
        if (!passwords.matches(currentPassword, user.passwordHash())) {
            throw new InvalidPasswordException("currentPassword", "Current password is incorrect");
        }
        String hash;
        try {
            hash = passwords.hash(newPassword);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPasswordException("newPassword", exception.getMessage());
        }
        users.save(user.changePassword(hash));
    }

    public User createUser(String username, String email, UserRole role, String temporaryPassword) {
        User administrator = users.findById(currentUser.id()).orElseThrow(() -> new AccessDeniedException("Administrator access required"));
        if (!administrator.enabled() || administrator.mustChangePassword() || administrator.role() != UserRole.ADMIN) {
            throw new AccessDeniedException("Administrator access required");
        }

        String hash;
        try {
            hash = passwords.hash(temporaryPassword);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPasswordException("temporaryPassword", exception.getMessage());
        }
        User user;
        try {
            user = User.newAccount(username, email, hash, role);
        } catch (IllegalArgumentException exception) {
            throw new InvalidAccountException(exception.getMessage());
        }
        return users.save(user);
    }
}

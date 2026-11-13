package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;

public interface AuthenticationService {
    LoginResult login(String login, String password);
    User me();
    void logout();
    void changePassword(String currentPassword, String newPassword);
    User createUser(String username, String email, UserRole role, String temporaryPassword);

    class InvalidAccountException extends RuntimeException {
        public InvalidAccountException(String message) { super(message); }
    }
}

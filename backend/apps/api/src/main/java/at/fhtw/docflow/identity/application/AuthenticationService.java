package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;

public interface AuthenticationService {
    LoginResult login(String login, String password);
    User me();
    void logout();
    void changePassword(String currentPassword, String newPassword);
    User createUser(String username, String email, UserRole role, String temporaryPassword);

    record LoginResult(User user, String accessToken) {
        @Override
        public String toString() { return "LoginResult[redacted]"; }
    }

    class InvalidLoginException extends RuntimeException {
        public InvalidLoginException() { super("Invalid login or password"); }
    }

    class InvalidPasswordException extends RuntimeException {
        private final String field;

        public InvalidPasswordException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() { return field; }
    }

    class InvalidAccountException extends RuntimeException {
        public InvalidAccountException(String message) { super(message); }
    }
}

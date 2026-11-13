package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;

public interface AuthenticationService {
    LoginResult login(String login, String password);
    User me();
    void logout();
    void changePassword(String currentPassword, String newPassword);
}

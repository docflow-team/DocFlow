package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultAdminBootstrapService implements AdminBootstrapService {

    private final UserRepository users;
    private final PasswordHasher passwords;

    public DefaultAdminBootstrapService(UserRepository users, PasswordHasher passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public boolean bootstrap(String username, String email, String password) {
        if (users.existsAny()) {
            return false;
        }
        User admin = User.initialAdmin(username, email, passwords.hash(password));
        users.save(admin);
        return true;
    }
}

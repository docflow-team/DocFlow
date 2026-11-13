package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsAny();
}

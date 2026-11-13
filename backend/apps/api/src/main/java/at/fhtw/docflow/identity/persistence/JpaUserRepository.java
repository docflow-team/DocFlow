package at.fhtw.docflow.identity.persistence;

import at.fhtw.docflow.identity.application.UserRepository;
import at.fhtw.docflow.identity.domain.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository users;
    private final UserPersistenceMapper mapper;

    public JpaUserRepository(SpringDataUserRepository users, UserPersistenceMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    public User save(User user) {
        UserEntity entity;
        if (user.id() == null) {
            entity = mapper.toNewEntity(user);
        } else {
            entity = users.findById(user.id()).orElseThrow();
            mapper.update(user, entity);
        }
        return mapper.toDomain(users.save(entity));
    }

    public Optional<User> findByUsername(String username) {
        return users.findByUsername(User.normalizeIdentifier(username)).map(mapper::toDomain);
    }

    public Optional<User> findByEmail(String email) {
        return users.findByEmail(User.normalizeIdentifier(email)).map(mapper::toDomain);
    }

    public boolean existsAny() {
        return users.count() > 0;
    }
}

package at.fhtw.docflow.identity.persistence;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.identity.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MappingConfig.class)
public interface UserPersistenceMapper {

    User toDomain(UserEntity entity);

    // Database-generated identity is not supplied by the creator.
    @Mapping(target = "id", ignore = true)
    UserEntity toNewEntity(User user);

    // Only application services supply account state; identity is immutable.
    @Mapping(target = "id", ignore = true)
    void update(User user, @MappingTarget UserEntity entity);
}

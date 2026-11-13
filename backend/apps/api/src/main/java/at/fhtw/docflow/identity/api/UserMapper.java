package at.fhtw.docflow.identity.api;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.identity.domain.User;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface UserMapper {
    UserResponse toResponse(User user);
}

package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.document.domain.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface CategoryPersistenceMapper {

    Category toDomain(CategoryEntity entity);

    // New category IDs are assigned by PostgreSQL, not by the caller.
    @Mapping(target = "id", ignore = true)
    CategoryEntity toNewEntity(Category category);
}

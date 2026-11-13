package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.document.domain.DocumentType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface DocumentTypePersistenceMapper {

    DocumentType toDomain(DocumentTypeEntity entity);

    // New type IDs are assigned by PostgreSQL, not by the caller.
    @Mapping(target = "id", ignore = true)
    DocumentTypeEntity toNewEntity(DocumentType documentType);
}

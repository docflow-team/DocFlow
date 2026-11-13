package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.document.domain.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MappingConfig.class)
public interface DocumentPersistenceMapper {

    @Mapping(target = "documentTypeIds", source = "documentTypes")
    Document toDomain(DocumentEntity entity);

    // Hibernate generates identity/time; the adapter resolves shared type references.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "documentTypes", ignore = true)
    DocumentEntity toNewEntity(Document document);

    // Metadata changes preserve identity, creation time and existing assignments.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "documentTypes", ignore = true)
    void updateMetadata(Document document, @MappingTarget DocumentEntity entity);

    default Long toTypeId(DocumentTypeEntity entity) {
        return entity.getId();
    }
}

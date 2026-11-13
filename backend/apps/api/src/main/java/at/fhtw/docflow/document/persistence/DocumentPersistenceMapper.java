package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MappingConfig.class)
public interface DocumentPersistenceMapper {
    @Mapping(target = "metadata", source = ".")
    Document toDomain(DocumentEntity entity);

    Document.Metadata toMetadata(DocumentEntity entity);

    // Persistence generates identity/timestamps; the adapter resolves shared categories and tags.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "tags", ignore = true)
    DocumentEntity toNewEntity(Document document);

    // Only title and description change; file identity, ownership, timestamps and assignments are protected.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "originalFilename", ignore = true)
    @Mapping(target = "contentType", ignore = true)
    @Mapping(target = "objectKey", ignore = true)
    @Mapping(target = "sizeBytes", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "tags", ignore = true)
    void updateMetadata(Document document, @MappingTarget DocumentEntity entity);

    default String toTagName(TagEntity tag) { return tag.getName(); }

    Category toCategory(CategoryEntity entity);
}

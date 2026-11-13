package at.fhtw.docflow.document.api;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.document.domain.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface DocumentMapper {

    // Requests supply metadata only; persisted identity, time and assignments are protected.
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "documentTypeIds", ignore = true)
    Document toDomain(DocumentRequest request);

    DocumentResponse toResponse(Document document);
}

package at.fhtw.docflow.document.api;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.document.domain.DocumentType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface DocumentTypeMapper {

    // Identity belongs to persistence, not the type-create representation.
    @Mapping(target = "id", ignore = true)
    DocumentType toDomain(DocumentTypeRequest request);

    DocumentTypeResponse toResponse(DocumentType documentType);
}

package at.fhtw.docflow.document.api;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.document.api.DocumentController.*;
import at.fhtw.docflow.document.application.DocumentService.DocumentPage;
import at.fhtw.docflow.document.domain.Document;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface DocumentMapper {
    Document.Metadata toMetadata(UploadRequest request);
    Document.Metadata toMetadata(MetadataRequest request);
    DocumentResponse toResponse(Document document);
    DocumentPageResponse toResponse(DocumentPage page);
}

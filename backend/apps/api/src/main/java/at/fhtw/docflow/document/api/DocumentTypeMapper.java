package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.api.DocumentTypeRequest;
import at.fhtw.docflow.document.api.DocumentTypeResponse;
import at.fhtw.docflow.document.persistence.DocumentType;
import org.springframework.stereotype.Component;

@Component
public class DocumentTypeMapper {

    public DocumentType toEntity(DocumentTypeRequest request) {
        return new DocumentType(
                request.name(),
                request.description()
        );
    }

    public DocumentTypeResponse toResponse(DocumentType documentType) {
        return new DocumentTypeResponse(
                documentType.getId(),
                documentType.getName(),
                documentType.getDescription()
                );
    }
}

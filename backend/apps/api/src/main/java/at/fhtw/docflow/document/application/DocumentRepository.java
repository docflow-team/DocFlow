package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.application.DocumentService.DocumentPage;
import java.util.Optional;

public interface DocumentRepository {
    Document save(Document document);
    DocumentPage findAllByOwnerId(Long ownerId, int page, int size);
    Optional<Document> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<Document> deleteByIdAndOwnerId(Long id, Long ownerId);
}

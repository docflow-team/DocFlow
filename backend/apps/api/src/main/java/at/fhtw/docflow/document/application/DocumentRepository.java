package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    Document save(Document document);

    List<Document> findAllByOwnerId(Long ownerId);

    Optional<Document> findByIdAndOwnerId(Long id, Long ownerId);

    boolean deleteByIdAndOwnerId(Long id, Long ownerId);
}

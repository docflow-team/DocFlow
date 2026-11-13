package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;

import java.util.List;
import java.util.Optional;

public interface DocumentService {

    Document createDocument(Document document);

    List<Document> getAllDocuments();

    Optional<Document> getDocumentById(Long id);

    Optional<Document> updateDocument(Long id, Document changes);

    boolean deleteDocument(Long id);

    void addDocumentType(Long documentId, Long typeId);
}

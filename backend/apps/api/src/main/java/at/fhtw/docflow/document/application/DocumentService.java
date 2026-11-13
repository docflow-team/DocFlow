package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import java.util.List;

public interface DocumentService {
    Document upload(Document.Metadata metadata, String filename, String contentType, byte[] content);
    DocumentPage getAllDocuments(int page, int size);
    Document getDocumentById(Long id);
    Document updateMetadata(Long id, Document.Metadata metadata);
    Download download(Long id);
    void deleteDocument(Long id);
    Document replaceCategories(Long documentId, List<Long> categoryIds);

    Document replaceTags(Long documentId, List<String> tags);

    record DocumentPage(List<Document> items, int page, int size, long totalElements, int totalPages) { }
    record Download(String originalFilename, byte[] content) { }
}

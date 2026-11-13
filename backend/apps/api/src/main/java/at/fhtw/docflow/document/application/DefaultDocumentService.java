package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DefaultDocumentService implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;

    public DefaultDocumentService(DocumentRepository documentRepository, DocumentTypeRepository documentTypeRepository) {
        this.documentRepository = documentRepository;
        this.documentTypeRepository = documentTypeRepository;
    }

    public Document createDocument(Document document) {
        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    public Optional<Document> updateDocument(Long id, Document updateDocument) {
        return documentRepository.findById(id)
                .map(document -> {
                    document.updateMetadata(
                            updateDocument.getFilename(),
                            updateDocument.getContentType(),
                            updateDocument.getDescription()
                    );
                    return documentRepository.save(document);
                });
    }

    public boolean deleteDocument(Long id) {
        if (!documentRepository.existsById(id)) {
            return false;
        }

        documentRepository.deleteById(id);
        return true;
    }

    public void addDocumentType(Long documentId, Long typeId) {

        Document document = documentRepository
                .findById(documentId)
                .orElseThrow();

        documentTypeRepository
                .findById(typeId)
                .orElseThrow();

        document.assignDocumentType(typeId);

        documentRepository.save(document);
    }
}

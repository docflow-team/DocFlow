package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.identity.application.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DefaultDocumentService implements DocumentService {

    private final CurrentUser currentUser;
    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;

    public DefaultDocumentService(DocumentRepository documentRepository, DocumentTypeRepository documentTypeRepository, CurrentUser currentUser) {
        this.currentUser = currentUser;
        this.documentRepository = documentRepository;
        this.documentTypeRepository = documentTypeRepository;
    }

    public Document createDocument(Document document) {
        document.assignOwner(currentUser.id());
        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public List<Document> getAllDocuments() {
        return documentRepository.findAllByOwnerId(currentUser.id());
    }

    @Transactional(readOnly = true)
    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findByIdAndOwnerId(id, currentUser.id());
    }

    public Optional<Document> updateDocument(Long id, Document updateDocument) {
        return documentRepository.findByIdAndOwnerId(id, currentUser.id())
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
        if (!documentRepository.existsByIdAndOwnerId(id, currentUser.id())) {
            return false;
        }

        documentRepository.deleteByIdAndOwnerId(id, currentUser.id());
        return true;
    }

    public void addDocumentType(Long documentId, Long typeId) {

        Document document = documentRepository
                .findByIdAndOwnerId(documentId, currentUser.id())
                .orElseThrow();

        documentTypeRepository
                .findById(typeId)
                .orElseThrow();

        document.assignDocumentType(typeId);

        documentRepository.save(document);
    }
}

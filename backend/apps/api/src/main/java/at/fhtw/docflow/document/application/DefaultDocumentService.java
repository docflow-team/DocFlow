package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.Document.InvalidDocumentException;
import at.fhtw.docflow.document.domain.Document.InvalidDocumentException.Reason;
import at.fhtw.docflow.document.application.DocumentStorage.StorageException;
import at.fhtw.docflow.identity.application.CurrentUser;
import org.apache.pdfbox.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

@Service
public class DefaultDocumentService implements DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentService.class);
    private final DocumentRepository documents;
    private final DocumentTypeRepository types;
    private final DocumentStorage storage;
    private final CurrentUser currentUser;
    private final TransactionTemplate transactions;

    public DefaultDocumentService(DocumentRepository documents, DocumentTypeRepository types,
                                  DocumentStorage storage, CurrentUser currentUser,
                                  PlatformTransactionManager transactionManager) {
        this.documents = documents;
        this.types = types;
        this.storage = storage;
        this.currentUser = currentUser;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Document upload(Document.Metadata metadata, String filename, String contentType, byte[] content) {
        String originalFilename = Document.normalizeFilename(filename);
        validatePdf(content, contentType);
        Long owner = currentUser.id();
        String key = storage.store(new ByteArrayInputStream(content), content.length);
        try {
            // Include the actual commit in this boundary so commit failures also clean up the object.
            return transactions.execute(status -> documents.save(new Document(null, owner, metadata,
                    originalFilename, key, content.length, null, null, Set.of())));
        } catch (RuntimeException exception) {
            removeObject(null, key);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public DocumentPage getAllDocuments(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidDocumentException("Page must be non-negative and size must be between 1 and 100");
        }
        return documents.findAllByOwnerId(currentUser.id(), page, size);
    }

    @Transactional(readOnly = true)
    public Document getDocumentById(Long id) {
        return documents.findByIdAndOwnerId(id, currentUser.id()).orElseThrow();
    }

    @Transactional
    public Document updateMetadata(Long id, Document.Metadata metadata) {
        Document document = getDocumentById(id);
        document.updateMetadata(metadata);
        return documents.save(document);
    }

    @Transactional(readOnly = true)
    public Download download(Long id) {
        Document document = getDocumentById(id);
        try (var content = storage.read(document.getObjectKey())) {
            return new Download(document.getOriginalFilename(), content.readAllBytes());
        } catch (IOException exception) {
            throw new StorageException(exception);
        }
    }

    public void deleteDocument(Long id) {
        Document document = transactions.execute(status -> documents
                .deleteByIdAndOwnerId(id, currentUser.id()).orElseThrow());
        removeObject(document.getId(), document.getObjectKey());
    }

    @Transactional
    public void addDocumentType(Long documentId, Long typeId) {
        Document document = getDocumentById(documentId);
        types.findById(typeId).orElseThrow();
        document.assignDocumentType(typeId);
        documents.save(document);
    }

    private void removeObject(Long documentId, String key) {
        try {
            storage.delete(key);
        } catch (StorageException exception) {
            log.warn("Object cleanup failed for document {} and key {}; manual cleanup is required", documentId, key);
        }
    }

    private static void validatePdf(byte[] content, String contentType) {
        if (content == null || content.length == 0) throw new InvalidDocumentException("PDF must not be empty");
        if (content.length > Document.MAX_PDF_BYTES) {
            throw new InvalidDocumentException(Reason.TOO_LARGE, "PDF must not exceed 10 MiB");
        }
        if (!"application/pdf".equalsIgnoreCase(contentType)) {
            throw new InvalidDocumentException(Reason.UNSUPPORTED_TYPE, "Only application/pdf files are supported");
        }
        try (var pdf = Loader.loadPDF(content)) {
            if (pdf.isEncrypted()) throw new InvalidDocumentException("Encrypted PDFs are not supported");
            if (pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 50) {
                throw new InvalidDocumentException("PDF must contain 1-50 pages");
            }
        } catch (IOException exception) {
            throw new InvalidDocumentException("File must be a parseable, unencrypted PDF");
        }
    }
}

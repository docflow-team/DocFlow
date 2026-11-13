package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import at.fhtw.docflow.identity.application.CurrentUser;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @Mock
    private CurrentUser currentUser;

    @BeforeEach
    void currentAccount() { when(currentUser.id()).thenReturn(1L); }

    @InjectMocks
    private DefaultDocumentService documentService;

    @Test
    void createDocumentShouldSaveDocument() {

        Document document = Document.create(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        );

        when(documentRepository.save(document))
                .thenReturn(document);

        Document result = documentService.createDocument(document);

        assertSame(document, result);
        verify(documentRepository).save(document);
    }

    @Test
    void getAllDocumentsShouldReturnDocuments() {
        Document doc1 = Document.create(
                "rechnung.pdf", "application/pdf", "Rechung"
        );

        Document doc2 = Document.create(
                "vertrag.pdf", "application/pdf", "Vertrag"
        );

        List<Document> documents = List.of(doc1, doc2);

        when(documentRepository.findAllByOwnerId(1L)).thenReturn(documents);

        List<Document> result = documentService.getAllDocuments();

        assertEquals(2, result.size());
        assertEquals(documents, result);
        verify(documentRepository).findAllByOwnerId(1L);
    }

    @Test
    void getDocumentByIdShouldReturnDocument() {

        Long id = 1L;
        Document document = Document.create(
                "rechung.pdf", "application/pdf", "Rechnung"
        );

        when(documentRepository.findByIdAndOwnerId(id, 1L))
                .thenReturn(Optional.of(document));

        Optional<Document> result = documentService.getDocumentById(id);

        assertTrue(result.isPresent());
        assertSame(document, result.get());
        verify(documentRepository).findByIdAndOwnerId(id, 1L);
    }

    @Test
    void getDocumentByIdShouldReturnEmptyWhenNotFound() {

        Long id = 999L;

        when(documentRepository.findByIdAndOwnerId(id, 1L))
                .thenReturn(Optional.empty());

        Optional<Document> result = documentService.getDocumentById(id);

        assertTrue(result.isEmpty());
        verify(documentRepository).findByIdAndOwnerId(id, 1L);
    }

    @Test
    void updateDocumentShouldUpdateMetadata() {

        Long id = 1L;

        Document existing = Document.create(
                "alt.pdf", "application/pdf", "Alte Beschreibung"
        );

        Document updated = Document.create(
                "neu.pdf", "application/pdf", "Neue Beschreibung"
        );

        when(documentRepository.findByIdAndOwnerId(id, 1L))
                .thenReturn(Optional.of(existing));

        when(documentRepository.save(existing))
                .thenReturn(existing);

        Optional<Document> result =
                documentService.updateDocument(id, updated);

        assertTrue(result.isPresent());
        assertEquals("neu.pdf", result.get().getFilename());
        assertEquals("application/pdf", result.get().getContentType());
        assertEquals("Neue Beschreibung", result.get().getDescription());

        verify(documentRepository).findByIdAndOwnerId(id, 1L);
        verify(documentRepository).save(existing);
    }

    @Test
    void updateDocumentShouldReturnEmptyWhenNotFound() {

        Long id = 999L;

        Document updated = Document.create(
                "neu.pdf", "application/pdf", "Beschreibung"
        );

        when(documentRepository.findByIdAndOwnerId(id, 1L))
                .thenReturn(Optional.empty());

        Optional<Document> result =
                documentService.updateDocument(id, updated);

        assertTrue(result.isEmpty());

        verify(documentRepository).findByIdAndOwnerId(id, 1L);
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void updateDocumentIgnoresIncomingIdentityTimeAndAssignments() {
        var createdAt = java.time.LocalDateTime.of(2026, 11, 13, 12, 0);
        Document existing = new Document(1L, "old.pdf", "application/pdf", "Old", createdAt, java.util.Set.of(2L), null);
        Document changes = new Document(99L, "new.pdf", "application/pdf", null,
                createdAt.plusDays(1), java.util.Set.of(3L), null);
        when(documentRepository.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(existing));
        when(documentRepository.save(existing)).thenReturn(existing);

        Document result = documentService.updateDocument(1L, changes).orElseThrow();

        assertEquals(1L, result.getId());
        assertEquals(createdAt, result.getCreatedAt());
        assertEquals(java.util.Set.of(2L), result.getDocumentTypeIds());
        assertNull(result.getDescription());
        verify(documentRepository).save(existing);
    }

    @Test
    void deleteDocumentShouldDeleteExistingDocument() {

        Long id = 1L;

        when(documentRepository.deleteByIdAndOwnerId(id, 1L)).thenReturn(true);

        boolean result = documentService.deleteDocument(id);

        assertTrue(result);
        verify(documentRepository).deleteByIdAndOwnerId(id, 1L);
    }

    @Test
    void deleteDocumentShouldReturnFalseWhenNotFound() {

        Long id = 999L;

        when(documentRepository.deleteByIdAndOwnerId(id, 1L)).thenReturn(false);

        boolean result = documentService.deleteDocument(id);

        assertFalse(result);

        verify(documentRepository).deleteByIdAndOwnerId(id, 1L);
    }

    @Test
    void addDocumentTypeShouldAssignTypeToDocument() {

        Long documentId = 1L;
        Long typeId = 2L;

        Document document = Document.create(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        );

        DocumentType documentType = DocumentType.create(
                "Invoice",
                "Invoice document"
        );

        when(documentRepository.findByIdAndOwnerId(documentId, 1L))
                .thenReturn(Optional.of(document));

        when(documentTypeRepository.findById(typeId))
                .thenReturn(Optional.of(documentType));

        documentService.addDocumentType(documentId, typeId);

        assertTrue(document.getDocumentTypeIds().contains(typeId));

        verify(documentRepository).save(document);
    }

    @Test
    void addDocumentTypeShouldThrowWhenDocumentNotFound() {

        Long documentId = 999L;
        Long typeId = 2L;

        when(documentRepository.findByIdAndOwnerId(documentId, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> documentService.addDocumentType(documentId, typeId)
        );

        verify(documentRepository, never())
                .save(any(Document.class));
    }

    @Test
    void addDocumentTypeShouldThrowWhenTypeNotFound() {

        Long documentId = 1L;
        Long typeId = 999L;

        Document document = Document.create(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        );

        when(documentRepository.findByIdAndOwnerId(documentId, 1L))
                .thenReturn(Optional.of(document));

        when(documentTypeRepository.findById(typeId))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> documentService.addDocumentType(documentId, typeId)
        );

        assertTrue(document.getDocumentTypeIds().isEmpty());

        verify(documentRepository, never())
                .save(any(Document.class));
    }

}

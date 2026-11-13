package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentType;

import org.junit.jupiter.api.Test;
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

    @InjectMocks
    private DefaultDocumentService documentService;

    @Test
    void createDocumentShouldSaveDocument() {

        // Arrange
        Document document = Document.create(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        );

        when(documentRepository.save(document))
                .thenReturn(document);

        // Act
        Document result = documentService.createDocument(document);

        // Assert
        assertSame(document, result);
        verify(documentRepository).save(document);
    }

    @Test
    void getAllDocumentsShouldReturnDocuments() {
        // Arrange
        Document doc1 = Document.create(
                "rechnung.pdf", "application/pdf", "Rechung"
        );

        Document doc2 = Document.create(
                "vertrag.pdf", "application/pdf", "Vertrag"
        );

        List<Document> documents = List.of(doc1, doc2);

        when(documentRepository.findAll()).thenReturn(documents);

        // Act
        List<Document> result = documentService.getAllDocuments();

        // Assert
        assertEquals(2, result.size());
        assertEquals(documents, result);
        verify(documentRepository).findAll();
    }

    @Test
    void getDocumentByIdShouldReturnDocument() {

        // Arrange
        Long id = 1L;
        Document document = Document.create(
                "rechung.pdf", "application/pdf", "Rechnung"
        );

        when(documentRepository.findById(id))
                .thenReturn(Optional.of(document));

        // Act
        Optional<Document> result = documentService.getDocumentById(id);

        // Assert
        assertTrue(result.isPresent());
        assertSame(document, result.get());
        verify(documentRepository).findById(id);
    }

    @Test
    void getDocumentByIdShouldReturnEmptyWhenNotFound() {

        // Arrange
        Long id = 999L;

        when(documentRepository.findById(id))
                .thenReturn(Optional.empty());

        // Act
        Optional<Document> result = documentService.getDocumentById(id);

        // Assert
        assertTrue(result.isEmpty());
        verify(documentRepository).findById(id);
    }

    @Test
    void updateDocumentShouldUpdateMetadata() {

        // Arrange
        Long id = 1L;

        Document existing = Document.create(
                "alt.pdf", "application/pdf", "Alte Beschreibung"
        );

        Document updated = Document.create(
                "neu.pdf", "application/pdf", "Neue Beschreibung"
        );

        when(documentRepository.findById(id))
                .thenReturn(Optional.of(existing));

        when(documentRepository.save(existing))
                .thenReturn(existing);

        // Act
        Optional<Document> result =
                documentService.updateDocument(id, updated);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("neu.pdf", result.get().getFilename());
        assertEquals("application/pdf", result.get().getContentType());
        assertEquals("Neue Beschreibung", result.get().getDescription());

        verify(documentRepository).findById(id);
        verify(documentRepository).save(existing);
    }

    @Test
    void updateDocumentShouldReturnEmptyWhenNotFound() {

        // Arrange
        Long id = 999L;

        Document updated = Document.create(
                "neu.pdf", "application/pdf", "Beschreibung"
        );

        when(documentRepository.findById(id))
                .thenReturn(Optional.empty());

        // Act
        Optional<Document> result =
                documentService.updateDocument(id, updated);

        // Assert
        assertTrue(result.isEmpty());

        verify(documentRepository).findById(id);
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void updateDocumentIgnoresIncomingIdentityTimeAndAssignments() {
        var createdAt = java.time.LocalDateTime.of(2026, 11, 13, 12, 0);
        Document existing = new Document(1L, "old.pdf", "application/pdf", "Old", createdAt, java.util.Set.of(2L));
        Document changes = new Document(99L, "new.pdf", "application/pdf", null,
                createdAt.plusDays(1), java.util.Set.of(3L));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(existing));
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

        // Arrange
        Long id = 1L;

        when(documentRepository.existsById(id)).thenReturn(true);

        // Act
        boolean result = documentService.deleteDocument(id);

        // Assert
        assertTrue(result);
        verify(documentRepository).existsById(id);
        verify(documentRepository).deleteById(id);
    }

    @Test
    void deleteDocumentShouldReturnFalseWhenNotFound() {

        // Arrange
        Long id = 999L;

        when(documentRepository.existsById(id)).thenReturn(false);

        // Act
        boolean result = documentService.deleteDocument(id);

        // Assert
        assertFalse(result);

        verify(documentRepository).existsById(id);
        verify(documentRepository, never()).deleteById(anyLong());
    }

    @Test
    void addDocumentTypeShouldAssignTypeToDocument() {

        // Arrange
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

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(document));

        when(documentTypeRepository.findById(typeId))
                .thenReturn(Optional.of(documentType));

        // Act
        documentService.addDocumentType(documentId, typeId);

        // Assert
        assertTrue(document.getDocumentTypeIds().contains(typeId));

        verify(documentRepository).save(document);
    }

    @Test
    void addDocumentTypeShouldThrowWhenDocumentNotFound() {

        // Arrange
        Long documentId = 999L;
        Long typeId = 2L;

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                NoSuchElementException.class,
                () -> documentService.addDocumentType(documentId, typeId)
        );

        verify(documentRepository, never())
                .save(any(Document.class));
    }

    @Test
    void addDocumentTypeShouldThrowWhenTypeNotFound() {

        // Arrange
        Long documentId = 1L;
        Long typeId = 999L;

        Document document = Document.create(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        );

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(document));

        when(documentTypeRepository.findById(typeId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                NoSuchElementException.class,
                () -> documentService.addDocumentType(documentId, typeId)
        );

        assertTrue(document.getDocumentTypeIds().isEmpty());

        verify(documentRepository, never())
                .save(any(Document.class));
    }

}

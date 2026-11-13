package at.fhtw.docflow.document.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTest {

    @Test
    void metadataChangesPreserveIdentityTimeAndAssignmentsAndCanClearDescription() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 13, 12, 0);
        Document document = new Document(1L, "original.pdf", "application/pdf", "Original", createdAt, Set.of(2L), null);

        document.updateMetadata("renamed.pdf", "application/pdf", null);

        assertEquals("renamed.pdf", document.getFilename());
        assertNull(document.getDescription());
        assertEquals(1L, document.getId());
        assertEquals(createdAt, document.getCreatedAt());
        assertEquals(Set.of(2L), document.getDocumentTypeIds());
    }

    @Test
    void invalidMetadataDoesNotPartiallyChangeTheDocument() {
        Document document = Document.create("original.pdf", "application/pdf", "Original");

        assertThrows(IllegalArgumentException.class,
                () -> document.updateMetadata("new.pdf", " ", null));
        assertThrows(IllegalArgumentException.class,
                () -> document.updateMetadata("new.pdf", "application/pdf", "x".repeat(256)));
        assertEquals("original.pdf", document.getFilename());
        assertEquals("Original", document.getDescription());
    }

    @Test
    void requiredMetadataIsValidatedWithoutSpring() {
        assertThrows(IllegalArgumentException.class, () -> Document.create(null, "application/pdf", null));
        assertThrows(IllegalArgumentException.class, () -> Document.create(" ", "application/pdf", null));
        assertThrows(IllegalArgumentException.class, () -> Document.create("x".repeat(256), "application/pdf", null));
        assertThrows(IllegalArgumentException.class, () -> Document.create("file.pdf", null, null));
    }

    @Test
    void assignmentsAreDefensivelyCopiedAndDuplicateAssignmentIsIdempotent() {
        Set<Long> source = new HashSet<>(Set.of(2L));
        Document document = new Document(1L, "file.pdf", "application/pdf", null, null, source, null);
        source.clear();

        document.assignDocumentType(2L);
        assertEquals(Set.of(2L), document.getDocumentTypeIds());
        assertThrows(UnsupportedOperationException.class, () -> document.getDocumentTypeIds().clear());
        assertThrows(IllegalArgumentException.class, () -> document.assignDocumentType(0L));
        assertThrows(IllegalArgumentException.class, () -> document.assignDocumentType(null));
    }
    @Test
    void ownerIsAssignedOnceAndPreservedByMetadataChanges() {
        Document document = Document.create("owned.pdf", "application/pdf", null);
        assertThrows(IllegalArgumentException.class, () -> document.assignOwner(null));
        document.assignOwner(1L);
        document.updateMetadata("renamed.pdf", "application/pdf", null);
        assertThrows(IllegalArgumentException.class, () -> document.assignOwner(2L));
        assertEquals(1L, document.getOwnerId());
    }
}

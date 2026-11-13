package at.fhtw.docflow.document.domain;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static at.fhtw.docflow.document.DocumentTestFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

class DocumentTest {
    @Test
    void metadataChangesPreserveOwnershipFileIdentityTimesAndAssignments() {
        Document document = document(1, 7);
        document.assignDocumentType(2L);
        var created = document.getCreatedAt();
        document.updateMetadata(new Document.Metadata(" Renamed ", null));
        assertEquals("Renamed", document.getTitle());
        assertNull(document.getDescription());
        assertEquals(1L, document.getId());
        assertEquals(7L, document.getOwnerId());
        assertEquals("original.pdf", document.getOriginalFilename());
        assertEquals("documents/1.pdf", document.getObjectKey());
        assertEquals(512, document.getSizeBytes());
        assertEquals(created, document.getCreatedAt());
        assertEquals(Set.of(2L), document.getDocumentTypeIds());
    }

    @Test
    void metadataValidationIsIndependentOfSpringAndDoesNotMutateExistingState() {
        Document document = document(1, 7);
        for (String title : new String[]{null, " ", "x".repeat(256)}) {
            assertThrows(Document.InvalidDocumentException.class, () -> new Document.Metadata(title, null));
        }
        assertThrows(Document.InvalidDocumentException.class, () -> new Document.Metadata("New", "x".repeat(256)));
        assertEquals("Original", document.getTitle());
        assertEquals("Original description", document.getDescription());
        assertEquals("x".repeat(255), new Document.Metadata("  " + "x".repeat(255) + "  ", null).title());
    }

    @Test
    void filenamesBecomeSafeBasenamesWithoutUsingThemAsStorageKeys() {
        assertEquals("invoice.pdf", Document.normalizeFilename("C:\\fakepath\\invoice.pdf"));
        assertEquals("invoice.pdf", Document.normalizeFilename("../../invoice.pdf"));
        assertEquals("invoice.pdf", Document.normalizeFilename("invo\r\nice.pdf"));
        for (String filename : new String[]{null, "", "folder/", "x".repeat(256)}) {
            assertThrows(Document.InvalidDocumentException.class, () -> Document.normalizeFilename(filename));
        }
    }

    @Test
    void assignmentsAreCopiedAndRepeatedAssignmentIsIdempotent() {
        var source = new HashSet<>(Set.of(2L));
        var document = new Document(1L, 1L, new Document.Metadata("File", null), "file.pdf",
                "documents/file.pdf", 1, null, null, source);
        source.clear();
        document.assignDocumentType(2L);
        assertEquals(Set.of(2L), document.getDocumentTypeIds());
        assertThrows(UnsupportedOperationException.class, () -> document.getDocumentTypeIds().clear());
        assertThrows(Document.InvalidDocumentException.class, () -> document.assignDocumentType(0L));
    }
}

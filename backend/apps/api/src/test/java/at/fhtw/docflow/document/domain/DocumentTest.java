package at.fhtw.docflow.document.domain;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import static at.fhtw.docflow.document.DocumentTestFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

class DocumentTest {
    @Test
    void metadataChangesPreserveOwnershipFileIdentityTimesAndAssignments() {
        Document document = document(1, 7);
        document.replaceCategories(List.of(new Category(2L, "Invoice", null)));
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
        assertEquals(List.of(new Category(2L, "Invoice", null)), document.getCategories());
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
    void assignmentsAreCopiedSortedAndCanBeReplacedOrCleared() {
        var source = new HashSet<>(Set.of(new Category(2L, "Invoice", null)));
        var document = new Document(1L, 1L, new Document.Metadata("File", null), "file.pdf",
                "documents/file.pdf", 1, null, null, source);
        source.clear();
        assertEquals(List.of(new Category(2L, "Invoice", null)), document.getCategories());
        assertThrows(UnsupportedOperationException.class, () -> document.getCategories().clear());
        document.replaceCategories(List.of(new Category(3L, "Report", null), new Category(1L, "Contract", null)));
        assertEquals(List.of("Contract", "Report"), document.getCategories().stream().map(Category::name).toList());
        document.replaceCategories(List.of());
        assertTrue(document.getCategories().isEmpty());
    }
}

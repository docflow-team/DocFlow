package at.fhtw.docflow;

import at.fhtw.docflow.document.application.DocumentService;
import at.fhtw.docflow.document.application.DocumentStorage;
import at.fhtw.docflow.document.application.CategoryService;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.Category;
import at.fhtw.docflow.identity.application.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.List;
import static at.fhtw.docflow.document.DocumentTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:docflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS api",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "docflow.jwt.secret=synthetic-test-key-at-least-thirty-two-bytes"
})
class BackendApplicationTests {
    @MockitoBean DocumentStorage storage;
    @MockitoBean CurrentUser currentUser;
    private Map<String, byte[]> objects;

    @BeforeEach
    void fixtures() {
        when(currentUser.id()).thenReturn(1L);
        objects = mockStorage(storage);
    }

    @Test
    void adaptersPreservePdfMetadataAssignmentsAndSharedTypesAcrossTransactions(
            @Autowired DocumentService documents, @Autowired CategoryService categories, @Autowired JdbcTemplate jdbc) {
        byte[] content = pdf(1);
        Document first = documents.upload(new Document.Metadata("Layering", "Original"), "layering.pdf", "application/pdf", content);
        Document second = documents.upload(new Document.Metadata("Newer", null), "newer.pdf", "application/pdf", content);
        assertTrue(first.getTags().isEmpty());
        documents.replaceTags(first.getId(), List.of(" Housing ", "housing", "Study"));
        documents.replaceTags(second.getId(), List.of("HOUSING"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM api.tags", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM api.document_tags", Integer.class));
        var labels = categories.findAll();
        assertEquals(7, labels.size());
        categories.seedDefaults();
        assertEquals(labels, categories.findAll());
        assertEquals(labels.stream().map(Category::name).sorted().toList(), labels.stream().map(Category::name).toList());
        Category type = labels.stream().filter(label -> label.name().equals("Invoice")).findFirst().orElseThrow();
        Category report = labels.stream().filter(label -> label.name().equals("Report")).findFirst().orElseThrow();
        assertNotNull(first.getId());
        assertNotNull(first.getCreatedAt());
        assertNotNull(first.getUpdatedAt());
        jdbc.update("INSERT INTO api.documents(owner_id, filename, content_type, created_at) VALUES (1, 'legacy.pdf', 'application/pdf', CURRENT_TIMESTAMP)");
        Long legacyId = jdbc.queryForObject("SELECT id FROM api.documents WHERE filename='legacy.pdf'", Long.class);
        assertThrows(NoSuchElementException.class, () -> documents.getDocumentById(legacyId));

        documents.replaceCategories(first.getId(), List.of(type.id(), report.id()));
        documents.replaceCategories(first.getId(), List.of(type.id(), report.id()));
        Document updated = documents.updateMetadata(first.getId(), new Document.Metadata("Renamed", null));
        assertEquals(first.getId(), updated.getId());
        assertEquals(first.getCreatedAt(), updated.getCreatedAt());
        assertFalse(updated.getUpdatedAt().isBefore(first.getUpdatedAt()));
        assertEquals(first.getObjectKey(), updated.getObjectKey());
        assertEquals("layering.pdf", updated.getOriginalFilename());
        assertEquals(content.length, updated.getSizeBytes());
        assertNull(updated.getDescription());
        assertEquals(List.of("housing", "study"), updated.getTags());
        assertThrows(Document.InvalidDocumentException.class, () -> documents.replaceTags(first.getId(), List.of("valid", " ")));
        assertEquals(List.of("housing", "study"), documents.getDocumentById(first.getId()).getTags());
        assertEquals(List.of(type, report), documents.getDocumentById(first.getId()).getCategories());
        assertThrows(Document.InvalidDocumentException.class, () -> documents.replaceCategories(first.getId(), List.of(type.id(), 99999L)));
        assertEquals(List.of(type, report), documents.getDocumentById(first.getId()).getCategories());
        documents.replaceCategories(first.getId(), List.of(report.id()));
        assertEquals(List.of(report), documents.getDocumentById(first.getId()).getCategories());
        documents.replaceCategories(first.getId(), List.of());
        assertTrue(documents.getDocumentById(first.getId()).getCategories().isEmpty());
        documents.replaceCategories(first.getId(), List.of(type.id()));
        assertEquals(List.of("housing", "study"), documents.getDocumentById(first.getId()).getTags());
        documents.replaceTags(first.getId(), List.of("study"));
        assertEquals(List.of("housing"), documents.getDocumentById(second.getId()).getTags());
        documents.replaceTags(first.getId(), List.of());
        assertTrue(documents.getDocumentById(first.getId()).getTags().isEmpty());
        documents.replaceTags(first.getId(), List.of("housing", "study"));
        assertArrayEquals(content, documents.download(first.getId()).content());
        var page = documents.getAllDocuments(0, 1);
        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(second.getId(), page.items().getFirst().getId());
        assertEquals(first.getId(), documents.getAllDocuments(1, 1).items().getFirst().getId());

        documents.deleteDocument(first.getId());
        assertThrows(NoSuchElementException.class, () -> documents.deleteDocument(first.getId()));
        assertThrows(NoSuchElementException.class, () -> documents.getDocumentById(first.getId()));
        assertEquals(labels, categories.findAll());
        assertEquals(List.of("housing"), documents.getDocumentById(second.getId()).getTags());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM api.tags", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM api.document_tags", Integer.class));
        documents.deleteDocument(second.getId());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM api.document_tags", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM api.tags", Integer.class));
        assertTrue(objects.isEmpty());
        assertEquals(0, documents.getAllDocuments(0, 20).totalElements());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM api.documents", Integer.class));
    }
}

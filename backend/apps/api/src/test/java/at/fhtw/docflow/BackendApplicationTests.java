package at.fhtw.docflow;

import at.fhtw.docflow.document.application.DocumentService;
import at.fhtw.docflow.document.application.DocumentTypeService;
import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.DocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:docflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS api",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
        "docflow.jwt.secret=synthetic-test-key-at-least-thirty-two-bytes"
})
class BackendApplicationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    at.fhtw.docflow.identity.application.CurrentUser currentUser;

    @org.junit.jupiter.api.BeforeEach
    void account() { org.mockito.Mockito.when(currentUser.id()).thenReturn(1L); }


	@Test
	void contextLoads() {
	}

	@Test
	void adaptersPreserveAssignmentsAndSharedTypesAcrossTransactions(
			@Autowired DocumentService documents, @Autowired DocumentTypeService types) {
		Document document = documents.createDocument(Document.create("layering.pdf", "application/pdf", "Original"));
		DocumentType type = types.create(DocumentType.create("Layering test", null));
		assertNotNull(document.getId());
		assertNotNull(document.getCreatedAt());
		assertNotNull(type.id());

		documents.addDocumentType(document.getId(), type.id());
		documents.addDocumentType(document.getId(), type.id());
		Document updated = documents.updateDocument(document.getId(),
				Document.create("renamed.pdf", "application/pdf", null)).orElseThrow();

		assertEquals(document.getId(), updated.getId());
		assertEquals(document.getCreatedAt(), updated.getCreatedAt());
		assertNull(updated.getDescription());
		assertEquals(Set.of(type.id()), documents.getDocumentById(document.getId()).orElseThrow().getDocumentTypeIds());
		assertEquals(1, documents.getAllDocuments().size());
		assertTrue(documents.deleteDocument(document.getId()));
		assertTrue(documents.getDocumentById(document.getId()).isEmpty());
		assertEquals(type, types.findAll().getFirst());
	}

}

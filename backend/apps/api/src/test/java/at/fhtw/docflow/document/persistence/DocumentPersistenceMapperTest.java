package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.domain.Document;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import at.fhtw.docflow.document.domain.Category;
import static at.fhtw.docflow.document.DocumentTestFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

class DocumentPersistenceMapperTest {
    private final DocumentPersistenceMapper mapper = Mappers.getMapper(DocumentPersistenceMapper.class);

    @Test
    void mapsSharedCategoriesAndAllPdfMetadataToADetachedModel() {
        DocumentEntity entity = entity();
        var type = new CategoryEntity();
        ReflectionTestUtils.setField(type, "id", 2L);
        type.setName("Invoice");
        type.setDescription("Amount requested");
        entity.getCategories().add(type);
        entity.getTags().add(new TagEntity("housing"));
        Document document = mapper.toDomain(entity);
        entity.getCategories().clear();
        entity.getTags().clear();
        assertEquals(List.of("housing"), document.getTags());
        assertEquals(List.of(new Category(2L, "Invoice", "Amount requested")), document.getCategories());
        assertEquals("Original", document.getTitle());
        assertEquals("original.pdf", document.getOriginalFilename());
        assertEquals(512, document.getSizeBytes());
        assertEquals("documents/1.pdf", document.getObjectKey());
    }

    @Test
    void newEntityMappingProtectsGeneratedIdentityTimesAndRelationships() {
        Document incoming = document(99, 7);
        incoming.replaceTags(List.of("housing"));
        incoming.replaceCategories(List.of(new Category(2L, "Invoice", null)));
        DocumentEntity entity = mapper.toNewEntity(incoming);
        assertNull(entity.getId());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
        assertTrue(entity.getCategories().isEmpty());
        assertTrue(entity.getTags().isEmpty());
        assertEquals(7L, entity.getOwnerId());
        assertEquals("documents/99.pdf", entity.getObjectKey());
    }

    @Test
    void metadataMappingPreservesFileOwnershipTimesAndAssignments() {
        DocumentEntity entity = entity();
        Instant created = Instant.parse("2026-11-13T12:00:00Z");
        ReflectionTestUtils.setField(entity, "id", 1L);
        ReflectionTestUtils.setField(entity, "createdAt", created);
        ReflectionTestUtils.setField(entity, "updatedAt", created);
        var type = new CategoryEntity();
        entity.getCategories().add(type);
        var tag = new TagEntity("housing");
        entity.getTags().add(tag);
        Document changes = document(99, 8);
        changes.replaceTags(List.of("forged"));
        changes.updateMetadata(new Document.Metadata("Renamed", null));
        changes.replaceCategories(List.of(new Category(3L, "Report", null)));
        mapper.updateMetadata(changes, entity);
        assertEquals(1L, entity.getId());
        assertEquals(7L, entity.getOwnerId());
        assertEquals("documents/1.pdf", entity.getObjectKey());
        assertEquals("original.pdf", entity.getOriginalFilename());
        assertEquals(512L, entity.getSizeBytes());
        assertEquals(created, entity.getCreatedAt());
        assertEquals(created, entity.getUpdatedAt());
        assertEquals(Set.of(type), entity.getCategories());
        assertEquals(Set.of(tag), entity.getTags());
        assertEquals("Renamed", entity.getTitle());
        assertNull(entity.getDescription());
    }

    private DocumentEntity entity() {
        return mapper.toNewEntity(document(1, 7));
    }
}

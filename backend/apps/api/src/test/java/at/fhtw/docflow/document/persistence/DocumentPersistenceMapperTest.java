package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.domain.Document;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.Set;
import static at.fhtw.docflow.document.DocumentTestFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

class DocumentPersistenceMapperTest {
    private final DocumentPersistenceMapper mapper = Mappers.getMapper(DocumentPersistenceMapper.class);

    @Test
    void mapsSharedTypesAndAllPdfMetadataToADetachedModel() {
        DocumentEntity entity = entity();
        var type = new DocumentTypeEntity();
        ReflectionTestUtils.setField(type, "id", 2L);
        entity.getDocumentTypes().add(type);
        Document document = mapper.toDomain(entity);
        entity.getDocumentTypes().clear();
        assertEquals(Set.of(2L), document.getDocumentTypeIds());
        assertEquals("Original", document.getTitle());
        assertEquals("original.pdf", document.getOriginalFilename());
        assertEquals(512, document.getSizeBytes());
        assertEquals("documents/1.pdf", document.getObjectKey());
    }

    @Test
    void newEntityMappingProtectsGeneratedIdentityTimesAndRelationships() {
        Document incoming = document(99, 7);
        incoming.assignDocumentType(2L);
        DocumentEntity entity = mapper.toNewEntity(incoming);
        assertNull(entity.getId());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
        assertTrue(entity.getDocumentTypes().isEmpty());
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
        var type = new DocumentTypeEntity();
        entity.getDocumentTypes().add(type);
        Document changes = document(99, 8);
        changes.updateMetadata(new Document.Metadata("Renamed", null));
        changes.assignDocumentType(3L);
        mapper.updateMetadata(changes, entity);
        assertEquals(1L, entity.getId());
        assertEquals(7L, entity.getOwnerId());
        assertEquals("documents/1.pdf", entity.getObjectKey());
        assertEquals("original.pdf", entity.getOriginalFilename());
        assertEquals(512L, entity.getSizeBytes());
        assertEquals(created, entity.getCreatedAt());
        assertEquals(created, entity.getUpdatedAt());
        assertEquals(Set.of(type), entity.getDocumentTypes());
        assertEquals("Renamed", entity.getTitle());
        assertNull(entity.getDescription());
    }

    private DocumentEntity entity() {
        return mapper.toNewEntity(document(1, 7));
    }
}

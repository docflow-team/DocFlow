package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.domain.Document;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentPersistenceMapperTest {

    private final DocumentPersistenceMapper mapper = Mappers.getMapper(DocumentPersistenceMapper.class);

    @Test
    void mapsSharedTypeEntitiesToDetachedIdentifiers() {
        DocumentEntity entity = entity();
        DocumentTypeEntity type = new DocumentTypeEntity();
        ReflectionTestUtils.setField(type, "id", 2L);
        entity.getDocumentTypes().add(type);

        Document document = mapper.toDomain(entity);
        entity.getDocumentTypes().clear();

        assertEquals(Set.of(2L), document.getDocumentTypeIds());
        assertNull(document.getDescription());
    }

    @Test
    void newEntityMappingDoesNotCopyCallerIdentityTimeOrRelationships() {
        Document document = new Document(99L, "file.pdf", "application/pdf", null,
                LocalDateTime.of(2026, 11, 13, 12, 0), Set.of(2L), null);

        DocumentEntity entity = mapper.toNewEntity(document);

        assertNull(entity.getId());
        assertNull(entity.getCreatedAt());
        assertTrue(entity.getDocumentTypes().isEmpty());
    }

    @Test
    void metadataMappingPreservesProtectedFieldsAndClearsNullableDescription() {
        DocumentEntity entity = entity();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 13, 12, 0);
        ReflectionTestUtils.setField(entity, "id", 1L);
        ReflectionTestUtils.setField(entity, "createdAt", createdAt);
        entity.setDescription("Original");
        entity.setOwnerId(7L);
        DocumentTypeEntity type = new DocumentTypeEntity();
        entity.getDocumentTypes().add(type);
        Document changes = new Document(99L, "new.pdf", "application/pdf", null,
                createdAt.plusDays(1), Set.of(3L), null);

        mapper.updateMetadata(changes, entity);

        assertEquals(1L, entity.getId());
        assertEquals(7L, entity.getOwnerId());
        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals(Set.of(type), entity.getDocumentTypes());
        assertEquals("new.pdf", entity.getFilename());
        assertNull(entity.getDescription());
    }

    private DocumentEntity entity() {
        DocumentEntity entity = new DocumentEntity();
        entity.setFilename("file.pdf");
        entity.setContentType("application/pdf");
        return entity;
    }
}

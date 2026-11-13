package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.domain.Document;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

class DocumentMapperTest {

    private final DocumentMapper mapper = Mappers.getMapper(DocumentMapper.class);

    @Test
    void requestMappingLeavesServerControlledFieldsUnsetAndAllowsNullDescription() {
        Document document = mapper.toDomain(new DocumentRequest("file.pdf", "application/pdf", null));

        assertNull(document.getId());
        assertNull(document.getCreatedAt());
        assertTrue(document.getDocumentTypeIds().isEmpty());
        assertNull(document.getDescription());
    }

    @Test
    void absentSourcesDoNotCreateEmptyModels() {
        assertNull(mapper.toDomain(null));
        assertNull(mapper.toResponse(null));
    }
}


package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.DocumentType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentTypeServiceTest {

    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @InjectMocks
    private DefaultDocumentTypeService documentTypeService;

    @Test
    void createDocumentTypeShouldSaveDocumentType() {

        DocumentType documentType =
                DocumentType.create("Invoice", "Invoice document");

        DocumentType saved = new DocumentType(1L, "Invoice", "Invoice document");

        when(documentTypeRepository.save(documentType))
                .thenReturn(saved);

        DocumentType result =
                documentTypeService.create(documentType);

        assertEquals("Invoice", result.name());
        assertEquals("Invoice document", result.description());
        assertEquals(1L, result.id());

        verify(documentTypeRepository).save(documentType);
    }

    @Test
    void getAllDocumentTypesShouldReturnDocumentTypes() {

        DocumentType type1 =
                new DocumentType(1L, "Invoice", "Invoice document");

        DocumentType type2 =
                new DocumentType(2L, "Contract", "Contract document");

        when(documentTypeRepository.findAll())
                .thenReturn(List.of(type1, type2));

        List<DocumentType> result =
                documentTypeService.findAll();

        assertEquals(2, result.size());
        assertEquals("Invoice", result.get(0).name());
        assertEquals("Contract", result.get(1).name());

        verify(documentTypeRepository).findAll();
    }
}

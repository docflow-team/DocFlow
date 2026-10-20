package com.docflow.backend.controller;

import com.docflow.backend.entity.Document;
import com.docflow.backend.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentControllerTest {

    private DocumentService documentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);

        DocumentController controller =
                new DocumentController(documentService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void getDocumentByIdShouldReturn404WhenNotFound() throws Exception {

        // Arrange
        when(documentService.getDocumentById(999L))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/documents/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(404));

        verify(documentService).getDocumentById(999L);
    }


    @Test
    void getDocumentByIdShouldReturn200WhenFound() throws Exception {

        // Arrange
        Document document = new Document(
                "rechnung.pdf",
                "application/pdf",
                "Stromrechnung"
        );

        when(documentService.getDocumentById(1L))
                .thenReturn(Optional.of(document));

        // Act & Assert
        mockMvc.perform(get("/api/documents/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.filename").value("rechnung.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.description").value("Stromrechnung"));

        verify(documentService).getDocumentById(1L);
    }

    @Test
    void getAllDocumentsShouldReturn200() throws Exception {

        // Arrange
        Document doc1 = new Document(
                "rechnung.pdf", "application/pdf", "Rechnung"
        );

        Document doc2 = new Document(
                "vertrag.pdf", "application/pdf", "Vertrag"
        );

        when(documentService.getAllDocuments())
                .thenReturn(List.of(doc1, doc2));

        // Act & Assert
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].filename").value("rechnung.pdf"))
                .andExpect(jsonPath("$[1].filename").value("vertrag.pdf"));

        verify(documentService).getAllDocuments();
    }

    @Test
    void createDocumentShouldReturn201() throws Exception {

        // Arrange
        Document document = new Document(
                "rechnung.pdf", "application/pdf", "Rechnung"
        );

        when(documentService.createDocument(any(Document.class)))
                .thenReturn(document);

        String json = """
            {
                "filename": "rechnung.pdf",
                "contentType": "application/pdf",
                "description": "Rechnung"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/documents")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.filename").value("rechnung.pdf"))
                .andExpect(jsonPath("$.description").value("Rechnung"));

        verify(documentService).createDocument(any(Document.class));
    }

    @Test
    void updateDocumentShouldReturn200() throws Exception {

        // Arrange
        Long id = 1L;

        Document updated = new Document(
                "neu.pdf", "application/pdf", "Neue Beschreibung"
        );

        when(documentService.updateDocument(
                eq(id), any(Document.class)))
                .thenReturn(Optional.of(updated));

        String json = """
            {
                "filename": "neu.pdf",
                "contentType": "application/pdf",
                "description": "Neue Beschreibung"
            }
            """;

        // Act & Assert
        mockMvc.perform(put("/api/documents/{id}", id)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.filename").value("neu.pdf"))
                .andExpect(jsonPath("$.description").value("Neue Beschreibung"));

        verify(documentService).updateDocument(
                eq(id), any(Document.class));
    }

    @Test
    void updateDocumentShouldReturn404WhenNotFound() throws Exception {

        // Arrange
        Long id = 999L;

        when(documentService.updateDocument(
                eq(id), any(Document.class)))
                .thenReturn(Optional.empty());

        String json = """
            {
                "filename": "neu.pdf",
                "contentType": "application/pdf",
                "description": "Neue Beschreibung"
            }
            """;

        // Act & Assert
        mockMvc.perform(put("/api/documents/{id}", id)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(404));

        verify(documentService).updateDocument(
                eq(id), any(Document.class));
    }

    @Test
    void deleteDocumentShouldReturn204() throws Exception {

        // Arrange
        Long id = 1L;

        when(documentService.deleteDocument(id))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(documentService).deleteDocument(id);
    }


    @Test
    void deleteDocumentShouldReturn404WhenNotFound() throws Exception {

        // Arrange
        Long id = 999L;

        when(documentService.deleteDocument(id))
                .thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(404));

        verify(documentService).deleteDocument(id);
    }


    @Test
    void createDocumentShouldReturn400WhenFilenameIsBlank() throws Exception {

        // Arrange
        String json = """
            {
                "filename": "",
                "contentType": "application/pdf",
                "description": "Stromrechnung"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/documents")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(400));

        verify(documentService, never())
                .createDocument(any(Document.class));
    }



    @Test
    void addDocumentTypeShouldReturn200() throws Exception {

        // Arrange
        Long documentId = 1L;
        Long typeId = 2L;

        // Act & Assert
        mockMvc.perform(post(
                        "/api/documents/{documentId}/types/{typeId}",
                        documentId,
                        typeId
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.typeId").value(typeId));

        verify(documentService)
                .addDocumentType(documentId, typeId);
    }


    @Test
    void addDocumentTypeShouldReturnJson404WhenResourceIsMissing() throws Exception {
        doThrow(new java.util.NoSuchElementException())
                .when(documentService).addDocumentType(1L, 999L);

        mockMvc.perform(post("/api/documents/1/types/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void malformedJsonShouldReturnJson400() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void nonJsonRequestBodyShouldReturnJson415() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType("text/plain")
                        .content("document"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void unexpectedFailureShouldReturnJsonWithoutInternalDetails() throws Exception {
        when(documentService.getAllDocuments())
                .thenThrow(new IllegalStateException("Private database details"));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}

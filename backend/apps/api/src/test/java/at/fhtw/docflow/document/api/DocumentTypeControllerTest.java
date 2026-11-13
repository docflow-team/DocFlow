
package at.fhtw.docflow.document.api;

import at.fhtw.docflow.api.ApiExceptionHandler;

import at.fhtw.docflow.document.domain.DocumentType;
import at.fhtw.docflow.document.application.DocumentTypeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentTypeControllerTest {

    private DocumentTypeService documentTypeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        documentTypeService = mock(DocumentTypeService.class);

        DocumentTypeController controller =
                new DocumentTypeController(documentTypeService, org.mapstruct.factory.Mappers.getMapper(DocumentTypeMapper.class));

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createDocumentTypeShouldReturn201() throws Exception {

        DocumentType response =
                new DocumentType(
                        1L,
                        "Invoice",
                        "Invoice document"
                );

        when(documentTypeService.create(any(DocumentType.class)))
                .thenReturn(response);

        String json = """
                {
                    "name": "Invoice",
                    "description": "Invoice document"
                }
                """;

        mockMvc.perform(post("/api/document-types")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Invoice"))
                .andExpect(jsonPath("$.description").value("Invoice document"));

        verify(documentTypeService)
                .create(any(DocumentType.class));
    }

    @Test
    void getAllDocumentTypesShouldReturn200() throws Exception {

        DocumentType type1 =
                new DocumentType(
                        1L, "Invoice", "Invoice document"
                );

        DocumentType type2 =
                new DocumentType(
                        2L, "Contract", "Contract document"
                );

        when(documentTypeService.findAll())
                .thenReturn(List.of(type1, type2));

        mockMvc.perform(get("/api/document-types"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Invoice"))
                .andExpect(jsonPath("$[1].name").value("Contract"));

        verify(documentTypeService).findAll();
    }
}

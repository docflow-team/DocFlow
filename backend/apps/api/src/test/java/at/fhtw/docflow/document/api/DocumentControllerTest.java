package at.fhtw.docflow.document.api;

import at.fhtw.docflow.api.ApiExceptionHandler;
import at.fhtw.docflow.document.application.DocumentService;
import at.fhtw.docflow.document.application.DocumentStorage;
import at.fhtw.docflow.document.domain.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import static at.fhtw.docflow.document.DocumentTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentControllerTest {
    private final DocumentMapper mapper = Mappers.getMapper(DocumentMapper.class);
    private final DocumentService documents = mock(DocumentService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new DocumentController(documents, mapper))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void uploadReturns202WithTheDocumentAndInactiveProcessingLocation() throws Exception {
        when(documents.upload(any(), anyString(), anyString(), any())).thenReturn(document(1, 7));
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "original.pdf", "application/pdf", pdf(1)))
                        .param("title", " Invoice "))
                .andExpect(status().isAccepted()).andExpect(header().string("Location", "/api/v1/documents/1/processing"))
                .andExpect(jsonPath("$.document.id").value(1))
                .andExpect(jsonPath("$.document.objectKey").doesNotExist())
                .andExpect(jsonPath("$.document.ownerId").doesNotExist())
                .andExpect(jsonPath("$.processingUrl").value("/api/v1/documents/1/processing"))
                .andExpect(jsonPath("$.processing.processingAvailable").value(false))
                .andExpect(jsonPath("$.processing.ocr").value("NOT_STARTED"));
        verify(documents).upload(eq(new Document.Metadata("Invoice", null)), eq("original.pdf"), eq("application/pdf"), any());
    }

    @Test
    void requiredMultipartFieldsAndJsonCreationAreRejected() throws Exception {
        mvc.perform(multipart("/api/v1/documents").param("title", "Invoice"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.file").exists());
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "original.pdf", "application/pdf", pdf(1))).param("title", " "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").exists());
        mvc.perform(post("/api/v1/documents").contentType("application/json").content("{}"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(content().contentType("application/json"));
        verifyNoInteractions(documents);
    }

    @Test
    void listsUseThePageEnvelopeAndSafeDocumentMapping() throws Exception {
        when(documents.getAllDocuments(0, 20))
                .thenReturn(new DocumentService.DocumentPage(List.of(document(1, 7)), 0, 20, 1, 1));
        mvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("Original"))
                .andExpect(jsonPath("$.items[0].objectKey").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void metadataMappingAcceptsOnlyEditableFieldsAndCanClearDescription() throws Exception {
        var metadata = mapper.toMetadata(new DocumentController.MetadataRequest(" Renamed ", null));
        assertEquals(new Document.Metadata("Renamed", null), metadata);
        when(documents.updateMetadata(1L, metadata)).thenReturn(document(1, 7));
        mvc.perform(put("/api/v1/documents/1/metadata").contentType("application/json")
                        .content("{\"title\":\" Renamed \" ,\"description\":null,\"ownerId\":99,\"objectKey\":\"forged\"}"))
                .andExpect(status().isOk());
        verify(documents).updateMetadata(1L, metadata);
    }

    @Test
    void metadataValidationAndMalformedJsonReturnSafe400s() throws Exception {
        mvc.perform(put("/api/v1/documents/1/metadata").contentType("application/json").content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").exists());
        mvc.perform(put("/api/v1/documents/1/metadata").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(content().contentType("application/json"));
        verifyNoInteractions(documents);
    }

    @Test
    void downloadsArePdfAttachmentsAndPrivateResponsesAreNotCached() throws Exception {
        byte[] content = pdf(1);
        when(documents.download(1L)).thenReturn(new DocumentService.Download("original.pdf", content));
        mvc.perform(get("/api/v1/documents/1/content").accept("application/pdf"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(content().bytes(content));
    }

    @Test
    void processingChecksOwnershipAndReportsNoBackgroundWork() throws Exception {
        when(documents.getDocumentById(1L)).thenReturn(document(1, 7));
        mvc.perform(get("/api/v1/documents/1/processing"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processingAvailable").value(false))
                .andExpect(jsonPath("$.ocr").value("NOT_STARTED"))
                .andExpect(jsonPath("$.indexing").value("NOT_STARTED"))
                .andExpect(jsonPath("$.summary").value("NOT_STARTED"))
                .andExpect(jsonPath("$.classification").value("NOT_STARTED"));
    }

    @Test
    void deletionIsEmptyAndMissingResourcesAreSafeJson404s() throws Exception {
        mvc.perform(delete("/api/v1/documents/1")).andExpect(status().isNoContent()).andExpect(content().string(""));
        when(documents.getDocumentById(9L)).thenThrow(new NoSuchElementException());
        when(documents.download(9L)).thenThrow(new NoSuchElementException());
        mvc.perform(get("/api/v1/documents/9")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/v1/documents/9/content").accept("application/pdf"))
                .andExpect(status().isNotFound()).andExpect(content().contentType("application/json"));
    }

    @Test
    void documentAndStorageFailuresUseTheAgreedHttpStatuses() throws Exception {
        for (var reason : Document.InvalidDocumentException.Reason.values()) {
            doThrow(new Document.InvalidDocumentException(reason, "Invalid document"))
                    .when(documents).upload(any(), anyString(), anyString(), any());
            int expected = switch (reason) { case INVALID -> 400; case TOO_LARGE -> 413; case UNSUPPORTED_TYPE -> 415; };
            mvc.perform(multipart("/api/v1/documents").file(new MockMultipartFile("file", "file.pdf", "application/pdf", pdf(1)))
                            .param("title", "File"))
                    .andExpect(status().is(expected)).andExpect(content().contentType("application/json"));
        }
        when(documents.download(1L)).thenThrow(new DocumentStorage.StorageException(new IOException("Private storage details")));
        mvc.perform(get("/api/v1/documents/1/content"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value("Document storage is unavailable"));
    }

    @Test
    void unexpectedErrorsDoNotExposeInternalDetailsAndLegacyRoutesAreGone() throws Exception {
        when(documents.getDocumentById(1L)).thenThrow(new IllegalStateException("Private database details"));
        mvc.perform(get("/api/v1/documents/1"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        mvc.perform(get("/api/documents")).andExpect(status().isNotFound());
    }
}

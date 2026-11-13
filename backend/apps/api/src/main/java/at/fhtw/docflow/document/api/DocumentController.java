package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.application.DocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import at.fhtw.docflow.document.api.CategoryController.CategoryResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Profile("!bootstrap-admin")
@RestController
@RequestMapping(value = "/api/v1/documents", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentController {
    private final DocumentService documents;
    private final DocumentMapper mapper;

    public DocumentController(DocumentService documents, DocumentMapper mapper) {
        this.documents = documents;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@Valid @ModelAttribute UploadRequest request) throws IOException {
        var file = request.file();
        var document = documents.upload(mapper.toMetadata(request), file.getOriginalFilename(),
                file.getContentType(), file.getBytes());
        String processingUrl = "/api/v1/documents/" + document.getId() + "/processing";
        return ResponseEntity.accepted().location(URI.create(processingUrl))
                .body(new UploadResponse(mapper.toResponse(document), processingUrl, notStarted(document.getId())));
    }

    @GetMapping
    public DocumentPageResponse getAllDocuments(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return mapper.toResponse(documents.getAllDocuments(page, size));
    }

    @GetMapping("/{id}")
    public DocumentResponse getDocumentById(@PathVariable Long id) {
        return mapper.toResponse(documents.getDocumentById(id));
    }

    @PutMapping(value = "/{id}/metadata", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DocumentResponse updateMetadata(@PathVariable Long id, @Valid @RequestBody MetadataRequest request) {
        return mapper.toResponse(documents.updateMetadata(id, mapper.toMetadata(request)));
    }

    @GetMapping(value = "/{id}/content", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        var content = documents.download(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(content.content());
    }

    @GetMapping("/{id}/processing")
    public ProcessingResponse processing(@PathVariable Long id) {
        return notStarted(documents.getDocumentById(id).getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documents.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{id}/categories", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DocumentResponse replaceCategories(@PathVariable Long id, @Valid @RequestBody CategoriesRequest request) {
        return mapper.toResponse(documents.replaceCategories(id, request.categoryIds()));
    }

    @PutMapping(value = "/{id}/tags", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DocumentResponse replaceTags(@PathVariable Long id, @Valid @RequestBody TagsRequest request) {
        return mapper.toResponse(documents.replaceTags(id, request.tags()));
    }

    private static ProcessingResponse notStarted(Long id) {
        return new ProcessingResponse(id, false, "NOT_STARTED", "NOT_STARTED", "NOT_STARTED", "NOT_STARTED");
    }

    public record UploadRequest(@NotNull MultipartFile file, @NotBlank String title,
                                @Size(max = 255) String description) { }
    public record MetadataRequest(@NotBlank String title, @Size(max = 255) String description) { }
    public record DocumentResponse(Long id, String title, String description, String originalFilename,
                                   String contentType, long sizeBytes, Instant createdAt, Instant updatedAt, List<CategoryResponse> categories, List<String> tags) { }
    public record UploadResponse(DocumentResponse document, String processingUrl, ProcessingResponse processing) { }
    public record ProcessingResponse(Long documentId, boolean processingAvailable, String ocr, String indexing,
                                     String summary, String classification) { }
    public record DocumentPageResponse(List<DocumentResponse> items, int page, int size,
                                       long totalElements, int totalPages) { }
    public record TagsRequest(@NotNull List<@NotNull String> tags) { }
    public record CategoriesRequest(@NotNull List<@NotNull @Positive Long> categoryIds) { }
}

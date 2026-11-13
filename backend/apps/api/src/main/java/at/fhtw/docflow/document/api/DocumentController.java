package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.application.DocumentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping(value = "/api/documents", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentMapper mapper;

    public DocumentController(DocumentService documentService, DocumentMapper mapper) {
        this.documentService = documentService;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createDocument(@Valid @RequestBody DocumentRequest request) {
        Document document = mapper.toDomain(request);

        Document savedDocument = documentService.createDocument(document);

        return mapper.toResponse(savedDocument);
    }

    @GetMapping
    public List<DocumentResponse> getAllDocuments() {
        return documentService.getAllDocuments()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable Long id) {
        return documentService.getDocumentById(id)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequest request) {

        Document updatedDocument = mapper.toDomain(request);

        return documentService.updateDocument(id, updatedDocument)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        if (documentService.deleteDocument(id)) {
            return ResponseEntity.noContent().build();
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
    }

    @PostMapping("/{documentId}/types/{typeId}")
    public DocumentTypeAssignmentResponse addDocumentType(
            @PathVariable Long documentId,
            @PathVariable Long typeId) {

        documentService.addDocumentType(documentId, typeId);
        return new DocumentTypeAssignmentResponse(documentId, typeId);
    }

    public record DocumentRequest(@NotBlank @Size(max = 255) String filename,
                                  @NotBlank @Size(max = 255) String contentType,
                                  @Size(max = 255) String description) { }

    public record DocumentResponse(Long id, String filename, String contentType,
                                   String description, LocalDateTime createdAt) { }

    public record DocumentTypeAssignmentResponse(Long documentId, Long typeId) { }
}

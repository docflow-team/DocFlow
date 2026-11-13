package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.api.DocumentRequest;
import at.fhtw.docflow.document.api.DocumentTypeAssignmentResponse;
import at.fhtw.docflow.document.api.DocumentResponse;
import at.fhtw.docflow.document.persistence.Document;
import at.fhtw.docflow.document.api.DocumentMapper;
import at.fhtw.docflow.document.application.DocumentService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping(value = "/api/documents", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createDocument(@Valid @RequestBody DocumentRequest request) {
        Document document = DocumentMapper.toEntity(request);

        Document savedDocument = documentService.createDocument(document);

        return DocumentMapper.toResponse(savedDocument);
    }

    @GetMapping
    public List<DocumentResponse> getAllDocuments() {
        return documentService.getAllDocuments()
                .stream()
                .map(DocumentMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable Long id) {
        return documentService.getDocumentById(id)
                .map(DocumentMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequest request) {

        Document updatedDocument = DocumentMapper.toEntity(request);

        return documentService.updateDocument(id, updatedDocument)
                .map(DocumentMapper::toResponse)
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
}

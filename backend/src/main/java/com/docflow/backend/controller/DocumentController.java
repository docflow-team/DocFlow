package com.docflow.backend.controller;

import com.docflow.backend.dto.DocumentRequest;
import com.docflow.backend.dto.DocumentTypeAssignmentResponse;
import com.docflow.backend.dto.DocumentResponse;
import com.docflow.backend.entity.Document;
import com.docflow.backend.mapper.DocumentMapper;
import com.docflow.backend.service.DocumentService;

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

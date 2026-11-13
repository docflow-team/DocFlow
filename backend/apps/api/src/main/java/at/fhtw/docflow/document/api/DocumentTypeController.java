package at.fhtw.docflow.document.api;


import at.fhtw.docflow.document.api.DocumentTypeRequest;
import at.fhtw.docflow.document.api.DocumentTypeResponse;
import at.fhtw.docflow.document.persistence.DocumentType;
import at.fhtw.docflow.document.application.DocumentTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/document-types", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentTypeController {

    private final DocumentTypeService service;

    public DocumentTypeController(DocumentTypeService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTypeResponse create(
            @RequestBody DocumentTypeRequest request) {

        return service.create(request);
    }

    @GetMapping
    public List<DocumentTypeResponse> findAll() {
        return service.findAll();
    }
}

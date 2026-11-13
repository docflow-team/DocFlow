package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.application.DocumentTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/document-types", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentTypeController {

    private final DocumentTypeService service;
    private final DocumentTypeMapper mapper;

    public DocumentTypeController(DocumentTypeService service, DocumentTypeMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTypeResponse create(
            @RequestBody DocumentTypeRequest request) {

        return mapper.toResponse(service.create(mapper.toDomain(request)));
    }

    @GetMapping
    public List<DocumentTypeResponse> findAll() {
        return service.findAll().stream().map(mapper::toResponse).toList();
    }

    public record DocumentTypeRequest(String name, String description) { }

    public record DocumentTypeResponse(Long id, String name, String description) { }
}

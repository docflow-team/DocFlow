package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.api.DocumentTypeRequest;
import at.fhtw.docflow.document.api.DocumentTypeResponse;
import at.fhtw.docflow.document.persistence.DocumentType;
import at.fhtw.docflow.document.api.DocumentTypeMapper;
import at.fhtw.docflow.document.persistence.DocumentTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentTypeService {

    private final DocumentTypeRepository repository;
    private final DocumentTypeMapper mapper;

    public DocumentTypeService(DocumentTypeRepository repository, DocumentTypeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public DocumentTypeResponse create(DocumentTypeRequest request) {
        DocumentType documentType = mapper.toEntity(request);

        DocumentType saved = repository.save(documentType);

        return mapper.toResponse(saved);
    }

    public List<DocumentTypeResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}

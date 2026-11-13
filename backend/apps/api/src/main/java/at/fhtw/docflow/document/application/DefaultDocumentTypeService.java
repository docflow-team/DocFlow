package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.DocumentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DefaultDocumentTypeService implements DocumentTypeService {

    private final DocumentTypeRepository repository;
    public DefaultDocumentTypeService(DocumentTypeRepository repository) {
        this.repository = repository;
    }

    public DocumentType create(DocumentType documentType) {
        return repository.save(documentType);
    }

    @Transactional(readOnly = true)
    public List<DocumentType> findAll() {
        return repository.findAll();
    }
}

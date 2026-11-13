package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.application.DocumentTypeRepository;
import at.fhtw.docflow.document.domain.DocumentType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaDocumentTypeRepository implements DocumentTypeRepository {

    private final SpringDataDocumentTypeRepository types;
    private final DocumentTypePersistenceMapper mapper;

    public JpaDocumentTypeRepository(SpringDataDocumentTypeRepository types,
                                     DocumentTypePersistenceMapper mapper) {
        this.types = types;
        this.mapper = mapper;
    }

    public DocumentType save(DocumentType documentType) {
        return mapper.toDomain(types.save(mapper.toNewEntity(documentType)));
    }

    public List<DocumentType> findAll() {
        return types.findAll().stream().map(mapper::toDomain).toList();
    }

    public Optional<DocumentType> findById(Long id) {
        return types.findById(id).map(mapper::toDomain);
    }
}

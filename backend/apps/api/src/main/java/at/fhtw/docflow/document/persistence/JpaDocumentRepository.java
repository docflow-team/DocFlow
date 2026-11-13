package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.application.DocumentRepository;
import at.fhtw.docflow.document.domain.Document;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Repository
public class JpaDocumentRepository implements DocumentRepository {

    private final SpringDataDocumentRepository documents;
    private final SpringDataDocumentTypeRepository types;
    private final DocumentPersistenceMapper mapper;

    public JpaDocumentRepository(SpringDataDocumentRepository documents,
                                 SpringDataDocumentTypeRepository types,
                                 DocumentPersistenceMapper mapper) {
        this.documents = documents;
        this.types = types;
        this.mapper = mapper;
    }

    public Document save(Document document) {
        DocumentEntity entity;
        if (document.getId() == null) {
            entity = mapper.toNewEntity(document);
        } else {
            entity = documents.findById(document.getId()).orElseThrow();
            mapper.updateMetadata(document, entity);
        }
        List<DocumentTypeEntity> assignedTypes = types.findAllById(document.getDocumentTypeIds());
        if (assignedTypes.size() != document.getDocumentTypeIds().size()) {
            throw new NoSuchElementException("Document type not found");
        }
        entity.getDocumentTypes().clear();
        entity.getDocumentTypes().addAll(assignedTypes);
        return mapper.toDomain(documents.save(entity));
    }

    public List<Document> findAll() {
        return documents.findAll().stream().map(mapper::toDomain).toList();
    }

    public Optional<Document> findById(Long id) {
        return documents.findById(id).map(mapper::toDomain);
    }

    public boolean existsById(Long id) {
        return documents.existsById(id);
    }

    public void deleteById(Long id) {
        documents.deleteById(id);
    }
}

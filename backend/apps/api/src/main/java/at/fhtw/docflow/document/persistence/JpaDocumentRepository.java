package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.application.DocumentRepository;
import at.fhtw.docflow.document.application.DocumentService.DocumentPage;
import at.fhtw.docflow.document.domain.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.NoSuchElementException;
import at.fhtw.docflow.document.domain.Category;
import java.util.Optional;

@Repository
public class JpaDocumentRepository implements DocumentRepository {
    private final SpringDataDocumentRepository documents;
    private final SpringDataCategoryRepository categories;
    private final DocumentPersistenceMapper mapper;

    public JpaDocumentRepository(SpringDataDocumentRepository documents,
                                 SpringDataCategoryRepository categories, DocumentPersistenceMapper mapper) {
        this.documents = documents;
        this.categories = categories;
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
        var ids = document.getCategories().stream().map(Category::id).toList();
        List<CategoryEntity> assignedCategories = categories.findAllById(ids);
        if (assignedCategories.size() != ids.size()) {
            throw new NoSuchElementException("Category not found");
        }
        entity.getCategories().clear();
        entity.getCategories().addAll(assignedCategories);
        return mapper.toDomain(documents.saveAndFlush(entity));
    }

    public DocumentPage findAllByOwnerId(Long ownerId, int page, int size) {
        var result = documents.findAllByOwnerIdAndObjectKeyIsNotNull(ownerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new DocumentPage(result.getContent().stream().map(mapper::toDomain).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public Optional<Document> findByIdAndOwnerId(Long id, Long ownerId) {
        return documents.findByIdAndOwnerIdAndObjectKeyIsNotNull(id, ownerId).map(mapper::toDomain);
    }

    public Optional<Document> deleteByIdAndOwnerId(Long id, Long ownerId) {
        return documents.findByIdAndOwnerIdAndObjectKeyIsNotNull(id, ownerId).map(entity -> {
            Document document = mapper.toDomain(entity);
            documents.delete(entity);
            return document;
        });
    }
}

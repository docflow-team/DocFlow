package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.DocumentType;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository {

    DocumentType save(DocumentType documentType);

    List<DocumentType> findAll();

    Optional<DocumentType> findById(Long id);
}

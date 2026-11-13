package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.DocumentType;

import java.util.List;

public interface DocumentTypeService {

    DocumentType create(DocumentType documentType);

    List<DocumentType> findAll();
}

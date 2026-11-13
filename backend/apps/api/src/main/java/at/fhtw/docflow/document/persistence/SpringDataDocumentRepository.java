package at.fhtw.docflow.document.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataDocumentRepository extends JpaRepository<DocumentEntity, Long> {

}

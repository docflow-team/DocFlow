package at.fhtw.docflow.document.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataDocumentRepository extends JpaRepository<DocumentEntity, Long> {

    List<DocumentEntity> findAllByOwnerId(Long ownerId);
    Optional<DocumentEntity> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
    void deleteByIdAndOwnerId(Long id, Long ownerId);
}

package at.fhtw.docflow.document.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpringDataDocumentRepository extends JpaRepository<DocumentEntity, Long> {
    Page<DocumentEntity> findAllByOwnerIdAndObjectKeyIsNotNull(Long ownerId, Pageable pageable);
    Optional<DocumentEntity> findByIdAndOwnerIdAndObjectKeyIsNotNull(Long id, Long ownerId);
}

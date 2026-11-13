package at.fhtw.docflow.document.persistence;

import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable only for pre-authentication metadata; owner-scoped queries hide legacy rows.
    @Column(updatable = false)
    private Long ownerId;

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    private String filename;
    private String contentType;
    private String description;

    @CreationTimestamp
    private LocalDateTime createdAt;

    protected DocumentEntity() {

    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @ManyToMany
    @JoinTable(
            name = "document_document_types",
            joinColumns = @JoinColumn(name = "document_id"),
            inverseJoinColumns = @JoinColumn(name = "document_type_id")
    )
    private Set<DocumentTypeEntity> documentTypes = new HashSet<>();

    public Set<DocumentTypeEntity> getDocumentTypes() {
        return documentTypes;
    }

}

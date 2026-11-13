package at.fhtw.docflow.document.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "documents")
public class DocumentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long ownerId;
    private String title;
    private String description;
    // Preserve the existing physical column; metadata-only legacy rows have no object key.
    @Column(name = "filename", updatable = false)
    private String originalFilename;
    @Column(updatable = false)
    private String contentType;
    @Column(unique = true, updatable = false)
    private String objectKey;
    @Column(updatable = false)
    private Long sizeBytes;
    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void recordUpdate() { updatedAt = Instant.now(); }

    @ManyToMany
    @JoinTable(name = "document_document_types",
            joinColumns = @JoinColumn(name = "document_id"),
            inverseJoinColumns = @JoinColumn(name = "document_type_id"))
    private Set<CategoryEntity> categories = new HashSet<>();

    protected DocumentEntity() { }
    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<CategoryEntity> getCategories() { return categories; }
}

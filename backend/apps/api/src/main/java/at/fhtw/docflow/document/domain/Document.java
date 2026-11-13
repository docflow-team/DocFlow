package at.fhtw.docflow.document.domain;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class Document {

    private final Long id;
    private Long ownerId;
    private String filename;
    private String contentType;
    private String description;
    private final LocalDateTime createdAt;
    private final Set<Long> documentTypeIds;

    public Document(Long id, String filename, String contentType, String description,
                    LocalDateTime createdAt, Set<Long> documentTypeIds, Long ownerId) {
        this.ownerId = ownerId;
        this.id = id;
        this.createdAt = createdAt;
        this.documentTypeIds = new HashSet<>(documentTypeIds == null ? Set.of() : documentTypeIds);
        updateMetadata(filename, contentType, description);
    }

    public static Document create(String filename, String contentType, String description) {
        return new Document(null, filename, contentType, description, null, Set.of(), null);
    }

    public void updateMetadata(String filename, String contentType, String description) {
        requireText(filename, "filename");
        requireText(contentType, "contentType");
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("description must not exceed 255 characters");
        }
        this.filename = filename;
        this.contentType = contentType;
        this.description = description;
    }

    public void assignDocumentType(Long typeId) {
        if (typeId == null || typeId <= 0) {
            throw new IllegalArgumentException("document type ID must be positive");
        }
        documentTypeIds.add(typeId);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException(field + " must be non-blank and at most 255 characters");
        }
    }

    public Long getOwnerId() { return ownerId; }

    public void assignOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0 || (this.ownerId != null && !this.ownerId.equals(ownerId))) {
            throw new IllegalArgumentException("A valid immutable owner is required");
        }
        this.ownerId = ownerId;
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

    public Set<Long> getDocumentTypeIds() {
        return Set.copyOf(documentTypeIds);
    }
}

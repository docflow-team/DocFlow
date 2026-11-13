package at.fhtw.docflow.document.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Set;

public class Document {
    public static final int MAX_PDF_BYTES = 10 * 1024 * 1024;

    private final Long id;
    private final Long ownerId;
    private Metadata metadata;
    private final String originalFilename;
    private final String objectKey;
    private final long sizeBytes;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Set<Category> categories;

    public Document(Long id, Long ownerId, Metadata metadata, String originalFilename,
                    String objectKey, long sizeBytes, Instant createdAt, Instant updatedAt,
                    Set<Category> categories) {
        if (ownerId == null || ownerId <= 0 || metadata == null || objectKey == null || objectKey.isBlank()) {
            throw new InvalidDocumentException("Document ownership, metadata and stored content are required");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_PDF_BYTES) {
            throw new InvalidDocumentException("PDF size must be between 1 byte and 10 MiB");
        }
        this.id = id;
        this.ownerId = ownerId;
        this.metadata = metadata;
        this.originalFilename = normalizeFilename(originalFilename);
        this.objectKey = objectKey;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.categories = new HashSet<>(categories == null ? Set.of() : categories);
    }

    public void updateMetadata(Metadata metadata) {
        if (metadata == null) throw new InvalidDocumentException("Document metadata is required");
        this.metadata = metadata;
    }

    public void replaceCategories(List<Category> categories) {
        this.categories.clear();
        this.categories.addAll(categories);
    }

    public static String normalizeFilename(String filename) {
        if (filename == null) throw new InvalidDocumentException("An original filename is required");
        String basename = filename.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1).replaceAll("\\p{Cc}", "").trim();
        if (basename.isBlank() || basename.length() > 255) {
            throw new InvalidDocumentException("Original filename must contain 1-255 characters");
        }
        return basename;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getTitle() { return metadata.title(); }
    public String getDescription() { return metadata.description(); }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return "application/pdf"; }
    public String getObjectKey() { return objectKey; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Category> getCategories() { return categories.stream().sorted(Comparator.comparing(Category::name)).toList(); }

    public record Metadata(String title, String description) {
        public Metadata {
            title = title == null ? null : title.trim();
            if (title == null || title.isBlank() || title.length() > 255) {
                throw new InvalidDocumentException("Title must contain 1-255 characters after trimming");
            }
            if (description != null && description.length() > 255) {
                throw new InvalidDocumentException("Description must not exceed 255 characters");
            }
        }
    }

    public static class InvalidDocumentException extends IllegalArgumentException {
        public enum Reason { INVALID, TOO_LARGE, UNSUPPORTED_TYPE }
        private final Reason reason;

        public InvalidDocumentException(String message) { this(Reason.INVALID, message); }
        public InvalidDocumentException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }
        public Reason reason() { return reason; }
    }
}

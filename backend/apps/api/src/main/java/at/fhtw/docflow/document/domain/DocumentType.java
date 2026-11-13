package at.fhtw.docflow.document.domain;

public record DocumentType(Long id, String name, String description) {

    public static DocumentType create(String name, String description) {
        return new DocumentType(null, name, description);
    }
}

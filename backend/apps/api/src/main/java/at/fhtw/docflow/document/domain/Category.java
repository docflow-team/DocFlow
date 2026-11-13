package at.fhtw.docflow.document.domain;

public record Category(Long id, String name, String description) {

    public static Category create(String name, String description) {
        return new Category(null, name, description);
    }
}

package at.fhtw.docflow.document.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "tags")
public class TagEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50, updatable = false)
    private String name;

    protected TagEntity() { }
    TagEntity(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}

package at.fhtw.docflow.document.api;

import at.fhtw.docflow.document.application.CategoryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/categories", produces = MediaType.APPLICATION_JSON_VALUE)
public class CategoryController {
    private final CategoryService service;
    private final CategoryMapper mapper;

    public CategoryController(CategoryService service, CategoryMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<CategoryResponse> findAll() {
        return service.findAll().stream().map(mapper::toResponse).toList();
    }

    public record CategoryResponse(Long id, String name, String description) { }
}

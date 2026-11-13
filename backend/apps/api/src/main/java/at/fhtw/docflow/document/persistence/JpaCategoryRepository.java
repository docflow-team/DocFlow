package at.fhtw.docflow.document.persistence;

import at.fhtw.docflow.document.application.CategoryRepository;
import at.fhtw.docflow.document.domain.Category;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Sort;

@Repository
public class JpaCategoryRepository implements CategoryRepository {

    private final SpringDataCategoryRepository categories;
    private final CategoryPersistenceMapper mapper;

    public JpaCategoryRepository(SpringDataCategoryRepository categories,
                                     CategoryPersistenceMapper mapper) {
        this.categories = categories;
        this.mapper = mapper;
    }

    public Category save(Category category) {
        return mapper.toDomain(categories.save(mapper.toNewEntity(category)));
    }

    public List<Category> findAll() {
        return categories.findAll(Sort.by("name")).stream().map(mapper::toDomain).toList();
    }

    public List<Category> findAllById(List<Long> ids) {
        return categories.findAllById(ids).stream().map(mapper::toDomain).toList();
    }
}

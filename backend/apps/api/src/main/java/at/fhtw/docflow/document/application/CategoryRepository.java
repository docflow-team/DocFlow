package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Category;
import java.util.List;

public interface CategoryRepository {
    Category save(Category category);
    List<Category> findAll();
    List<Category> findAllById(List<Long> ids);
}

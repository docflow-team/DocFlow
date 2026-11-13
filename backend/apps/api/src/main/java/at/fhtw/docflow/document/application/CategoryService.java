package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Category;
import java.util.List;

public interface CategoryService {
    void seedDefaults();
    List<Category> findAll();
}

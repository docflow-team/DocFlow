package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DefaultCategoryService implements CategoryService {
    private final CategoryRepository repository;

    public DefaultCategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    public void seedDefaults() {
        var existingNames = repository.findAll().stream().map(Category::name).collect(Collectors.toSet());
        var defaults = List.of(
                Category.create("Invoice", "Amount requested for goods/services, tuition invoice"),
                Category.create("Receipt", "Proof of payment or purchase"),
                Category.create("Contract", "Rental, employment or service agreement"),
                Category.create("Certificate", "Enrollment, qualification, attendance or insurance certificate"),
                Category.create("Official correspondence", "Decisions, notices and formal letters from institutions"),
                Category.create("Study material", "Lecture slides, assignments and study notes"),
                Category.create("Report", "Project, technical or inspection report"));
        defaults.stream().filter(category -> !existingNames.contains(category.name())).forEach(repository::save);
    }

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return repository.findAll();
    }
}

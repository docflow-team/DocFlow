package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Category;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryServiceTest {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryService service = new DefaultCategoryService(repository);

    @Test
    void seedsMissingLabelsWithoutReplacingExistingDefinitionsOrCustomCategories() {
        when(repository.findAll()).thenReturn(List.of(
                new Category(42L, "Invoice", "Existing definition"), new Category(43L, "Custom", null)));
        service.seedDefaults();
        var added = org.mockito.ArgumentCaptor.forClass(Category.class);
        verify(repository, times(6)).save(added.capture());
        assertEquals(List.of("Receipt", "Contract", "Certificate", "Official correspondence", "Study material", "Report"),
                added.getAllValues().stream().map(Category::name).toList());
        assertTrue(added.getAllValues().stream().allMatch(category -> category.id() == null && category.description() != null));
    }

    @Test
    void listsExistingCategoriesWithoutChangingThem() {
        var categories = List.of(new Category(1L, "Contract", null), new Category(2L, "Invoice", "Existing"));
        when(repository.findAll()).thenReturn(categories);
        assertEquals(categories, service.findAll());
        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
    }
}

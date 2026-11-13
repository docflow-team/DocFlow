package at.fhtw.docflow.document.api;

import at.fhtw.docflow.configuration.MappingConfig;
import at.fhtw.docflow.document.api.CategoryController.CategoryResponse;
import at.fhtw.docflow.document.domain.Category;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface CategoryMapper {
    CategoryResponse toResponse(Category category);
}

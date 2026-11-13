package at.fhtw.docflow.document.api;

import at.fhtw.docflow.api.ApiExceptionHandler;
import at.fhtw.docflow.document.domain.Category;
import at.fhtw.docflow.document.application.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoryControllerTest {
    private final CategoryService service = mock(CategoryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CategoryController(service, Mappers.getMapper(CategoryMapper.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void listsCategoryDefinitionsAsJson() throws Exception {
        when(service.findAll()).thenReturn(List.of(new Category(42L, "Invoice", "Amount requested")));
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$[0].id").value(42)).andExpect(jsonPath("$[0].name").value("Invoice"))
                .andExpect(jsonPath("$[0].description").value("Amount requested"));
    }

    @Test
    void taxonomyWritesAndLegacyRoutesAreUnavailable() throws Exception {
        mvc.perform(post("/api/v1/categories").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/document-types")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}

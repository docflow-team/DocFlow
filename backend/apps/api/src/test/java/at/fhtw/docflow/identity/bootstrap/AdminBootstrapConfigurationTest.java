package at.fhtw.docflow.identity.bootstrap;

import at.fhtw.docflow.identity.bootstrap.AdminBootstrapConfiguration.BootstrapAdminProperties;
import at.fhtw.docflow.identity.application.DefaultAdminBootstrapService;
import at.fhtw.docflow.identity.application.PasswordHasher;
import at.fhtw.docflow.identity.application.UserRepository;
import at.fhtw.docflow.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapConfigurationTest {

    @Test
    void invalidConfigurationIsRejectedWithoutEchoingTheSecret() {
        UserRepository users = mock(UserRepository.class);
        PasswordHasher passwords = mock(PasswordHasher.class);
        when(passwords.hash("synthetic-password")).thenReturn("encoded-password");
        var service = new DefaultAdminBootstrapService(users, passwords);
        var properties = new BootstrapAdminProperties("admin", "invalid", "synthetic-password");
        var runner = new AdminBootstrapConfiguration().adminBootstrapRunner(service, properties);

        var error = assertThrows(IllegalArgumentException.class, () -> runner.run(new DefaultApplicationArguments()));

        assertFalse(error.getMessage().contains(properties.password()));
        assertFalse(properties.toString().contains(properties.password()));
        verify(users, never()).save(any());
    }

    @Test
    void fullBootstrapContextStartsWithoutStorageJwtOrCategorySeeding() {
        try (var context = new SpringApplicationBuilder(BackendApplication.class)
                .profiles("bootstrap-admin").web(WebApplicationType.NONE)
                .run("--spring.datasource.url=jdbc:h2:mem:bootstrap;MODE=PostgreSQL;INIT=CREATE SCHEMA IF NOT EXISTS api",
                        "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--docflow.jwt.secret=", "--docflow.storage.secret-key=",
                        "--docflow.storage.endpoint=http://127.0.0.1:1",
                        "--docflow.bootstrap.username=bootstrap.admin",
                        "--docflow.bootstrap.email=bootstrap@example.invalid",
                        "--docflow.bootstrap.password=synthetic-bootstrap-password")) {
            assertFalse(context.containsBean("documentStorage"));
            assertFalse(context.containsBean("defaultDocumentService"));
            assertFalse(context.containsBean("documentController"));
            var jdbc = context.getBean(JdbcTemplate.class);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM api.users", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM api.document_types", Integer.class));
        }
    }
}

package at.fhtw.docflow.identity.bootstrap;

import at.fhtw.docflow.identity.application.AdminBootstrapService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapConfigurationTest {

    @Test
    void invalidConfigurationIsRejectedWithoutEchoingTheSecret() {
        AdminBootstrapService service = mock(AdminBootstrapService.class);
        BootstrapAdminProperties properties = new BootstrapAdminProperties("admin", "invalid", "secret");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var runner = new AdminBootstrapConfiguration().adminBootstrapRunner(service, properties, factory.getValidator());

            var error = assertThrows(IllegalArgumentException.class, () -> runner.run(new DefaultApplicationArguments()));

            assertFalse(error.getMessage().contains("secret"));
            assertFalse(properties.toString().contains("secret"));
            verifyNoInteractions(service);
        }
    }
}

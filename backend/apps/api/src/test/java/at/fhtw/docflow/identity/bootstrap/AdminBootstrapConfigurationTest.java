package at.fhtw.docflow.identity.bootstrap;

import at.fhtw.docflow.identity.bootstrap.AdminBootstrapConfiguration.BootstrapAdminProperties;
import at.fhtw.docflow.identity.application.DefaultAdminBootstrapService;
import at.fhtw.docflow.identity.application.PasswordHasher;
import at.fhtw.docflow.identity.application.UserRepository;
import org.junit.jupiter.api.Test;
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
}

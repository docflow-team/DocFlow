package at.fhtw.docflow.identity.bootstrap;

import at.fhtw.docflow.identity.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docflow.bootstrap")
public record BootstrapAdminProperties(
        @NotBlank @Pattern(regexp = "[a-z0-9._-]{3,50}") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = 8) String password) {

    public BootstrapAdminProperties {
        username = User.normalizeIdentifier(username);
        email = User.normalizeIdentifier(email);
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[redacted]";
    }
}

package at.fhtw.docflow.identity.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docflow.bootstrap")
public record BootstrapAdminProperties(
        String username, String email, String password) {

    @Override
    public String toString() {
        return "BootstrapAdminProperties[redacted]";
    }
}

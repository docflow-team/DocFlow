package at.fhtw.docflow.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("docflow.jwt")
public record JwtProperties(String secret, String issuer, String audience) {
    public static final long LIFETIME_SECONDS = 900;

    @Override
    public String toString() { return "JwtProperties[redacted]"; }
}

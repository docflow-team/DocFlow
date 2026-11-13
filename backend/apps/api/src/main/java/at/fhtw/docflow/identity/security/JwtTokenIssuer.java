package at.fhtw.docflow.identity.security;

import at.fhtw.docflow.identity.application.TokenIssuer;
import at.fhtw.docflow.identity.domain.User;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Profile("!bootstrap-admin")
public class JwtTokenIssuer implements TokenIssuer {
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .audience(List.of(properties.audience())).subject(user.id().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(JwtProperties.LIFETIME_SECONDS))
                .claim("ver", user.tokenVersion()).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}

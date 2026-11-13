package at.fhtw.docflow.identity.security;

import at.fhtw.docflow.identity.application.UserRepository;
import at.fhtw.docflow.identity.domain.User;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class AccountJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;

    public AccountJwtConverter(UserRepository users) { this.users = users; }

    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user;
        try {
            user = users.findById(Long.valueOf(jwt.getSubject())).orElseThrow(() -> new InvalidBearerTokenException("Invalid token"));
        } catch (NumberFormatException exception) {
            throw new InvalidBearerTokenException("Invalid token");
        }
        Object version = jwt.getClaim("ver");
        if (!user.enabled() || !(version instanceof Number number) || number.longValue() != user.tokenVersion() || jwt.getExpiresAt() == null) {
            throw new InvalidBearerTokenException("Invalid token");
        }
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        if (!user.mustChangePassword()) authorities.add(new SimpleGrantedAuthority("PASSWORD_CHANGED"));
        return new JwtAuthenticationToken(jwt, authorities, user.id().toString());
    }
}

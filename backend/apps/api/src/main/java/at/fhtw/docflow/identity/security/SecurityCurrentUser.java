package at.fhtw.docflow.identity.security;

import at.fhtw.docflow.identity.application.CurrentUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityCurrentUser implements CurrentUser {
    public Long id() {
        return Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}

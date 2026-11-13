package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;

public interface TokenIssuer {
    String issue(User user);
}

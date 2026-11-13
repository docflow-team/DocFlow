package at.fhtw.docflow.identity.application;

import at.fhtw.docflow.identity.domain.User;

public record LoginResult(User user, String accessToken) {
    @Override
    public String toString() { return "LoginResult[redacted]"; }
}

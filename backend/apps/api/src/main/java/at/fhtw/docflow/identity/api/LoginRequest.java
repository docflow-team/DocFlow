package at.fhtw.docflow.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(@NotBlank String login, @NotNull String password) {
    @Override
    public String toString() { return "LoginRequest[redacted]"; }
}

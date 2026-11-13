package at.fhtw.docflow.identity.api;

import jakarta.validation.constraints.NotNull;

public record PasswordChangeRequest(@NotNull String currentPassword, @NotNull String newPassword) {
    @Override
    public String toString() { return "PasswordChangeRequest[redacted]"; }
}

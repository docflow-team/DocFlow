package at.fhtw.docflow.identity.api;

import at.fhtw.docflow.identity.domain.UserRole;

public record UserResponse(Long id, String username, String email, UserRole role,
                           boolean enabled, boolean mustChangePassword) { }

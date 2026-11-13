package at.fhtw.docflow.identity.api;

import at.fhtw.docflow.identity.application.AuthenticationService;
import at.fhtw.docflow.identity.domain.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Profile("!bootstrap-admin")
@RestController
@RequestMapping(value = "/api/v1/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminUserController {
    private final AuthenticationService service;
    private final UserMapper mapper;

    public AdminUserController(AuthenticationService service, UserMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        var user = service.createUser(request.username(), request.email(), request.role(), request.temporaryPassword());
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + user.id()))
                .body(mapper.toResponse(user));
    }

    public record CreateUserRequest(@NotBlank String username, @NotBlank String email,
                                    @NotNull UserRole role, @NotNull String temporaryPassword) {
        @Override
        public String toString() { return "CreateUserRequest[redacted]"; }
    }
}

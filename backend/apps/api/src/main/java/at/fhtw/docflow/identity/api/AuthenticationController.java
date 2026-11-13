package at.fhtw.docflow.identity.api;

import at.fhtw.docflow.identity.application.AuthenticationService;
import at.fhtw.docflow.identity.security.JwtProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@org.springframework.context.annotation.Profile("!bootstrap-admin")
@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthenticationController {
    private final AuthenticationService service;
    private final UserMapper mapper;

    public AuthenticationController(AuthenticationService service, UserMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        var result = service.login(request.login(), request.password());
        return new TokenResponse(result.accessToken(), "Bearer", JwtProperties.LIFETIME_SECONDS, mapper.toResponse(result.user()));
    }

    @GetMapping("/me")
    public UserResponse me() { return mapper.toResponse(service.me()); }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() { service.logout(); }

    @PutMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(request.currentPassword(), request.newPassword());
    }
}

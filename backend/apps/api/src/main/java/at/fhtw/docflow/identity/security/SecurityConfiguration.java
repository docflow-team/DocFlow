package at.fhtw.docflow.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@Profile("!bootstrap-admin")
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountJwtConverter accounts, JsonMapper json) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable()).logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/password", "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/document-types").access(org.springframework.security.authorization.AuthorizationManagers.allOf(
                                org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole("ADMIN"),
                                org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority("PASSWORD_CHANGED")))
                        .anyRequest().hasAuthority("PASSWORD_CHANGED"))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> problem(json, request, response, 401))
                        .accessDeniedHandler((request, response, error) -> problem(json, request, response, 403)))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(accounts))
                        .authenticationEntryPoint((request, response, error) -> problem(json, request, response, 401))
                        .accessDeniedHandler((request, response, error) -> problem(json, request, response, 403)))
                .build();
    }

    private static void problem(JsonMapper json, HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        json.writeValue(response.getOutputStream(), Map.of("type", "about:blank", "title", status == 401 ? "Unauthorized" : "Forbidden",
                "status", status, "detail", status == 401 ? "Authentication is required or the token is invalid" : "Access is forbidden",
                "instance", request.getRequestURI()));
    }
}

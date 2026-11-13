package at.fhtw.docflow.identity.security;

import at.fhtw.docflow.identity.application.PasswordHasher;
import at.fhtw.docflow.identity.application.UserRepository;
import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authentication;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS api",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "docflow.jwt.secret=synthetic-test-key-at-least-thirty-two-bytes"
})
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    private static final String PASSWORD = " SyntheticPassword ";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordHasher passwords;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;

    private User account(UserRole role, boolean temporary, boolean enabled) {
        String name = "test-" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(new User(null, name, name + "@example.invalid", passwords.hash(PASSWORD), role, enabled, temporary, 0));
    }

    private String login(User user) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("login", " " + user.email().toUpperCase() + " ", "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andExpect(jsonPath("$.user.tokenVersion").doesNotExist())
                .andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        Jwt decoded = decoder.decode(token);
        assertEquals(user.id().toString(), decoded.getSubject());
        assertEquals(900, decoded.getExpiresAt().getEpochSecond() - decoded.getIssuedAt().getEpochSecond());
        return token;
    }

    @Test
    void healthIsPublicButDocumentsAndMeRequireBearerAuthentication() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json")).andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documents").header("Authorization", "Basic dGVzdDp0ZXN0"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.login").exists());
    }

    @Test
    void failedLoginsHaveTheSameSafeResponseAndPasswordWhitespaceMatters() throws Exception {
        User user = account(UserRole.USER, false, true);
        User disabled = account(UserRole.USER, false, false);
        String expected = null;
        for (var request : List.of(Map.of("login", "missing", "password", PASSWORD),
                Map.of("login", user.username(), "password", PASSWORD.trim()),
                Map.of("login", disabled.username(), "password", PASSWORD))) {
            var result = mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized()).andExpect(content().contentType("application/json")).andReturn();
            String body = result.getResponse().getContentAsString();
            assertFalse(body.contains(PASSWORD));
            if (expected == null) expected = body;
            else assertEquals(expected, body);
        }
        String token = login(user);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void temporaryAccountsMustChangePasswordAndOldTokensAreRevoked() throws Exception {
        User user = account(UserRole.USER, true, true);
        String token = login(user);
        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("currentPassword", "wrong", "newPassword", "NewPassword"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.currentPassword").exists());
        mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "\u00e9".repeat(37)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.newPassword").exists());
        mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "shortpw"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.newPassword").exists());
        mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "NewPassword"))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        User changed = users.findById(user.id()).orElseThrow();
        assertFalse(changed.mustChangePassword());
        assertEquals(1, changed.tokenVersion());
        assertTrue(passwords.matches("NewPassword", changed.passwordHash()));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("login", user.username(), "password", "NewPassword"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.mustChangePassword").value(false));
    }

    @Test
    void logoutRevokesAllSessionsIncludingTemporaryAccounts() throws Exception {
        User user = account(UserRole.USER, true, true);
        String first = login(user);
        String second = login(user);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + second)).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerScopeIncludesUpdatesDeletesAndAssignmentsAndDoesNotGrantAdminsAccess() throws Exception {
        User owner = account(UserRole.USER, false, true);
        String token = login(owner);
        var created = mvc.perform(post("/api/documents").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"filename\":\"owned.pdf\",\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerId").doesNotExist()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        String otherToken = login(account(UserRole.ADMIN, false, true));
        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/documents/" + id).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(put("/api/documents/" + id).header("Authorization", "Bearer " + otherToken).contentType("application/json")
                        .content("{\"filename\":\"stolen.pdf\",\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/documents/" + id + "/types/99999").header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/" + id).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filename").value("owned.pdf"));
        mvc.perform(delete("/api/documents/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
    }

    @Test
    void accountStateIsReadOnEachRequestAndTypeCreationIsAdminOnly() throws Exception {
        User user = account(UserRole.USER, false, true);
        String token = login(user);
        mvc.perform(post("/api/document-types").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"name\":\"Forbidden type\"}"))
                .andExpect(status().isForbidden());
        users.save(new User(user.id(), user.username(), user.email(), user.passwordHash(), UserRole.USER, false, false, 0));
        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        String admin = login(account(UserRole.ADMIN, false, true));
        mvc.perform(post("/api/document-types").header("Authorization", "Bearer " + admin).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", UUID.randomUUID().toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidSignaturesExpiredTokensAndWrongIssuerOrAudienceAreRejected() throws Exception {
        User user = account(UserRole.USER, false, true);
        String token = login(user);
        for (String invalid : List.of(token.substring(0, token.length() - 10) + "aaaaaaaaaa",
                signed(user, "wrong-issuer", "docflow-api", false), signed(user, "docflow", "wrong-audience", false),
                signed(user, "docflow", "docflow-api", true))) {
            mvc.perform(get("/api/documents").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized()).andExpect(content().contentType("application/json"));
        }
    }

    private String signed(User user, String issuer, String audience, boolean expired) {
        Instant start = Instant.now().minusSeconds(expired ? 3600 : 0);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(user.id().toString()).issuer(issuer).audience(List.of(audience))
                        .issuedAt(start).expiresAt(start.plusSeconds(900)).claim("ver", user.tokenVersion()).build())).getTokenValue();
    }
}

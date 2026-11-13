package at.fhtw.docflow.identity.security;

import at.fhtw.docflow.document.application.DocumentStorage;
import at.fhtw.docflow.identity.application.PasswordHasher;
import at.fhtw.docflow.identity.application.UserRepository;
import at.fhtw.docflow.identity.api.AdminUserController.CreateUserRequest;
import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static at.fhtw.docflow.document.DocumentTestFixtures.*;
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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    DocumentStorage documentStorage;

    @BeforeEach
    void storageFixtures() { mockStorage(documentStorage); }

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
        String token = login(" " + user.email().toUpperCase() + " ", PASSWORD);
        assertEquals(user.id().toString(), decoder.decode(token).getSubject());
        return token;
    }

    private String login(String name, String password) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("login", name, "password", password))))
                .andExpect(status().isOk()).andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andExpect(jsonPath("$.user.tokenVersion").doesNotExist())
                .andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        Jwt decoded = decoder.decode(token);
        assertEquals(900, decoded.getExpiresAt().getEpochSecond() - decoded.getIssuedAt().getEpochSecond());
        return token;
    }

    @Test
    void healthIsPublicButDocumentsAndMeRequireBearerAuthentication() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json")).andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/documents").header("Authorization", "Basic dGVzdDp0ZXN0"))
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
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
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
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
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
    void ownerScopeCoversPdfContentMetadataDeletionAssignmentsAndProcessing() throws Exception {
        User owner = account(UserRole.USER, false, true);
        String token = login(owner);
        byte[] content = pdf(1);
        var created = mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "owned.pdf", "application/pdf", content))
                        .param("title", " Owned ").header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.document.ownerId").doesNotExist())
                .andExpect(jsonPath("$.document.objectKey").doesNotExist())
                .andExpect(jsonPath("$.document.createdAt").isString()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("document").get("id").asLong();
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        String otherToken = login(account(UserRole.ADMIN, false, true));
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        for (String suffix : List.of("", "/content", "/processing")) {
            mvc.perform(get("/api/v1/documents/" + id + suffix).header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/v1/documents/" + id + "/metadata").header("Authorization", "Bearer " + otherToken)
                        .contentType("application/json").content("{\"title\":\"Stolen\",\"description\":null}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/documents/" + id + "/types/99999").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/documents/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/" + id + "/content").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(content().bytes(content));
        mvc.perform(put("/api/v1/documents/" + id + "/metadata").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"title\":\"Renamed\",\"description\":null,\"ownerId\":999,\"originalFilename\":\"forged.pdf\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originalFilename").value("owned.pdf"));
        mvc.perform(get("/api/v1/documents/" + id + "/processing").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processingAvailable").value(false))
                .andExpect(jsonPath("$.classification").value("NOT_STARTED"));
        mvc.perform(delete("/api/v1/documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void accountStateIsReadOnEachRequestAndTypeCreationIsAdminOnly() throws Exception {
        User user = account(UserRole.USER, false, true);
        String token = login(user);
        mvc.perform(post("/api/document-types").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"name\":\"Forbidden type\"}"))
                .andExpect(status().isForbidden());
        users.save(new User(user.id(), user.username(), user.email(), user.passwordHash(), UserRole.USER, false, false, 0));
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
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
            mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized()).andExpect(content().contentType("application/json"));
        }
    }

    private String signed(User user, String issuer, String audience, boolean expired) {
        Instant start = Instant.now().minusSeconds(expired ? 3600 : 0);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(user.id().toString()).issuer(issuer).audience(List.of(audience))
                        .issuedAt(start).expiresAt(start.plusSeconds(900)).claim("ver", user.tokenVersion()).build())).getTokenValue();
    }
    private Map<String, Object> request() {
        String name = "created-" + UUID.randomUUID().toString().substring(0, 8);
        return new LinkedHashMap<>(Map.of("username", name, "email", name + "@example.invalid",
                "role", "USER", "temporaryPassword", PASSWORD));
    }

    @Test
    void adminCreatesAccountThatMustChangePasswordBeforeAccessingDocuments() throws Exception {
        User admin = account(UserRole.ADMIN, false, true);
        String adminToken = login(admin.username(), PASSWORD);
        var payload = request();
        String name = (String) payload.get("username");
        payload.put("username", " " + name.toUpperCase() + " ");
        payload.put("email", " " + name.toUpperCase() + "@EXAMPLE.INVALID ");

        var result = mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.username").value(name)).andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertEquals("/api/v1/admin/users/" + id, result.getResponse().getHeader("Location"));
        assertFalse(result.getResponse().getContentAsString().contains(PASSWORD));
        User saved = users.findById(id).orElseThrow();
        assertEquals(name + "@example.invalid", saved.email());
        assertEquals(0, saved.tokenVersion());
        assertTrue(saved.passwordHash().startsWith("{bcrypt}"));
        assertTrue(passwords.matches(PASSWORD, saved.passwordHash()));

        String temporaryToken = login(saved.email(), PASSWORD);
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + temporaryToken)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + temporaryToken).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "NewSyntheticPassword"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + temporaryToken)).andExpect(status().isUnauthorized());
        String changedToken = login(name, "NewSyntheticPassword");
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + changedToken)).andExpect(status().isOk());
    }

    @Test
    void adminMayCreateAnotherAdministratorWithTheSameTemporaryPasswordRules() throws Exception {
        User admin = account(UserRole.ADMIN, false, true);
        String token = login(admin.username(), PASSWORD);
        var payload = request();
        payload.put("role", "ADMIN");
        mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        assertEquals(admin, users.findById(admin.id()).orElseThrow());
    }

    @Test
    void anonymousUsersOrdinaryUsersAndTemporaryAdministratorsCannotCreateAccounts() throws Exception {
        var payload = request();
        mvc.perform(post("/api/v1/admin/users").contentType("application/json").content(json.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
        for (User user : List.of(account(UserRole.USER, false, true), account(UserRole.ADMIN, true, true))) {
            String token = login(user.username(), PASSWORD);
            mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token).contentType("application/json")
                            .content(json.writeValueAsString(payload)))
                    .andExpect(status().isForbidden()).andExpect(content().contentType("application/json"));
        }
        assertTrue(users.findByUsername((String) payload.get("username")).isEmpty());
    }

    @Test
    void rejectsInvalidFieldsAndPasswordByteLimitsWithoutEchoingSecrets() throws Exception {
        User admin = account(UserRole.ADMIN, false, true);
        String token = login(admin.username(), PASSWORD);
        for (var invalid : List.of(Map.of("username", "ab"), Map.of("username", "with@symbol"), Map.of("email", "invalid"),
                Map.of("role", "OWNER"), Map.of("temporaryPassword", "short"), Map.of("temporaryPassword", "\u00e9".repeat(37)))) {
            var payload = request();
            payload.putAll(invalid);
            var result = mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
                            .contentType("application/json").content(json.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest()).andExpect(content().contentType("application/json")).andReturn();
            assertFalse(result.getResponse().getContentAsString().contains((String) payload.get("temporaryPassword")));
            assertTrue(users.findByUsername((String) payload.get("username")).isEmpty());
        }
        var payload = request();
        payload.remove("temporaryPassword");
        mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.temporaryPassword").exists());
        assertFalse(new CreateUserRequest("test", "test@example.invalid", UserRole.USER, PASSWORD).toString().contains(PASSWORD));
    }

    @Test
    void normalizedDuplicateIdentifiersReturnConflictWithoutChangingExistingCredentials() throws Exception {
        User admin = account(UserRole.ADMIN, false, true);
        String token = login(admin.username(), PASSWORD);
        var payload = request();
        mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isCreated());
        User original = users.findByUsername((String) payload.get("username")).orElseThrow();
        for (var duplicate : List.of(Map.of("username", " " + original.username().toUpperCase() + " "),
                Map.of("email", " " + original.email().toUpperCase() + " "))) {
            var conflicting = request();
            conflicting.putAll(duplicate);
            var result = mvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
                            .contentType("application/json").content(json.writeValueAsString(conflicting)))
                    .andExpect(status().isConflict()).andExpect(content().contentType("application/json")).andReturn();
            String body = result.getResponse().getContentAsString();
            assertFalse(body.contains(original.passwordHash()));
            assertFalse(body.contains(PASSWORD));
            assertFalse(body.contains("SQL"));
            assertFalse(body.contains("api.users"));
        }
        assertEquals(original, users.findById(original.id()).orElseThrow());
    }
}

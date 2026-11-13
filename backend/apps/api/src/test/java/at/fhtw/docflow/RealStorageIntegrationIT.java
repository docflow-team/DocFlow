package at.fhtw.docflow;

import at.fhtw.docflow.document.DocumentTestFixtures;
import at.fhtw.docflow.identity.application.PasswordHasher;
import at.fhtw.docflow.identity.application.UserRepository;
import at.fhtw.docflow.identity.domain.User;
import at.fhtw.docflow.identity.domain.UserRole;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Docker-backed integration test exercising real PostgreSQL and the source-built MinIO image.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class RealStorageIntegrationIT {

    private static final String BUCKET = "docflow-it-documents";
    private static final String MINIO_USER = "docflow-it-minio";
    private static final String MINIO_SECRET = "docflow-it-minio-secret";
    private static final Path REPO_ROOT = findRepoRoot();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))
            .withDatabaseName("docflow_it")
            .withUsername("docflow_it")
            .withPassword("docflow-it-db-secret")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(REPO_ROOT.resolve("infra/postgres/01-api-schema.sql")),
                    "/docker-entrypoint-initdb.d/01-api-schema.sql")
            .withStartupTimeout(Duration.ofSeconds(60));

    @Container
    static final GenericContainer<?> MINIO = minioContainer()
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).forStatusCode(200))
            .withStartupTimeout(Duration.ofSeconds(90));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("docflow.jwt.secret", () -> "docflow-integration-test-jwt-secret-at-least-32-bytes");
        registry.add("docflow.storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("docflow.storage.access-key", () -> MINIO_USER);
        registry.add("docflow.storage.secret-key", () -> MINIO_SECRET);
        registry.add("docflow.storage.bucket", () -> BUCKET);
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordHasher passwords;
    @Autowired JdbcTemplate jdbc;

    @Test
    void endToEndWorkflowPersistsInPostgresAndMinioWhileEnforcingIsolationAndCleanup() throws Exception {
        byte[] samplePdf = Files.readAllBytes(REPO_ROOT.resolve("tests/fixtures/sample.pdf"));
        users.save(new User(null, "it-admin", "it-admin@example.invalid",
                passwords.hash("TempAdminPass1!"), UserRole.ADMIN, true, true, 0));

        // Admin must change temporary password before accessing admin endpoints.
        String adminTempToken = login("it-admin", "TempAdminPass1!", true);
        changePassword(adminTempToken, "TempAdminPass1!", "AdminPassword2!");
        String adminToken = login("it-admin", "AdminPassword2!", false);

        // Create second USER account with temporary password, force change and revoke old token.
        mvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "username", "it-alice",
                                "email", "it-alice@example.invalid",
                                "role", "USER",
                                "temporaryPassword", "TempAlicePass1!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        String aliceTempToken = login("it-alice", "TempAlicePass1!", true);
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + aliceTempToken))
                .andExpect(status().isForbidden());
        changePassword(aliceTempToken, "TempAlicePass1!", "AlicePassword2!");
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + aliceTempToken))
                .andExpect(status().isUnauthorized());
        String aliceToken = login("it-alice", "AlicePassword2!", false);

        // Upload real fixture PDF as Alice and synthetic PDF as Admin.
        long aliceDocId = uploadPdf(aliceToken, samplePdf, "sample.pdf", "Alice Utility Bill", "Monthly statement");
        long adminDocId = uploadPdf(adminToken, DocumentTestFixtures.pdf(1), "admin.pdf", "Admin Contract", null);
        String aliceObjectKey = objectKeyFor(aliceDocId);
        String adminObjectKey = objectKeyFor(adminDocId);

        MinioClient minioClient = MinioClient.builder()
                .endpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000))
                .credentials(MINIO_USER, MINIO_SECRET)
                .build();
        assertEquals(samplePdf.length, minioClient.statObject(
                StatObjectArgs.builder().bucket(BUCKET).object(aliceObjectKey).build()).size());

        // List, details and exact binary download for Alice.
        mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(aliceDocId));

        byte[] downloaded = mvc.perform(get("/api/v1/documents/{id}/content", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(samplePdf, downloaded);

        // Metadata update (including null description clearing), categories and normalized tags.
        mvc.perform(put("/api/v1/documents/{id}/metadata", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"title\":\"Updated Bill\",\"description\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Bill"))
                .andExpect(jsonPath("$.description").isEmpty());

        JsonNode categories = json.readTree(mvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andReturn().getResponse().getContentAsString());
        long invoiceId = categoryIdByName(categories, "Invoice");
        long receiptId = categoryIdByName(categories, "Receipt");

        mvc.perform(put("/api/v1/documents/{id}/categories", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("categoryIds", List.of(receiptId, invoiceId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].name").value("Invoice"))
                .andExpect(jsonPath("$.categories[1].name").value("Receipt"));

        mvc.perform(put("/api/v1/documents/{id}/tags", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("tags", List.of(" Housing ", "Study-Material", "housing")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("housing"))
                .andExpect(jsonPath("$.tags[1]").value("study-material"));

        // Rejected updates preserve existing categories, tags and metadata intact.
        mvc.perform(put("/api/v1/documents/{id}/categories", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("categoryIds", List.of(invoiceId, 99999L)))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/documents/{id}/tags", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("tags", List.of("ok", "   ")))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/documents/{id}", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Bill"))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.tags.length()").value(2));

        // Admin shares "housing" tag and Invoice category on own doc, but receives 404 on Alice's doc.
        mvc.perform(put("/api/v1/documents/{id}/categories", adminDocId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("categoryIds", List.of(invoiceId)))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/documents/{id}/tags", adminDocId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("tags", List.of("housing")))))
                .andExpect(status().isOk());

        for (var request : List.of(
                get("/api/v1/documents/{id}", aliceDocId),
                get("/api/v1/documents/{id}/content", aliceDocId),
                put("/api/v1/documents/{id}/categories", aliceDocId)
                        .contentType("application/json").content("{\"categoryIds\":[]}"),
                delete("/api/v1/documents/{id}", aliceDocId))) {
            mvc.perform(request.header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/documents/{id}", adminDocId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());

        // Category & tag clearing with explicit empty arrays, then restore before deletion test.
        mvc.perform(put("/api/v1/documents/{id}/categories", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"categoryIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isEmpty());
        mvc.perform(put("/api/v1/documents/{id}/tags", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"tags\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags").isEmpty());

        mvc.perform(put("/api/v1/documents/{id}/categories", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("categoryIds", List.of(receiptId)))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/documents/{id}/tags", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("tags", List.of("study-material", "housing")))))
                .andExpect(status().isOk());

        // Delete Alice's document: removes row, joins and MinIO object while preserving shared rows and Admin's doc.
        mvc.perform(delete("/api/v1/documents/{id}", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/documents/{id}", aliceDocId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());

        ErrorResponseException missing = assertThrows(ErrorResponseException.class, () -> minioClient.statObject(
                StatObjectArgs.builder().bucket(BUCKET).object(aliceObjectKey).build()));
        assertEquals("NoSuchKey", missing.errorResponse().code());

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM api.document_document_types WHERE document_id = ?", Integer.class, aliceDocId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM api.document_tags WHERE document_id = ?", Integer.class, aliceDocId));
        assertEquals(7, jdbc.queryForObject("SELECT count(*) FROM api.document_types", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM api.tags", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM api.documents", Integer.class));
        assertTrue(minioClient.statObject(
                StatObjectArgs.builder().bucket(BUCKET).object(adminObjectKey).build()).size() > 0);
    }

    private String login(String username, String password, boolean expectedMustChangePassword) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("login", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(expectedMustChangePassword))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private void changePassword(String token, String currentPassword, String newPassword) throws Exception {
        mvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", currentPassword,
                                "newPassword", newPassword))))
                .andExpect(status().isNoContent());
    }

    private long uploadPdf(String token, byte[] bytes, String filename, String title, String description) throws Exception {
        var request = multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", filename, "application/pdf", bytes))
                .param("title", title)
                .header("Authorization", "Bearer " + token);
        if (description != null) {
            request.param("description", description);
        }
        String body = mvc.perform(request)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("document").get("id").asLong();
    }

    private String objectKeyFor(long documentId) {
        return jdbc.queryForObject("SELECT object_key FROM api.documents WHERE id = ?", String.class, documentId);
    }

    private static long categoryIdByName(JsonNode categories, String name) {
        for (JsonNode category : categories) {
            if (name.equals(category.get("name").asText())) {
                return category.get("id").asLong();
            }
        }
        throw new AssertionError("Missing category: " + name);
    }

    private static GenericContainer<?> minioContainer() {
        String configuredImage = System.getProperty("docflow.test.minio-image",
                System.getenv().getOrDefault("DOCFLOW_TEST_MINIO_IMAGE", ""));
        if (!configuredImage.isBlank()) {
            return new GenericContainer<>(DockerImageName.parse(configuredImage));
        }
        return new GenericContainer<>(new ImageFromDockerfile("docflow-minio:it", false)
                .withFileFromPath(".", REPO_ROOT.resolve("infra/minio")));
    }

    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("tests/fixtures/sample.pdf"))
                    && Files.exists(dir.resolve("infra/minio/Dockerfile"))
                    && Files.exists(dir.resolve("infra/postgres/01-api-schema.sql"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate DocFlow repository root from " + Path.of("").toAbsolutePath());
    }
}

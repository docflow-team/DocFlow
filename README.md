# DocFlow

Document management coursework project for SWEN3. `backend/pom.xml` is the Maven parent build; the Spring Boot REST application lives in `backend/apps/api/` and uses PostgreSQL persistence. The Maven Wrapper stays in `backend/`.

## Prerequisites and configuration

- Docker with Docker Compose v2 or newer
- JDK 25 for running the backend and tests outside Docker
- Network access on the first build so Maven and Docker can download dependencies and images

Copy the committed example configuration to the ignored local `.env` file:

```bash
cp .env.example .env
```

Replace the example `POSTGRES_PASSWORD` and set `MINIO_ROOT_PASSWORD` in `.env` to your own local passwords; MinIO requires at least 8 characters. If you already have `.env`, add the MinIO settings from `.env.example`. Keep `.env` out of Git.

Set `DOCFLOW_JWT_SECRET` in `.env` to a generated value:

```bash
# macOS/Linux
openssl rand -hex 32
```

```powershell
# Windows PowerShell
$jwtBytes = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes); [Convert]::ToBase64String($jwtBytes)
```

## Run with Docker Compose

Run these commands from the repository root:

```bash
docker compose config --quiet
docker compose up --detach --wait db
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/01-api-schema.sql'
docker compose up --build --detach --wait
docker compose ps
```

The first MinIO build downloads and compiles its pinned source inside Docker, which can take several minutes. The API creates `MINIO_BUCKET` if missing; restarting retains the bucket and stored files.

With the default ports, the endpoints are:

- Health: `http://localhost:8080/api/health`
- Documents: `http://localhost:8080/api/v1/documents`
- MinIO console: `http://localhost:9001` (credentials from `.env`)

Follow the backend logs with:

```bash
docker compose logs --follow backend
```

Stop the stack while retaining its PostgreSQL and MinIO volumes:

```bash
docker compose down
```

PostgreSQL uses a named volume. Changing the password in `.env` does not change the password of an already initialized database volume.

The API uses `ddl-auto=update` and retains data across restarts. See [schema lifecycle](docs/architecture.md#database-schema-lifecycle) for limitations and reset behavior.

## Bootstrap the first administrator

After starting the stack, run the command for your shell once. If an account already exists, bootstrap skips creation.

macOS/Linux (Bash or Zsh):

```bash
(
    export DOCFLOW_BOOTSTRAP_USERNAME=admin
    export DOCFLOW_BOOTSTRAP_EMAIL=admin@example.invalid
    printf "Initial admin password: "
    IFS= read -r -s DOCFLOW_BOOTSTRAP_PASSWORD || exit
    printf "\n"
    export DOCFLOW_BOOTSTRAP_PASSWORD
    docker compose run --rm --no-deps -T \
        -e DOCFLOW_BOOTSTRAP_USERNAME -e DOCFLOW_BOOTSTRAP_EMAIL -e DOCFLOW_BOOTSTRAP_PASSWORD \
        backend --spring.profiles.active=bootstrap-admin
)
```

Windows (PowerShell):

```powershell
$env:DOCFLOW_BOOTSTRAP_USERNAME = 'admin'
$env:DOCFLOW_BOOTSTRAP_EMAIL = 'admin@example.invalid'
$bootstrapSecret = Read-Host 'Initial admin password' -AsSecureString
$env:DOCFLOW_BOOTSTRAP_PASSWORD = [System.Net.NetworkCredential]::new('', $bootstrapSecret).Password
try {
    docker compose run --rm --no-deps -T `
        -e DOCFLOW_BOOTSTRAP_USERNAME -e DOCFLOW_BOOTSTRAP_EMAIL -e DOCFLOW_BOOTSTRAP_PASSWORD `
        backend --spring.profiles.active=bootstrap-admin
} finally {
    Remove-Item Env:\DOCFLOW_BOOTSTRAP_USERNAME, Env:\DOCFLOW_BOOTSTRAP_EMAIL, Env:\DOCFLOW_BOOTSTRAP_PASSWORD
    $bootstrapSecret.Dispose()
}
```

Choose a password of at least 8 characters and at most 72 UTF-8 bytes. The administrator must change it at first login. Log in at `/api/v1/auth/login` and change the temporary password at `/api/v1/auth/password` before using document endpoints.

## Build and test

Run from `backend/`:

```bash
./mvnw -B -ntp test
./mvnw -B -ntp clean verify
```

The parent build includes the API module. Service/controller tests mock I/O, domain/mapping tests run without Spring, and application-context/adapter round-trip tests use H2; tests do not require Docker. Reports are written to `backend/apps/api/target/surefire-reports/`. On Windows, use `mvnw.cmd`.

With PostgreSQL and MinIO running and the datasource, MinIO and `DOCFLOW_JWT_SECRET` environment variables set, start the API from `backend/`:

```bash
./mvnw -pl apps/api spring-boot:run
```

Alternatively, after `clean verify`, run the packaged application from `backend/`:

```bash
java -jar apps/api/target/api-0.0.1-SNAPSHOT.jar
```

For direct Java startup, provision the `api` schema using the Compose command above.

## Current API

Send `Authorization: Bearer <accessToken>` on protected requests. Tokens expire after 15 minutes; logout/password change revokes existing tokens. Documents belong to the logged-in user; another owner's records return `404`. Existing metadata-only rows without stored PDFs are hidden.

| Method | Route | Purpose |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | `{login, password}`; username or email |
| GET | `/api/v1/auth/me` | Current user |
| PUT | `/api/v1/auth/password` | `{currentPassword, newPassword}`; then log in again |
| POST | `/api/v1/auth/logout` | Revoke tokens |
| POST | `/api/v1/admin/users` | ADMIN creates `{username, email, role, temporaryPassword}`; `201` |

| Method | Route | Success |
| --- | --- | --- |
| GET | `/api/health` | `200` |
| POST | `/api/v1/documents` | `202`; multipart `file`, `title`, optional `description` |
| GET | `/api/v1/documents?page=0&size=20` | `200`; paginated own PDFs |
| GET | `/api/v1/documents/{id}` | `200`; metadata |
| PUT | `/api/v1/documents/{id}/metadata` | `200`; replace `{title, description}` |
| GET | `/api/v1/documents/{id}/content` | `200`; PDF attachment |
| GET | `/api/v1/documents/{id}/processing` | `200`; inactive `NOT_STARTED` stages |
| GET | `/api/v1/categories` | `200`; name-sorted shared categories |
| PUT | `/api/v1/documents/{id}/categories` | `200`; replace `{categoryIds: [...]}` |
| PUT | `/api/v1/documents/{id}/tags` | `200`; replace `{tags: [...]}` |
| DELETE | `/api/v1/documents/{id}` | `204`; no body |

PDFs must be parseable, unencrypted, nonempty, at most 10 MiB and contain 1-50 pages. Total multipart requests are limited to 12 MiB. Titles are trimmed and required; title/description are limited to 255 characters. Upload returns the document and processing URL; background processing is unavailable. Original files are immutable. Tags are trimmed/lowercased and deduplicated: up to 20 unique tags per document, 1-50 characters each; `[]` clears them. Responses and errors use JSON, except successful PDF downloads and empty deletions. See [OpenAPI](contracts/openapi.yaml) for the full contract.

Runnable examples are in [`tests/http/auth.http`](tests/http/auth.http) and [`tests/http/documents.http`](tests/http/documents.http). The planned target architecture remains documented in [`docs/architecture.md`](docs/architecture.md).

## Bruno collection

Open `DocFlow-Requests/` in Bruno 3.0 or newer and select the **Local** environment.
The collection uses [OpenCollection YAML](https://docs.usebruno.com/opencollection-yaml/overview)
and contains one request per implemented endpoint operation (including authentication).
Start the Docker Compose stack as described above, then send the endpoint requests as needed.
Set `baseUrl`, `documentId` and `categoryId` in the environment for your local data.
Run Login with your local credentials first; it captures the token as a runtime variable. After changing a temporary password, log in again. Upload and create requests capture returned IDs automatically. The upload example uses [sample.pdf](tests/fixtures/sample.pdf); select your own PDF if needed.
Categories are seeded at startup. Choose IDs from List categories; replacing assignments accepts an empty array to clear them.

Requests use the current `/api/...` controller routes; the exported `contracts/openapi.yaml`
documents the implemented routes; the architecture also describes planned features.

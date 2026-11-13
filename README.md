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

Replace the example `POSTGRES_PASSWORD` in `.env` with your own local PostgreSQL password. The example file contains no real secret, and `.env` must not be committed.

## Run with Docker Compose

Run these commands from the repository root:

```bash
docker compose config --quiet
docker compose up --detach --wait db
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/01-api-schema.sql'
docker compose up --build --detach --wait
docker compose ps
```

With the default ports, the endpoints are:

- Health: `http://localhost:8080/api/health`
- Documents: `http://localhost:8080/api/documents`

Follow the backend logs with:

```bash
docker compose logs --follow backend
```

Stop the stack while retaining its PostgreSQL volume:

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

Choose a password of at least 8 characters and at most 72 UTF-8 bytes. The administrator must change it at first login. Authentication endpoints are not implemented yet.

## Build and test

Run from `backend/`:

```bash
./mvnw -B -ntp test
./mvnw -B -ntp clean verify
```

The parent build includes the API module. Service/controller tests mock I/O, domain/mapping tests run without Spring, and application-context/adapter round-trip tests use H2; tests do not require Docker. Reports are written to `backend/apps/api/target/surefire-reports/`. On Windows, use `mvnw.cmd`.

With PostgreSQL running and the datasource environment variables set, start the API from `backend/`:

```bash
./mvnw -pl apps/api spring-boot:run
```

Alternatively, after `clean verify`, run the packaged application from `backend/`:

```bash
java -jar apps/api/target/api-0.0.1-SNAPSHOT.jar
```

For direct Java startup, provision the `api` schema using the Compose command above.

## Current API

| Method | Route | Success |
| --- | --- | --- |
| GET | `/api/health` | `200` |
| POST | `/api/documents` | `201` |
| GET | `/api/documents` | `200` |
| GET | `/api/documents/{id}` | `200` or `404` |
| PUT | `/api/documents/{id}` | `200` or `404` |
| DELETE | `/api/documents/{id}` | `204` or `404` |

All response bodies use `Content-Type: application/json`, including errors. Health returns
`{"status":"UP","message":"DocFlow REST Server is running!"}`. Type assignment returns
`{"documentId":1,"typeId":1}` with the requested IDs. Successful deletion returns
`204 No Content` with no body or content type.

POST and PUT accept JSON containing required `filename` and `contentType` values and an optional `description`. Each field is limited to 255 characters.

Runnable request examples are in [`tests/http/documents.http`](tests/http/documents.http). The planned target architecture remains documented in [`docs/architecture.md`](docs/architecture.md).

## Bruno collection

Open `DocFlow-Requests/` in Bruno 3.0 or newer and select the **Local** environment.
The collection uses [OpenCollection YAML](https://docs.usebruno.com/opencollection-yaml/overview)
and contains one request per implemented endpoint operation (nine requests total).
Start the Docker Compose stack as described above, then send the endpoint requests as needed.
Set `baseUrl`, `documentId` and `typeId` in the environment for your local data.
The create requests also capture returned IDs automatically for subsequent calls.
Document type names must be unique; change the example name if it already exists.

Requests use the current `/api/...` controller routes; the exported `contracts/openapi.yaml`
still contains older `/api/v1` routes and the architecture describes planned features.

# DocFlow

Document management coursework project for SWEN3, with a React frontend and Spring Boot API.

## Prerequisites and configuration

- Docker with Docker Compose v2 or newer
- JDK 25 for running the backend and tests outside Docker
- Node.js 24.22.3 for frontend development (see `frontend/.node-version`)
- Network access on the first build so Maven and Docker can download dependencies and images

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

In PowerShell, use `Copy-Item .env.example .env`.

Set your own `POSTGRES_PASSWORD` and `MINIO_ROOT_PASSWORD` in `.env`; MinIO requires at least 8 characters. Keep `.env` out of Git.

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

Once the stack is healthy, open [http://localhost](http://localhost) in your browser.
For a fresh setup, [bootstrap the first administrator](#bootstrap-the-first-administrator),
then sign in with that username or email and password. The app prompts you to change
the temporary password before continuing. If you set `WEB_PORT`, use
`http://localhost:<WEB_PORT>` instead (for example, `http://localhost:3000`).

The first MinIO source build can take several minutes.

Follow the backend logs with:

```bash
docker compose logs --follow backend
```

Stop the stack while retaining its PostgreSQL and MinIO volumes:

```bash
docker compose down
```

Data persists across restarts. Changing `POSTGRES_PASSWORD` in `.env` does not change an existing database password. See [schema lifecycle](docs/architecture.md#database-schema-lifecycle) for reset behavior.

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

Use a password of 8-72 characters. Sign in at [http://localhost](http://localhost); the UI requires a password change at first login.

## Build and test

Run the ordinary test suite and JaCoCo coverage check (>70% line minimum) from `backend/`:

```bash
./mvnw -B -ntp clean verify
```

These tests do not require Docker. On Windows, use `mvnw.cmd` instead of `./mvnw`.

With Docker running, include the PostgreSQL/MinIO integration test:

```bash
./mvnw -B -ntp clean verify -Pintegration-tests
```

Test reports are in `backend/apps/api/target/surefire-reports/` and `failsafe-reports/`; coverage is at `backend/apps/api/target/site/jacoco/index.html`.

## Frontend development

Use the Node version in [`frontend/.node-version`](frontend/.node-version). From the repository root, these commands work on Windows, macOS and Linux:

```bash
cd frontend
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Keep the Compose backend running; Vite forwards `/api` to `http://localhost:8080`. Reloading ends the in-memory login session.

Run frontend checks from `frontend/`:

```bash
npm run lint
npm run typecheck
npm run format:check
npm test
npm run build
```

The build goes to `frontend/dist/`. Tests use mocked API access and need no running backend.

## Current API

Use the web UI to upload, list, edit metadata/categories/tags, download and delete
your PDFs. PDFs must be unencrypted, contain 1-50 pages and be at most 10 MiB.
Background OCR, search and AI processing are not available yet.

Account creation is currently API-only: an administrator can use
`POST /api/v1/admin/users` through the [Bruno collection](DocFlow-Requests/)
(select **Local**, then log in) or [HTTP examples](tests/http/auth.http).

See [OpenAPI](contracts/openapi.yaml) for endpoint contracts,
[document request examples](tests/http/documents.http) for direct API usage,
[architecture](docs/architecture.md) for design and
[sprint summary](docs/sprint-summary.md) for delivered features and verification.

## Project ZIP

Run `bash scripts/create-zip.sh` from the project root using Bash, Git and `zip`
(on Windows, use Git Bash). It creates `../DocFlow.zip` with current tracked and
non-ignored new files, excluding other ZIPs. Git history, local `.env` settings,
dependencies and generated output are excluded by the repository's ignore rules.
Move or remove an existing archive before running it again.

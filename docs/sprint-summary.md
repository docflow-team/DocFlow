# DocFlow sprint summary

**Course:** SWEN3 / BIF5

**Repository:** [docflow-team/DocFlow](https://github.com/docflow-team/DocFlow)

**Current sprint:** Sprint 2

**Updated:** 13 November 2026

## Delivered functionality

The API stores PDFs in MinIO and owner-scoped metadata in PostgreSQL. A responsive React app served through non-root Nginx in Docker Compose supports login, password change, protected navigation and owner-only PDF, category and tag workflows on `http://localhost`.

- Validated PDF upload/download, paginated listing, details, title/description editing and deletion. Upload returns `202` with inactive `NOT_STARTED` processing stages.
- Owner-only document access, including updates, deletion and category assignment. Other owners' documents return `404`, including to administrators; metadata-only legacy rows are hidden.
- Seven seeded shared categories with a name-sorted list and owner-only replacement/clearing of assignments. Document responses include category IDs, names and descriptions. Existing type definitions and assignments are preserved.
- Owner-only tag replacement/clearing, with trimmed lowercase names, deduplication and limits of 20 unique tags per document and 50 characters per tag. Shared tag rows survive assignment/document deletion.
- Username-or-email login, current-user lookup, password change and logout. Spring Security validates 15-minute bearer JWTs and checks current account state on every authenticated request.
- Admin-only account creation with normalized unique identifiers, a hashed temporary password and a required first password change. Password change and logout revoke existing tokens.
- Explicit first-admin bootstrap, which skips creation when accounts already exist. Temporary-password accounts may only change password or log out.
- Public health endpoint, safe JSON errors and request examples in Bruno and HTTP files.
- Source-built MinIO service, persistent private bucket and a storage adapter for generated-key upload, reading and deletion. API startup creates a missing bucket; bootstrap does not require storage access.
- React login by username/email, required temporary-password change, ordinary password change and sign-out. In-memory sessions expire or end on authenticated `401`; private queries/UI are cleared and late responses ignored.
- React document screens for paginated listing, validated multipart PDF upload (`202` stored without background queue), read-only details and `NOT_STARTED` processing status, independent metadata editing (including `null` description clearing), manual category multi-select (`PUT /documents/{id}/categories` including `Unclassified` clearing), normalized per-tag editing (`PUT /documents/{id}/tags` including empty clearing and `409` retry), authenticated PDF download with original filename, confirmed deletion and neutral `404` handling.
- React/TypeScript/Vite foundation, CSS Modules, app error boundary and typed fetch client for JSON, multipart uploads, binary downloads, problem errors and request cancellation.
- Multi-stage non-root Nginx web container (`frontend/Dockerfile`, `frontend/nginx.conf`) wired into Docker Compose on port `80` (configurable via `WEB_PORT`) with SPA `index.html` fallback, `/api/` proxying to `backend:8080` preserving `/api/v1`, JSON error preservation, 12 MiB multipart request limit and no response caching.
- Explicit Docker-backed PostgreSQL `18.6` and source-built MinIO integration suite (`RealStorageIntegrationIT` via Testcontainers + Failsafe under `-Pintegration-tests`, with `-Ddocflow.test.minio-image` image reuse), JaCoCo `0.8.14` coverage enforcement strictly above `70%` (`0.71` line minimum; 80% target) and GitHub Actions CI reporting/verification.

The current routes and payloads are documented in [OpenAPI](../contracts/openapi.yaml); [README](../README.md#current-api) provides the quick reference. All document, category, authentication and account routes use `/api/v1`.

## Technical foundation

Java 25, Spring Boot 4.1.1, Maven Wrapper, PostgreSQL 18.6, source-built MinIO, Testcontainers 2.0.5 and JaCoCo 0.8.14 form the backend and verification stack; Node 24.22.3, React 19.3.1, TypeScript 6.0.3, Vite 8.3.8, React Router 8.5.1, TanStack Query 5.107.1, Vitest 5.1.0 and `nginxinc/nginx-unprivileged:1.30-alpine` form the frontend and web serving stack. Docker Compose is the primary local environment.

```text
React SPA -> Nginx /api/ proxy -> Controller -> application service interface/implementation
                                            -> repository interface -> JPA adapter -> PostgreSQL
                                            -> storage port -> MinIO adapter -> MinIO
```

HTTP DTOs, plain Java business models and JPA entities remain separate types. MapStruct maps their boundaries with strict unmapped-target checks. Endpoint-specific DTOs live with their controllers; authentication results/errors live with the service interface. Shared types retain separate files.

Services own transactions and authorization; repository queries enforce ownership. Open Session in View is disabled. Normal API startup uses `ddl-auto=update` in the `api` schema; disposable tests use `create-drop`, and bootstrap uses `validate`. The database volume retains rows across restarts. See [architecture](architecture.md) for schema limitations and design decisions.

## Verification

The suite covers document behavior, validation, protected mapping fields, assignment/deletion semantics, account persistence and uniqueness, password hashing, bootstrap, JWT validation/revocation and owner isolation. Controller/service tests mock I/O; H2 tests exercise actual Spring wiring, generated mappers and persistence adapters.

Verification on 13 November 2026: `clean verify` passed **78 Surefire tests (0 failures, 0 errors, 0 skipped)** without Docker, achieving **92.93% line coverage (526/566 lines)** on handwritten API code (excluding generated `*MapperImpl` classes) against the JaCoCo `0.8.14` minimum gate (`0.71` / >70%). Coverage includes PDF validation, ownership, protected mapping, pagination, cleanup, category seeding/replacement, tag normalization/limits/shared persistence and bootstrap startup without storage or category seeding.

With Docker running, the explicit `integration-tests` Maven profile (`RealStorageIntegrationIT` via Testcontainers `2.0.5` + Failsafe) passed **1 real-storage integration test (0 failures, 0 errors, 0 skipped)** against PostgreSQL `18.6` (`infra/postgres/01-api-schema.sql`) and the source-built MinIO image (reusing `-Ddocflow.test.minio-image=docflow-minio:ci`), bringing combined line coverage to **94.52% (535/566 lines)** above the 80% target. The integration suite verifies temporary-password enforcement, two-owner isolation (including `ADMIN` receiving `404`), `sample.pdf` upload/list/details/exact content download/metadata updates, category and tag replacement/clearing, invalid-input preservation and deletion removing MinIO objects and join rows while keeping shared `api.document_types`, `api.tags` and the other owner's document intact.

Clean frontend installs (`npm ci`), lint, typecheck, formatting, **36 tests across 3 files** (`vitest run`) and production builds (`vite build`) passed on Node `24.22.3`. Component and transport tests cover validation, pagination, empty states, multipart/metadata/category/tag payloads, failed category loads preserving assignments, draft isolation across sections, `409` tag conflict retry, detail/list query consistency, object-URL cleanup, deletion flows, cache cleanup, expiry, late responses after switching sessions and problem errors.

Docker Compose configuration validation, the multi-stage non-root `docflow-web` build (`node:24.22.3-bookworm-slim` + `nginxinc/nginx-unprivileged:1.30-alpine`), API image build and pinned MinIO source build passed without bundling `node_modules`, `.env`, secrets or `backend:8080` hostnames into client assets. End-to-end verification through Nginx on `http://localhost` (port `80`, disposable Compose stack) passed admin bootstrap (`bootstrap-admin`), temporary-password onboarding and password changes for `admin` and `alice`, uploading `tests/fixtures/sample.pdf`, metadata/category/tag updates persisting across a full `docker compose restart`, byte-identical authenticated download (`SHA-256` match), second-owner `404` isolation, deletion, clean sign-out, empty browser `localStorage`/`sessionStorage`/cookies and a clean console/network log. Earlier checks also verified deep-route SPA fallback, `404`/`401` JSON preservation on `/api/` and missing assets, `10.5 MiB` forwarding to backend `413` vs. `13 MiB` Nginx rejection, `375px`/`320px` responsive layouts and offline error recovery. Disposable containers, networks and volumes were removed while pre-existing volumes stayed intact.

GitHub Actions (`backend-ci.yml` and `frontend-ci.yml`) builds `docflow-minio:ci`, runs unit and integration tests once with `clean verify -Pintegration-tests -Ddocflow.test.minio-image=docflow-minio:ci`, publishes Surefire, Failsafe and combined JaCoCo reports, and builds `docflow-api:ci`. Frontend CI runs npm checks and builds `docflow-web:ci`. Current test commands and report paths are in [README](../README.md#build-and-test). Local verification of the consolidated command passed 78 unit tests, 1 PostgreSQL/MinIO integration test and the coverage gate after removing the unused Spring Boot Testcontainers dependency.

## Sprint 2 scope and remaining work

Sprint 2 delivers all planned authentication and owner-only PDF management capabilities through React/Nginx and local Docker Compose, including manual categories, normalized tags, automated PostgreSQL/MinIO integration verification and JaCoCo >70% coverage enforcement. Account creation stays API-only in Sprint 2; see [agreed contracts](architecture.md#sprint-2-contract-decisions) for contract boundaries.

## Current limitations

- OCR, search, messaging and AI processing remain pending.
- Account listing/editing/disabling/reset and administration screens are deferred; login throttling is required before hosted access.
- Category administration and classification approval/source metadata are deferred; current assignments are manual.
- Existing `public`-schema records are not migrated; metadata-only rows are retained without being exposed as stored PDFs.
- Browser reload ends the memory-only session until persistent cookie/session refresh is implemented in Sprint 5.

## Setup and demonstration

Use [README](../README.md) for cross-platform setup, signing-secret generation, first-admin bootstrap and build/test commands. Run the documented workflow with the [Bruno collection](../DocFlow-Requests/opencollection.yml) or [authentication](../tests/http/auth.http) and [document](../tests/http/documents.http) HTTP requests: bootstrap, change the temporary password, create an account, then upload/list/read/download/edit/tag/assign/delete a PDF.

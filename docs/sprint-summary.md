# DocFlow sprint summary

**Course:** SWEN3 / BIF5

**Updated:** 13 November 2026

## Delivered functionality

The API stores PDFs in MinIO and owner-scoped metadata in PostgreSQL. A responsive React app supports login, password change and protected navigation; document screens remain pending.

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
- React/TypeScript/Vite foundation, CSS Modules, app error boundary and typed fetch client for JSON, multipart uploads, binary downloads, problem errors and request cancellation.

The current routes and payloads are documented in [OpenAPI](../contracts/openapi.yaml); [README](../README.md#current-api) provides the quick reference. All document, category, authentication and account routes use `/api/v1`.

## Technical foundation

Java 25, Spring Boot 4.1.1, Maven Wrapper, PostgreSQL 18.6, MinIO and multi-stage API/storage Docker images form the current stack. Docker Compose is the primary local environment.

```text
Controller -> application service interface/implementation
           -> repository interface -> JPA adapter -> PostgreSQL
```

HTTP DTOs, plain Java business models and JPA entities remain separate types. MapStruct maps their boundaries with strict unmapped-target checks. Endpoint-specific DTOs live with their controllers; authentication results/errors live with the service interface. Shared types retain separate files.

Services own transactions and authorization; repository queries enforce ownership. Open Session in View is disabled. Normal API startup uses `ddl-auto=update` in the `api` schema; disposable tests use `create-drop`, and bootstrap uses `validate`. The database volume retains rows across restarts. See [architecture](architecture.md) for schema limitations and design decisions.

## Verification

The suite covers document behavior, validation, protected mapping fields, assignment/deletion semantics, account persistence and uniqueness, password hashing, bootstrap, JWT validation/revocation and owner isolation. Controller/service tests mock I/O; H2 tests exercise actual Spring wiring, generated mappers and persistence adapters.

Verification on 13 November 2026: `clean verify` passed **78 tests, 0 failures, 0 errors, 0 skipped** in an isolated source copy. Coverage includes PDF validation, ownership, protected mapping, pagination, cleanup, category seeding/replacement, tag normalization/limits/shared persistence and bootstrap startup without storage or category seeding. Ordinary tests mock storage and require no Docker.

The API image build and Compose configuration passed. The tag increment passed **57 HTTP checks** against an isolated PostgreSQL/MinIO deployment: schema upgrade with existing PDFs/categories/accounts, normalization and boundaries, cross-owner tag reuse/isolation, database rollback without partial changes, restart persistence and deletion retaining shared rows. Bootstrap passed without storage/JWT credentials. PDF bytes and category assignments were preserved; credential-log checks passed. Disposable containers, network and volumes were removed. Earlier checks verified PDF file/page/multipart limits and category seeding/replacement.

Clean frontend installs, lint, typecheck, formatting, **25 tests** and production builds passed on Node 24.22.3 on Windows and Linux. Chrome checks at 1280px, 375px and 320px verified responsive authentication screens without horizontal overflow. Against a disposable real Compose API, verified rejected login, temporary-password routing, server field errors, password change and fresh login, token revocation, logout, reload ending the session and offline retry. Browser storage/cookies stayed empty. Component tests cover validation, cache cleanup, expiry and late responses after switching sessions; transport tests cover JSON/multipart/binary and problem errors. Viewport emulation was cleared and disposable processes/containers/volumes were removed.

GitHub Actions runs Java verification for PRs and pushes to `dev`/`main`, publishes Surefire reports and verifies both API and MinIO source image builds. A separate frontend workflow runs the verified npm checks. Automated PostgreSQL/MinIO integration tests and the coverage gate remain planned. Current test commands and report paths are in [README](../README.md#build-and-test).

## Sprint 2 scope and remaining work

Sprint 2 targets login and owner-only PDF management through React/Nginx and local Docker Compose, including tags and manual categories. Account creation is API-only. See the [agreed contracts](architecture.md#sprint-2-contract-decisions) for scope and the [task list](sprint-2-tasks.md) for each task's purpose; task progress is tracked separately in [sprint-2-progress.md](sprint-2-progress.md).

Remaining work adds document React screens, Nginx and focused integration/coverage checks. Upload returns `202 Accepted` with non-active `NOT_STARTED` stages; background processing arrives later.

## Current limitations

- Document screens, Nginx serving, OCR, search, messaging and AI processing remain pending.
- Account listing/editing/disabling/reset and administration screens are deferred; login throttling is required before hosted access.
- Category administration and classification approval/source metadata are deferred; current assignments are manual.
- Existing `public`-schema records are not migrated; metadata-only rows are retained without being exposed as stored PDFs.
- Automated repository tests use H2; PostgreSQL verification is currently manual. Coverage enforcement is not configured yet.

## Setup and demonstration

Use [README](../README.md) for cross-platform setup, signing-secret generation, first-admin bootstrap and build/test commands. Run the documented workflow with the [Bruno collection](../DocFlow-Requests/opencollection.yml) or [authentication](../tests/http/auth.http) and [document](../tests/http/documents.http) HTTP requests: bootstrap, change the temporary password, create an account, then upload/list/read/download/edit/tag/assign/delete a PDF.

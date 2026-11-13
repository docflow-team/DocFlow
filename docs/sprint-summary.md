# DocFlow — Sprint summary

**Course:** SWEN3 / BIF5

**Updated:** 13 November 2026

**Status:** The backend foundation and the required document metadata API are implemented. Document types and their many-to-many assignment to documents are available as an additional first domain feature. Sprint 2 has moved the application into a Maven API module and separated its HTTP, business and persistence boundaries using MapStruct, without changing its HTTP behavior.

User persistence, password hashing and explicit first-admin bootstrap are now implemented. Authentication endpoints and document ownership are still pending. Normal API startup uses `ddl-auto=update` in the `api` schema, retaining existing rows across restarts.

## Sprint goal

Sprint 2 targets the local React/Nginx workflow for login and owner-only PDF management, including tags and manual categories. Account creation is API-only; administration screens, taxonomy lifecycle/approval metadata and hosting investigation are deferred. See the [architecture](architecture.md#sprint-2-contract-decisions) for the revised scope. These features remain planned unless listed as implemented below.

Sprint 1 established DocFlow as a Spring Boot REST application. The current application manages document metadata in PostgreSQL and exposes the required health and document CRUD endpoints.

The implementation is a backend foundation, not yet a complete document management system. It stores metadata only; no PDF bytes are uploaded or stored.

## Implemented functionality

### Required document API

- Create a document metadata record
- List all document metadata records
- Retrieve one document by ID
- Replace the editable metadata of an existing document
- Delete an existing document
- Return **404 Not Found** when a requested document does not exist
- Generate the database ID and creation timestamp automatically
- Validate the document request before it reaches the service

A document currently contains:

| Field | Description |
| --- | --- |
| id | Database-generated identifier |
| filename | Required, non-blank, maximum 255 characters |
| contentType | Required, non-blank, maximum 255 characters |
| description | Optional, maximum 255 characters |
| createdAt | Automatically generated creation timestamp |

The content type is currently validated only as required text. The application does not inspect actual PDF content because the request contains JSON metadata rather than file bytes.

### Document types

The following additional Sprint 1 foundation is implemented:

- Create a document type
- List all document types
- Enforce a unique document type name at database level
- Assign an existing type to an existing document
- Persist the many-to-many relationship in the **document_document_types** table

A document can have multiple types, and a type can belong to multiple documents. Example types include Invoice, Contract, and Certificate.

The assignment is persisted but is not yet included in the document response. It therefore cannot currently be read through the document GET endpoint.

### Technical foundation

- Java 25 and Spring Boot 4.1.1
- Spring Web MVC for the REST API
- Spring Data JPA and Hibernate for persistence
- PostgreSQL 18.6 in Docker Compose
- H2 for Spring application-context and adapter round-trip tests
- Separate HTTP DTOs, plain Java business models and JPA entities
- MapStruct 1.6.3 for DTO/model and entity/model boundaries, with strict unmapped-target checks
- Application services and business repository interfaces, backed by JPA adapters
- Service-owned transactions, read-only queries and disabled Open Session in View
- Separate User business/JPA models, normalized unique identifiers and persisted account state
- Spring Security crypto-only dependency for delegating bcrypt password hashing
- Explicit non-web first-admin bootstrap with schema validation
- Constructor injection in controllers and services
- Jakarta Bean Validation for document requests
- Maven Wrapper for reproducible builds
- Multi-stage Docker image for the backend
- GitHub Actions workflow for tests and Docker image verification

## Current architecture

~~~text
HTTP request
    -> Controller
    -> Application service interface / implementation
    -> Business repository interface
    -> JPA adapter and Spring Data repository
    -> PostgreSQL
~~~

The backend is organized as follows:

~~~text
backend/
├── pom.xml                         Maven parent; includes apps/api
├── mvnw / mvnw.cmd                 Maven Wrapper
└── apps/api/
    ├── pom.xml                     Spring Boot API dependencies/build
    └── src/main/java/at/fhtw/docflow/
        ├── BackendApplication.java
        ├── api/                    Health endpoint and shared error handling
        ├── configuration/          Shared MapStruct configuration
        ├── identity/               User domain/application/persistence, hashing and bootstrap
        └── document/
            ├── api/                Controllers, HTTP DTOs and DTO/model mappers
            ├── application/        Service interfaces/implementations and repository ports
            ├── domain/             Plain Java Document and DocumentType models
            └── persistence/        Entities, entity/model mappers, adapters and Spring Data
~~~

Tests follow the same packages under `apps/api/src/test/java`. Controllers and application services exchange business models rather than JPA entities; application services no longer use HTTP request/response DTOs. Document behavior validates metadata, preserves server-controlled fields and manages assignments by type ID. Persistence adapters resolve shared JPA references and map back to detached business models within service transactions. The database tables and API payloads remain unchanged.

## Required API contract

Default base URL: **http://localhost:8080**

| **Method** | **Route** | **Success** |
| --- | --- | --- |
| GET | /api/health | 200 |
| POST | /api/documents | 201 |
| GET | /api/documents | 200 |
| GET | /api/documents/{id} | 200 or 404 |
| PUT | /api/documents/{id} | 200 or 404 |
| DELETE | /api/documents/{id} | 204 or 404 |

The controllers implement this contract directly:

- POST returns **201 Created**.
- Successful reads and updates return **200 OK**.
- A successful delete returns **204 No Content**.
- Read, update, and delete return **404 Not Found** for an unknown document ID.
- Invalid document requests return **400 Bad Request**.

### Additional document-type endpoints

| Method | Route | Success |
| --- | --- | --- |
| POST | /api/document-types | 201 |
| GET | /api/document-types | 200 |
| POST | /api/documents/{documentId}/types/{typeId} | 200 |

These endpoints extend the required core contract without changing its routes or status codes.

All response bodies, including centralized problem-detail errors, use `application/json`.
Health returns a JSON status/message object; assignment returns the document/type IDs.
Assigning a missing document or type returns **404 Not Found**. Successful deletion
returns **204 No Content** with no body or content type.

## Persistence and configuration

The application uses PostgreSQL by default. The main variables are documented in **.env.example**:

| Variable | Example/default | Purpose |
| --- | --- | --- |
| POSTGRES_DB | docflow | Database name |
| POSTGRES_USER | docflow | Database user |
| POSTGRES_PASSWORD | docflow | Local example password; change it for your environment |
| POSTGRES_PORT | 5432 | PostgreSQL host port |
| API_PORT | 8080 | Backend host port with Docker Compose |
| SERVER_PORT | 8080 | Backend port when Java is started directly |

Hibernate now uses **spring.jpa.hibernate.ddl-auto=update** and default schema **api**. Normal API startup applies supported schema changes and retains existing rows, including user accounts, in the named **postgres18_data** volume. Column renames, changed constraints and new required fields with existing rows may need explicit SQL changes or a deliberate database reset. Disposable tests retain `create-drop`; bootstrap retains `validate`. `infra/postgres/01-api-schema.sql` provisions the namespace only; the table definitions come from JPA. Existing `public`-schema records are not migrated. Changing credentials in **.env** does not change the credentials of an already initialized database volume.

The explicit `bootstrap-admin` profile uses **validate**, starts without an HTTP server and exits after its work. It creates an enabled initial ADMIN with a temporary-password flag and token version 0 only when no account exists; repeating it preserves existing credentials. Username/email lookup is normalized, and both identifiers have separate unique constraints. Only encoded bcrypt passwords are persisted; the 72-byte limit is checked before hashing and comparison. Setup and credential input commands are in README. Authentication itself remains pending.

## Tests and continuous integration

Run the complete backend verification from the backend directory:

~~~bash
./mvnw -B -ntp clean verify
~~~

Verified result on 20 October 2026: **31 tests, 0 failures, 0 errors, 0 skipped** (`mvnw.cmd -B -ntp verify`). Controller tests check JSON response media types and bodies, including validation, missing resources, malformed JSON, unsupported request media types and unexpected errors.

Restructuring verification on 12 November 2026: the baseline suite passed before the move, and the parent/API reactor passed `mvnw.cmd -B -ntp clean verify` afterward with **31 tests, 0 failures, 0 errors, 0 skipped**. The executable artifact is now `backend/apps/api/target/api-0.0.1-SNAPSHOT.jar`, and CI collects reports from `backend/apps/api/target/surefire-reports/`. The `/api/...` routes, response bodies, validation and Hibernate `update` setting are unchanged.

Layering verification on 13 November 2026: `mvnw.cmd -B -ntp clean verify` passed **42 tests, 0 failures, 0 errors, 0 skipped**. New checks exercise metadata invariants without Spring, request/entity mapping of protected fields and null descriptions, conversion of shared type entities into IDs, and service protection against incoming identity/time/assignment changes. An H2 round-trip test uses the actual service proxies, adapters and generated mappers across transactions; repeated assignment remains unique, metadata updates preserve assignments, and document deletion preserves the shared type. PostgreSQL integration-test automation and the coverage gate remain pending.

The image rebuilt successfully after the layering changes. An isolated Compose deployment with PostgreSQL passed **24 HTTP checks** across all nine operations, including reads and metadata updates after assignment, repeated assignment, description clearing and shared-type preservation after deletion. The temporary containers, network and database volume were removed.

User foundation verification on 13 November 2026: `mvnw.cmd -B -ntp clean verify` passed **51 tests, 0 failures, 0 errors, 0 skipped**. Added tests cover identifier normalization/uniqueness, account-state persistence, bcrypt hashing and matching without password normalization, UTF-8 byte limits, first-admin creation, safe repeat behavior and invalid bootstrap configuration without secret echoing.

The user-foundation Docker image built successfully. An isolated PostgreSQL/Compose check passed **28 HTTP checks** plus database/bootstrap checks: all four mapped tables were in `api`, normal startup created no account, explicit bootstrap stored the expected normalized admin state and bcrypt hash, and repeating bootstrap preserved credentials and document data. The initial verification used the former `create` setting and demonstrated table resets; the follow-up verification for `update` is recorded below. Temporary containers, network and database volume were removed; existing project data was untouched.

Schema-policy follow-up on 13 November 2026: `mvnw.cmd -B -ntp verify` passed **51 tests, 0 failures, 0 errors, 0 skipped**. The Docker image build and Compose configuration passed. A fresh isolated PostgreSQL deployment using `update` passed **30 HTTP checks**, initial schema creation and repeat-bootstrap checks. A normal API restart preserved the administrator and its password hash, document metadata, document types and assignments. Disposable containers, network and database volume were removed; existing project data was untouched.

Bootstrap cleanup on 13 November 2026: removed duplicated configuration validation/normalization; the User model and password hasher own those rules. Secret redaction and repeat-bootstrap safety remain. All **51 tests** passed; the rebuilt Docker image and isolated PostgreSQL deployment passed **30 HTTP checks**, bootstrap checks and restart-persistence checks. The disposable stack and volume were removed.

Container verification on 13 November 2026: Compose configuration and the restructured Docker image build passed. An isolated Compose deployment of that image with PostgreSQL passed **15 HTTP checks across all nine endpoint operations**, covering the document/type workflow, generated/protected fields, JSON success/error media types, validation/malformed JSON (`400`), missing resources (`404`), response negotiation (`406`), unsupported request media types (`415`) and empty successful deletion (`204`). The temporary containers, network and database volume were removed; existing project data was untouched.

Docker Compose verification on 20 October 2026: rebuilt the backend and checked all nine endpoint operations. Every response body used `application/json`; successful deletion returned an empty **204**. Validation/malformed JSON (**400**), missing resources (**404**), unacceptable response media type (**406**) and unsupported request media type (**415**) also returned JSON. The demo document was deleted; one uniquely named verification type remains because type deletion is not implemented.

The test suite covers:

- Document service CRUD behavior
- Missing-document behavior for reads, updates, and deletes
- Document-type assignment logic
- Document controller routes, status codes, and request validation
- Health endpoint route, status, and response body
- Document type service and controller
- Plain Java document invariants and protected metadata updates
- Generated mapping boundaries, null values and type-ID conversion
- Complete Spring application-context startup and adapter round trip with H2
- User persistence/uniqueness, password hashing/limits and admin bootstrap behavior

GitHub Actions runs **clean verify** for pull requests and pushes to **dev** or **main**. After successful tests, it also verifies the backend Docker image build. A coverage gate and PostgreSQL integration tests are not configured yet.

## Known limitations

- Only metadata is stored; PDF upload and file storage are not implemented.
- MinIO or another object store is not connected.
- Authentication, authorization, and document ownership are not implemented.
- Document types currently provide only create and list operations.
- Assigned types are not included in document responses.
- Document type requests do not yet have Bean Validation constraints.
- Lists are not paginated and have no guaranteed order.
- **contracts/openapi.yaml** represents an earlier API design and is not aligned with the current unversioned routes.
- Frontend, search, OCR, messaging, and AI classification are not implemented.

## Usage and demonstration

### Prerequisites

- Docker with Docker Compose v2
- JDK 25 when running the backend outside Docker
- Network access during the first build to download Maven dependencies and Docker images

### 1. Start the complete stack

Run the commands from the repository root:

~~~bash
cp .env.example .env
~~~

Change the password in **.env** for your local environment, then start the stack:

~~~bash
docker compose config --quiet
docker compose up --detach --wait db
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/01-api-schema.sql'
docker compose up --build --detach --wait
docker compose ps
~~~

Follow the backend logs when needed:

~~~bash
docker compose logs --follow backend
~~~

### 2. Verify the health endpoint

~~~bash
BASE_URL=http://localhost:8080
curl -i "$BASE_URL/api/health"
~~~

Expected status: **200 OK**

Expected body:

~~~json
{"status":"UP","message":"DocFlow REST Server is running!"}
~~~

### 3. Create a document

~~~bash
curl -i -X POST "$BASE_URL/api/documents" \
  -H 'Content-Type: application/json' \
  -d '{"filename":"invoice.pdf","contentType":"application/pdf","description":"October electricity invoice"}'
~~~

Expected status: **201 Created**

The JSON response contains the generated ID. Use that value in the following commands:

~~~bash
DOCUMENT_ID=1
~~~

Replace 1 if the returned ID is different.

### 4. List and retrieve documents

~~~bash
curl -i "$BASE_URL/api/documents"
curl -i "$BASE_URL/api/documents/$DOCUMENT_ID"
~~~

Both successful requests return **200 OK**.

### 5. Update document metadata

~~~bash
curl -i -X PUT "$BASE_URL/api/documents/$DOCUMENT_ID" \
  -H 'Content-Type: application/json' \
  -d '{"filename":"invoice-october.pdf","contentType":"application/pdf","description":"Updated electricity invoice"}'
~~~

Expected status: **200 OK**

### 6. Demonstrate request validation

~~~bash
curl -i -X POST "$BASE_URL/api/documents" \
  -H 'Content-Type: application/json' \
  -d '{"filename":"","contentType":"application/pdf","description":"Invalid example"}'
~~~

Expected status: **400 Bad Request**

### 7. Create and assign a document type

Create a type:

~~~bash
curl -i -X POST "$BASE_URL/api/document-types" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Invoice","description":"Invoices and payment requests"}'
~~~

Expected status: **201 Created**

List the types and copy the returned ID:

~~~bash
curl -i "$BASE_URL/api/document-types"
TYPE_ID=1
~~~

Replace 1 if the returned ID is different, then assign the type:

~~~bash
curl -i -X POST "$BASE_URL/api/documents/$DOCUMENT_ID/types/$TYPE_ID"
~~~

Expected status: **200 OK** with JSON containing the assigned `documentId` and `typeId`.

Because assigned types are not yet exposed in the document response, the persisted join row can be shown directly in PostgreSQL:

~~~bash
docker compose exec db sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT * FROM document_document_types;"'
~~~

### 8. Delete the document and demonstrate 404

~~~bash
curl -i -X DELETE "$BASE_URL/api/documents/$DOCUMENT_ID"
~~~

Expected status: **204 No Content**

Requesting the deleted document demonstrates the required missing-resource behavior:

~~~bash
curl -i "$BASE_URL/api/documents/$DOCUMENT_ID"
~~~

Expected status: **404 Not Found**

### 9. Stop the stack

~~~bash
docker compose down
~~~

This retains the PostgreSQL volume and existing API rows across the next normal startup. To deliberately delete all local DocFlow database data:

~~~bash
docker compose down --volumes
~~~

### Alternative: run the backend with Java

Start only PostgreSQL:

~~~bash
docker compose up --detach --wait db
~~~

Load the trusted local **.env** file into the shell and start Spring Boot:

~~~bash
set -a
source .env
set +a
cd backend
./mvnw -pl apps/api spring-boot:run
~~~

The `api` schema must be provisioned before startup; README contains the current schema and first-admin setup commands.

Alternatively, build and run the executable JAR:

~~~bash
cd backend
./mvnw -B -ntp clean package
java -jar apps/api/target/api-0.0.1-SNAPSHOT.jar
~~~

Reusable HTTP requests are also available in **tests/http/documents.http**.

# DocFlow

Document management coursework project for SWEN3. The current backend is a single Spring Boot module in `backend/` with PostgreSQL persistence.

## Prerequisites and configuration

- Docker with Docker Compose v2 or newer
- JDK 25 for running the backend and tests outside Docker
- Network access on the first build so Maven and Docker can download dependencies and images

Copy the committed example configuration to the ignored local `.env` file:

```bash
cp .env.example .env
```

Replace `change-me-local-only` in `.env` with your own local PostgreSQL password. The example file contains no real secret, and `.env` must not be committed.

## Run with Docker Compose

Run these commands from the repository root:

```bash
docker compose config --quiet
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

## Build and test

Run from `backend/`:

```bash
./mvnw -B -ntp test
./mvnw -B -ntp clean package
```

The tests use an in-memory H2 database and do not require Docker. On Windows, use `mvnw.cmd`.

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

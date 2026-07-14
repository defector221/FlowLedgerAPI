# FlowLedger API

Multi-tenant Invoice, Inventory, Sales, and Purchase Management System backend.

## Stack

- Java 17+ (compatible with Java 21; local builds use Java 17)
- Spring Boot 3.4
- Spring Security + JWT (access + refresh tokens)
- Spring Data JPA / Hibernate
- PostgreSQL + Flyway
- MapStruct, Lombok
- MinIO object storage
- OpenSearch (derived global search index; PostgreSQL remains source of truth)
- OpenPDF invoice generation
- springdoc OpenAPI
- Testcontainers-ready test dependencies

## Quick start

```bash
# Start local OpenSearch + Dashboards (requires Docker)
docker compose up -d

# Run API (expects Postgres + MinIO running separately for full app use)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenSearch: https://localhost:19200 (`admin` / see `FLOWLEDGER_SEARCH_PASSWORD`)
- OpenSearch Dashboards: http://localhost:15601

> Local OpenSearch may run with security enabled and a self-signed certificate. That setup is for development only — **not production-ready**.

## Local OpenSearch

PostgreSQL is the **source of truth**. OpenSearch is a **rebuildable derived index** for global search.

### Start / stop

```bash
# Start OpenSearch + Dashboards
docker compose up -d

# Stop
docker compose down

# Stop and remove the search data volume
docker compose down -v
```

### Health and Dashboards

```bash
# Cluster health (security enabled)
curl -k -u admin:Vayupa2024wan https://localhost:19200/_cluster/health?pretty

# Open Dashboards in a browser
open http://localhost:15601
```

### Search configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `FLOWLEDGER_SEARCH_ENABLED` | `true` | Enable OpenSearch integration |
| `FLOWLEDGER_SEARCH_URL` | `https://localhost:19200` | OpenSearch URL (HTTPS when security is enabled) |
| `FLOWLEDGER_SEARCH_INDEX` | `flowledger-global-search-v1` | Index name |
| `FLOWLEDGER_SEARCH_USERNAME` | `admin` | OpenSearch basic-auth username |
| `FLOWLEDGER_SEARCH_PASSWORD` | _(set in application.yml)_ | OpenSearch basic-auth password |
| `FLOWLEDGER_SEARCH_SSL_VERIFY` | `false` | Verify TLS certificates (`false` for local self-signed) |

When search is disabled (`FLOWLEDGER_SEARCH_ENABLED=false`), the API still starts; search endpoints return a controlled error.

When OpenSearch is temporarily unreachable, `GET /api/v1/search` returns **503** (`Search Unavailable`) without leaking host details.

Local Compose currently runs OpenSearch **with security enabled**. Cluster health:

```bash
curl -k -u admin:Vayupa2024wan https://localhost:19200/_cluster/health?pretty
```

> Do not use local self-signed TLS + disabled certificate verification in production.

### Reindex current organization

Requires `ORGANIZATION_ADMIN` and a Bearer token. Organization is taken from the JWT / `TenantContext` (never from the request body).

```bash
curl -X POST http://localhost:8080/api/v1/search/reindex \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### Troubleshoot local connection

1. Confirm containers: `docker compose ps`
2. Confirm health: `curl -k -u admin:$FLOWLEDGER_SEARCH_PASSWORD https://localhost:19200/_cluster/health`
3. Confirm API config uses `https://localhost:19200` with matching username/password
4. Check API logs for OpenSearch connection warnings on startup
5. If the index is empty after upgrading search, run reindex

## Register & login

```http
POST /api/v1/auth/register
{
  "organizationName": "Acme Traders",
  "email": "admin@acme.test",
  "password": "Password@123",
  "firstName": "Admin",
  "lastName": "User"
}
```

Then update organization state / GSTIN via `PUT /api/v1/organizations/current` before creating GST invoices.

Use `Authorization: Bearer <accessToken>` on subsequent calls.

## Module layout

```
com.flowledger
  auth / organization / customer / supplier / product
  warehouse / inventory / sales / purchase / payment
  tax / template / pdf / report / dashboard / audit / storage / search
```

## Key behaviors

- Every business row is scoped by `organization_id`
- Inventory is append-only via `inventory_transactions` (idempotent keys)
- Sales invoice confirmation posts `SALE` stock movements once
- GRN confirmation posts `PURCHASE` stock movements once
- GST: intra-state CGST+SGST, inter-state IGST; tax snapshots stored on lines
- Document numbers use pessimistic locking on `document_sequences`
- Global search indexes products, customers, suppliers, sales invoices, and purchase invoices after PostgreSQL commit

## Tests

```bash
mvn test
```

## Configuration

See `src/main/resources/application.yml` for JWT, MinIO, datasource, search, and CORS settings.

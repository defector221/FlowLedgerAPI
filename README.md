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
- OpenPDF invoice generation
- springdoc OpenAPI
- Testcontainers-ready test dependencies

## Quick start

```bash
# Start PostgreSQL + MinIO (requires Docker Desktop running)
docker compose up -d

# Run API
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- MinIO console: http://localhost:9001 (`minioadmin` / `minioadmin`)

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
  tax / template / pdf / report / dashboard / audit / storage
```

## Key behaviors

- Every business row is scoped by `organization_id`
- Inventory is append-only via `inventory_transactions` (idempotent keys)
- Sales invoice confirmation posts `SALE` stock movements once
- GRN confirmation posts `PURCHASE` stock movements once
- GST: intra-state CGST+SGST, inter-state IGST; tax snapshots stored on lines
- Document numbers use pessimistic locking on `document_sequences`

## Tests

```bash
mvn test -Dtest=GstCalculationServiceTest,CommonUtilTest
```

## Configuration

See `src/main/resources/application.yml` for JWT, MinIO, datasource, and CORS settings.

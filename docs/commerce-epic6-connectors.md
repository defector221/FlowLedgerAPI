# Epic 6 — Enterprise Connector Platform

## Components

- `ConnectorSyncService` — enqueue sync jobs
- `ConnectorSyncProcessor` — poll, retry, DLQ
- Event triggers: `StorePublished`, `ProductUpdated`

## Schema

- V91: `commerce_connector_sync_jobs`, `commerce_connector_sync_dlq`

## API

- `GET /api/v1/commerce/connectors/jobs`
- `POST /api/v1/commerce/connectors/jobs` — manual enqueue

## Connector types (staged)

| Type | Status |
|------|--------|
| FLOWLEDGER | Native catalog adapter (stub executor) |
| REST | Generic REST adapter (stub) |
| SAP / Oracle | Plugin SPI (future) |

Job statuses: PENDING → RUNNING → COMPLETED | FAILED (→ DLQ after max attempts)

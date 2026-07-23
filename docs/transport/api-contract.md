# Transport API contract (multi-leg extension)

## Unchanged (backward compatible)

All existing `/api/v1/transport/shipments` workflow endpoints remain:

- `POST/GET/PUT /shipments`, `from-challan`, `submit`, `approve`, `reject`, `assign`,
  `start-loading`, `loaded`, `dispatch`, `checkpoint`, `deliver`, `close`, `cancel`, `search`, `timeline`

Single-leg clients keep working: `assign` with one leg is still valid.

## Additive endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/shipments/{id}/events` | Manual timeline note (no status change) |
| GET | `/shipments/{id}/legs` | List legs |
| POST | `/shipments/{id}/legs` | Add leg |
| PUT | `/legs/{id}` | Update leg |
| DELETE | `/legs/{id}` | Soft-delete leg |
| PATCH | `/legs/{id}/status` | Set leg status |
| PATCH | `/legs/{id}/location` | GPS update + history |
| PATCH | `/legs/{id}/dispatch` | Dispatch leg |
| PATCH | `/legs/{id}/arrive` | Mark arrived |
| PATCH | `/legs/{id}/complete` | Complete leg |
| GET | `/legs/{id}/documents` | List leg documents |
| POST | `/legs/{id}/documents` | Attach document metadata |
| GET | `/legs/{id}/locations` | GPS history |

## Validation

- Unique `sequenceNo` per shipment
- Destination of leg N should match origin of leg N+1 (strict sequential)
- Cannot dispatch leg N before leg N-1 is `COMPLETED` (default on)
- Arrival ≥ departure
- Driver/vehicle overlap on active legs (configurable flags, default on for vehicle/driver)

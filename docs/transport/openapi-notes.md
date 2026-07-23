# OpenAPI notes — multi-leg transport

Additive endpoints under `/api/v1/transport` (see `api-contract.md`).

Existing shipment workflow operations keep the same paths and payloads for single-leg clients.
`AssignmentRequest.legs` remains required for fleet assign; customer-arranged shipments skip assign.

New response fields on `ShipmentResponse` / `LegResponse` are additive JSON properties and do not remove prior fields.

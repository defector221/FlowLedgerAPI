# Service design

## ShipmentService

Owns shipment CRUD, challan adapters, existing workflow transitions, timeline listing,
manual events, cost rollup entry points, and shipment status recomputation from legs.

## ShipmentLegService

Owns leg CRUD and leg lifecycle (`dispatch` / `arrive` / `complete` / `location` / documents).
After each material change:

1. Validate sequence/chain/overlaps
2. Persist leg
3. Write `shipment_events` timeline row
4. Enqueue outbox (`LegCreated`, `LegDispatched`, …)
5. Call `ShipmentService.recomputeFromLegs(shipmentId)` for status + cost totals

## IntegrationOutboxService

Reused for `ShipmentCreated|Updated|Completed` and `Leg*` events. Subscribers stay external.

## Compatibility

`replaceLegs` on assign still works for single-leg clients but sets new columns
(`status=READY`, org/audit) so multi-leg features remain consistent.

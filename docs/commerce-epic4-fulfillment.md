# Epic 4 — Omnichannel Fulfillment

Epic 4 delivers a reusable **Fulfillment Engine** with pluggable strategies for delivery, pickup, click & collect, and Scan & Go. ERP documents post at type-specific milestones (hybrid model).

## Sub-epics

| Sub-epic | Scope |
|---------|--------|
| **4.1** | Engine, pick/pack/ready, delivery/pickup/collect, staff + customer UIs, events, APIs |
| **4.2** | Scheduled delivery/pickup slots, capacity, driver/workload helpers |
| **4.3** | Scan & Go session flow → order on exit verify |

## Lifecycle

```
CREATED → ACCEPTED → PICKING → PACKING → READY → FULFILLING → COMPLETED
                                              ↘ CANCELLED
```

Scan & Go bypasses pick/pack; order is created at exit verification.

## Schema

- `V82` — fulfillment orders, status history, picking/packing tasks, pickup sessions, collect tokens, delivery assignments
- `V84` — delivery/pickup slots, slot bookings, store capacity
- `V85` — scan sessions, items, exit tokens
- `V86` — `COMMERCE_FULFILLMENT_VIEW`, `COMMERCE_FULFILLMENT_MANAGE` permissions

## Staff APIs (tenant JWT)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/commerce/fulfillment/orders?storeId=&status=` | List fulfillment orders |
| GET | `/commerce/fulfillment/dashboard?storeId=` | Queue counts |
| POST | `/commerce/fulfillment/accept` | Accept order |
| POST | `/commerce/picking/start`, `/complete` | Picking tasks |
| POST | `/commerce/packing/start`, `/complete` | Packing tasks |
| POST | `/commerce/pickup/arrived`, `/verify`, `/collect/verify` | Pickup handoff |
| POST | `/commerce/delivery/assign`, `/dispatch`, `/delivered` | Delivery |
| GET | `/commerce/fulfillment/slots?storeId=&type=&date=` | Scheduled slots (4.2) |

## Customer APIs (commerce JWT)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/commerce/orders/{id}/tracking` | Status timeline |
| GET | `/commerce/orders/{id}/pickup-qr` | Collect QR when ready |
| POST | `/commerce/scan/session`, `/item`, `/payment`, `/exit` | Scan & Go (4.3) |

## ERP milestones

| Type | Milestone | ERP action |
|------|-----------|------------|
| Delivery | ACCEPTED | Sales order |
| Delivery | OUT_FOR_DELIVERY | Invoice |
| Pickup / Collect | PICKED_UP / QR_VERIFIED | POS sale |
| Scan & Go | SCAN_EXIT_VERIFIED | POS sale |

## UI

- **FlowLedgerUI** — `/commerce/fulfillment/*` dashboard and queues
- **flowledger-commerce-ui** — order tracking timeline, pickup QR, checkout fulfillment type

## Test flow (4.1)

1. Place order (Epic 3 checkout) → `FulfillmentOrder` CREATED
2. Staff accept → picking task created
3. Pick → pack → ready
4. Pickup verify or delivery delivered → ERP posted, order COMPLETED

# Platform Event Catalog

Transactional outbox table: `platform_event_outbox` (V88).

## Envelope

| Field | Description |
|-------|-------------|
| `eventType` | Canonical event name |
| `eventVersion` | Schema version (default 1) |
| `organizationId` | Tenant scope |
| `aggregateType` | Entity type (e.g. CommerceOrder) |
| `aggregateId` | Entity id |
| `payload` | JSON body |
| `correlationId` | Trace id |
| `actorId` | User/customer who triggered the event |
| `occurredAt` | Business timestamp |

## Launch events

| Event | Source | Payload keys |
|-------|--------|--------------|
| `OrderCreated` | `CommerceOrderPlacedEvent` | orderId, customerId |
| `OrderCompleted` | Fulfillment `completeFulfillment` | orderId, customerId |
| `PaymentSucceeded` | Checkout confirm | checkoutSessionId, amount |
| `CustomerRegistered` | `CustomerRegisteredEvent` | customerId |
| `StorePublished` | `StorePublishedEvent` | storeId |
| `ProductUpdated` | `CommerceCatalogChangeEvent` | entityType, entityId, storeId |
| `PromotionApplied` | Promotion redemption | ruleId, orderId, discountApplied |
| `RewardCredited` | Reward Engine (earn) | customerId, outcomeType, amount, ruleCode |
| `CartCreated` | Cart service (optional) | cartId, customerId |

## Dispatch

`PlatformEventDispatcher` polls unpublished rows and invokes registered handlers. Consumers register via `@PostConstruct` + `dispatcher.register(eventType, handler)`.

Package: `com.flowledger.platform.event.bus`

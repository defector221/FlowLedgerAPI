# FlowLedger AI — Flows

## Tools vs RAG

| Path | Source of truth | Use when |
|------|-----------------|----------|
| **Tools** | Live ERP services (`InventoryService`, `SalesInvoiceService`, …) | Current stock, invoices, payments |
| **RAG** | `ai_knowledge_documents` + embeddings | Policies, SOPs, pasted notes |

Chat orchestration may combine both: tool summaries + retrieved chunks → LLM.

## Recommendation heuristics (Phase 5)

| Trigger | Type | Signal |
|---------|------|--------|
| Product upsert / full generate | `INVENTORY_RISK` | `InventoryService.lowStockAlerts` |
| Payment scan | `DUPLICATE_PAYMENT` | Same amount+date+type ≥ 2 |
| Customer upsert / scan | `CUSTOMER_CREDIT_RISK` | Outstanding ≥ advisory threshold |
| Full generate | `CASH_FLOW_RISK` | Aggregate AR outstanding high |

Dedupes open (`NEW`/`OPEN`) rows per type + related entity.

## Event-driven (Phase 6)

- Listen: `SearchIndexUpsertEvent` / `SearchIndexDeleteEvent`
- Phase: `AFTER_COMMIT` only
- Condition: `@ConditionalOnAiEnabled`
- Side effects: `RecommendationGenerator` + `AiLifecycleEventPublisher`
- Prefer **no ERP method edits**; bridge reuses existing search upserts

## Forecasts (Phase 7 / v2.1)

Exponential smoothing over invoice totals (SALES/DEMAND) with MAPE persistence on `ai_forecast_runs`. Cashflow and inventory remain advisory proxies. Gated by `analytics-enabled`.

## Autonomous + Document AI (Phase 8 / v2.2)

`/workflow/document-extract` uses OpenAI vision when keyed, else heuristics. `/workflow/document-confirm` accepts reviewed fields for human-gated ERP create (never silent post).

## Automations (v2.2)

`ai_automations` + cron scheduler + EVENT hooks from `AiSearchEventBridge` (PRODUCT_UPSERT, CUSTOMER_UPSERT, …). Actions are advisory: recommendations / notify / workflow draft. Dry-run supported.

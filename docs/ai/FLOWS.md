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

## Forecasts (Phase 7)

Moving-average / position stubs from invoice counts and stock overview. Persisted to `ai_forecast_runs`. Gated by `analytics-enabled`.

## Autonomous stubs (Phase 8)

`suggest-from-text` returns draft field JSON (`suggestedDocumentType`, amount, GSTIN regex).  
Document/voice endpoints return “not configured” unless flags are on — still never post documents.

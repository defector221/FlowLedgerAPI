# FlowLedger AI — Architecture

## Package layout

```
com.flowledger.ai
├── agent/          # Agent types, factory, selector
├── analytics/      # ForecastService (heuristic)
├── audit/          # AiAuditService
├── chat/           # ChatOrchestrationService
├── config/         # AiProperties, ConditionalOnAiEnabled, AiAutoConfiguration
├── controller/     # REST under /api/v1/ai/**
├── dto/            # AiDtos
├── embedding/      # EmbeddingPipeline
├── entity/         # Conversations, messages, recommendations, forecasts, …
├── event/          # AiSearchEventBridge, AiLifecycleEvent(+Publisher)
├── memory/         # ConversationMemoryService
├── prompt/         # Markdown prompt templates
├── provider/       # AIProvider implementations
├── rag/            # Knowledge + RagService
├── recommendation/ # RecommendationService + RecommendationGenerator
├── repository/
├── tools/          # Domain @Tool facades (call ERP services only)
└── workflow/       # DocumentAiService, VoiceAiStub, WorkflowSuggestionService
```

## Design principles

1. **Toggle-off by default** — when `enabled=false`, AI beans are absent (404 on AI routes).
2. **Tools vs RAG** — tools read live ERP via domain services; RAG retrieves tenant knowledge docs / embeddings.
3. **Never inside business TX** — `AiSearchEventBridge` uses `@TransactionalEventListener(AFTER_COMMIT)` on search index events. Prefer bridge-only (zero ERP edits).
4. **Advisory only** — recommendations and forecasts are heuristics; they do not post journals, invoices, or stock.
5. **Tenant isolation** — all AI persistence is `organization_id`-scoped via `TenantContext`.

## Recommendation types

`INVENTORY_RISK`, `CASH_FLOW_RISK`, `DUPLICATE_PAYMENT`, `SUPPLIER_DELAY`, `CUSTOMER_CREDIT_RISK`, `DEAD_STOCK`, `GST_WARNING`, `PROFIT_OPPORTUNITY`

Status vocabulary: **NEW** (canonical; legacy OPEN normalized), **ACKNOWLEDGED**, **DISMISSED** (+ DONE).

## Event flow

```
ERP service → SearchIndexEventPublisher.upsert
           → AFTER_COMMIT SearchIndexEventListener (OpenSearch)
           → AFTER_COMMIT AiSearchEventBridge (heuristics + AiLifecycleEvent)
```

## Migrations

- `V33__ai_platform.sql` — core AI tables + permissions
- `V34__ai_forecasts.sql` — additive forecast index + OPEN→NEW normalize

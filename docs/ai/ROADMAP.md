# FlowLedger AI Platform — Roadmap

Default master switch: `flowledger.ai.enabled` (local YAML often `true` via `FLOWLEDGER_AI_ENABLED`; defaults to `false` in code). LangChain4j **0.36.2**.

| Phase | Status | Summary |
|-------|--------|---------|
| **1** Provider abstraction | Done | OpenAI + Claude/Gemini/Ollama implementations; Azure stub; `AIProviderRegistry` |
| **2** Chat + agents | Done | Multi-agent orchestration, memory, audit, tools bridge |
| **3** RAG / knowledge | Done | Knowledge docs, embeddings pipeline, retrieval |
| **4** Domain tools | Done | Sales, inventory, GST, payment, CRM, etc. via services |
| **5** Recommendation engine | Done | Heuristic types, NEW/ACKNOWLEDGED/DISMISSED, generator |
| **6** Event-driven AI | Done | `AiSearchEventBridge` AFTER_COMMIT; lifecycle publisher |
| **7** Predictive / analytics | Done | Exponential smoothing forecasts + MAPE; `analytics-enabled` gate |
| **8** Autonomous stubs | Done | Document extract + human-confirm draft gate; voice Whisper |
| **v2 Specialist Agents** | Done | Global Ask, CFO/Accountant/… catalog, multi-agent consult, Whisper STT, Workflow Builder drafts |
| **v2.1** | Done | Chunked RAG + optional pgvector ANN, citations, knowledge admin APIs, SSE chat stream, token budgets, LangChain4j tool catalog bridge, Claude/Gemini/Ollama providers |
| **v2.2 Automation / Commerce** | Done | `ai_automations` cron/event engine, Document AI extract/confirm, store assortment filters, commerce semantic search + support bot |

## Specialist agent catalog (v2)

| Code | Product name |
|------|----------------|
| `ASK` | Global Ask Agent (default; multi-agent) |
| `BUSINESS_ADVISOR` | AI Business Advisor |
| `CFO` | AI CFO (`FINANCE` alias) |
| `ACCOUNTANT` | AI Accountant (`ACCOUNTING` alias) |
| `INVENTORY_PLANNER` | AI Inventory Planner (`INVENTORY` alias) |
| `PROCUREMENT` | AI Procurement Assistant (`PURCHASE` alias) |
| `GST_EXPERT` | AI GST Expert (`GST` alias) |
| `SALES_COACH` | AI Sales Coach (`SALES` alias) |
| `COLLECTIONS` | AI Collections Agent |
| `CRM` | AI CRM |
| `CEO` | CEO Orchestrator |

## Feature flags (`application.yml`)

| Key | Default (code) | Purpose |
|-----|----------------|---------|
| `flowledger.ai.enabled` | `false` | Master switch (`@ConditionalOnAiEnabled`) |
| `chat-enabled` | `true` | Chat orchestration |
| `rag-enabled` | `true` | Retrieval |
| `embeddings-enabled` | `true` | Embedding pipeline |
| `analytics-enabled` | `false` | Forecasts |
| `document-ai-enabled` | `false` | Document extract |
| `voice-enabled` | `false` (local often `true`) | Whisper STT |
| `multi-agent-enabled` | `true` | Ask/CEO/Advisor consult fan-out |
| `workflow-builder-enabled` | `true` | Draft approval workflows |
| `automation-poll-ms` | `60000` | Cron automation scheduler poll |

## UI routes (FlowLedgerUI)

| Route | Permission module |
|-------|-------------------|
| `/ai/chat` | `ai` → `AI_CHAT` |
| `/ai/knowledge` | `ai` → `AI_CHAT` / `AI_KNOWLEDGE` |
| `/ai/recommendations` | `aiRecommendations` → `AI_RECOMMENDATION` |
| `/ai/analytics` | `ai` → `AI_CHAT` |
| `/ai/workflows` | `aiWorkflow` → `AI_WORKFLOW` |
| `/ai/automations` | `aiWorkflow` → `AI_AUTOMATION` |

Global **Ask AI** FAB (⌘J / Ctrl+J) on all authenticated pages when health is up + `AI_CHAT`.

Nav group **AI Assistant** is hidden when `GET /api/v1/ai/health` fails (404/503) or `enabled=false`.

## Commerce AI

| Method | Path | Notes |
|--------|------|-------|
| GET | `/commerce/ai/stores/{storeId}/search?q=` | Store-scoped semantic product search |
| GET | `/commerce/ai/stores/{storeId}/similar/{productIndexId}` | Similar products |
| POST | `/commerce/ai/support` | Authenticated shopper order/returns FAQ |

Catalog publish now scopes by store warehouse stock and optional `allowed_category_ids` on `store_commerce_profiles`.

# FlowLedger AI Platform — Roadmap

Default master switch: `flowledger.ai.enabled` (local YAML often `true` via `FLOWLEDGER_AI_ENABLED`; defaults to `false` in code). LangChain4j **0.36.2**.

| Phase | Status | Summary |
|-------|--------|---------|
| **1** Provider abstraction | Done | OpenAI + stub Claude/Gemini/Azure/Ollama; `AIProviderRegistry` |
| **2** Chat + agents | Done | Multi-agent orchestration, memory, audit, tools bridge |
| **3** RAG / knowledge | Done | Knowledge docs, embeddings pipeline, retrieval |
| **4** Domain tools | Done | Sales, inventory, GST, payment, CRM, etc. via services |
| **5** Recommendation engine | Done | Heuristic types, NEW/ACKNOWLEDGED/DISMISSED, generator |
| **6** Event-driven AI | Done | `AiSearchEventBridge` AFTER_COMMIT; lifecycle publisher |
| **7** Predictive / analytics | Done | Moving-average forecast stubs; `analytics-enabled` gate |
| **8** Autonomous stubs | Done | Document/Voice/Workflow suggest stubs; no document posting |
| **v2 Specialist Agents** | Done | Global Ask, CFO/Accountant/… catalog, multi-agent consult, Whisper STT, Workflow Builder drafts |
| **v2.1** | Planned | True LangChain4j AiServices tool-calling, streaming, pgvector ANN, OCR, workflow execution hooks, model budgets |

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
| `document-ai-enabled` | `false` | Document extract stub |
| `voice-enabled` | `false` (local often `true`) | Whisper STT |
| `multi-agent-enabled` | `true` | Ask/CEO/Advisor consult fan-out |
| `workflow-builder-enabled` | `true` | Draft approval workflows |

## UI routes (FlowLedgerUI)

| Route | Permission module |
|-------|-------------------|
| `/ai/chat` | `ai` → `AI_CHAT` |
| `/ai/recommendations` | `aiRecommendations` → `AI_RECOMMENDATION` |
| `/ai/analytics` | `ai` → `AI_CHAT` |
| `/ai/workflows` | `aiWorkflow` → `AI_WORKFLOW` |

Global **Ask AI** FAB (⌘J / Ctrl+J) on all authenticated pages when health is up + `AI_CHAT`.

Nav group **AI Assistant** is hidden when `GET /api/v1/ai/health` fails (404/503) or `enabled=false`.

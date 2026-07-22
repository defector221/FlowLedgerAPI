# FlowLedger AI Platform — Roadmap

Default: `flowledger.ai.enabled=false`. LangChain4j **0.36.2**.

| Phase | Status | Summary |
|-------|--------|---------|
| **1** Provider abstraction | Done | OpenAI + stub Claude/Gemini/Azure/Ollama; `AIProviderRegistry` |
| **2** Chat + agents | Done | Multi-agent orchestration, memory, audit, tools bridge |
| **3** RAG / knowledge | Done | Knowledge docs, embeddings pipeline, retrieval |
| **4** Domain tools | Done | Sales, inventory, GST, payment, CRM, etc. via services |
| **5** Recommendation engine | Done | Heuristic types, NEW/ACKNOWLEDGED/DISMISSED, generator |
| **6** Event-driven AI | Done | `AiSearchEventBridge` AFTER_COMMIT; lifecycle publisher |
| **7** Predictive / analytics | Done | Moving-average forecast stubs; `analytics-enabled` gate |
| **8** Autonomous stubs | Done | Document/Voice/Workflow suggest — no document posting |
| **v2** | Planned | True LangChain4j tool-calling agents, streaming, pgvector ANN, voice STT, document OCR, multi-tenant model budgets |

## Feature flags (`application.yml`)

| Key | Default | Purpose |
|-----|---------|---------|
| `flowledger.ai.enabled` | `false` | Master switch (`@ConditionalOnAiEnabled`) |
| `chat-enabled` | `true` | Chat orchestration |
| `rag-enabled` | `true` | Retrieval |
| `embeddings-enabled` | `true` | Embedding pipeline |
| `analytics-enabled` | `false` | Forecasts |
| `document-ai-enabled` | `false` | Document extract stub |
| `voice-enabled` | `false` | Voice stub |

## UI routes (FlowLedgerUI)

| Route | Permission module |
|-------|-------------------|
| `/ai/chat` | `ai` → `AI_CHAT` |
| `/ai/recommendations` | `aiRecommendations` → `AI_RECOMMENDATION` |
| `/ai/analytics` | `ai` → `AI_CHAT` |

Nav group **AI Assistant** is hidden when `GET /api/v1/ai/health` fails (404/503) or `enabled=false`.

# FlowLedger AI — OpenAPI-style reference

Base path: `/api/v1/ai`  
Auth: Bearer JWT. Permissions: `AI_CHAT`, `AI_RECOMMENDATION`, `AI_ANALYSIS`, `AI_ADMIN`, `AI_WORKFLOW` (or `ROLE_ORGANIZATION_ADMIN`).  
When `flowledger.ai.enabled=false`, endpoints are not registered (typically **404**).

## Health

`GET /health` → `AI_CHAT`  
Response: `{ enabled, provider, chatEnabled, ragEnabled, embeddingsEnabled, analyticsEnabled, documentAiEnabled, voiceEnabled, apiKeyConfigured, multiAgentEnabled, workflowBuilderEnabled }`

## Agents & chat

| Method | Path | Auth | Body / notes |
|--------|------|------|----------------|
| GET | `/agents` | AI_CHAT | Specialist catalog |
| POST | `/chat` | AI_CHAT | `{ conversationId?, message, agent?, useRag? }` → includes `consultedAgents[]` |
| POST | `/ask` | AI_CHAT | Forces Global Ask Agent |
| POST | `/voice-chat` | AI_CHAT | `{ conversationId?, contentType, audioBase64, agent? }` — Whisper then chat |
| GET | `/conversations` | AI_CHAT | list for current user |
| GET | `/conversations/{id}/messages` | AI_CHAT | message history |

Default agent when omitted: **ASK**. Legacy codes (`FINANCE`, `GST`, …) alias to v2 specialists.

## Recommendations

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/recommendations?status=` | AI_RECOMMENDATION | filter NEW/ACKNOWLEDGED/DISMISSED |
| PATCH | `/recommendations/{id}` | AI_RECOMMENDATION | `{ status }` |
| PATCH | `/recommendations/{id}/acknowledge` | AI_RECOMMENDATION | → ACKNOWLEDGED |
| PATCH | `/recommendations/{id}/dismiss` | AI_RECOMMENDATION | → DISMISSED |

## Knowledge

| Method | Path | Auth |
|--------|------|------|
| POST | `/knowledge` | AI_ADMIN |
| GET | `/knowledge?q=` | AI_CHAT / AI_ADMIN |

## Analytics

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/analytics/forecasts?type=` | AI_ANALYSIS / AI_CHAT | `DEMAND\|SALES\|CASHFLOW\|INVENTORY`. If analytics disabled: `{ enabled:false, message }` |

## Workflow & voice

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/workflow/suggest-from-text` | AI_CHAT / AI_WORKFLOW | `{ text }` → suggested fields JSON; **no PI/PO** |
| POST | `/workflow/suggest` | AI_CHAT / AI_WORKFLOW | NL → create draft workflow |
| GET | `/workflow/drafts` | AI_CHAT / AI_WORKFLOW | list |
| POST | `/workflow/drafts` | AI_WORKFLOW / AI_ADMIN | create |
| PUT | `/workflow/drafts/{id}` | AI_WORKFLOW / AI_ADMIN | update |
| POST | `/workflow/drafts/{id}/activate` | AI_WORKFLOW / AI_ADMIN | stores ACTIVE config only |
| POST | `/workflow/drafts/{id}/deactivate` | AI_WORKFLOW / AI_ADMIN | → DRAFT |
| POST | `/workflow/document-extract` | AI_CHAT | stub / not configured |
| POST | `/workflow/voice-transcribe` | AI_CHAT | Whisper STT `{ contentType, audioBase64 }` |

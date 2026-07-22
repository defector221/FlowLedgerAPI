# FlowLedger AI — OpenAPI-style reference

Base path: `/api/v1/ai`  
Auth: Bearer JWT. Permissions: `AI_CHAT`, `AI_RECOMMENDATION`, `AI_ANALYSIS`, `AI_ADMIN` (or `ROLE_ORGANIZATION_ADMIN`).  
When `flowledger.ai.enabled=false`, endpoints are not registered (typically **404**).

## Health

`GET /health` → `AI_CHAT`  
Response: `{ enabled, provider, chatEnabled, ragEnabled, embeddingsEnabled, analyticsEnabled, documentAiEnabled, voiceEnabled, apiKeyConfigured }`

## Chat

| Method | Path | Auth | Body / notes |
|--------|------|------|----------------|
| POST | `/chat` | AI_CHAT | `{ conversationId?, message, agent?, useRag? }` |
| GET | `/conversations` | AI_CHAT | list for current user |
| GET | `/conversations/{id}/messages` | AI_CHAT | message history |

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

## Workflow stubs

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/workflow/suggest-from-text` | AI_CHAT | `{ text }` → suggested fields JSON; **does not create PI/PO** |
| POST | `/workflow/document-extract` | AI_CHAT | stub / not configured |
| POST | `/workflow/voice-transcribe` | AI_CHAT | stub / not configured |

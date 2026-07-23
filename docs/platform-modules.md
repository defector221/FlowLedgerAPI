# Platform Modules (Phase 1)

FlowLedger is composed per organization via a **module catalog**, **editions**, and **entitlements**.

## Concepts

| Concept | Table / API | Purpose |
|---------|-------------|---------|
| Module catalog | `modules`, `module_features`, `module_dependencies` | Global definition of capabilities |
| Editions | `editions`, `edition_modules` | Presets: LITE → ENTERPRISE + CUSTOM |
| Entitlements | `organization_modules`, `organization_features` | What an org can use |
| FeatureService | `com.flowledger.platform.service.FeatureService` | Runtime checks |
| Capabilities | `GET /api/v1/platform/organization/capabilities` | UI bootstrap payload |

## Compatibility

- `organization_settings.retail_enabled` / `transport_enabled` remain and are **dual-written** with `organization_modules`.
- Existing REST paths and permission codes are unchanged.
- New APIs live under `/api/v1/platform/**`.

## FeatureService

```java
featureService.hasModule("RETAIL");
featureService.hasFeature("RETAIL", "POS");
featureService.canAccess("RETAIL", "POS", "RETAIL_POS");
```

Effective enablement requires `enabled && licensed && (expires_at is null or in the future)`.

## Adding a module (checklist)

1. Insert catalog row in a Flyway migration (`modules` + optional `module_features` / dependencies).
2. Add edition membership if needed (`edition_modules`).
3. Guard domain APIs with `FeatureService` / a module guard.
4. Register frontend mapping in `FlowLedgerUI/src/platform` (RBAC → platform code) and nav leaves.
5. Document any new permission codes (do not rename existing ones).

## Frontend

- Import `@/platform` once at app bootstrap (registers module stubs).
- `useCapabilities()` drives sidebar, command palette, and `FeatureGate` / `RetailModuleGate`.
- Admin UI: `/settings/platform` (ORGANIZATION_ADMIN).

## Plan → edition defaults

| Plan | Default edition |
|------|-----------------|
| FREE / STARTER | PROFESSIONAL |
| BUSINESS / PRO | ENTERPRISE |

Applied only when **provisioning new organizations**. Existing tenants are backfilled to PROFESSIONAL without stripping modules.

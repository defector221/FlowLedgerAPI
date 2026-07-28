# Platform Operations Center — Phase 1

## What shipped

- Isolated Postgres schema `platform` (users, roles, permissions, audit) — Flyway `V74`
- Product image metadata columns — Flyway `V75`
- Platform JWT (`typ=platform`) on `/api/v1/ops/**`; tenant JWT cannot access ops
- Operator bootstrap from `flowledger.platform.super-admin.*` into `platform.users` (`PLATFORM_ADMIN`)
- Ops APIs: dashboard, organizations, subscriptions/plans, demo seed, audit, health
- Demo product images under `classpath:demo-images/` via `ProductImageService` + `StorageService`
- SPA: `FlowLedgerPlatformUI` on port **5174**

## Local config

```yaml
flowledger.platform.super-admin.password: change-me-local
flowledger.demo.enabled: true
# optional separate ops JWT secret
flowledger.ops.jwt.secret: ""
```

## Smoke checklist

1. Start API; confirm log `Created platform PLATFORM_ADMIN operator` (or Ensured…)
2. `cd FlowLedgerPlatformUI && npm install && npm run dev` → http://localhost:5174
3. Login as `superadmin@flowledger.local` / yaml password
4. Dashboard shows tenant KPIs
5. Organizations lists tenants; suspend/resume works
6. Demo Center: list scenarios; seed with optional org name and mode `SKIP` / `RESET`; jobs poll every 2s
7. Organizations: Delete purges tenant + cascaded data (`DELETE /organizations/{id}` with `{ "confirmName": "<exact name>" }`)
8. Audit shows `DEMO_SEED_ENQUEUED` / `ORG_PURGE` / `ORG_*` rows
9. Poll `GET /api/v1/ops/demo/jobs/{id}`; `GET /api/v1/ops/demo/options` exposes `allowReset`
10. Tenant UI: product list/detail and POS search show `imageUrl` thumbnails when images exist
11. Tenant JWT calling `/api/v1/ops/**` → 401; platform JWT calling tenant APIs → 401/403

### Demo seed modes

- `SKIP` (default): if org name exists, job ends `SKIPPED`
- Custom `organizationName`: seed another tenant with the same scenario blueprint
- `RESET` (requires `flowledger.demo.allow-reset=true`): if name exists, seeds a **fresh sibling** named `… · reset <timestamp>` (destructive wipe is not implemented)

## Legacy

`/api/v1/admin/demo-data/**` returns **410 Gone** — use `/api/v1/ops/demo/**`.

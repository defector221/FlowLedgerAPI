# Sankhya IAM OIDC Integration

FlowLedger (Sankhya One) authenticates ERP Admin and Platform Ops users via **Sankhya IAM**, which orchestrates **Keycloak**. Commerce OTP authentication is unchanged.

## Architectural boundaries

| Layer | Owns |
|-------|------|
| **Keycloak** | Authentication and token issuance only |
| **Sankhya IAM** | Organization, membership, roles, permissions, product access, subscription, feature flags |
| **FlowLedger** | Enforcement of business authorization on its own resources |

## Architectural laws

1. **Keycloak is the only authentication / token issuer.** IAM is orchestration over Keycloak — not a second Authorization Server.
2. When `flowledger.iam.enabled=true`, Keycloak access/refresh tokens stay **server-side in the BFF**. The browser receives only an opaque **HttpOnly session cookie** (`fl_iam_session` / `fl_iam_ops_session`).
3. FlowLedger **does not mint HS256 bridge tokens** for the IAM path.
4. HS256 (`JwtService` / `PlatformJwtService`) is **legacy rollback only** when `flowledger.iam.enabled=false` — never part of IAM SSO.
5. Validate Keycloak JWTs with: **issuer allowlist**, **audience**, **signature (JWKS)**, **expiration**, **not-before**.
6. **Do not** call IAM authorization APIs on every business request.
7. AuthZ order: JWT claims → cached permissions → `GET /authorization/effective` only for refresh/thin-token.
8. Persist only **`iam_user_id`** and **`iam_organization_id`**. `orgSlug` is an optional **login bootstrap hint** — never a durable tenant identity.
9. OAuth uses confidential client **`flowledger-bff`** (+ `flowledger-ops-bff`) with Authorization Code + **PKCE**.
10. IAM public auth endpoints must enforce **registered `clientId` + exact `redirectUri`**.
11. Commerce OTP is unchanged for now; B2C Commerce may later use IAM `contextType=USER` entitlements (see iam-suite `docs/b2b-b2c-context.md`). FlowLedger ERP Admin remains `contextType=ORGANIZATION`.
12. Prefer BFF fallback: if IAM `login-url`/`resolve` fail, use `/auth/issuers` + Keycloak OIDC discovery constrained by `trusted-issuers`.

## Token ownership

| Token | Issuer | Stored where | Used for |
|-------|--------|--------------|----------|
| Authorization code | Keycloak | Transient BFF exchange | One-time login |
| ID token | Keycloak | Server session (logout hint) | Identity / nonce |
| Access token | Keycloak | **Server session store only** | Attached by BFF after session cookie lookup |
| Refresh token | Keycloak | **Server session store only** | Silent refresh |
| Session id | FlowLedger BFF | HttpOnly Secure cookie | Browser auth credential |
| FlowLedger HS256 | FlowLedger | Client (legacy) | Rollback when `iam.enabled=false` |

## Authentication sequence

```
UI  →  BFF GET /api/v1/auth/iam/login-url (credentials)
BFF →  IAM GET /auth/login-url (clientId=flowledger-bff, exact redirectUri)
     OR fallback: IAM /auth/issuers ∩ trusted-issuers → Keycloak discovery → build authorize URL
UI  →  Keycloak authorize
KC  →  BFF /callback?code&state
BFF     validate state/PKCE/redirect/client; exchange code (confidential + PKCE)
BFF     store access/refresh in server session; Set-Cookie fl_iam_session HttpOnly
BFF →  UI /auth/iam/complete?code=… (one-time claim → user/org shells, no tokens)
UI  →  API withCredentials (session cookie only)
BFF     load session → validate Keycloak JWT → UserPrincipal
```

## Durable tenant identity

- `organizations.iam_organization_id` ← IAM organization UUID (SoT)
- `users.iam_user_id` ← IAM user UUID
- Session also tracks active **`iam_membership_id`** for authorization
- `FLOWLEDGER_IAM_ORG_SLUG` / login `orgSlug`: **bootstrap only** for resolve/login-url when required by IAM

## AuthorizationContext cache

Permissions are cached as `authz:{iamUserId}:{iamOrganizationId}:{iamMembershipId}` (never user+org alone).

- `IamPermissionCache.fromEffective` maps IAM `GET /authorization/effective` into the triple + roles/permissions + status fields.
- Authorities are emitted only when the cached triple matches immutable `TenantContext` IAM identities **and** user/org/membership statuses are `ACTIVE` (fail-closed otherwise).

## Atomic organization switch

```
UI  →  POST /api/v1/auth/iam/organizations/{organizationId}/switch (session cookie)
BFF    resolve local org (id or iam_organization_id) → require ACTIVE IAM membership
BFF →  IAM GET /authorization/effective?organizationId=…
BFF    build AuthorizationContext B (must include membership id)
BFF    put(B) → evict(A) → only then update server session active org/membership
BFF    provision tenant shells → return LoginResponse (no tokens; IAM org list when available)
```

If effective authz / membership validation fails, session A is left untouched (fail-closed).

## Dual onboarding APIs (tenant BFF)

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/auth/iam/status` | `enabled`, `authMode`, `selfServiceSignupEnabled` (default **false** = invite-only) |
| GET | `/api/v1/auth/iam/organizations` | ACTIVE memberships → local shells |
| POST | `/api/v1/auth/iam/organizations/{organizationId}/switch` | Atomic switch sequence above |
| GET | `/api/v1/auth/iam/context` | Current session/authz debug view |

Invite-only is the default UI posture. Self-service org signup lives on IAM (`sankhya.iam.self-service-enabled`) and is not opened through FlowLedger unless status says so.

## Configuration

```yaml
flowledger:
  iam:
    enabled: false
    base-url: https://iam.sankhya.cloud
    client-id: flowledger-bff
    client-secret: ${FLOWLEDGER_IAM_CLIENT_SECRET}
    redirect-uri: http://localhost:7070/api/v1/auth/iam/callback
    trusted-issuers:
      - https://keycloak.sankhya.cloud/realms/sankhya
    audience: flowledger-bff
    fallback-direct-keycloak: true
    org-slug: ${FLOWLEDGER_IAM_ORG_SLUG:}   # bootstrap hint only
    cookie-secure: false   # set false for local http
    ops:
      client-id: flowledger-ops-bff
```

## Local enable checklist

1. Redeploy IAM with public `/auth/login-url|resolve|logout-url|issuers` + exact client/redirect validation.
2. Import/create Keycloak confidential clients `flowledger-bff` / `flowledger-ops-bff` (see `iam-suite/keycloak/realm-sankhya.json`); register exact BFF callbacks.
3. Seed IAM `oauth_client_registry` rows with exact redirect URIs (demo seed in `V5__seed_demo_org.sql`).
4. Set env (secrets, `ORG_SLUG=demo` bootstrap if needed, `COOKIE_SECURE=false` locally).
5. Flip `FLOWLEDGER_IAM_ENABLED=true`.
6. Verify: HttpOnly session cookie present; no Keycloak JWT in JS storage; HS256 rejected; `enabled=false` restores password login.

## Rollback

Set `flowledger.iam.enabled=false` and restart. Password + HS256 filters resume. Do **not** remove HS256 until cutover gates pass.

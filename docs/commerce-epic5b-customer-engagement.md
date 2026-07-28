# Epic 5B — Customer Engagement Platform

**Prerequisite:** Epic 5A. All rewards flow through Promotion Engine → Reward Engine.

## Modules

| Module | Package | Earn trigger |
|--------|---------|--------------|
| Wallet | `com.flowledger.commerce.wallet` | OrderCompleted → cashback rules |
| Loyalty | `com.flowledger.commerce.loyalty` | OrderCompleted → points rules (bridges retail loyalty) |
| Referrals | `com.flowledger.commerce.referral` | First OrderCompleted after registration |
| Notifications | `com.flowledger.commerce.engagement` | Event bus consumers |
| Wishlist / lists / reviews | `com.flowledger.commerce.engagement` | Customer APIs |

## Customer APIs

Base: `/api/v1/commerce/customers/me`

- `GET /wallet?organizationId=` — wallet accounts
- `GET /wallet/{accountId}/entries` — ledger history
- `GET /loyalty?organizationId=` — loyalty balance (ERP bridge)
- `GET /referral-code?organizationId=` — referral code
- `GET /notifications` — in-app notifications
- `GET /wishlist`, `POST /wishlist`, `DELETE /wishlist`
- `GET /saved-lists`, `POST /saved-lists`, `POST /saved-lists/{id}/items`
- `POST /reviews`
- `GET /timeline?organizationId=` — unified activity feed

## Schema

- V90: wallet, referral, notifications, wishlist, saved lists, reviews

## Checkout redemption

Wallet balance and loyalty points are redemption **inputs** to Promotion Engine at checkout (future: pass `walletRedeemAmount` / `pointsRedeemAmount` on `PromotionContext`).

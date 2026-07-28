# Epic 5A — Commerce Rules Platform

## Components

| Component | Package | Role |
|-----------|---------|------|
| Platform Event Bus | `com.flowledger.platform.event.bus` | Transactional outbox, poller, envelope |
| Rule Engine | `com.flowledger.commerce.rules.engine.RuleEngine` | Condition evaluators |
| Promotion Engine | `com.flowledger.commerce.rules.engine.PromotionEngine` | Match + stack promotions |
| Reward Engine | `com.flowledger.commerce.rules.engine.RewardEngine` | Discount, cashback, points outputs |
| Coupon Engine | `com.flowledger.commerce.rules.engine.CouponEngine` | Coupon validation |

## API

- `GET /api/v1/commerce/rules/promotions` — list active rules
- `POST /api/v1/commerce/rules/promotions` — create rule
- `POST /api/v1/commerce/rules/evaluate` — preview checkout discounts

## Integration

- **Commerce checkout**: `CommercePricingService.applyCoupon` → Promotion Engine (fallback: retail coupons)
- **POS**: `PosPromotionAdapter.evaluatePosSale`
- **Post-purchase earn**: `OrderCompleted` → `PromotionEngine.applyEarnRules()` → Reward Engine

## Schema

- V89: `commerce_promotion_rules`, `commerce_promotion_conditions`, `commerce_promotion_redemptions`, `commerce_reward_outcomes`

## Condition types

ORDER_VALUE, PRODUCT, CATEGORY, BRAND, CUSTOMER, STORE, CHANNEL, TIME, QUANTITY

## Promotion types

PERCENTAGE, FLAT, BOGO, BUNDLE, COUPON, FIRST_ORDER, CATEGORY, BRAND, SEGMENT, CASHBACK_EARN, POINTS_EARN

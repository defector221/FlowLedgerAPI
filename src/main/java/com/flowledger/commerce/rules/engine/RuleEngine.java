package com.flowledger.commerce.rules.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.rules.context.PromotionContext;
import com.flowledger.commerce.rules.domain.ConditionType;
import com.flowledger.commerce.rules.entity.CommercePromotionCondition;
import com.flowledger.commerce.rules.entity.CommercePromotionRule;
import com.flowledger.commerce.rules.repository.CommercePromotionConditionRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RuleEngine {
    private final CommercePromotionConditionRepository conditions;
    private final ObjectMapper objectMapper;

    public RuleEngine(CommercePromotionConditionRepository conditions, ObjectMapper objectMapper) {
        this.conditions = conditions;
        this.objectMapper = objectMapper;
    }

    public boolean matches(CommercePromotionRule rule, PromotionContext ctx) {
        if (!rule.isActive()) return false;
        OffsetDateTime now = ctx.evaluatedAt() != null ? ctx.evaluatedAt() : OffsetDateTime.now();
        if (rule.getStartsAt() != null && now.isBefore(rule.getStartsAt())) return false;
        if (rule.getEndsAt() != null && now.isAfter(rule.getEndsAt())) return false;
        if (rule.getSalesChannel() != null && ctx.channel() != null && rule.getSalesChannel() != rule.getSalesChannel()) {
            return false;
        }
        if (rule.getStoreId() != null && ctx.storeId() != null && !rule.getStoreId().equals(ctx.storeId())) {
            return false;
        }
        if (rule.getMinOrderTotal() != null && nz(ctx.orderTotal()).compareTo(rule.getMinOrderTotal()) < 0) {
            return false;
        }
        if (rule.getMaxRedemptions() != null && rule.getRedemptionCount() >= rule.getMaxRedemptions()) {
            return false;
        }

        List<CommercePromotionCondition> ruleConditions = conditions.findByRuleId(rule.getId());
        for (CommercePromotionCondition condition : ruleConditions) {
            if (!evaluateCondition(condition, ctx)) return false;
        }
        return true;
    }

    private boolean evaluateCondition(CommercePromotionCondition condition, PromotionContext ctx) {
        Map<String, Object> values = parseJson(condition.getValueJson());
        return switch (condition.getConditionType()) {
            case ORDER_VALUE -> evaluateOrderValue(values, ctx);
            case PRODUCT -> evaluateProduct(values, ctx);
            case CATEGORY -> evaluateCategory(values, ctx);
            case BRAND -> evaluateBrand(values, ctx);
            case CUSTOMER -> evaluateCustomer(values, ctx);
            case STORE -> evaluateStore(values, ctx);
            case CHANNEL -> evaluateChannel(values, ctx);
            case TIME -> evaluateTime(values, ctx);
            case QUANTITY -> evaluateQuantity(values, ctx);
        };
    }

    private boolean evaluateOrderValue(Map<String, Object> values, PromotionContext ctx) {
        BigDecimal min = decimal(values.get("minOrderTotal"));
        BigDecimal max = decimal(values.get("maxOrderTotal"));
        BigDecimal total = nz(ctx.orderTotal());
        if (min != null && total.compareTo(min) < 0) return false;
        if (max != null && total.compareTo(max) > 0) return false;
        return true;
    }

    private boolean evaluateProduct(Map<String, Object> values, PromotionContext ctx) {
        UUID productId = uuid(values.get("productId"));
        if (productId == null) return true;
        return ctx.lines().stream().anyMatch(l -> productId.equals(l.productId()));
    }

    private boolean evaluateCategory(Map<String, Object> values, PromotionContext ctx) {
        UUID categoryId = uuid(values.get("categoryId"));
        if (categoryId == null) return true;
        return ctx.lines().stream().anyMatch(l -> categoryId.equals(l.categoryId()));
    }

    private boolean evaluateBrand(Map<String, Object> values, PromotionContext ctx) {
        String brand = string(values.get("brand"));
        if (brand == null) return true;
        return ctx.lines().stream().anyMatch(l -> brand.equalsIgnoreCase(l.brand()));
    }

    private boolean evaluateCustomer(Map<String, Object> values, PromotionContext ctx) {
        if (Boolean.TRUE.equals(values.get("firstOrder")) && !ctx.firstOrder()) return false;
        String segment = string(values.get("segment"));
        if (segment != null && !segment.isBlank()) {
            // Segment matching deferred to future CRM integration
        }
        return true;
    }

    private boolean evaluateStore(Map<String, Object> values, PromotionContext ctx) {
        UUID storeId = uuid(values.get("storeId"));
        if (storeId == null) return true;
        return storeId.equals(ctx.storeId());
    }

    private boolean evaluateChannel(Map<String, Object> values, PromotionContext ctx) {
        String channel = string(values.get("channel"));
        if (channel == null || ctx.channel() == null) return true;
        return ctx.channel().name().equalsIgnoreCase(channel);
    }

    private boolean evaluateTime(Map<String, Object> values, PromotionContext ctx) {
        OffsetDateTime now = ctx.evaluatedAt() != null ? ctx.evaluatedAt() : OffsetDateTime.now();
        String dayOfWeek = string(values.get("dayOfWeek"));
        if (dayOfWeek != null && !dayOfWeek.equalsIgnoreCase(now.getDayOfWeek().name())) return false;
        Integer hourFrom = intVal(values.get("hourFrom"));
        Integer hourTo = intVal(values.get("hourTo"));
        if (hourFrom != null && now.getHour() < hourFrom) return false;
        if (hourTo != null && now.getHour() >= hourTo) return false;
        return true;
    }

    private boolean evaluateQuantity(Map<String, Object> values, PromotionContext ctx) {
        BigDecimal minQty = decimal(values.get("minQty"));
        BigDecimal maxQty = decimal(values.get("maxQty"));
        BigDecimal totalQty = ctx.lines().stream()
                .map(l -> nz(l.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (minQty != null && totalQty.compareTo(minQty) < 0) return false;
        if (maxQty != null && totalQty.compareTo(maxQty) > 0) return false;
        return true;
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json != null ? json : "{}", new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) return null;
        return new BigDecimal(v.toString());
    }

    private static UUID uuid(Object v) {
        if (v == null) return null;
        return UUID.fromString(v.toString());
    }

    private static String string(Object v) {
        return v != null ? v.toString() : null;
    }

    private static Integer intVal(Object v) {
        if (v == null) return null;
        return Integer.parseInt(v.toString());
    }
}

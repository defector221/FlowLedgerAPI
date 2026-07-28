package com.flowledger.commerce.order;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.commerce.order.repository.CommerceOrderLineRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
    private final CommerceOrderLineRepository orderLines;
    private final ObjectMapper objectMapper;

    public OrderMapper(CommerceOrderLineRepository orderLines, ObjectMapper objectMapper) {
        this.orderLines = orderLines;
        this.objectMapper = objectMapper;
    }

    public CommerceDtos.CommerceOrderResponse toOrderResponse(CommerceOrder order) {
        List<CommerceOrderLine> lines = orderLines.findByOrderIdOrderByCreatedAtAsc(order.getId());
        return new CommerceDtos.CommerceOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStoreId(),
                order.getStatus().name(),
                order.getFulfillmentType().name(),
                order.getCurrency(),
                order.getSubtotal(),
                order.getDiscountTotal(),
                order.getTaxTotal(),
                order.getShippingTotal(),
                order.getGrandTotal(),
                order.getPlacedAt(),
                order.getConfirmedAt(),
                lines.stream().map(this::toLineResponse).toList());
    }

    private CommerceDtos.CommerceOrderLineResponse toLineResponse(CommerceOrderLine line) {
        Map<String, Object> product = parseJson(line.getProductSnapshot());
        return new CommerceDtos.CommerceOrderLineResponse(
                line.getId(),
                line.getProductId(),
                line.getQuantity(),
                line.getLineSubtotal(),
                line.getLineTax(),
                line.getLineTotal(),
                stringVal(product, "name"),
                stringVal(product, "sku"));
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static String stringVal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}

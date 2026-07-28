package com.flowledger.commerce.cart;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.cart.entity.CommerceCart;
import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.dto.CommerceDtos;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CartMapper {
    private final ObjectMapper objectMapper;

    public CartMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CommerceDtos.CartResponse toCartResponse(CommerceCart cart, List<CommerceCartItem> items) {
        return new CommerceDtos.CartResponse(
                cart.getId(),
                cart.getStoreId(),
                cart.getOrganizationId(),
                cart.getStatus().name(),
                cart.getCurrency(),
                cart.getSubtotal(),
                cart.getDiscountTotal(),
                cart.getTaxTotal(),
                cart.getGrandTotal(),
                cart.getItemCount(),
                items.stream().map(this::toItemResponse).toList());
    }

    public CommerceDtos.CartItemResponse toItemResponse(CommerceCartItem item) {
        Map<String, Object> product = parseJson(item.getProductSnapshot());
        List<String> images = parseImages(item.getImageSnapshot());
        return new CommerceDtos.CartItemResponse(
                item.getId(),
                item.getProductId(),
                item.getVariantId(),
                item.getQuantity(),
                item.getLineSubtotal(),
                item.getLineTax(),
                item.getLineTotal(),
                stringVal(product, "name"),
                stringVal(product, "sku"),
                stringVal(product, "barcode"),
                images);
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<String> parseImages(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String stringVal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}

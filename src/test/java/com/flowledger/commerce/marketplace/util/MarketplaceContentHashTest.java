package com.flowledger.commerce.marketplace.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketplaceContentHashTest {
    @Test
    void samePayloadProducesSameHash() {
        ObjectMapper mapper = new ObjectMapper();
        String a = MarketplaceContentHash.hash(mapper, Map.of("sku", "A", "price", 10));
        String b = MarketplaceContentHash.hash(mapper, Map.of("sku", "A", "price", 10));
        assertEquals(a, b);
    }

    @Test
    void differentPayloadProducesDifferentHash() {
        ObjectMapper mapper = new ObjectMapper();
        String a = MarketplaceContentHash.hash(mapper, Map.of("sku", "A"));
        String b = MarketplaceContentHash.hash(mapper, Map.of("sku", "B"));
        assertNotEquals(a, b);
    }
}

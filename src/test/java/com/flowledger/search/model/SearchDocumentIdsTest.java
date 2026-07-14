package com.flowledger.search.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchDocumentIdsTest {
    @Test
    void deterministicAndTenantScoped() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        String a = SearchDocumentIds.of(orgA, SearchEntityType.PRODUCT, entity);
        String b = SearchDocumentIds.of(orgB, SearchEntityType.PRODUCT, entity);
        assertEquals(orgA + ":PRODUCT:" + entity, a);
        assertNotEquals(a, b);
    }
}

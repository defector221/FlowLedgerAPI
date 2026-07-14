package com.flowledger.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.search.config.OpenSearchClientHolder;
import com.flowledger.search.config.SearchProperties;
import com.flowledger.search.dto.SearchDtos.Response;
import com.flowledger.search.exception.SearchUnavailableException;
import com.flowledger.search.model.SearchDocument;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.search.model.SearchPageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalSearchServiceTest {
    private final UUID orgId = UUID.randomUUID();
    private FakeIndexService indexService;
    private SearchProperties properties;
    private OpenSearchClientHolder holder;
    private GlobalSearchService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(orgId, UUID.randomUUID());
        properties = new SearchProperties();
        properties.setEnabled(false);
        properties.setUrl("http://localhost:19200");
        properties.setIndex("flowledger-global-search-v1");
        holder = new OpenSearchClientHolder(properties);
        properties.setEnabled(true);
        indexService = new FakeIndexService();
        service = new GlobalSearchService(indexService, holder, null, null, null, null, null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsBlankQuery() {
        assertThrows(IllegalArgumentException.class, () -> service.search(" ", null, null, null));
    }

    @Test
    void rejectsOversizedLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.search("iphone", null, 100, null));
    }

    @Test
    void rejectsInvalidPage() {
        assertThrows(IllegalArgumentException.class, () -> service.search("iphone", null, 10, 0));
    }

    @Test
    void filtersByEntityTypeAndOrganization() {
        indexService.docs = List.of(SearchDocument.builder()
                .entityId(UUID.randomUUID().toString())
                .entityType("PRODUCT")
                .title("Apple iPhone 16")
                .referenceNumber("IP16-128")
                .build());
        indexService.total = 1;

        Response response = service.search("iphone", "PRODUCT", 20, 1);
        assertEquals(1, response.results().size());
        assertEquals("PRODUCT", response.results().get(0).entityType());
        assertEquals(1, response.total());
        assertEquals(1, response.page());
        assertEquals(20, response.size());
        assertFalse(response.hasMore());
        assertEquals(orgId, indexService.lastOrgId);
        assertEquals(List.of(SearchEntityType.PRODUCT), indexService.lastTypes);
        assertEquals(0, indexService.lastFrom);
    }

    @Test
    void paginatesWithOffset() {
        indexService.docs = List.of();
        indexService.total = 45;
        Response response = service.search("iphone", null, 20, 2);
        assertEquals(20, indexService.lastSize);
        assertEquals(20, indexService.lastFrom);
        assertEquals(2, response.page());
        assertEquals(45, response.total());
        assertTrue(response.hasMore());
    }

    @Test
    void throwsWhenSearchDisabled() {
        properties.setEnabled(false);
        assertThrows(SearchUnavailableException.class, () -> service.search("iphone", null, 10, null));
    }

    @Test
    void neverQueriesOtherOrganization() {
        indexService.docs = List.of();
        service.search("Secret Product A", null, 10, null);
        assertEquals(orgId, indexService.lastOrgId);
        assertEquals("Secret Product A", indexService.lastQuery);
    }

    private static final class FakeIndexService extends SearchIndexService {
        UUID lastOrgId;
        String lastQuery;
        List<SearchEntityType> lastTypes = List.of();
        int lastFrom;
        int lastSize;
        long total;
        List<SearchDocument> docs = new ArrayList<>();

        FakeIndexService() {
            super(null);
        }

        @Override
        public SearchPageResult searchPage(
                UUID organizationId, String query, List<SearchEntityType> types, int from, int size) {
            lastOrgId = organizationId;
            lastQuery = query;
            lastTypes = types == null ? List.of() : types;
            lastFrom = from;
            lastSize = size;
            return new SearchPageResult(docs, total, from, size);
        }
    }
}

package com.flowledger.search.service;

import com.flowledger.search.config.OpenSearchClientHolder;
import com.flowledger.search.exception.SearchUnavailableException;
import com.flowledger.search.model.SearchDocument;
import com.flowledger.search.model.SearchDocumentIds;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.search.model.SearchPageResult;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {
    private final OpenSearchClientHolder holder;

    @PostConstruct
    void ensureIndex() {
        if (!holder.isAvailable()) {
            return;
        }
        try {
            ensureIndexExists();
        } catch (Exception ex) {
            holder.markUnavailable(ex.getMessage());
        }
    }

    public void index(SearchDocument document) {
        if (!holder.isEnabled()) {
            return;
        }
        requireAvailable();
        try {
            ensureIndexExists();
            holder.client()
                    .index(IndexRequest.of(i ->
                            i.index(holder.index()).id(document.getDocumentId()).document(document)));
            log.debug("Indexed search document type={} entityId={}", document.getEntityType(), document.getEntityId());
        } catch (Exception ex) {
            log.warn(
                    "Failed to index search document type={} entityId={}: {}",
                    document.getEntityType(),
                    document.getEntityId(),
                    ex.getMessage());
            throw new SearchUnavailableException("Search is temporarily unavailable. Please try again.", ex);
        }
    }

    public int indexAll(Collection<SearchDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        if (!holder.isEnabled()) {
            return 0;
        }
        requireAvailable();
        try {
            ensureIndexExists();
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (SearchDocument document : documents) {
                bulk.operations(op -> op.index(idx ->
                        idx.index(holder.index()).id(document.getDocumentId()).document(document)));
            }
            BulkResponse response = holder.client().bulk(bulk.build());
            int failed = 0;
            if (response.errors()) {
                failed = (int) response.items().stream()
                        .filter(item -> item.error() != null)
                        .count();
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .limit(5)
                        .forEach(item -> log.warn(
                                "Bulk index item failed id={} type={} reason={}",
                                item.id(),
                                item.error().type(),
                                item.error().reason()));
                log.warn("Bulk index completed with {} failures", failed);
            }
            return documents.size() - failed;
        } catch (Exception ex) {
            log.warn("Bulk index failed: {}", ex.getMessage(), ex);
            throw new SearchUnavailableException("Search is temporarily unavailable. Please try again.", ex);
        }
    }

    public void delete(UUID organizationId, SearchEntityType type, UUID entityId) {
        if (!holder.isEnabled()) {
            return;
        }
        if (!holder.isAvailable()) {
            log.warn("Skipping search delete; OpenSearch unavailable type={} entityId={}", type, entityId);
            return;
        }
        try {
            holder.client().delete(DeleteRequest.of(d -> d.index(holder.index())
                    .id(SearchDocumentIds.of(organizationId, type, entityId))));
            log.debug("Deleted search document type={} entityId={}", type, entityId);
        } catch (Exception ex) {
            log.warn("Failed to delete search document type={} entityId={}: {}", type, entityId, ex.getMessage());
        }
    }

    public void deleteOrganizationDocuments(UUID organizationId) {
        if (!holder.isEnabled() || !holder.isAvailable()) {
            return;
        }
        try {
            holder.client().deleteByQuery(d -> d.index(holder.index())
                    .query(q ->
                            q.term(t -> t.field("organizationId").value(FieldValue.of(organizationId.toString())))));
        } catch (Exception ex) {
            log.warn("Failed to delete organization search documents: {}", ex.getMessage());
            throw new SearchUnavailableException("Search is temporarily unavailable. Please try again.", ex);
        }
    }

    public List<SearchDocument> search(UUID organizationId, String query, List<SearchEntityType> types, int limit) {
        return searchPage(organizationId, query, types, 0, limit).documents();
    }

    public SearchPageResult searchPage(
            UUID organizationId, String query, List<SearchEntityType> types, int from, int size) {
        requireAvailable();
        try {
            ensureIndexExists();
            String q = query.trim();
            String wildcard = "*" + escapeWildcard(q.toLowerCase()) + "*";
            SearchResponse<SearchDocument> response = holder.client()
                    .search(
                            s -> s.index(holder.index())
                                    .from(Math.max(0, from))
                                    .size(size)
                                    .trackTotalHits(t -> t.enabled(true))
                                    .query(qb -> qb.bool(b -> {
                                        b.filter(f -> f.term(t -> t.field("organizationId")
                                                .value(FieldValue.of(organizationId.toString()))));
                                        if (types != null && !types.isEmpty()) {
                                            b.filter(f -> f.terms(t -> t.field("entityType")
                                                    .terms(tv -> tv.value(types.stream()
                                                            .map(type -> FieldValue.of(type.name()))
                                                            .toList()))));
                                        }
                                        // Exact hits
                                        b.should(sh -> sh.term(t -> t.field("referenceNumber")
                                                .value(FieldValue.of(q))
                                                .boost(12.0f)));
                                        b.should(sh -> sh.term(t -> t.field("title.keyword")
                                                .value(FieldValue.of(q))
                                                .boost(10.0f)));
                                        // Typeahead / partial tokens: "paw" → "Pawan"
                                        b.should(sh -> sh.multiMatch(mm -> mm.query(q)
                                                .fields("title^8", "searchText^3", "subtitle")
                                                .type(TextQueryType.BoolPrefix)
                                                .operator(Operator.Or)
                                                .boost(6.0f)));
                                        b.should(sh -> sh.matchPhrasePrefix(
                                                mpp -> mpp.field("title").query(q).boost(5.0f)));
                                        b.should(sh -> sh.prefix(p -> p.field("title.keyword")
                                                .value(q)
                                                .caseInsensitive(true)
                                                .boost(4.0f)));
                                        b.should(sh -> sh.prefix(p -> p.field("referenceNumber")
                                                .value(q)
                                                .caseInsensitive(true)
                                                .boost(4.0f)));
                                        // Codes / mid-string: "paw" in CUST-PAWAN-...
                                        b.should(sh -> sh.wildcard(w -> w.field("referenceNumber")
                                                .value(wildcard)
                                                .caseInsensitive(true)
                                                .boost(3.0f)));
                                        b.should(sh -> sh.wildcard(w -> w.field("title.keyword")
                                                .value(wildcard)
                                                .caseInsensitive(true)
                                                .boost(2.0f)));
                                        b.minimumShouldMatch("1");
                                        return b;
                                    })),
                            SearchDocument.class);

            List<SearchDocument> docs = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                if (hit.source() != null) {
                    docs.add(hit.source());
                }
            }
            long total = 0;
            if (response.hits().total() != null) {
                total = response.hits().total().value();
            }
            return new SearchPageResult(docs, total, Math.max(0, from), size);
        } catch (SearchUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            holder.markUnavailable(ex.getMessage());
            log.error("OpenSearch query failed", ex);
            throw new SearchUnavailableException("Search is temporarily unavailable. Please try again.", ex);
        }
    }

    private static String escapeWildcard(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '*' || c == '?' || c == '\\') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    private void requireAvailable() {
        if (!holder.isEnabled()) {
            throw new SearchUnavailableException("Search is disabled.");
        }
        if (!holder.isAvailable()) {
            throw new SearchUnavailableException("Search is temporarily unavailable. Please try again.");
        }
    }

    private void ensureIndexExists() throws IOException {
        OpenSearchClient client = holder.client();
        boolean exists = client.indices()
                .exists(ExistsRequest.of(e -> e.index(holder.index())))
                .value();
        if (exists) {
            return;
        }
        Property keyword = Property.of(p -> p.keyword(k -> k));
        Property text = Property.of(p -> p.text(t -> t));
        Property title = Property.of(p -> p.text(t -> t.fields("keyword", f -> f.keyword(k -> k))));
        Property date = Property.of(p -> p.date(d -> d));
        Map<String, Property> properties = new LinkedHashMap<>();
        properties.put("documentId", keyword);
        properties.put("organizationId", keyword);
        properties.put("entityId", keyword);
        properties.put("entityType", keyword);
        properties.put("title", title);
        properties.put("subtitle", text);
        properties.put("searchText", text);
        properties.put("referenceNumber", keyword);
        properties.put("status", keyword);
        properties.put("updatedAt", date);
        client.indices().create(CreateIndexRequest.of(c -> c.index(holder.index())
                .mappings(TypeMapping.of(m -> m.properties(properties)))));
        log.info("Created OpenSearch index {}", holder.index());
    }
}

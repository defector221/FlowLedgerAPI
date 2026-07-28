package com.flowledger.commerce.marketplace.search;

import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.search.config.OpenSearchClientHolder;
import com.flowledger.search.exception.SearchUnavailableException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.GeoLocation;
import org.opensearch.client.opensearch._types.LatLonGeoLocation;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceOpenSearchIndexService {
    private final OpenSearchClientHolder holder;
    private final CommerceProperties commerceProperties;

    @PostConstruct
    void ensureIndex() {
        if (!isAvailable()) {
            return;
        }
        try {
            ensureIndexExists();
        } catch (Exception ex) {
            log.warn("Marketplace OpenSearch index init failed: {}", ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return holder.isAvailable();
    }

    public void index(MarketplaceSearchDocument document) {
        if (!isAvailable()) {
            return;
        }
        try {
            ensureIndexExists();
            holder.client()
                    .index(IndexRequest.of(i -> i.index(indexName()).id(document.getDocumentId()).document(document)));
        } catch (Exception ex) {
            log.warn("Marketplace index failed id={}: {}", document.getDocumentId(), ex.getMessage());
        }
    }

    public void delete(String documentId) {
        if (!isAvailable()) {
            return;
        }
        try {
            holder.client().delete(DeleteRequest.of(d -> d.index(indexName()).id(documentId)));
        } catch (Exception ex) {
            log.warn("Marketplace delete failed id={}: {}", documentId, ex.getMessage());
        }
    }

    public void bulkIndex(List<MarketplaceSearchDocument> documents) {
        if (!isAvailable() || documents.isEmpty()) {
            return;
        }
        try {
            ensureIndexExists();
            List<BulkOperation> ops = new ArrayList<>();
            for (MarketplaceSearchDocument doc : documents) {
                ops.add(BulkOperation.of(b -> b.index(i -> i.index(indexName()).id(doc.getDocumentId()).document(doc))));
            }
            holder.client().bulk(BulkRequest.of(b -> b.operations(ops)));
        } catch (Exception ex) {
            log.warn("Marketplace bulk index failed: {}", ex.getMessage());
        }
    }

    public MarketplaceSearchResult search(MarketplaceSearchQuery query) {
        if (!isAvailable()) {
            throw new SearchUnavailableException("Marketplace search is temporarily unavailable.");
        }
        try {
            ensureIndexExists();
            int from = Math.max(0, query.from());
            int size = Math.max(1, query.size());
            SearchResponse<MarketplaceSearchDocument> response = holder.client()
                    .search(
                            s -> s.index(indexName())
                                    .from(from)
                                    .size(size)
                                    .trackTotalHits(t -> t.enabled(true))
                                    .query(qb -> qb.bool(b -> {
                                        b.filter(f -> f.term(t -> t.field("entityType")
                                                .value(FieldValue.of(query.entityType()))));
                                        b.filter(f -> f.term(t -> t.field("published")
                                                .value(FieldValue.of(true))));
                                        if (query.city() != null) {
                                            b.filter(f -> f.term(t -> t.field("city.keyword")
                                                    .value(FieldValue.of(query.city()))));
                                        }
                                        if (query.postalCode() != null) {
                                            b.filter(f -> f.term(t -> t.field("postalCode")
                                                    .value(FieldValue.of(query.postalCode()))));
                                        }
                                        if (query.barcode() != null) {
                                            b.filter(f -> f.term(t -> t.field("barcode")
                                                    .value(FieldValue.of(query.barcode()))));
                                        }
                                        if (query.sku() != null) {
                                            b.filter(f -> f.term(t -> t.field("sku")
                                                    .value(FieldValue.of(query.sku()))));
                                        }
                                        if (query.brand() != null) {
                                            b.filter(f -> f.term(t -> t.field("brand.keyword")
                                                    .value(FieldValue.of(query.brand()))));
                                        }
                                        if (query.categoryId() != null) {
                                            b.filter(f -> f.term(t -> t.field("categoryId")
                                                    .value(FieldValue.of(query.categoryId()))));
                                        }
                                        if (query.storeId() != null) {
                                            b.filter(f -> f.term(t -> t.field("storeId")
                                                    .value(FieldValue.of(query.storeId()))));
                                        }
                                        if (query.text() != null && !query.text().isBlank()) {
                                            String q = query.text().trim();
                                            b.must(m -> m.multiMatch(mm -> mm.query(q)
                                                    .fields(
                                                            "title^8",
                                                            "searchText^4",
                                                            "sku^6",
                                                            "barcode^6",
                                                            "brand^3")
                                                    .type(TextQueryType.BestFields)
                                                    .operator(Operator.Or)));
                                        }
                                        if (query.lat() != null && query.lng() != null && query.radiusKm() != null) {
                                            b.filter(f -> f.geoDistance(g -> g.field("location")
                                                    .location(GeoLocation.of(gl -> gl.latlon(LatLonGeoLocation.of(l ->
                                                            l.lat(query.lat()).lon(query.lng())))))
                                                    .distance(query.radiusKm() + "km")));
                                        }
                                        return b;
                                    })),
                            MarketplaceSearchDocument.class);

            List<MarketplaceSearchDocument> docs = new ArrayList<>();
            response.hits().hits().forEach(hit -> {
                if (hit.source() != null) {
                    docs.add(hit.source());
                }
            });
            long total = response.hits().total() != null ? response.hits().total().value() : docs.size();
            return new MarketplaceSearchResult(docs, total, from, size);
        } catch (SearchUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Marketplace OpenSearch query failed", ex);
            throw new SearchUnavailableException("Marketplace search is temporarily unavailable.", ex);
        }
    }

    public String indexName() {
        return commerceProperties.getMarketplace().getSearch().getIndex();
    }

    private void ensureIndexExists() throws IOException {
        OpenSearchClient client = holder.client();
        boolean exists =
                client.indices().exists(ExistsRequest.of(e -> e.index(indexName()))).value();
        if (exists) {
            return;
        }
        Property keyword = Property.of(p -> p.keyword(k -> k));
        Property text = Property.of(p -> p.text(t -> t.fields("keyword", f -> f.keyword(k -> k))));
        Property geo = Property.of(p -> p.geoPoint(g -> g));
        Map<String, Property> properties = new LinkedHashMap<>();
        properties.put("documentId", keyword);
        properties.put("entityType", keyword);
        properties.put("indexRowId", keyword);
        properties.put("storeId", keyword);
        properties.put("productId", keyword);
        properties.put("title", text);
        properties.put("searchText", text);
        properties.put("sku", keyword);
        properties.put("barcode", keyword);
        properties.put("gtin", keyword);
        properties.put("brand", text);
        properties.put("categoryId", keyword);
        properties.put("categoryName", text);
        properties.put("price", Property.of(p -> p.double_(d -> d)));
        properties.put("currency", keyword);
        properties.put("inventoryQty", Property.of(p -> p.double_(d -> d)));
        properties.put("city", text);
        properties.put("postalCode", keyword);
        properties.put("visibility", keyword);
        properties.put("published", Property.of(p -> p.boolean_(b -> b)));
        properties.put("imageUrls", Property.of(p -> p.keyword(k -> k)));
        properties.put("latitude", Property.of(p -> p.double_(d -> d)));
        properties.put("longitude", Property.of(p -> p.double_(d -> d)));
        properties.put("location", geo);
        properties.put("updatedAt", Property.of(p -> p.date(d -> d)));
        client.indices().create(CreateIndexRequest.of(c -> c.index(indexName())
                .mappings(TypeMapping.of(m -> m.properties(properties)))));
        log.info("Created marketplace OpenSearch index {}", indexName());
    }

    public record MarketplaceSearchQuery(
            String entityType,
            String text,
            String city,
            String postalCode,
            String barcode,
            String sku,
            String brand,
            String categoryId,
            String storeId,
            Double lat,
            Double lng,
            Double radiusKm,
            int from,
            int size) {}

    public record MarketplaceSearchResult(
            List<MarketplaceSearchDocument> documents, long total, int from, int size) {}
}

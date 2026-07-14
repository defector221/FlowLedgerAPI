package com.flowledger.search.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.customer.entity.Customer;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.search.config.OpenSearchClientHolder;
import com.flowledger.search.dto.SearchDtos.ReindexResponse;
import com.flowledger.search.dto.SearchDtos.Response;
import com.flowledger.search.dto.SearchDtos.Result;
import com.flowledger.search.exception.SearchUnavailableException;
import com.flowledger.search.mapper.SearchDocumentMapper;
import com.flowledger.search.model.SearchDocument;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.supplier.entity.Supplier;
import com.flowledger.supplier.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSearchService extends OrganizationScopedService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int BATCH_SIZE = 500;

    private final SearchIndexService indexService;
    private final OpenSearchClientHolder holder;
    private final SearchDocumentMapper mapper;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final SalesInvoiceRepository salesInvoices;

    @PersistenceContext
    private EntityManager entityManager;

    public Response search(String query, String typesCsv, Integer limit, Integer page) {
        if (!holder.isEnabled()) {
            throw new SearchUnavailableException("Search is disabled.");
        }
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw new IllegalArgumentException("Query must be at least 2 characters");
        }
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        int pageNumber = page == null ? 1 : page;
        if (pageNumber < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        int from = (pageNumber - 1) * size;
        List<SearchEntityType> types = parseTypes(typesCsv);
        var pageResult = indexService.searchPage(orgId(), q, types, from, size);
        List<Result> results = pageResult.documents().stream()
                .map(doc -> new Result(
                        UUID.fromString(doc.getEntityId()),
                        doc.getEntityType(),
                        doc.getTitle(),
                        doc.getSubtitle(),
                        doc.getReferenceNumber()))
                .toList();
        return new Response(q, results, pageResult.total(), pageNumber, size, pageResult.hasMore());
    }

    @Transactional(readOnly = true)
    public ReindexResponse reindex() {
        if (!holder.isEnabled()) {
            throw new SearchUnavailableException("Search is disabled.");
        }
        if (!holder.isAvailable()) {
            throw new SearchUnavailableException("Search is temporarily unavailable. Please try again.");
        }
        UUID org = orgId();
        indexService.deleteOrganizationDocuments(org);

        int indexed = 0;
        int failed = 0;

        for (int page = 0; ; page++) {
            Page<Product> batch = products.findByOrganizationId(org, PageRequest.of(page, BATCH_SIZE));
            List<SearchDocument> docs = batch.stream()
                    .filter(Product::isActive)
                    .map(mapper::fromProduct)
                    .toList();
            int[] counts = bulkSafe(docs);
            indexed += counts[0];
            failed += counts[1];
            if (!batch.hasNext()) break;
        }

        for (int page = 0; ; page++) {
            Page<Customer> batch = customers.findByOrganizationId(org, PageRequest.of(page, BATCH_SIZE));
            List<SearchDocument> docs = batch.stream()
                    .filter(c -> !c.isArchived())
                    .map(mapper::fromCustomer)
                    .toList();
            int[] counts = bulkSafe(docs);
            indexed += counts[0];
            failed += counts[1];
            if (!batch.hasNext()) break;
        }

        for (int page = 0; ; page++) {
            Page<Supplier> batch = suppliers.findByOrganizationId(org, PageRequest.of(page, BATCH_SIZE));
            List<SearchDocument> docs = batch.stream()
                    .filter(s -> !s.isArchived())
                    .map(mapper::fromSupplier)
                    .toList();
            int[] counts = bulkSafe(docs);
            indexed += counts[0];
            failed += counts[1];
            if (!batch.hasNext()) break;
        }

        for (int page = 0; ; page++) {
            Page<SalesInvoice> batch = salesInvoices.findByOrganizationId(org, PageRequest.of(page, BATCH_SIZE));
            List<SearchDocument> docs = new ArrayList<>();
            for (SalesInvoice invoice : batch) {
                String customerName = customers
                        .findByIdAndOrganizationId(invoice.getCustomerId(), org)
                        .map(Customer::getCustomerName)
                        .orElse(null);
                docs.add(mapper.fromSalesInvoice(invoice, customerName));
            }
            int[] counts = bulkSafe(docs);
            indexed += counts[0];
            failed += counts[1];
            if (!batch.hasNext()) break;
        }

        for (int offset = 0; ; offset += BATCH_SIZE) {
            List<PurchaseInvoice> batch = entityManager
                    .createQuery(
                            "from PurchaseInvoice i where i.organizationId=:org order by i.id", PurchaseInvoice.class)
                    .setParameter("org", org)
                    .setFirstResult(offset)
                    .setMaxResults(BATCH_SIZE)
                    .getResultList();
            if (batch.isEmpty()) break;
            List<SearchDocument> docs = new ArrayList<>();
            for (PurchaseInvoice invoice : batch) {
                String supplierName = suppliers
                        .findByIdAndOrganizationId(invoice.getSupplierId(), org)
                        .map(Supplier::getSupplierName)
                        .orElse(null);
                docs.add(mapper.fromPurchaseInvoice(invoice, supplierName));
            }
            int[] counts = bulkSafe(docs);
            indexed += counts[0];
            failed += counts[1];
            if (batch.size() < BATCH_SIZE) break;
        }

        return new ReindexResponse(indexed, failed);
    }

    private int[] bulkSafe(List<SearchDocument> docs) {
        if (docs.isEmpty()) {
            return new int[] {0, 0};
        }
        try {
            int ok = indexService.indexAll(docs);
            return new int[] {ok, docs.size() - ok};
        } catch (Exception ex) {
            log.warn("Reindex bulk failed for {} documents: {}", docs.size(), ex.getMessage());
            return new int[] {0, docs.size()};
        }
    }

    private List<SearchEntityType> parseTypes(String typesCsv) {
        if (typesCsv == null || typesCsv.isBlank()) {
            return List.of();
        }
        Set<SearchEntityType> allowed = EnumSet.allOf(SearchEntityType.class);
        return Arrays.stream(typesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .map(SearchEntityType::valueOf)
                .filter(allowed::contains)
                .distinct()
                .collect(Collectors.toList());
    }
}

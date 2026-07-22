package com.flowledger.search.event;

import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.search.mapper.SearchDocumentMapper;
import com.flowledger.search.model.SearchDocument;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.search.service.SearchIndexService;
import com.flowledger.supplier.repository.SupplierRepository;
import com.flowledger.transport.repository.ShipmentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexEventListener {
    private final SearchIndexService indexService;
    private final SearchDocumentMapper mapper;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final SalesInvoiceRepository salesInvoices;
    private final ShipmentRepository shipments;

    @PersistenceContext
    private EntityManager entityManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpsert(SearchIndexUpsertEvent event) {
        try {
            SearchDocument document = loadDocument(event.organizationId(), event.entityType(), event.entityId());
            if (document == null) {
                indexService.delete(event.organizationId(), event.entityType(), event.entityId());
                return;
            }
            indexService.index(document);
        } catch (Exception ex) {
            log.warn(
                    "AFTER_COMMIT search upsert failed type={} entityId={}: {}",
                    event.entityType(),
                    event.entityId(),
                    ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDelete(SearchIndexDeleteEvent event) {
        try {
            indexService.delete(event.organizationId(), event.entityType(), event.entityId());
        } catch (Exception ex) {
            log.warn(
                    "AFTER_COMMIT search delete failed type={} entityId={}: {}",
                    event.entityType(),
                    event.entityId(),
                    ex.getMessage());
        }
    }

    private SearchDocument loadDocument(UUID organizationId, SearchEntityType type, UUID entityId) {
        return switch (type) {
            case PRODUCT ->
                products.findByIdAndOrganizationId(entityId, organizationId)
                        .filter(p -> p.isActive())
                        .map(mapper::fromProduct)
                        .orElse(null);
            case CUSTOMER ->
                customers
                        .findByIdAndOrganizationId(entityId, organizationId)
                        .filter(c -> !c.isArchived())
                        .map(mapper::fromCustomer)
                        .orElse(null);
            case SUPPLIER ->
                suppliers
                        .findByIdAndOrganizationId(entityId, organizationId)
                        .filter(s -> !s.isArchived())
                        .map(mapper::fromSupplier)
                        .orElse(null);
            case SALES_INVOICE ->
                salesInvoices
                        .findByIdAndOrganizationId(entityId, organizationId)
                        .map(invoice -> {
                            String customerName = customers
                                    .findByIdAndOrganizationId(invoice.getCustomerId(), organizationId)
                                    .map(c -> c.getCustomerName())
                                    .orElse(null);
                            return mapper.fromSalesInvoice(invoice, customerName);
                        })
                        .orElse(null);
            case PURCHASE_INVOICE -> {
                PurchaseInvoice invoice = entityManager.find(PurchaseInvoice.class, entityId);
                if (invoice == null || !organizationId.equals(invoice.getOrganizationId())) {
                    yield null;
                }
                String supplierName = suppliers
                        .findByIdAndOrganizationId(invoice.getSupplierId(), organizationId)
                        .map(s -> s.getSupplierName())
                        .orElse(null);
                yield mapper.fromPurchaseInvoice(invoice, supplierName);
            }
            case SHIPMENT ->
                shipments
                        .findByIdAndOrganizationIdAndDeletedFalse(entityId, organizationId)
                        .map(mapper::fromShipment)
                        .orElse(null);
        };
    }
}

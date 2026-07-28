package com.flowledger.search.service;

import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.search.mapper.SearchDocumentMapper;
import com.flowledger.search.model.SearchDocument;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.supplier.repository.SupplierRepository;
import com.flowledger.transport.repository.ShipmentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Loads searchable documents for index/AI bridges — shared existence check for AFTER_COMMIT listeners. */
@Service
@RequiredArgsConstructor
public class SearchEntityDocumentLoader {
    private final SearchDocumentMapper mapper;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final SalesInvoiceRepository salesInvoices;
    private final ShipmentRepository shipments;

    @PersistenceContext
    private EntityManager entityManager;

    public SearchDocument load(UUID organizationId, SearchEntityType type, UUID entityId) {
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

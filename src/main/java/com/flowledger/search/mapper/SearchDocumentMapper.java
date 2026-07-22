package com.flowledger.search.mapper;

import com.flowledger.customer.entity.Customer;
import com.flowledger.product.entity.Product;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.search.model.SearchDocument;
import com.flowledger.search.model.SearchDocumentIds;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.supplier.entity.Supplier;
import com.flowledger.transport.entity.Shipment;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SearchDocumentMapper {

    public SearchDocument fromProduct(Product product) {
        return base(
                        product.getOrganizationId(),
                        SearchEntityType.PRODUCT,
                        product.getId(),
                        product.getName(),
                        join("SKU: " + nullToEmpty(product.getSku()), product.getBrand()),
                        product.getSku(),
                        product.isActive() ? "ACTIVE" : "INACTIVE",
                        toInstant(product.getUpdatedAt()),
                        product.getName(),
                        product.getSku(),
                        product.getBarcode(),
                        product.getHsnSacCode(),
                        product.getBrand(),
                        product.getDescription())
                .build();
    }

    public SearchDocument fromCustomer(Customer customer) {
        return base(
                        customer.getOrganizationId(),
                        SearchEntityType.CUSTOMER,
                        customer.getId(),
                        customer.getCustomerName(),
                        join(customer.getCustomerCode(), customer.getCompanyName(), customer.getEmail()),
                        customer.getCustomerCode(),
                        customer.isArchived() ? "ARCHIVED" : "ACTIVE",
                        toInstant(customer.getUpdatedAt()),
                        customer.getCustomerName(),
                        customer.getCustomerCode(),
                        customer.getCompanyName(),
                        customer.getEmail(),
                        customer.getPhone(),
                        customer.getGstin())
                .build();
    }

    public SearchDocument fromSupplier(Supplier supplier) {
        return base(
                        supplier.getOrganizationId(),
                        SearchEntityType.SUPPLIER,
                        supplier.getId(),
                        supplier.getSupplierName(),
                        join(supplier.getSupplierCode(), supplier.getCompanyName(), supplier.getEmail()),
                        supplier.getSupplierCode(),
                        supplier.isArchived() ? "ARCHIVED" : "ACTIVE",
                        toInstant(supplier.getUpdatedAt()),
                        supplier.getSupplierName(),
                        supplier.getSupplierCode(),
                        supplier.getCompanyName(),
                        supplier.getEmail(),
                        supplier.getPhone(),
                        supplier.getGstin())
                .build();
    }

    public SearchDocument fromSalesInvoice(SalesInvoice invoice, String customerName) {
        String status = invoice.getStatus() == null ? null : invoice.getStatus().name();
        return base(
                        invoice.getOrganizationId(),
                        SearchEntityType.SALES_INVOICE,
                        invoice.getId(),
                        invoice.getInvoiceNumber(),
                        join(customerName, status, invoice.getCustomerGstin()),
                        invoice.getInvoiceNumber(),
                        status,
                        toInstant(invoice.getUpdatedAt()),
                        invoice.getInvoiceNumber(),
                        customerName,
                        invoice.getCustomerGstin(),
                        invoice.getNotes(),
                        status)
                .build();
    }

    public SearchDocument fromPurchaseInvoice(PurchaseInvoice invoice, String supplierName) {
        return base(
                        invoice.getOrganizationId(),
                        SearchEntityType.PURCHASE_INVOICE,
                        invoice.getId(),
                        invoice.getInvoiceNumber(),
                        join(supplierName, invoice.getSupplierInvoiceNumber(), invoice.getStatus()),
                        firstNonBlank(invoice.getInvoiceNumber(), invoice.getSupplierInvoiceNumber()),
                        invoice.getStatus(),
                        toInstant(invoice.getUpdatedAt()),
                        invoice.getInvoiceNumber(),
                        invoice.getSupplierInvoiceNumber(),
                        supplierName,
                        invoice.getSupplierGstin(),
                        invoice.getNotes(),
                        invoice.getStatus())
                .build();
    }

    public SearchDocument fromShipment(Shipment shipment) {
        String status = shipment.getStatus() == null ? null : shipment.getStatus().name();
        return base(
                        shipment.getOrganizationId(),
                        SearchEntityType.SHIPMENT,
                        shipment.getId(),
                        shipment.getShipmentNumber(),
                        join(
                                shipment.getSourceDocumentType(),
                                status,
                                shipment.getEwayBillNumber(),
                                shipment.getGpsTrackingUrl()),
                        shipment.getShipmentNumber(),
                        status,
                        toInstant(shipment.getUpdatedAt()),
                        shipment.getShipmentNumber(),
                        shipment.getSourceDocumentType(),
                        shipment.getEwayBillNumber(),
                        shipment.getEinvoiceReference(),
                        shipment.getRemarks(),
                        status)
                .build();
    }

    private SearchDocument.SearchDocumentBuilder base(
            java.util.UUID organizationId,
            SearchEntityType type,
            java.util.UUID entityId,
            String title,
            String subtitle,
            String referenceNumber,
            String status,
            Instant updatedAt,
            String... searchParts) {
        return SearchDocument.builder()
                .documentId(SearchDocumentIds.of(organizationId, type, entityId))
                .organizationId(organizationId.toString())
                .entityId(entityId.toString())
                .entityType(type.name())
                .title(nullToEmpty(title))
                .subtitle(subtitle)
                .referenceNumber(referenceNumber)
                .status(status)
                .updatedAt(updatedAt == null ? Instant.now().toString() : updatedAt.toString())
                .searchText(join(searchParts));
    }

    private static Instant toInstant(java.time.OffsetDateTime value) {
        return value == null ? Instant.now() : value.toInstant();
    }

    private static String join(String... parts) {
        return Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

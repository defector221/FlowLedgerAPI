package com.flowledger.search.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.customer.entity.Customer;
import com.flowledger.product.entity.Product;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.search.model.SearchDocument;
import com.flowledger.search.model.SearchDocumentIds;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.supplier.entity.Supplier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchDocumentMapperTest {
    private final SearchDocumentMapper mapper = new SearchDocumentMapper();

    @Test
    void mapsProductByNameAndSku() {
        UUID org = UUID.randomUUID();
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setOrganizationId(org);
        product.setName("Apple iPhone 16");
        product.setSku("IP16-128");
        product.setBarcode("890123");
        product.setHsnSacCode("8517");
        product.setActive(true);

        SearchDocument doc = mapper.fromProduct(product);
        assertEquals(SearchDocumentIds.of(org, SearchEntityType.PRODUCT, product.getId()), doc.getDocumentId());
        assertEquals("PRODUCT", doc.getEntityType());
        assertEquals("Apple iPhone 16", doc.getTitle());
        assertEquals("IP16-128", doc.getReferenceNumber());
        assertTrue(doc.getSearchText().contains("IP16-128"));
        assertTrue(doc.getSearchText().contains("890123"));
    }

    @Test
    void mapsCustomerEmailAndPhone() {
        UUID org = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setOrganizationId(org);
        customer.setCustomerName("YRV Solutions");
        customer.setCustomerCode("CUST-1");
        customer.setEmail("hello@yrv.test");
        customer.setPhone("9999999999");
        customer.setGstin("27AAAAA0000A1Z5");

        SearchDocument doc = mapper.fromCustomer(customer);
        assertEquals("CUSTOMER", doc.getEntityType());
        assertTrue(doc.getSearchText().contains("hello@yrv.test"));
        assertTrue(doc.getSearchText().contains("9999999999"));
        assertTrue(doc.getSearchText().contains("27AAAAA0000A1Z5"));
    }

    @Test
    void mapsSupplierGstin() {
        UUID org = UUID.randomUUID();
        Supplier supplier = new Supplier();
        supplier.setId(UUID.randomUUID());
        supplier.setOrganizationId(org);
        supplier.setSupplierName("Acme Supplies");
        supplier.setSupplierCode("SUP-1");
        supplier.setGstin("29BBBBB0000B1Z5");

        SearchDocument doc = mapper.fromSupplier(supplier);
        assertEquals("SUPPLIER", doc.getEntityType());
        assertTrue(doc.getSearchText().contains("29BBBBB0000B1Z5"));
    }

    @Test
    void mapsSalesInvoiceNumber() {
        UUID org = UUID.randomUUID();
        SalesInvoice invoice = new SalesInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrganizationId(org);
        invoice.setInvoiceNumber("INV-2026-001");
        invoice.setInvoiceDate(LocalDate.of(2026, 1, 1));
        invoice.setCustomerId(UUID.randomUUID());
        invoice.setStatus(SalesInvoice.Status.CONFIRMED);

        SearchDocument doc = mapper.fromSalesInvoice(invoice, "YRV Solutions");
        assertEquals("SALES_INVOICE", doc.getEntityType());
        assertEquals("INV-2026-001", doc.getReferenceNumber());
        assertTrue(doc.getSearchText().contains("YRV Solutions"));
    }

    @Test
    void mapsPurchaseInvoiceReference() {
        UUID org = UUID.randomUUID();
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrganizationId(org);
        invoice.setInvoiceNumber("PINV-1");
        invoice.setSupplierInvoiceNumber("SUP-INV-9");
        invoice.setSupplierId(UUID.randomUUID());
        invoice.setStatus("CONFIRMED");
        invoice.setGrandTotal(BigDecimal.TEN);

        SearchDocument doc = mapper.fromPurchaseInvoice(invoice, "Acme Supplies");
        assertEquals("PURCHASE_INVOICE", doc.getEntityType());
        assertTrue(doc.getSearchText().contains("SUP-INV-9"));
        assertTrue(doc.getSearchText().contains("Acme Supplies"));
    }
}

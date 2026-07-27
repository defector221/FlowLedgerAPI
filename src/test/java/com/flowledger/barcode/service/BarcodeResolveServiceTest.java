package com.flowledger.barcode.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.barcode.dto.BarcodeDtos.ScanResolveRequest;
import com.flowledger.barcode.entity.ScanHistory;
import com.flowledger.barcode.repository.ScanHistoryRepository;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.product.repository.SupplierCatalogItemRepository;
import com.flowledger.retail.dto.RetailDtos.ProductLookupResponse;
import com.flowledger.retail.entity.RetailProductBarcode;
import com.flowledger.retail.repository.RetailProductBarcodeRepository;
import com.flowledger.retail.repository.RetailProductVariantRepository;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BarcodeResolveServiceTest {
    UUID orgId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    AtomicReference<RetailProductBarcode> activeBarcode = new AtomicReference<>();
    AtomicReference<ScanHistory> savedScan = new AtomicReference<>();

    BarcodeResolveService service;

    @BeforeEach
    void setUp() {
        activeBarcode.set(null);
        savedScan.set(null);

        ScanHistoryRepository scanHistory = proxy(ScanHistoryRepository.class, (m, a) -> switch (m.getName()) {
            case "save" -> {
                savedScan.set((ScanHistory) a[0]);
                yield savedScan.get();
            }
            default -> defaultValue(m.getReturnType());
        });

        RetailProductBarcodeRepository barcodes = proxy(RetailProductBarcodeRepository.class, (m, a) -> switch (m.getName()) {
            case "findActiveByOrganizationIdAndBarcode" -> Optional.ofNullable(activeBarcode.get());
            default -> defaultValue(m.getReturnType());
        });

        RetailProductVariantRepository variants =
                proxy(RetailProductVariantRepository.class, (m, a) -> Optional.empty());

        Product product = new Product();
        product.setId(productId);
        product.setOrganizationId(orgId);
        product.setSku("SKU-1");
        product.setName("Widget");
        product.setSellingPrice(BigDecimal.TEN);
        product.setMrp(BigDecimal.TEN);
        product.setUnitId(UUID.randomUUID());

        ProductRepository products = proxy(ProductRepository.class, (m, a) -> switch (m.getName()) {
            case "findByIdAndOrganizationId" -> Optional.of(product);
            case "findFirstByOrganizationIdAndBarcode" -> Optional.empty();
            case "findByOrganizationIdAndActiveTrue" -> List.of(product);
            default -> defaultValue(m.getReturnType());
        });

        SupplierCatalogItemRepository supplierCatalog =
                proxy(SupplierCatalogItemRepository.class, (m, a) -> Optional.empty());

        service = new BarcodeResolveService(scanHistory, barcodes, variants, products, supplierCatalog);
        TenantContext.set(orgId, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void resolve_skipsInactiveBarcodeByUsingActiveQuery() {
        RetailProductBarcode deleted = new RetailProductBarcode();
        deleted.setOrganizationId(orgId);
        deleted.setProductId(productId);
        deleted.setBarcode("9990001112223");
        deleted.setStatus("INACTIVE");
        deleted.setDeletedAt(OffsetDateTime.now());
        activeBarcode.set(null);

        var response = service.resolve(new ScanResolveRequest("9990001112223", "SCANNER", "TEST"));
        assertFalse(response.success());
    }

    @Test
    void resolve_returnsActiveAlias() {
        RetailProductBarcode row = new RetailProductBarcode();
        row.setOrganizationId(orgId);
        row.setProductId(productId);
        row.setBarcode("8901234567890");
        row.setStatus("ACTIVE");
        activeBarcode.set(row);

        var response = service.resolve(new ScanResolveRequest("8901234567890", "SCANNER", "TEST"));
        assertTrue(response.success());
        assertEquals("RETAIL_ALIAS", response.matchType());
        assertEquals(productId, response.product().productId());
    }

    @Test
    void lookupByBarcode_throwsWhenNotFound() {
        activeBarcode.set(null);
        assertThrows(ResponseStatusException.class, () -> service.lookupByBarcode("missing"));
    }

    @Test
    void lookupByBarcodeOptional_returnsEmptyWhenNotFound() {
        activeBarcode.set(null);
        Optional<ProductLookupResponse> result = service.lookupByBarcodeOptional("missing");
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invoker invoker) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(p, args);
            }
            return invoker.invoke(method, args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }

    @FunctionalInterface
    private interface Invoker {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}

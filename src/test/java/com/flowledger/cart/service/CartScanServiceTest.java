package com.flowledger.cart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.flowledger.barcode.service.BarcodeResolveService;
import com.flowledger.cart.dto.CartDtos.CartScanRequest;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.inventory.allocation.*;
import com.flowledger.retail.dto.RetailDtos.ProductLookupResponse;
import com.flowledger.retail.service.PosSaleService;
import com.flowledger.retail.service.RetailModuleGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CartScanServiceTest {
    UUID orgId = UUID.randomUUID();
    UUID warehouseId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    CartScanService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(orgId, UUID.randomUUID());

        RetailModuleGuard guard = new RetailModuleGuard(null) {
            @Override
            public UUID ensureEnabled() {
                return orgId;
            }
        };

        ProductLookupResponse product = new ProductLookupResponse(
                productId,
                null,
                "Widget",
                "8901234567890",
                BigDecimal.TEN,
                BigDecimal.TEN,
                null,
                UUID.randomUUID(),
                null);

        BarcodeResolveService barcodeResolve = new BarcodeResolveService(null, null, null, null, null) {
            @Override
            public ProductLookupResponse lookupByBarcode(String barcode) {
                return product;
            }
        };

        InventoryAllocationEngine allocationEngine = stubAllocationEngine(warehouseId);

        PosSaleService posSales = new StubPosSaleService();

        service = new CartScanService(guard, barcodeResolve, allocationEngine, posSales);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void scan_returnsAllocatedProduct() {
        var response = service.scan(new CartScanRequest("8901234567890", BigDecimal.ONE, warehouseId, null, null, null));
        assertEquals(AllocationStatus.AUTO_ALLOCATED.name(), response.status());
        assertNotNull(response.product());
        assertEquals(productId, response.product().productId());
    }

    @Test
    void scan_returnsInvalidProductWhenBarcodeMissing() {
        BarcodeResolveService missing = new BarcodeResolveService(null, null, null, null, null) {
            @Override
            public ProductLookupResponse lookupByBarcode(String barcode) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "missing");
            }
        };
        RetailModuleGuard guard = new RetailModuleGuard(null) {
            @Override
            public UUID ensureEnabled() {
                return orgId;
            }
        };
        CartScanService local = new CartScanService(guard, missing, stubAllocationEngine(warehouseId), new StubPosSaleService());

        var response = local.scan(new CartScanRequest("missing", BigDecimal.ONE, warehouseId, null, null, null));
        assertEquals(AllocationStatus.INVALID_PRODUCT.name(), response.status());
    }

    private static InventoryAllocationEngine stubAllocationEngine(UUID warehouseId) {
        AllocationResult allocated = new AllocationResult(
                AllocationStatus.AUTO_ALLOCATED,
                new AllocatedInventory(
                        UUID.randomUUID(),
                        warehouseId,
                        "Main",
                        "B-1",
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        AllocationMode.BATCH_AUTO),
                List.of(),
                null);
        return new InventoryAllocationEngine() {
            @Override
            public AllocationResult allocate(AllocationRequest request) {
                return allocated;
            }

            @Override
            public AllocationResult allocateWithBatch(AllocationRequest request, UUID batchId) {
                return allocated;
            }

            @Override
            public CheckoutValidationResult revalidateForCheckout(List<CartLineAllocation> cartLines) {
                return null;
            }

            @Override
            public ReservationResult reserveForDocument(DocumentLineAllocationRequest request) {
                return null;
            }

            @Override
            public void releaseByReference(String referenceType, UUID referenceId) {}

            @Override
            public void consumeByReference(String referenceType, UUID referenceId) {}
        };
    }

    private static final class StubPosSaleService extends PosSaleService {
        private StubPosSaleService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}

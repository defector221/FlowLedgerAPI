package com.flowledger.cart.service;

import static com.flowledger.cart.dto.CartDtos.*;
import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.barcode.service.BarcodeResolveService;
import com.flowledger.inventory.allocation.*;
import com.flowledger.retail.service.PosSaleService;
import com.flowledger.retail.service.RetailModuleGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CartScanService {
    private final RetailModuleGuard guard;
    private final BarcodeResolveService barcodeResolve;
    private final InventoryAllocationEngine allocationEngine;
    private final PosSaleService posSales;

    public CartScanService(
            RetailModuleGuard guard,
            BarcodeResolveService barcodeResolve,
            InventoryAllocationEngine allocationEngine,
            PosSaleService posSales) {
        this.guard = guard;
        this.barcodeResolve = barcodeResolve;
        this.allocationEngine = allocationEngine;
        this.posSales = posSales;
    }

    public CartScanResponse scan(CartScanRequest request) {
        org();
        ProductLookupResponse product = lookupProduct(request.barcode());
        if (product == null) {
            return new CartScanResponse(
                    AllocationStatus.INVALID_PRODUCT.name(),
                    null,
                    null,
                    List.of(),
                    "Product not found for barcode",
                    null);
        }
        AllocationResult result = allocationEngine.allocate(
                new AllocationRequest(org(), product.productId(), request.warehouseId(), request.quantity(), null));
        return finalizeScan(request.barcode(), product, result, request.cartId());
    }

    public CartScanResponse confirm(CartScanConfirmRequest request) {
        org();
        ProductLookupResponse product = lookupProduct(request.barcode());
        if (product == null) {
            return new CartScanResponse(
                    AllocationStatus.INVALID_PRODUCT.name(),
                    null,
                    null,
                    List.of(),
                    "Product not found for barcode",
                    null);
        }
        AllocationResult result = allocationEngine.allocateWithBatch(
                new AllocationRequest(
                        org(), product.productId(), request.warehouseId(), request.quantity(), request.batchId()),
                request.batchId());
        return finalizeScan(request.barcode(), product, result, request.cartId());
    }

    private CartScanResponse finalizeScan(
            String barcode, ProductLookupResponse product, AllocationResult result, UUID cartId) {
        PosScanResponse scan = mapScan(product, result);
        PosSaleResponse cart = null;
        if (cartId != null
                && AllocationStatus.AUTO_ALLOCATED.name().equals(scan.status())
                && scan.allocated() != null
                && scan.product() != null) {
            cart = posSales.addLine(
                    cartId,
                    new PosLineRequest(
                            scan.product().productId(),
                            scan.product().variantId(),
                            scan.product().name(),
                            scan.product().barcode() != null ? scan.product().barcode() : barcode,
                            scan.allocated().quantity() != null
                                    ? scan.allocated().quantity()
                                    : BigDecimal.ONE,
                            scan.product().sellingPrice() != null
                                    ? scan.product().sellingPrice()
                                    : BigDecimal.ZERO,
                            null,
                            null,
                            scan.allocated().warehouseId(),
                            scan.allocated().batchId(),
                            scan.allocated().allocationMode()));
        }
        return new CartScanResponse(
                scan.status(), scan.product(), scan.allocated(), scan.candidates(), scan.reason(), cart);
    }

    private ProductLookupResponse lookupProduct(String barcode) {
        try {
            return barcodeResolve.lookupByBarcode(barcode.trim());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return null;
            }
            throw ex;
        }
    }

    private PosScanResponse mapScan(ProductLookupResponse product, AllocationResult result) {
        if (product == null) {
            return new PosScanResponse(
                    AllocationStatus.INVALID_PRODUCT.name(), null, null, List.of(), "Product not found for barcode");
        }
        return new PosScanResponse(
                result.status().name(),
                product,
                mapAllocated(result.allocated()),
                result.candidates().stream().map(this::mapCandidate).toList(),
                result.reason());
    }

    private AllocationCandidateResponse mapCandidate(AllocationCandidate candidate) {
        return new AllocationCandidateResponse(
                candidate.batchId(),
                candidate.warehouseId(),
                candidate.warehouseName(),
                candidate.batchNumber(),
                candidate.availableQty(),
                candidate.expiryDate(),
                candidate.receivedDate(),
                candidate.lotNumber(),
                candidate.qualityStatus(),
                candidate.reservedQty());
    }

    private AllocatedInventoryResponse mapAllocated(AllocatedInventory allocated) {
        if (allocated == null) {
            return null;
        }
        return new AllocatedInventoryResponse(
                allocated.batchId(),
                allocated.warehouseId(),
                allocated.warehouseName(),
                allocated.batchNumber(),
                allocated.quantity(),
                allocated.expiryDate(),
                allocated.receivedDate(),
                allocated.lotNumber(),
                allocated.allocationMode().name());
    }

    private UUID org() {
        return guard.ensureEnabled();
    }
}

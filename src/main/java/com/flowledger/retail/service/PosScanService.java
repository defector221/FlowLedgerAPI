package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.inventory.allocation.*;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PosScanService {
    private final RetailModuleGuard guard;
    private final RetailCatalogService catalog;
    private final InventoryAllocationEngine allocationEngine;
    private final RetailStoreRepository stores;

    public PosScanService(
            RetailModuleGuard guard,
            RetailCatalogService catalog,
            InventoryAllocationEngine allocationEngine,
            RetailStoreRepository stores) {
        this.guard = guard;
        this.catalog = catalog;
        this.allocationEngine = allocationEngine;
        this.stores = stores;
    }

    public PosScanResponse scan(PosScanRequest request) {
        ProductLookupResponse product = lookupProduct(request.barcode());
        UUID warehouseId = resolveWarehouse(request.warehouseId());
        AllocationResult result = allocationEngine.allocate(
                new AllocationRequest(org(), product.productId(), warehouseId, request.quantity(), null));
        return mapScan(product, result);
    }

    public PosScanResponse confirm(PosScanConfirmRequest request) {
        ProductLookupResponse product = lookupProduct(request.barcode());
        UUID warehouseId = resolveWarehouse(request.warehouseId());
        AllocationResult result = allocationEngine.allocateWithBatch(
                new AllocationRequest(org(), product.productId(), warehouseId, request.quantity(), request.batchId()),
                request.batchId());
        return mapScan(product, result);
    }

    private ProductLookupResponse lookupProduct(String barcode) {
        try {
            return catalog.lookupByBarcode(barcode.trim());
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

    private UUID resolveWarehouse(UUID requested) {
        if (requested != null) {
            return requested;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseId is required");
    }

    public UUID warehouseForStore(UUID storeId) {
        RetailStore store = stores.findByIdAndOrganizationIdAndDeletedFalse(storeId, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"));
        return store.getWarehouseId();
    }

    private UUID org() {
        return guard.ensureEnabled();
    }
}

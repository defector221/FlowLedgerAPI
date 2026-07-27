package com.flowledger.inventory.allocation;

import com.flowledger.inventory.service.StockReservationService;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryAllocationEngineImpl implements InventoryAllocationEngine {
    private final ProductRepository products;
    private final OrganizationSettingsRepository orgSettings;
    private final WarehouseRepository warehouses;
    private final WarehousePoolAllocator warehousePoolAllocator;
    private final BatchAllocationEngine batchAllocationEngine;
    private final StockReservationService reservations;

    public InventoryAllocationEngineImpl(
            ProductRepository products,
            OrganizationSettingsRepository orgSettings,
            WarehouseRepository warehouses,
            WarehousePoolAllocator warehousePoolAllocator,
            BatchAllocationEngine batchAllocationEngine,
            StockReservationService reservations) {
        this.products = products;
        this.orgSettings = orgSettings;
        this.warehouses = warehouses;
        this.warehousePoolAllocator = warehousePoolAllocator;
        this.batchAllocationEngine = batchAllocationEngine;
        this.reservations = reservations;
    }

    @Override
    public AllocationResult allocate(AllocationRequest request) {
        Product product = products
                .findByIdAndOrganizationId(request.productId(), request.organizationId())
                .orElse(null);
        if (product == null) {
            return AllocationResult.invalidProduct("Product not found");
        }
        if (!product.isBatchTracking()) {
            return warehousePoolAllocator.allocate(request);
        }
        AllocationStrategyType strategy = resolveStrategy(request.organizationId());
        UUID preferredWarehouse = warehouses
                .findFirstByOrganizationIdAndDefaultWarehouseTrue(request.organizationId())
                .map(w -> w.getId())
                .orElse(request.warehouseId());
        return batchAllocationEngine.allocate(request, strategy, preferredWarehouse);
    }

    @Override
    public AllocationResult allocateWithBatch(AllocationRequest request, UUID batchId) {
        Product product = products
                .findByIdAndOrganizationId(request.productId(), request.organizationId())
                .orElse(null);
        if (product == null) {
            return AllocationResult.invalidProduct("Product not found");
        }
        if (!product.isBatchTracking()) {
            return warehousePoolAllocator.allocate(request);
        }
        return batchAllocationEngine.allocateWithBatch(request, batchId);
    }

    @Override
    public CheckoutValidationResult revalidateForCheckout(List<CartLineAllocation> cartLines) {
        List<CheckoutLineIssue> issues = new ArrayList<>();
        List<CartLineAllocation> resolved = new ArrayList<>();
        AllocationStatus overall = AllocationStatus.AUTO_ALLOCATED;

        for (CartLineAllocation line : cartLines) {
            Product product = products
                    .findByIdAndOrganizationId(line.productId(), line.organizationId())
                    .orElse(null);
            if (product == null) {
                issues.add(new CheckoutLineIssue(
                        line.lineId(),
                        line.productId(),
                        AllocationStatus.INVALID_PRODUCT,
                        false,
                        "Product not found",
                        List.of()));
                overall = AllocationStatus.INVALID_PRODUCT;
                continue;
            }

            AllocationRequest request = new AllocationRequest(
                    line.organizationId(),
                    line.productId(),
                    line.warehouseId(),
                    line.quantity(),
                    line.inventoryBatchId());

            if (!product.isBatchTracking()) {
                AllocationResult fresh = warehousePoolAllocator.allocate(request);
                if (fresh.status() == AllocationStatus.OUT_OF_STOCK) {
                    issues.add(new CheckoutLineIssue(
                            line.lineId(),
                            line.productId(),
                            AllocationStatus.OUT_OF_STOCK,
                            false,
                            fresh.reason(),
                            List.of()));
                    overall = AllocationStatus.OUT_OF_STOCK;
                } else {
                    resolved.add(line);
                }
                continue;
            }

            AllocationResult fresh;
            if (line.allocationMode() == AllocationMode.BATCH_MANUAL && line.inventoryBatchId() != null) {
                fresh = batchAllocationEngine.allocateWithBatch(request, line.inventoryBatchId());
            } else {
                AllocationStrategyType strategy = resolveStrategy(line.organizationId());
                UUID preferredWarehouse = warehouses
                        .findFirstByOrganizationIdAndDefaultWarehouseTrue(line.organizationId())
                        .map(w -> w.getId())
                        .orElse(line.warehouseId());
                fresh = batchAllocationEngine.allocate(request, strategy, preferredWarehouse);
            }

            if (fresh.status() == AllocationStatus.OUT_OF_STOCK) {
                issues.add(new CheckoutLineIssue(
                        line.lineId(),
                        line.productId(),
                        AllocationStatus.OUT_OF_STOCK,
                        false,
                        fresh.reason(),
                        List.of()));
                overall = AllocationStatus.OUT_OF_STOCK;
                continue;
            }

            if (fresh.status() == AllocationStatus.CONFLICT) {
                issues.add(new CheckoutLineIssue(
                        line.lineId(),
                        line.productId(),
                        AllocationStatus.CONFLICT,
                        line.allocationMode() != AllocationMode.BATCH_MANUAL,
                        "Multiple batches available — confirm selection",
                        fresh.candidates()));
                overall = AllocationStatus.CONFLICT;
                continue;
            }

            UUID freshBatchId = fresh.allocated() != null ? fresh.allocated().batchId() : null;
            boolean changed = !Objects.equals(freshBatchId, line.inventoryBatchId())
                    || (line.allocationMode() == AllocationMode.BATCH_AUTO
                            && fresh.status() == AllocationStatus.CONFLICT);
            if (changed) {
                issues.add(new CheckoutLineIssue(
                        line.lineId(),
                        line.productId(),
                        AllocationStatus.CONFLICT,
                        true,
                        "Inventory allocation changed since scan",
                        fresh.candidates()));
                overall = AllocationStatus.CONFLICT;
                continue;
            }

            resolved.add(line);
        }

        if (!issues.isEmpty()) {
            return new CheckoutValidationResult(overall, issues, resolved);
        }
        return CheckoutValidationResult.ok(resolved);
    }

    @Override
    @Transactional
    public ReservationResult reserveForDocument(DocumentLineAllocationRequest request) {
        AllocationRequest allocationRequest = new AllocationRequest(
                request.organizationId(),
                request.productId(),
                request.warehouseId(),
                request.quantity(),
                request.preferredBatchId(),
                request.excludeReservationId());

        AllocationResult allocated;
        if (request.preferredMode() == AllocationMode.BATCH_MANUAL && request.preferredBatchId() != null) {
            allocated = allocateWithBatch(allocationRequest, request.preferredBatchId());
        } else if (request.preferredBatchId() != null) {
            allocated = allocateWithBatch(allocationRequest, request.preferredBatchId());
        } else {
            allocated = allocate(allocationRequest);
        }

        if (allocated.status() == AllocationStatus.INVALID_PRODUCT) {
            return ReservationResult.invalidProduct(allocated.reason());
        }
        if (allocated.status() == AllocationStatus.OUT_OF_STOCK) {
            return ReservationResult.outOfStock(allocated.reason());
        }
        if (allocated.status() == AllocationStatus.CONFLICT) {
            return ReservationResult.conflict(allocated.candidates());
        }

        AllocatedInventory inv = allocated.allocated();
        var reservation = reservations.reserve(
                request.productId(),
                inv.warehouseId(),
                request.quantity(),
                inv.batchId(),
                inv.allocationMode(),
                request.referenceType(),
                request.referenceId(),
                request.lineReferenceId(),
                request.expiresAt());
        return ReservationResult.reserved(reservation.getId(), inv);
    }

    @Override
    @Transactional
    public void releaseByReference(String referenceType, UUID referenceId) {
        reservations.releaseByReference(referenceType, referenceId);
    }

    @Override
    @Transactional
    public void consumeByReference(String referenceType, UUID referenceId) {
        reservations.consumeByReference(referenceType, referenceId);
    }

    private AllocationStrategyType resolveStrategy(UUID orgId) {
        return orgSettings
                .findByOrganizationId(orgId)
                .map(OrganizationSettings::getAllocationStrategy)
                .map(AllocationStrategyType::fromSetting)
                .orElse(AllocationStrategyType.FIFO)
                .effective();
    }
}

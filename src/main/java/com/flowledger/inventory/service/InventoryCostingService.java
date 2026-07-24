package com.flowledger.inventory.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.inventory.entity.InventoryCostLayer;
import com.flowledger.inventory.repository.InventoryCostLayerRepository;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCostingService {
    public static final String METHOD_FIFO = "FIFO";
    public static final String METHOD_WAC = "WAC";

    private final InventoryCostLayerRepository layers;
    private final OrganizationRepository organizations;

    public InventoryCostingService(InventoryCostLayerRepository layers, OrganizationRepository organizations) {
        this.layers = layers;
        this.organizations = organizations;
    }

    public String costingMethod(UUID orgId) {
        Organization org = organizations.findById(orgId).orElseThrow();
        String method = org.getInventoryCostingMethod();
        if (method == null || method.isBlank()) {
            return METHOD_WAC;
        }
        return method.trim().toUpperCase();
    }

    @Transactional
    public InventoryCostLayer receive(
            UUID orgId, UUID productId, UUID warehouseId, UUID batchId, BigDecimal qty, BigDecimal unitCost) {
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessException("Receive quantity must be positive");
        }
        BigDecimal cost = unitCost == null ? BigDecimal.ZERO : unitCost;
        String method = costingMethod(orgId);

        if (METHOD_WAC.equals(method)) {
            return receiveWac(orgId, productId, warehouseId, batchId, qty, cost);
        }
        InventoryCostLayer layer = newLayer(orgId, productId, warehouseId, batchId, qty, cost, METHOD_FIFO);
        return layers.save(layer);
    }

    private InventoryCostLayer receiveWac(
            UUID orgId, UUID productId, UUID warehouseId, UUID batchId, BigDecimal qty, BigDecimal unitCost) {
        List<InventoryCostLayer> open = layers.findOpenLayers(orgId, productId, warehouseId);
        BigDecimal existingQty = BigDecimal.ZERO;
        BigDecimal existingValue = BigDecimal.ZERO;
        for (InventoryCostLayer layer : open) {
            existingQty = existingQty.add(layer.getQtyRemaining());
            existingValue = existingValue.add(layer.getQtyRemaining().multiply(layer.getUnitCost()));
            layer.setQtyRemaining(BigDecimal.ZERO);
            layers.save(layer);
        }
        BigDecimal newValue = qty.multiply(unitCost);
        BigDecimal totalQty = existingQty.add(qty);
        BigDecimal avg = totalQty.signum() == 0
                ? unitCost
                : existingValue.add(newValue).divide(totalQty, 6, RoundingMode.HALF_UP);
        InventoryCostLayer layer = newLayer(orgId, productId, warehouseId, batchId, totalQty, avg, METHOD_WAC);
        return layers.save(layer);
    }

    /**
     * Consumes quantity from cost layers. Returns total cost of goods issued and average unit cost.
     */
    @Transactional
    public ConsumeResult consume(UUID orgId, UUID productId, UUID warehouseId, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessException("Consume quantity must be positive");
        }
        String method = costingMethod(orgId);
        if (METHOD_WAC.equals(method)) {
            return consumeWac(orgId, productId, warehouseId, qty);
        }
        return consumeFifo(orgId, productId, warehouseId, qty);
    }

    private ConsumeResult consumeFifo(UUID orgId, UUID productId, UUID warehouseId, BigDecimal qty) {
        List<InventoryCostLayer> open = layers.findOpenLayersFifo(orgId, productId, warehouseId);
        BigDecimal remaining = qty;
        BigDecimal totalCost = BigDecimal.ZERO;
        List<LayerConsumption> detail = new ArrayList<>();
        for (InventoryCostLayer layer : open) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = remaining.min(layer.getQtyRemaining());
            if (take.signum() <= 0) {
                continue;
            }
            BigDecimal lineCost = take.multiply(layer.getUnitCost());
            totalCost = totalCost.add(lineCost);
            layer.setQtyRemaining(layer.getQtyRemaining().subtract(take));
            layers.save(layer);
            detail.add(new LayerConsumption(layer.getId(), take, layer.getUnitCost(), lineCost));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            // Insufficient cost layers — treat remainder at zero cost (stock may still exist without layers)
            detail.add(new LayerConsumption(null, remaining, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        BigDecimal avg = qty.signum() == 0 ? BigDecimal.ZERO : totalCost.divide(qty, 6, RoundingMode.HALF_UP);
        return new ConsumeResult(totalCost, avg, detail);
    }

    private ConsumeResult consumeWac(UUID orgId, UUID productId, UUID warehouseId, BigDecimal qty) {
        List<InventoryCostLayer> open = layers.findOpenLayers(orgId, productId, warehouseId);
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (InventoryCostLayer layer : open) {
            totalQty = totalQty.add(layer.getQtyRemaining());
            totalValue = totalValue.add(layer.getQtyRemaining().multiply(layer.getUnitCost()));
        }
        BigDecimal avg =
                totalQty.signum() == 0 ? BigDecimal.ZERO : totalValue.divide(totalQty, 6, RoundingMode.HALF_UP);
        BigDecimal remaining = qty;
        BigDecimal totalCost = BigDecimal.ZERO;
        List<LayerConsumption> detail = new ArrayList<>();
        for (InventoryCostLayer layer : open) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = remaining.min(layer.getQtyRemaining());
            if (take.signum() <= 0) {
                continue;
            }
            BigDecimal lineCost = take.multiply(avg);
            totalCost = totalCost.add(lineCost);
            layer.setQtyRemaining(layer.getQtyRemaining().subtract(take));
            // Keep layer unit cost at WAC average for remaining stock
            layer.setUnitCost(avg);
            layers.save(layer);
            detail.add(new LayerConsumption(layer.getId(), take, avg, lineCost));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            detail.add(new LayerConsumption(null, remaining, avg, remaining.multiply(avg)));
            totalCost = totalCost.add(remaining.multiply(avg));
        }
        return new ConsumeResult(totalCost, avg, detail);
    }

    @Transactional(readOnly = true)
    public BigDecimal currentUnitCost(UUID orgId, UUID productId, UUID warehouseId) {
        List<InventoryCostLayer> open = layers.findOpenLayers(orgId, productId, warehouseId);
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (InventoryCostLayer layer : open) {
            totalQty = totalQty.add(layer.getQtyRemaining());
            totalValue = totalValue.add(layer.getQtyRemaining().multiply(layer.getUnitCost()));
        }
        if (totalQty.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalValue.divide(totalQty, 6, RoundingMode.HALF_UP);
    }

    private static InventoryCostLayer newLayer(
            UUID orgId,
            UUID productId,
            UUID warehouseId,
            UUID batchId,
            BigDecimal qty,
            BigDecimal unitCost,
            String method) {
        InventoryCostLayer layer = new InventoryCostLayer();
        layer.setOrganizationId(orgId);
        layer.setProductId(productId);
        layer.setWarehouseId(warehouseId);
        layer.setBatchId(batchId);
        layer.setQtyRemaining(qty);
        layer.setUnitCost(unitCost);
        layer.setReceivedAt(OffsetDateTime.now());
        layer.setMethod(method);
        return layer;
    }

    public record LayerConsumption(UUID layerId, BigDecimal qty, BigDecimal unitCost, BigDecimal cost) {}

    public record ConsumeResult(BigDecimal totalCost, BigDecimal averageUnitCost, List<LayerConsumption> layers) {}
}

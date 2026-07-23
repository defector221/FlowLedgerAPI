package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.retail.domain.RetailEnums.CountType;
import com.flowledger.retail.domain.RetailEnums.LocationType;
import com.flowledger.retail.entity.RetailInventoryLocation;
import com.flowledger.retail.entity.RetailStockCount;
import com.flowledger.retail.entity.RetailStockCountLine;
import com.flowledger.retail.repository.RetailInventoryLocationRepository;
import com.flowledger.retail.repository.RetailStockCountLineRepository;
import com.flowledger.retail.repository.RetailStockCountRepository;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RetailInventoryService {
    private final RetailModuleGuard guard;
    private final RetailInventoryLocationRepository locations;
    private final RetailStockCountRepository counts;
    private final RetailStockCountLineRepository countLines;
    private final RetailStoreRepository stores;

    public RetailInventoryService(
            RetailModuleGuard guard,
            RetailInventoryLocationRepository locations,
            RetailStockCountRepository counts,
            RetailStockCountLineRepository countLines,
            RetailStoreRepository stores) {
        this.guard = guard;
        this.locations = locations;
        this.counts = counts;
        this.countLines = countLines;
        this.stores = stores;
    }

    // --------------------------------------------------------------- Locations
    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations(UUID storeId) {
        return locations.findByOrganizationIdAndStoreIdAndDeletedFalseOrderByNameAsc(org(), storeId).stream()
                .map(this::map)
                .toList();
    }

    public LocationResponse createLocation(LocationRequest r) {
        stores.findByIdAndOrganizationIdAndDeletedFalse(r.storeId(), org())
                .orElseThrow(() -> notFound("Store not found"));
        String code = code(r.code());
        if (locations.existsByOrganizationIdAndStoreIdAndCodeIgnoreCaseAndDeletedFalse(org(), r.storeId(), code)) {
            conflict("Location code already exists for store");
        }
        RetailInventoryLocation e = new RetailInventoryLocation();
        e.setOrganizationId(org());
        e.setStoreId(r.storeId());
        e.setWarehouseId(r.warehouseId());
        e.setCode(code);
        e.setName(r.name());
        e.setLocationType(r.locationType() == null ? LocationType.SHELF : r.locationType());
        audit(e, true);
        return map(locations.save(e));
    }

    public LocationResponse updateLocation(UUID id, LocationRequest r) {
        RetailInventoryLocation e = locations
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Location not found"));
        e.setName(r.name());
        e.setWarehouseId(r.warehouseId());
        if (r.locationType() != null) {
            e.setLocationType(r.locationType());
        }
        audit(e, false);
        return map(locations.save(e));
    }

    public void deleteLocation(UUID id) {
        RetailInventoryLocation e = locations
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Location not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    // ------------------------------------------------------------- Stock counts
    @Transactional(readOnly = true)
    public List<StockCountResponse> listStockCounts() {
        return counts.findByOrganizationIdAndDeletedFalseOrderByCreatedAtDesc(org()).stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public StockCountResponse getStockCount(UUID id) {
        return map(loadCount(id));
    }

    public StockCountResponse createStockCount(StockCountRequest r) {
        stores.findByIdAndOrganizationIdAndDeletedFalse(r.storeId(), org())
                .orElseThrow(() -> notFound("Store not found"));
        if (r.locationId() != null) {
            locations
                    .findByIdAndOrganizationIdAndDeletedFalse(r.locationId(), org())
                    .orElseThrow(() -> notFound("Location not found"));
        }

        RetailStockCount e = new RetailStockCount();
        e.setOrganizationId(org());
        e.setStoreId(r.storeId());
        e.setLocationId(r.locationId());
        e.setCountType(r.countType() == null ? CountType.CYCLE : r.countType());
        e.setStatus("DRAFT");
        e.setNotes(r.notes());
        audit(e, true);
        e = counts.save(e);

        for (StockCountLineRequest lineReq : r.lines()) {
            saveLine(e.getId(), lineReq);
        }
        return map(e);
    }

    public StockCountResponse addLines(UUID countId, List<StockCountLineRequest> lineRequests) {
        RetailStockCount e = loadCount(countId);
        if ("COMPLETED".equals(e.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add lines to a completed stock count");
        }
        for (StockCountLineRequest lineReq : lineRequests) {
            saveLine(e.getId(), lineReq);
        }
        audit(e, false);
        return map(counts.save(e));
    }

    public StockCountResponse complete(UUID countId) {
        RetailStockCount e = loadCount(countId);
        if ("COMPLETED".equals(e.getStatus())) {
            return map(e);
        }
        e.setStatus("COMPLETED");
        e.setCountedAt(OffsetDateTime.now());
        audit(e, false);
        return map(counts.save(e));
    }

    private void saveLine(UUID countId, StockCountLineRequest r) {
        BigDecimal systemQty = r.systemQty() == null ? BigDecimal.ZERO : r.systemQty();
        BigDecimal countedQty = r.countedQty();
        RetailStockCountLine line = new RetailStockCountLine();
        line.setOrganizationId(org());
        line.setCountId(countId);
        line.setProductId(r.productId());
        line.setSystemQty(systemQty);
        line.setCountedQty(countedQty);
        line.setVarianceQty(countedQty.subtract(systemQty));
        audit(line, true);
        countLines.save(line);
    }

    private RetailStockCount loadCount(UUID id) {
        return counts.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Stock count not found"));
    }

    private LocationResponse map(RetailInventoryLocation e) {
        return new LocationResponse(
                e.getId(),
                e.getStoreId(),
                e.getWarehouseId(),
                e.getCode(),
                e.getName(),
                e.getLocationType(),
                e.getVersion());
    }

    private StockCountResponse map(RetailStockCount e) {
        List<StockCountLineResponse> lineResponses =
                countLines.findByOrganizationIdAndCountId(org(), e.getId()).stream()
                        .map(l -> new StockCountLineResponse(
                                l.getId(), l.getProductId(), l.getSystemQty(), l.getCountedQty(), l.getVarianceQty()))
                        .toList();
        return new StockCountResponse(
                e.getId(),
                e.getStoreId(),
                e.getLocationId(),
                e.getCountType(),
                e.getStatus(),
                e.getCountedAt(),
                e.getNotes(),
                lineResponses,
                e.getVersion());
    }

    private UUID org() {
        return guard.ensureEnabled();
    }

    private String code(String provided) {
        return provided.trim().toUpperCase(Locale.ROOT);
    }

    private void audit(com.flowledger.common.entity.AuditedEntity e, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) {
                e.setCreatedBy(u);
            }
            e.setUpdatedBy(u);
        });
    }

    private ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }

    private void conflict(String m) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}

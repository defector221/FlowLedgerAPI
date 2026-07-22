package com.flowledger.transport.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.purchase.entity.PurchaseReturn;
import com.flowledger.transport.dto.TransportDtos.LineRequest;
import com.flowledger.transport.dto.TransportDtos.ShipmentRequest;
import com.flowledger.transport.dto.TransportDtos.ShipmentResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adapters that attach reusable shipments to non-challan source documents.
 * Stock transfers currently post as inventory movements without a header document;
 * callers can still create a free-form shipment with sourceDocumentType=STOCK_TRANSFER.
 */
@Service
@Transactional
public class TransportSourceAdapterService {
    @PersistenceContext
    private EntityManager em;

    private final ShipmentService shipments;

    public TransportSourceAdapterService(ShipmentService shipments) {
        this.shipments = shipments;
    }

    public ShipmentResponse createFromPurchaseReturn(UUID purchaseReturnId, List<LineRequest> lines) {
        PurchaseReturn ret = em.find(PurchaseReturn.class, purchaseReturnId);
        if (ret == null || !TenantContext.getOrganizationId().equals(ret.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase return not found");
        }
        List<LineRequest> selected = lines == null
                ? List.of()
                : lines;
        ShipmentRequest request = new ShipmentRequest(
                "PURCHASE_RETURN",
                ret.getId(),
                true,
                null,
                null,
                null,
                null,
                "SUPPLIER",
                ret.getSupplierId(),
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                ret.getNotes(),
                List.of(),
                selected);
        return shipments.create(request);
    }

    public ShipmentResponse createForStockTransfer(
            UUID fromWarehouseId,
            UUID toWarehouseId,
            String remarks,
            List<LineRequest> lines) {
        if (fromWarehouseId == null || toWarehouseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from/to warehouse are required");
        }
        ShipmentRequest request = new ShipmentRequest(
                "STOCK_TRANSFER",
                toWarehouseId,
                true,
                null,
                null,
                null,
                fromWarehouseId,
                "WAREHOUSE",
                toWarehouseId,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                remarks,
                List.of(),
                lines == null ? List.of() : lines);
        return shipments.create(request);
    }
}

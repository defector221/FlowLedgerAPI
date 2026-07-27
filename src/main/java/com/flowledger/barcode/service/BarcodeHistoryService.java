package com.flowledger.barcode.service;

import com.flowledger.barcode.entity.ProductBarcodeHistory;
import com.flowledger.barcode.repository.ProductBarcodeHistoryRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BarcodeHistoryService {
    private final ProductBarcodeHistoryRepository repository;

    public BarcodeHistoryService(ProductBarcodeHistoryRepository repository) {
        this.repository = repository;
    }

    public void record(
            UUID organizationId,
            UUID productId,
            UUID barcodeId,
            String oldBarcode,
            String newBarcode,
            String operation,
            String reason) {
        ProductBarcodeHistory row = new ProductBarcodeHistory();
        row.setOrganizationId(organizationId);
        row.setProductId(productId);
        row.setBarcodeId(barcodeId);
        row.setOldBarcode(oldBarcode);
        row.setNewBarcode(newBarcode);
        row.setOperation(operation);
        row.setReason(reason);
        TenantContext.userId().ifPresent(row::setCreatedBy);
        repository.save(row);
    }
}

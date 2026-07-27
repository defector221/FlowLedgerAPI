package com.flowledger.barcode.repository;

import com.flowledger.barcode.entity.ProductBarcodeHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBarcodeHistoryRepository extends JpaRepository<ProductBarcodeHistory, UUID> {
    List<ProductBarcodeHistory> findByOrganizationIdAndProductIdOrderByCreatedAtDesc(
            UUID organizationId, UUID productId);
}

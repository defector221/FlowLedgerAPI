package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailProductBarcode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailProductBarcodeRepository extends JpaRepository<RetailProductBarcode, UUID> {
    Optional<RetailProductBarcode> findByOrganizationIdAndBarcode(UUID organizationId, String barcode);

    List<RetailProductBarcode> findByOrganizationIdAndProductId(UUID organizationId, UUID productId);
}

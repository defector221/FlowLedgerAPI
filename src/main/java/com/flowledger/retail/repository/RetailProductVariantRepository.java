package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailProductVariant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailProductVariantRepository extends JpaRepository<RetailProductVariant, UUID> {
    List<RetailProductVariant> findByOrganizationIdAndParentProductIdAndDeletedFalse(
            UUID organizationId, UUID parentProductId);

    Optional<RetailProductVariant> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    Optional<RetailProductVariant> findFirstByOrganizationIdAndBarcodeAndDeletedFalse(
            UUID organizationId, String barcode);
}

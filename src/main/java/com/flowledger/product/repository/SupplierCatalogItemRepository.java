package com.flowledger.product.repository;

import com.flowledger.product.entity.SupplierCatalogItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierCatalogItemRepository extends JpaRepository<SupplierCatalogItem, UUID> {
    List<SupplierCatalogItem>
            findByOrganizationIdAndSupplierIdAndActiveTrueAndDeletedFalseOrderBySupplierProductNameAsc(
                    UUID organizationId, UUID supplierId);

    List<SupplierCatalogItem> findByOrganizationIdAndProductIdAndDeletedFalseOrderByPreferredDesc(
            UUID organizationId, UUID productId);

    Optional<SupplierCatalogItem> findByOrganizationIdAndProductIdAndSupplierIdAndDeletedFalse(
            UUID organizationId, UUID productId, UUID supplierId);

    Optional<SupplierCatalogItem> findByIdAndOrganizationIdAndSupplierIdAndDeletedFalse(
            UUID id, UUID organizationId, UUID supplierId);

    Optional<SupplierCatalogItem>
            findByOrganizationIdAndProductIdAndSupplierIdAndActiveTrueAndDeletedFalse(
                    UUID organizationId, UUID productId, UUID supplierId);

    Optional<SupplierCatalogItem> findByOrganizationIdAndProductIdAndPreferredTrueAndActiveTrueAndDeletedFalse(
            UUID organizationId, UUID productId);

    List<SupplierCatalogItem>
            findByOrganizationIdAndProductIdAndPreferredTrueAndActiveTrueAndDeletedFalseAndIdNot(
                    UUID organizationId, UUID productId, UUID id);

    boolean existsByOrganizationIdAndSupplierSkuIgnoreCaseAndDeletedFalse(UUID organizationId, String supplierSku);

    long countByOrganizationIdAndProductIdAndDeletedFalse(UUID organizationId, UUID productId);

    @Query(
            """
            select item from SupplierCatalogItem item
            where item.organizationId = :organizationId
              and item.supplierId = :supplierId
              and item.active = true
              and item.deleted = false
              and (item.validFrom is null or item.validFrom <= :today)
              and (item.validTo is null or item.validTo >= :today)
            order by item.supplierProductName asc
            """)
    List<SupplierCatalogItem> findActiveForSupplier(
            @Param("organizationId") UUID organizationId,
            @Param("supplierId") UUID supplierId,
            @Param("today") LocalDate today);
}

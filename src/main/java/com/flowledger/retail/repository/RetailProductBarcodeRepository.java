package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailProductBarcode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailProductBarcodeRepository extends JpaRepository<RetailProductBarcode, UUID> {
    Optional<RetailProductBarcode> findByOrganizationIdAndBarcode(UUID organizationId, String barcode);

    @Query(
            """
            select b from RetailProductBarcode b
            where b.organizationId = :organizationId
              and lower(b.barcode) = lower(:barcode)
              and b.deletedAt is null
              and b.status = 'ACTIVE'
            """)
    Optional<RetailProductBarcode> findActiveByOrganizationIdAndBarcode(
            @Param("organizationId") UUID organizationId, @Param("barcode") String barcode);

    List<RetailProductBarcode> findByOrganizationIdAndProductIdAndDeletedAtIsNullOrderByPrimaryDescCreatedAtAsc(
            UUID organizationId, UUID productId);

    List<RetailProductBarcode>
            findByOrganizationIdAndProductIdAndDeletedAtIsNullAndStatusOrderByPrimaryDescCreatedAtAsc(
                    UUID organizationId, UUID productId, String status);

    Optional<RetailProductBarcode> findByIdAndOrganizationIdAndProductIdAndDeletedAtIsNull(
            UUID id, UUID organizationId, UUID productId);

    @Query(
            """
            select case when count(b) > 0 then true else false end
            from RetailProductBarcode b
            where b.organizationId = :organizationId
              and lower(b.barcode) = lower(:barcode)
              and b.deletedAt is null
              and b.status = 'ACTIVE'
            """)
    boolean existsActiveByOrganizationIdAndBarcode(
            @Param("organizationId") UUID organizationId, @Param("barcode") String barcode);

    List<RetailProductBarcode> findByOrganizationIdAndProductIdAndPrimaryTrueAndDeletedAtIsNull(
            UUID organizationId, UUID productId);
}

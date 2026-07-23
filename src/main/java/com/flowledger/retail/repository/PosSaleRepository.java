package com.flowledger.retail.repository;

import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.entity.PosSale;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosSaleRepository extends JpaRepository<PosSale, UUID> {
    Optional<PosSale> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    List<PosSale> findByOrganizationIdAndDeletedFalseOrderByCreatedAtDesc(UUID organizationId);

    List<PosSale> findByOrganizationIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            UUID organizationId, PosSaleStatus status);

    List<PosSale> findByOrganizationIdAndStatusAndCompletedAtBetweenAndDeletedFalse(
            UUID organizationId, PosSaleStatus status, OffsetDateTime from, OffsetDateTime to);

    List<PosSale> findByOrganizationIdAndStoreIdAndStatusAndCompletedAtBetweenAndDeletedFalse(
            UUID organizationId,
            UUID storeId,
            PosSaleStatus status,
            OffsetDateTime from,
            OffsetDateTime to);

    List<PosSale> findByOrganizationIdAndShiftIdAndDeletedFalse(UUID organizationId, UUID shiftId);
}

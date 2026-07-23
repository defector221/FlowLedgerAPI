package com.flowledger.retail.repository;

import com.flowledger.retail.domain.RetailEnums.ShiftStatus;
import com.flowledger.retail.entity.RetailShift;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailShiftRepository extends JpaRepository<RetailShift, UUID> {
    List<RetailShift> findByOrganizationIdAndDeletedFalseOrderByOpenedAtDesc(UUID organizationId);

    List<RetailShift> findByOrganizationIdAndStoreIdAndDeletedFalseOrderByOpenedAtDesc(
            UUID organizationId, UUID storeId);

    Optional<RetailShift> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    List<RetailShift> findByOrganizationIdAndCashierIdAndStatusAndDeletedFalse(
            UUID organizationId, UUID cashierId, ShiftStatus status);
}

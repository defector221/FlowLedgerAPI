package com.flowledger.finance.dimension.repository;

import com.flowledger.finance.dimension.entity.CostCenter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostCenterRepository extends JpaRepository<CostCenter, UUID> {
    Optional<CostCenter> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<CostCenter> findByOrganizationIdOrderByCodeAsc(UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}

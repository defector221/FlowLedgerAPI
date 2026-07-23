package com.flowledger.retail.repository;

import com.flowledger.retail.entity.PosReturn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosReturnRepository extends JpaRepository<PosReturn, UUID> {
    List<PosReturn> findByOrganizationIdAndDeletedFalseOrderByCreatedAtDesc(UUID organizationId);

    Optional<PosReturn> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);
}

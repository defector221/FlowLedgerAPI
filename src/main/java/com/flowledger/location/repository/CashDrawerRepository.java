package com.flowledger.location.repository;

import com.flowledger.location.entity.CashDrawer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashDrawerRepository extends JpaRepository<CashDrawer, UUID> {
    List<CashDrawer> findByOrganizationIdAndDeletedFalseOrderByDrawerNameAsc(UUID organizationId);

    List<CashDrawer> findByOrganizationIdAndTerminalIdAndDeletedFalseOrderByDrawerNameAsc(
            UUID organizationId, UUID terminalId);

    Optional<CashDrawer> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndTerminalIdAndDrawerCodeIgnoreCaseAndDeletedFalse(
            UUID organizationId, UUID terminalId, String drawerCode);
}

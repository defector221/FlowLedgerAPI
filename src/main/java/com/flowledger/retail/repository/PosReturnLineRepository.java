package com.flowledger.retail.repository;

import com.flowledger.retail.entity.PosReturnLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosReturnLineRepository extends JpaRepository<PosReturnLine, UUID> {
    List<PosReturnLine> findByOrganizationIdAndPosReturnId(UUID organizationId, UUID posReturnId);
}

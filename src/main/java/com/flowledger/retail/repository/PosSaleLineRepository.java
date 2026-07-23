package com.flowledger.retail.repository;

import com.flowledger.retail.entity.PosSaleLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosSaleLineRepository extends JpaRepository<PosSaleLine, UUID> {
    List<PosSaleLine> findByOrganizationIdAndPosSaleIdOrderByLineOrderAsc(UUID organizationId, UUID posSaleId);

    void deleteByPosSaleId(UUID posSaleId);
}

package com.flowledger.barcode.repository;

import com.flowledger.barcode.entity.ScanHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanHistoryRepository extends JpaRepository<ScanHistory, UUID> {
    Page<ScanHistory> findByOrganizationIdOrderByScannedAtDesc(UUID organizationId, Pageable pageable);
}

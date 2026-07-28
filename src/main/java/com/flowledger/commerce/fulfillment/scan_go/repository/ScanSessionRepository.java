package com.flowledger.commerce.fulfillment.scan_go.repository;

import com.flowledger.commerce.fulfillment.scan_go.domain.ScanSessionStatus;
import com.flowledger.commerce.fulfillment.scan_go.entity.ScanSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanSessionRepository extends JpaRepository<ScanSession, UUID> {
    List<ScanSession> findByStoreIdAndStatusIn(UUID storeId, List<ScanSessionStatus> statuses);

    Optional<ScanSession> findByCustomerIdAndStoreIdAndStatus(UUID customerId, UUID storeId, ScanSessionStatus status);

    Optional<ScanSession> findByIdAndCustomerId(UUID id, UUID customerId);
}

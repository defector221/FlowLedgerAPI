package com.flowledger.commerce.fulfillment.scan_go.repository;

import com.flowledger.commerce.fulfillment.scan_go.entity.ScanSessionItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanSessionItemRepository extends JpaRepository<ScanSessionItem, UUID> {
    List<ScanSessionItem> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<ScanSessionItem> findBySessionIdAndProductId(UUID sessionId, UUID productId);
}

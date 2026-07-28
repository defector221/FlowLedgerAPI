package com.flowledger.platform.event.bus;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlatformEventOutboxRepository extends JpaRepository<PlatformEventOutbox, UUID> {
    @Query("SELECT e FROM PlatformEventOutbox e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<PlatformEventOutbox> findUnpublished(Pageable pageable);
}

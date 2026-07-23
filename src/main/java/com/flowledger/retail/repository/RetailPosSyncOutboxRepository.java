package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailPosSyncOutbox;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailPosSyncOutboxRepository extends JpaRepository<RetailPosSyncOutbox, UUID> {
    Optional<RetailPosSyncOutbox> findByOrganizationIdAndClientIdAndClientTxnId(
            UUID organizationId, String clientId, String clientTxnId);
}

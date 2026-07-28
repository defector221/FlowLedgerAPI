package com.flowledger.commerce.fulfillment.scan_go.repository;

import com.flowledger.commerce.fulfillment.scan_go.entity.ScanExitToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanExitTokenRepository extends JpaRepository<ScanExitToken, UUID> {
    Optional<ScanExitToken> findByTokenHash(String tokenHash);

    Optional<ScanExitToken> findBySessionId(UUID sessionId);
}

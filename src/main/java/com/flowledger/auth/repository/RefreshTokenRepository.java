package com.flowledger.auth.repository;

import com.flowledger.auth.entity.RefreshToken;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    void deleteByUserId(UUID userId);
}

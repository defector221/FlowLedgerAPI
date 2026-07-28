package com.flowledger.ops.repository;

import com.flowledger.ops.entity.PlatformUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {
    Optional<PlatformUser> findByEmailIgnoreCase(String email);
}

package com.flowledger.ops.repository;

import com.flowledger.ops.entity.PlatformRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformRoleRepository extends JpaRepository<PlatformRole, UUID> {
    Optional<PlatformRole> findByCode(String code);
}

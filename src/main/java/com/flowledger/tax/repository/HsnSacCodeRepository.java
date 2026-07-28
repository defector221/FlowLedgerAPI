package com.flowledger.tax.repository;

import com.flowledger.tax.entity.HsnSacCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HsnSacCodeRepository extends JpaRepository<HsnSacCode, UUID> {
    Optional<HsnSacCode> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<HsnSacCode> findByOrganizationIdAndActiveTrue(UUID organizationId);

    List<HsnSacCode> findByOrganizationId(UUID organizationId);

    Optional<HsnSacCode> findByOrganizationIdAndCode(UUID organizationId, String code);
}

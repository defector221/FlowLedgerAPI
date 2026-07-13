package com.flowledger.product.repository;

import com.flowledger.product.entity.Unit;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
    Optional<Unit> findByIdAndOrganizationId(UUID id, UUID org);

    List<Unit> findBySystemUnitTrueOrOrganizationId(UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID org, String code);
}
